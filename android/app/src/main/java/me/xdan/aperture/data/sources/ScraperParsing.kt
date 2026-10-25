package me.xdan.aperture.data.sources

import com.squareup.moshi.Moshi
import org.jsoup.Jsoup
import java.util.Base64

internal object SourceJson {
    private val adapter = Moshi.Builder().build().adapter(Any::class.java).lenient()
    fun parse(text: String): Any? = runCatching { adapter.fromJson(text) }.getOrNull()
}

internal fun Any?.obj(): Map<*, *> = this as? Map<*, *> ?: emptyMap<Any, Any>()
internal fun Any?.items(): List<Any?> = this as? List<*> ?: emptyList()
internal fun Map<*, *>.text(key: String): String? = when (val value = this[key]) {
    is String -> value.takeIf { it.isNotBlank() }
    is Number -> value.toLong().toString()
    else -> null
}
internal fun Map<*, *>.number(key: String): Int? = text(key)?.toIntOrNull()
internal fun sourceYear(value: String?): Int? = value?.let { Regex("(?:19|20)\\d{2}").find(it)?.value?.toIntOrNull() }
internal fun quality(value: String?): Int? = value?.let {
    if (it.contains("4k", true)) 2160 else Regex("(?i)(\\d{3,4})p?").find(it)?.groupValues?.get(1)?.toIntOrNull()
}

internal fun inertia(page: ScraperPage): Map<*, *> {
    val root = if (page.body.trimStart().startsWith('{')) SourceJson.parse(page.body).obj() else {
        val document = Jsoup.parse(page.body, page.url)
        SourceJson.parse(document.selectFirst("[data-page]")?.attr("data-page").orEmpty()).obj()
    }
    return root["props"].obj().ifEmpty {
        throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Source page has no catalog data.")
    }
}

/** Read a literal without executing scripts. The returned index is immediately after its closing quote. */
internal fun jsString(text: String, start: Int): Pair<String, Int>? {
    val quote = text.getOrNull(start)?.takeIf { it == '\'' || it == '"' } ?: return null
    val result = StringBuilder()
    var index = start + 1
    while (index < text.length) {
        val char = text[index++]
        if (char == quote) return result.toString() to index
        if (char != '\\') { result.append(char); continue }
        when (val escaped = text.getOrNull(index++) ?: return null) {
            'n' -> result.append('\n')
            'r' -> result.append('\r')
            't' -> result.append('\t')
            '\n' -> Unit
            'u', 'x' -> {
                val count = if (escaped == 'u') 4 else 2
                val end = index + count
                val code = text.substring(index, end.coerceAtMost(text.length)).takeIf { it.length == count }?.toIntOrNull(16) ?: return null
                result.append(code.toChar()); index = end
            }
            else -> result.append(escaped)
        }
    }
    return null
}

internal fun literalAfter(text: String, marker: String): Any? {
    val match = Regex(marker).find(text) ?: return null
    var start = match.range.last + 1
    while (text.getOrNull(start)?.isWhitespace() == true) start++
    if (text.getOrNull(start) == '\'' || text.getOrNull(start) == '"') return jsString(text, start)?.first
    val open = text.getOrNull(start) ?: return null
    if (open != '{' && open != '[') return null
    val stack = ArrayDeque<Char>()
    var index = start
    while (index < text.length) {
        when (val char = text[index]) {
            '\'', '"' -> { index = jsString(text, index)?.second ?: return null; continue }
            '{', '[' -> { if (stack.size >= 64) return null; stack.addLast(char) }
            '}', ']' -> {
                if (stack.isEmpty() || stack.removeLast() != if (char == '}') '{' else '[') return null
                if (stack.isEmpty()) {
                    // Moshi accepts single quotes/unquoted keys; trim JavaScript object trailing commas.
                    val value = removeTrailingCommas(text.substring(start, index + 1))
                    return SourceJson.parse(value)
                }
            }
        }
        index++
    }
    return null
}

private fun removeTrailingCommas(text: String): String {
    val result = StringBuilder()
    var index = 0
    while (index < text.length) {
        if (text[index] == '\'' || text[index] == '"') {
            val end = jsString(text, index)?.second ?: return text
            result.append(text, index, end); index = end; continue
        }
        if (text[index] == ',') {
            var next = index + 1
            while (text.getOrNull(next)?.isWhitespace() == true) next++
            if (text.getOrNull(next) in listOf('}', ']')) { index++; continue }
        }
        result.append(text[index++])
    }
    return result.toString()
}

/** Dean Edwards P.A.C.K.E.R decoding, with bounded dictionaries/output and no JavaScript engine. */
internal object PackedScript {
    fun unpack(script: String): String {
        var text = script
        repeat(3) {
            val unpacked = unpackOnce(text) ?: return text
            if (unpacked == text) return text
            text = unpacked
        }
        return text
    }

    private fun unpackOnce(script: String): String? {
        if (!script.contains("function(p,a,c,k,")) return null
        val start = Regex("}\\s*\\(\\s*(['\"])").find(script)?.let { it.range.last } ?: return null
        val (payload, end) = jsString(script, start) ?: return null
        val numbers = Regex("^\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*").find(script.substring(end)) ?: return null
        val radix = numbers.groupValues[1].toIntOrNull()?.takeIf { it in 2..62 } ?: return null
        val count = numbers.groupValues[2].toIntOrNull()?.takeIf { it in 0..10_000 } ?: return null
        val (dictionary, _) = jsString(script, end + numbers.value.length) ?: return null
        val words = dictionary.split('|')
        val replacements = (0 until minOf(count, words.size)).associate { encode(it, radix) to words[it] }
        var size = 0
        return Regex("\\b[0-9A-Za-z]+\\b").replace(payload) { match ->
            val word = replacements[match.value]?.takeIf(String::isNotEmpty) ?: match.value
            size += word.length
            if (size > OkHttpScraperClient.MaxResponseBytes) throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Packed source is too large.")
            word
        }
    }

