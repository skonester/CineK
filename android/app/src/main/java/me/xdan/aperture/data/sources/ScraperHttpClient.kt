package me.xdan.aperture.data.sources

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val ScraperUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

data class ScraperPage(val url: String, val body: String, val contentType: String = "text/html")

fun interface ScraperHttpClient {
    suspend fun get(url: String, referer: String?): ScraperPage
}

/** Uses its own client: metadata clients can log URLs containing signed playback tokens. */
class OkHttpScraperClient(private val client: OkHttpClient) : ScraperHttpClient {
    override suspend fun get(url: String, referer: String?): ScraperPage {
        val address = httpUrl(url) ?: throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Invalid source URL.")
        val request = Request.Builder().url(address).header("User-Agent", ScraperUserAgent)
            .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            .apply { referer?.let(::httpUrl)?.let { header("Referer", it.toString()) } }.build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        if (e is java.io.InterruptedIOException) SourceException(SourceFailureKind.TIMEOUT, "Source request timed out.")
                        else SourceException(SourceFailureKind.NETWORK, "Source request failed."))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val page = response.use {
                            if (it.code in listOf(401, 403, 429)) throw SourceException(
                                SourceFailureKind.BLOCKED, "Source refused the request (HTTP ${it.code}).")
                            if (!it.isSuccessful) throw SourceException(
                                SourceFailureKind.NETWORK, "Source returned HTTP ${it.code}.")
                            val body = it.body ?: throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Empty source response.")
                            val bytes = body.byteStream().use { stream ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                while (output.size() <= MaxResponseBytes) {
                                    val count = stream.read(buffer, 0, minOf(buffer.size, MaxResponseBytes + 1 - output.size()))
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            }
                            if (bytes.size > MaxResponseBytes) throw SourceException(
                                SourceFailureKind.INVALID_RESPONSE, "Source response exceeds 4 MB.")
                            val text = bytes.toString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
                            if (text.contains("cf-chl-", true) || text.contains("Just a moment...", true)) {
                                throw SourceException(SourceFailureKind.BLOCKED, "Source requires browser verification.")
                            }
                            ScraperPage(it.request.url.toString(), text, body.contentType()?.toString().orEmpty())
                        }
                        if (continuation.isActive) continuation.resume(page)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(
                            if (e is SourceException) e else SourceException(SourceFailureKind.NETWORK, "Could not read source response."))
                    }
                }
            })
        }
    }

    companion object { const val MaxResponseBytes = 4 * 1024 * 1024 }
}

internal fun httpUrl(value: String): HttpUrl? = value.trim().toHttpUrlOrNull()?.takeIf {
    it.username.isEmpty() && it.password.isEmpty()
}

internal fun resolveUrl(base: String, value: String): String? = httpUrl(base)?.resolve(value.trim())?.let {
    httpUrl(it.toString())?.toString()
}

internal fun endpoint(origin: String, path: String): String = resolveUrl(origin, path)
    ?: throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Invalid source address.")

internal fun queryUrl(url: String, name: String, value: String): String = requireNotNull(httpUrl(url))
    .newBuilder().addQueryParameter(name, value).build().toString()
