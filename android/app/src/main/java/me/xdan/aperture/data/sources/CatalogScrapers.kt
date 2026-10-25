package me.xdan.aperture.data.sources

import org.jsoup.Jsoup
import java.text.Normalizer

internal interface CatalogScraper {
    suspend fun search(query: SourceQuery): List<SourceTitle>
    suspend fun playbackPage(query: SourceQuery): ScraperPage
}

internal fun selectTitle(titles: List<SourceTitle>, query: SourceQuery): SourceTitle {
    val candidates = titles.filter { it.type == query.type }
    query.imdbId?.let { id -> candidates.firstOrNull { it.imdbId.equals(id, true) }?.let { return it } }
    query.tmdbId?.let { id -> candidates.firstOrNull { it.tmdbId == id }?.let { return it } }
    val matching = candidates.filter { normalizeTitle(it.title) == normalizeTitle(query.title) }
        .filter { query.year == null || it.year == null || it.year == query.year }
    // Avoid silently selecting the wrong remake when the catalog supplies several candidates.
    return matching.singleOrNull() ?: matching.singleOrNull { it.year == query.year && query.year != null }
        ?: throw SourceException(SourceFailureKind.NO_RESULTS, "No unambiguous matching title was found.")
}

private fun normalizeTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

internal class RidomoviesScraper(private val http: ScraperHttpClient, private val config: ScraperConfig) : CatalogScraper {
    override suspend fun search(query: SourceQuery): List<SourceTitle> {
        val page = http.get(queryUrl(endpoint(config.ridomoviesOrigin, "api/search"), "q", query.title), config.ridomoviesOrigin)
        val root = SourceJson.parse(page.body).obj()
        val data = root["data"]
        val entries = if (data is List<*>) data else data.obj()["items"].items().ifEmpty { data.obj()["data"].items() }
        if (data == null) throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Ridomovies search data is missing.")
        return entries.take(100).mapNotNull { entry ->
            val item = entry.obj()
            val slug = item.text("slug") ?: return@mapNotNull null
            val title = item.text("title") ?: item.text("name") ?: return@mapNotNull null
            val type = when (item.text("type")?.lowercase()) {
                "tv", "show", "series" -> SourceMediaType.SHOW
                "movie", "film" -> SourceMediaType.MOVIE
                else -> query.type
            }
            val prefix = if (type == SourceMediaType.SHOW) "tv/" else "movie/"
            val url = resolveUrl(page.url, "/$prefix$slug") ?: return@mapNotNull null
            // Provider-controlled slugs must remain on the catalog host.
            if (httpUrl(url)?.host != httpUrl(page.url)?.host) return@mapNotNull null
            SourceTitle(SourceSite.RIDOMOVIES, item.text("id") ?: slug, title, type, url,
                sourceYear(item.text("release_date") ?: item.text("year")),
                (item.text("poster_path") ?: item.text("poster"))?.let { resolveUrl(page.url, it) },
                item.text("imdb_id"), item.number("tmdb_id"))
        }.distinctBy { it.pageUrl }
    }

    override suspend fun playbackPage(query: SourceQuery): ScraperPage {
        val title = selectTitle(search(query), query)
        val address = if (query.type == SourceMediaType.SHOW) {
            val season = query.season ?: throw SourceException(SourceFailureKind.NO_RESULTS, "Select a season and episode.")
            "${title.pageUrl.trimEnd('/')}/season-$season/episode-${query.episode}"
        } else title.pageUrl
        return http.get(address, config.ridomoviesOrigin)
    }
}

internal class StreamingUnityScraper(private val http: ScraperHttpClient, private val config: ScraperConfig) : CatalogScraper {
    override suspend fun search(query: SourceQuery): List<SourceTitle> {
        val page = http.get(queryUrl(endpoint(config.streamingUnityOrigin, "en/search"), "q", query.title), config.streamingUnityOrigin)
        val props = inertia(page)
        val data = props["titles"]
        val entries = if (data is List<*>) data else data.obj()["data"].items()
        if (data == null) throw SourceException(SourceFailureKind.INVALID_RESPONSE, "StreamingUnity search data is missing.")
        return entries.take(100).mapNotNull { entry ->
            val item = entry.obj()
            val id = item.text("id") ?: return@mapNotNull null
            val slug = item.text("slug") ?: return@mapNotNull null
            val name = item.text("name") ?: item.text("title") ?: return@mapNotNull null
            val address = resolveUrl(page.url, "/en/titles/$id-$slug") ?: return@mapNotNull null
            if (httpUrl(address)?.host != httpUrl(page.url)?.host) return@mapNotNull null
            val poster = item["images"].items().map(Any?::obj).firstOrNull { it.text("type") == "poster" }
            val posterUrl = poster?.text("original_url_field") ?: poster?.text("url")
            SourceTitle(SourceSite.STREAMING_UNITY, id, name,
                if (item.text("type") in listOf("tv", "show", "series")) SourceMediaType.SHOW else SourceMediaType.MOVIE,
                address, sourceYear(item.text("release_date") ?: item.text("last_air_date")),
                posterUrl?.let { resolveUrl(page.url, it) }, item.text("imdb_id"), item.number("tmdb_id"))
        }.distinctBy { it.id }
    }

    override suspend fun playbackPage(query: SourceQuery): ScraperPage {
        val title = selectTitle(search(query), query)
        var details = http.get(title.pageUrl, config.streamingUnityOrigin)
        var props = inertia(details)
        var iframe = endpoint(details.url, "/en/iframe/${title.id}")
        if (query.type == SourceMediaType.SHOW) {
            val seasonNumber = query.season ?: throw SourceException(SourceFailureKind.NO_RESULTS, "Select a season and episode.")
            val season = props["title"].obj()["seasons"].items().map(Any?::obj)
                .firstOrNull { it.number("number") == seasonNumber }
                ?: throw SourceException(SourceFailureKind.NO_RESULTS, "The requested season is unavailable.")
            val seasonId = season.text("id") ?: throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Season ID is missing.")
            if (props["loadedSeason"].obj().text("id") != seasonId) {
                details = http.get(queryUrl(title.pageUrl, "season_id", seasonId), title.pageUrl)
                props = inertia(details)
            }
            val loaded = props["loadedSeason"].obj()
            if (loaded.text("id") != seasonId) throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Source returned a different season.")
            val episode = loaded["episodes"].items().map(Any?::obj).firstOrNull { it.number("number") == query.episode }
                ?: throw SourceException(SourceFailureKind.NO_RESULTS, "The requested episode is unavailable.")
            val episodeId = episode.text("id") ?: throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Episode ID is missing.")
            iframe = queryUrl(iframe, "episode_id", episodeId)
        }
        // The iframe route returns the Vixcloud embed; the common resolver handles that host.
        return http.get(iframe, details.url)
    }
}