    private fun encode(number: Int, radix: Int): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        if (number < radix) return alphabet[number].toString()
        return encode(number / radix, radix) + alphabet[number % radix]
    }
}

internal object EmbedParsing {
    fun directStream(url: String, site: SourceSite, referer: String): ResolvedStream? =
        mimeType(url, null)?.let { stream(site, url, referer, site.displayName, it) }

    fun expanded(html: String): String {
        val document = Jsoup.parse(html)
        val scripts = document.select("script").map { it.data() }
        val result = StringBuilder(html)
        for (script in scripts) {
            if (script.contains("function(p,a,c,k,")) result.append('\n').append(PackedScript.unpack(script))
            Regex("\\batob\\(\\s*(['\"])").findAll(script).take(8).forEach { match ->
                val literal = jsString(script, match.range.last)?.first ?: return@forEach
                val decoded = runCatching { Base64.getDecoder().decode(literal).toString(Charsets.UTF_8) }.getOrNull()
                if (decoded != null && decoded.length <= 256_000) result.append('\n').append(decoded)
            }
            // Next.js/server data can contain escaped iframe markup inside JSON strings.
            Regex("(['\"])(?:<iframe|https?[:\\\\/])").findAll(script).take(32).forEach { match ->
                jsString(script, match.range.first)?.first?.let { result.append('\n').append(it) }
            }
        }
        if (result.length > OkHttpScraperClient.MaxResponseBytes * 2) throw SourceException(
            SourceFailureKind.INVALID_RESPONSE, "Expanded source is too large.")
        return result.toString()
    }

    fun iframes(page: ScraperPage): List<String> = Jsoup.parse(expanded(page.body), page.url)
        .select("iframe[src], iframe[data-src]").mapNotNull {
            resolveUrl(page.url, it.attr("src").ifBlank { it.attr("data-src") })
        }.distinct().take(12)

    fun streams(page: ScraperPage, site: SourceSite): List<ResolvedStream> {
        if (page.body.trimStart().startsWith("#EXTM3U")) return listOf(stream(site, page.url, page.url, "HLS"))
        val text = expanded(page.body)
        val document = Jsoup.parse(text, page.url)
        val found = mutableListOf<ResolvedStream>()
        val subtitles = mutableListOf<SourceSubtitle>()
        fun add(value: String?, label: String? = null, type: String? = null) {
            val url = value?.let { resolveUrl(page.url, it) } ?: return
            val path = httpUrl(url)?.encodedPath.orEmpty()
            val mime = mimeType(url, type)
            if (mime == null && !path.endsWith(".m3u8", true) && !path.endsWith(".mp4", true) && !path.endsWith(".webm", true)) return
            found += stream(site, url, page.url, label ?: site.displayName, type)
        }
        fun visit(value: Any?, depth: Int = 0) {
            if (depth > 16) return
            when (value) {
                is Map<*, *> -> {
                    val url = value.text("file") ?: value.text("src") ?: value.text("url")
                    val kind = value.text("kind")
                    if (kind in listOf("captions", "subtitles") || url?.substringBefore('?')?.endsWith(".vtt", true) == true ||
                        url?.substringBefore('?')?.endsWith(".srt", true) == true) {
                        url?.let { resolveUrl(page.url, it) }?.let {
                            subtitles += SourceSubtitle(it, value.text("label") ?: "Subtitles", value.text("srclang") ?: value.text("language"))
                        }
                    } else add(url, value.text("label") ?: value.text("quality"), value.text("type"))
                    listOf("sources", "playlist", "tracks", "result", "data").forEach { visit(value[it], depth + 1) }
                }
                is List<*> -> value.take(100).forEach { visit(it, depth + 1) }
            }
        }
        if (text.trimStart().let { it.startsWith('{') || it.startsWith('[') }) visit(SourceJson.parse(text))
        Regex("\\.setup\\s*\\(|\\b(?:sources|playlist|tracks)\\s*[:=]\\s*").findAll(text).take(32).forEach {
            visit(literalAfter(text.substring(it.range.first), Regex.escape(it.value)))
        }
        document.select("video[src], video source[src]").forEach { add(it.attr("src"), type = it.attr("type")) }
        document.select("track[src]").forEach { element ->
            resolveUrl(page.url, element.attr("src"))?.let {
                subtitles += SourceSubtitle(it, element.attr("label").ifBlank { "Subtitles" }, element.attr("srclang").ifBlank { null })
            }
        }
        Regex("(?i)\\b(?:file|src)\\s*[:=]\\s*(['\"])").findAll(text).take(100).forEach {
            add(jsString(text, it.range.last)?.first)
        }
        return found.distinctBy { it.url }.map { it.copy(subtitles = subtitles.distinctBy(SourceSubtitle::url)) }
    }

    fun stream(site: SourceSite, url: String, referer: String, label: String, type: String? = null): ResolvedStream =
        ResolvedStream(site, url, label, mimeType(url, type), quality(label),
            mapOf("User-Agent" to ScraperUserAgent, "Referer" to referer))

    private fun mimeType(url: String, hint: String?): String? = when {
        hint?.contains("mpegurl", true) == true || hint.equals("hls", true) || httpUrl(url)?.encodedPath?.endsWith(".m3u8", true) == true -> "application/x-mpegURL"
        hint.equals("video/mp4", true) || hint.equals("mp4", true) || httpUrl(url)?.encodedPath?.endsWith(".mp4", true) == true -> "video/mp4"
        httpUrl(url)?.encodedPath?.endsWith(".webm", true) == true -> "video/webm"
        else -> null
    }
}
