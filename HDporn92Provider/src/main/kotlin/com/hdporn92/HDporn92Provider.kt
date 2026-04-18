package com.hdporn92

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class HDporn92Provider : MainAPI() {
    override var name = "HDporn92"
    override var mainUrl = "https://hdporn92.com"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Others)


    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest",
        "$mainUrl/category/naughty-america/" to "Naughty America",
        "$mainUrl/category/brazzers/" to "Brazzers",
        "$mainUrl/category/blacked/" to "Blacked",
        "$mainUrl/category/bangbros/" to "Bangbros",
        "$mainUrl/category/mylf/" to "MYLF",
        "$mainUrl/category/tushy/" to "Tushy",
        "$mainUrl/category/hardx/" to "HardX",
        "$mainUrl/category/reality-kings/" to "Reality Kings",
        "$mainUrl/category/nubiles-porn/" to "Nubiles Porn"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            val base = request.data.trimEnd('/')
            "$base/page/$page/"
        } else {
            request.data
        }
        
        val doc = app.get(url, headers = headers).document
        val items = doc.select("article.thumb-block").mapNotNull { el ->
            val a = el.selectFirst("a") ?: return@mapNotNull null
            val href = a.attr("href").ifBlank { return@mapNotNull null }
            val title = a.attr("title").ifBlank { el.selectFirst("img")?.attr("alt") }?.trim() ?: return@mapNotNull null
            val poster = el.selectFirst("img")?.attr("src") 
                ?: el.selectFirst("img")?.attr("data-src")
                ?: el.attr("data-main-thumb")
                ?: return@mapNotNull null
            
            if (title.isBlank() || !href.startsWith(mainUrl)) return@mapNotNull null
            
            newMovieSearchResponse(title, href, TvType.Movie) { 
                posterUrl = poster 
            }
        }
        return newHomePageResponse(request.name, items, page < 10)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}", headers = headers).document
        return doc.select("article.thumb-block").mapNotNull { el ->
            val a = el.selectFirst("a") ?: return@mapNotNull null
            val href = a.attr("href").ifBlank { return@mapNotNull null }
            val title = a.attr("title").ifBlank { el.selectFirst("img")?.attr("alt") }?.trim() ?: return@mapNotNull null
            val poster = el.selectFirst("img")?.attr("src") 
                ?: el.selectFirst("img")?.attr("data-src")
                ?: el.attr("data-main-thumb")
                ?: return@mapNotNull null
            
            if (title.isBlank()) return@mapNotNull null
            
            newMovieSearchResponse(title, href, TvType.Movie) { 
                posterUrl = poster 
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = headers).document
        
        val title = doc.selectFirst("h1")?.text()?.trim() 
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: url.substringAfterLast("/").replace("-", " ")
            
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") 
            ?: doc.selectFirst(".post-thumbnail img")?.attr("src")
            
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
            ?: doc.selectFirst(".entry-content p")?.text()
            
        // Find iframe embed URL
        val embedUrl = doc.select("iframe[src]")
    .map { it.attr("src") }
    .firstOrNull { it.contains("minochinos.com") || it.contains("vidhide") }
    ?: ""

        return newMovieLoadResponse(title, url, TvType.Movie, embedUrl) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    if (data.isBlank()) return false

    if (data.contains(".m3u8") || data.contains(".mp4")) {
        callback(
            newExtractorLink(name, name, data, ExtractorLinkType.M3U8) {
                this.quality = Qualities.P1080.value
            }
        )
        return true
    }

    val embedHtml = app.get(
        data,
        headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
            "Referer" to mainUrl
        )
    ).text

    // file_id from cookie setter
    val fileId = Regex("""file_id['"]\s*,\s*['"](\d+)['"]""")
        .find(embedHtml)?.groupValues?.get(1) ?: return false

    // word list থেকে token আর timestamp বের করো
    val hjkMatch = Regex("""[A-Za-z0-9+/=_\-]+\|(\d+)\|hjkrhuihghfvu\|([A-Za-z0-9+/=_\-]+)""")
    .find(embedHtml) ?: return false
val timestamp = hjkMatch.groupValues[1]
val token = hjkMatch.groupValues[2]
    val m3u8Url = "https://minochinos.com/stream/$token/hjkrhuihghfvu/$timestamp/$fileId/master.m3u8"

    callback(
        newExtractorLink(name, name, m3u8Url, ExtractorLinkType.M3U8) {
            this.quality = Qualities.P1080.value
            this.referer = data
        }
    )
    return true
}
}
