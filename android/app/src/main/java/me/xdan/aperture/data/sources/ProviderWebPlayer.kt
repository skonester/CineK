package me.xdan.aperture.data.sources

/** A provider page for WebView, kept separate from resolved Media3 stream URLs. */
data class ProviderWebTarget(val site: SourceSite, val url: String, val title: String)

internal fun providerWebTarget(
    site: SourceSite, query: SourceQuery, config: ScraperConfig = ScraperConfig()
): ProviderWebTarget? {
    val url = when (site) {
        SourceSite.VIDSRC -> {
            val id = query.tmdbId?.toString() ?: query.imdbId ?: return null
            val path = when (query.type) {
                SourceMediaType.MOVIE -> "embed/movie/$id"
                SourceMediaType.SHOW -> {
                    val season = query.season ?: return null
                    val episode = query.episode ?: return null
                    "embed/tv/$id/$season/$episode"
                }
            }
            endpoint(config.vidsrcOrigin, path)
        }
        // Search is also blocked, so do not invent a movie slug or claim an exact title match.
        SourceSite.RIDOMOVIES -> config.ridomoviesOrigin
        else -> return null
    }
    val title = query.title + if (query.season != null) " · S${query.season}E${query.episode}" else ""
    return ProviderWebTarget(site, url, title)
}

internal fun browserFallback(failure: SourceFailure, query: SourceQuery?): ProviderWebTarget? {
    if (query == null) return null
    val supported = when (failure.site) {
        SourceSite.VIDSRC -> failure.kind == SourceFailureKind.UNSUPPORTED || failure.kind == SourceFailureKind.BLOCKED
        SourceSite.RIDOMOVIES -> failure.kind == SourceFailureKind.BLOCKED
        else -> false
    }
    return if (supported) providerWebTarget(failure.site, query) else null
}

/** Only the provider may navigate the top-level page; its HTTPS iframe player can use other hosts. */
internal fun providerNavigationAllowed(target: ProviderWebTarget, address: String, mainFrame: Boolean): Boolean {
    val url = httpUrl(address) ?: return false
    if (url.scheme != "https") return false
    if (!mainFrame) return true
    val origin = httpUrl(target.url) ?: return false
    val host = url.host.removePrefix("www.")
    return host == origin.host.removePrefix("www.") ||
        (target.site == SourceSite.VIDSRC && host == "vidsrc.sh")
}
