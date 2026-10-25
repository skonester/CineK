package me.xdan.aperture.data.sources

import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.util.Base64

internal class HostResolvers(private val http: ScraperHttpClient, private val config: ScraperConfig) {
    suspend fun resolve(url: String, site: SourceSite, referer: String? = null): SourceResults {
        EmbedParsing.directStream(url, site, referer ?: url)?.let { return SourceResults(listOf(it)) }
        return resolvePage(http.get(url, referer), site, 0, mutableSetOf())
    }

    suspend fun resolvePage(page: ScraperPage, site: SourceSite, depth: Int = 0, visited: MutableSet<String> = mutableSetOf()): SourceResults {
        if (depth >= config.maxEmbedDepth || visited.size >= 32 || !visited.add(page.url)) throw SourceException(
            SourceFailureKind.UNSUPPORTED, "Embed chain is too deep, too large, or cyclic.")
        val direct = EmbedParsing.streams(page, site)
        if (direct.isNotEmpty()) return SourceResults(direct)
        val host = requireNotNull(httpUrl(page.url)).host
        val detected = when {
            host.contains("dood", true) -> SourceSite.DOOD
            host.contains("filemoon", true) -> SourceSite.FILEMOON
            host.contains("vidsrc", true) || host.contains("vsembed", true) -> SourceSite.VIDSRC
            else -> site
        }
        if (detected == SourceSite.DOOD && page.body.contains("/pass_md5/")) return SourceResults(listOf(dood(page)))
        if (page.body.contains("window.masterPlaylist")) return SourceResults(listOf(vixcloud(page)))
        val children = EmbedParsing.iframes(page).toMutableList()
        if (detected == SourceSite.VIDSRC && children.isEmpty()) children += vidsrc(page)
        if (children.isEmpty()) throw SourceException(SourceFailureKind.UNSUPPORTED, "No supported media or embed was found.")
        val streams = mutableListOf<ResolvedStream>()
        val failures = mutableListOf<SourceFailure>()
        for (child in children.take(8)) {
            try {
                val directChild = EmbedParsing.directStream(child, detected, page.url)
                if (directChild != null) streams += directChild else {
                    val result = resolvePage(http.get(child, page.url), detected, depth + 1, visited)
                    streams += result.streams; failures += result.failures
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failures += failure(detected, e) }
        }
        return SourceResults(streams.distinctBy { it.url to it.headers }, failures)
    }

    private suspend fun dood(page: ScraperPage): ResolvedStream {
        val path = Regex("/pass_md5/[^'\"\\s<>]+").find(page.body)?.value
            ?: throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Dood token path is missing.")
        val passUrl = endpoint(page.url, path)
        val prefix = http.get(passUrl, page.url).body.trim()
        if (httpUrl(prefix) == null) throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Dood returned an invalid media address.")
        val token = path.substringAfterLast('/').takeIf { it.isNotEmpty() }
            ?: throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Dood token is empty.")
        val suffix = java.util.UUID.randomUUID().toString().replace("-", "").take(10)
        val url = queryUrl(queryUrl(prefix + suffix, "token", token), "expiry", System.currentTimeMillis().toString())
        return EmbedParsing.stream(SourceSite.DOOD, url, page.url, Jsoup.parse(page.body).title().ifBlank { "Dood" }, "video/mp4")
    }

    private fun vixcloud(page: ScraperPage): ResolvedStream {
        val master = literalAfter(page.body, "window\\.masterPlaylist\\s*=\\s*").obj()
        val value = master.text("url") ?: throw SourceException(SourceFailureKind.INVALID_RESPONSE, "StreamingUnity playlist is missing.")
        var url = resolveUrl(page.url, value) ?: throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Invalid playlist URL.")
        master["params"].obj().forEach { (key, entry) ->
            if (key is String && entry is String && entry.isNotEmpty()) url = queryUrl(url, key, entry)
        }
        if (Regex("window\\.canPlayFHD\\s*=\\s*true").containsMatchIn(page.body)) url = queryUrl(url, "h", "1")
        return EmbedParsing.stream(SourceSite.STREAMING_UNITY, url, page.url, "StreamingUnity", "hls")
    }

    /** Flixvision's vidsrc.to AJAX flow. Public protocol keys are fetched as data, never executed. */
    private suspend fun vidsrc(page: ScraperPage): List<String> {
        val document = Jsoup.parse(page.body, page.url)
        val id = document.selectFirst("ul.episodes li a[data-id], [data-id][data-episode]")?.attr("data-id")
            ?: throw SourceException(SourceFailureKind.UNSUPPORTED, "This Vidsrc page format is unsupported.")
        val keys = SourceJson.parse(http.get(config.vidsrcKeysUrl, null).body).obj()
        val encrypt = keys["encrypt"].items().firstOrNull() as? String
        val decrypt = keys["decrypt"].items().firstOrNull() as? String
        if (encrypt.isNullOrEmpty() || decrypt.isNullOrEmpty()) throw SourceException(
            SourceFailureKind.INVALID_RESPONSE, "Vidsrc protocol keys are unavailable.")
        fun token(value: String): String = Base64.getUrlEncoder().encodeToString(rc4(encrypt, value.toByteArray(Charsets.UTF_8)))
        val sourceUrl = queryUrl(endpoint(page.url, "/ajax/embed/episode/${segment(id)}/sources"), "token", token(id))
        val sources = SourceJson.parse(http.get(sourceUrl, page.url).body).obj()["result"].items()
        val links = mutableListOf<String>()
        var lastError: Exception? = null
        for (entry in sources.take(8)) {
            try {
                val sourceId = entry.obj().text("id") ?: continue
                val source = queryUrl(endpoint(page.url, "/ajax/embed/source/${segment(sourceId)}"), "token", token(sourceId))
                val encrypted = SourceJson.parse(http.get(source, page.url).body).obj()["result"].obj().text("url") ?: continue
                val plain = if (httpUrl(encrypted) != null) encrypted else {
                    runCatching {
                        val bytes = Base64.getUrlDecoder().decode(encrypted)
                        URLDecoder.decode(rc4(decrypt, bytes).toString(Charsets.UTF_8), "UTF-8")
                    }.getOrNull() ?: continue
                }
                httpUrl(plain)?.let { links += it.toString() }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { lastError = e }
        }
        if (links.isEmpty() && lastError != null) throw lastError
        return links.distinct()
    }
}

internal fun segment(value: String): String {
    if (!value.matches(Regex("[A-Za-z0-9_-]{1,256}"))) throw SourceException(
        SourceFailureKind.INVALID_RESPONSE, "Source supplied an invalid identifier.")
    return value
}

internal fun rc4(key: String, bytes: ByteArray): ByteArray {
    require(key.isNotEmpty())
    val keyBytes = key.toByteArray(Charsets.UTF_8)
    val state = IntArray(256) { it }
    var j = 0
    for (i in state.indices) {
        j = (j + state[i] + (keyBytes[i % keyBytes.size].toInt() and 255)) and 255
        val swap = state[i]; state[i] = state[j]; state[j] = swap
    }
    var i = 0; j = 0
    return ByteArray(bytes.size) { index ->
        i = (i + 1) and 255; j = (j + state[i]) and 255
        val swap = state[i]; state[i] = state[j]; state[j] = swap
        (bytes[index].toInt() xor state[(state[i] + state[j]) and 255]).toByte()
    }
}

internal fun failure(site: SourceSite, error: Exception): SourceFailure = if (error is SourceException) {
    SourceFailure(site, error.kind, error.message.orEmpty())
} else SourceFailure(site, SourceFailureKind.INVALID_RESPONSE, "The source response could not be parsed.")
