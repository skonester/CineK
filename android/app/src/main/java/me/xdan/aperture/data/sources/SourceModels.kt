package me.xdan.aperture.data.sources

enum class SourceSite(val displayName: String) {
    RIDOMOVIES("Ridomovies"), STREAMING_UNITY("StreamingUnity"),
    VIDSRC("Vidsrc"), FILEMOON("FileMoon"), DOOD("Dood")
}

enum class SourceMediaType { MOVIE, SHOW }

data class SourceQuery(
    val title: String,
    val type: SourceMediaType = SourceMediaType.MOVIE,
    val year: Int? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null
) {
    init {
        require(title.isNotBlank()) { "A title is required." }
        require(tmdbId == null || tmdbId > 0)
        require(imdbId == null || imdbId.matches(Regex("tt[0-9]+")))
        require((season == null) == (episode == null)) { "Supply both season and episode." }
        require(season == null || season > 0)
        require(episode == null || episode > 0)
        require(type == SourceMediaType.SHOW || season == null)
    }
}

data class SourceTitle(
    val site: SourceSite,
    val id: String,
    val title: String,
    val type: SourceMediaType,
    val pageUrl: String,
    val year: Int? = null,
    val posterUrl: String? = null,
    val imdbId: String? = null,
    val tmdbId: Int? = null
)

data class SourceSubtitle(val url: String, val label: String, val language: String? = null)

/** A resolved media address, never an HTML embed page. Re-resolve rather than persist signed URLs. */
data class ResolvedStream(
    val site: SourceSite,
    val url: String,
    val label: String,
    val mimeType: String? = null,
    val quality: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SourceSubtitle> = emptyList()
)

enum class SourceFailureKind { NETWORK, BLOCKED, NO_RESULTS, UNSUPPORTED, INVALID_RESPONSE, TIMEOUT }

data class SourceFailure(val site: SourceSite, val kind: SourceFailureKind, val message: String)
data class SourceResults(val streams: List<ResolvedStream>, val failures: List<SourceFailure> = emptyList())

class SourceException(val kind: SourceFailureKind, message: String) : Exception(message)

/** Origins are configurable because these services move. No account tokens or remote scripts are needed. */
data class ScraperConfig(
    val ridomoviesOrigin: String = "https://ridomovies.tv/",
    val streamingUnityOrigin: String = "https://streamingunity.fun/",
    val vidsrcOrigin: String = "https://vidsrc.to/",
    val vidsrcKeysUrl: String = "https://raw.githubusercontent.com/Ciarands/vidsrc-keys/main/keys.json",
    val providerTimeoutMs: Long = 30_000,
    val maxEmbedDepth: Int = 4
) {
    init {
        require(providerTimeoutMs > 0)
        require(maxEmbedDepth in 1..8)
        listOf(ridomoviesOrigin, streamingUnityOrigin, vidsrcOrigin, vidsrcKeysUrl).forEach {
            require(httpUrl(it) != null) { "Scraper endpoints must be HTTP(S) URLs." }
        }
    }
}
