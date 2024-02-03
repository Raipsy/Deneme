package eu.kanade.tachiyomi.extension.en.cutiecomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class CutieComics : ParsedHttpSource() {

    override val name = "Cutie Comics"

    override val baseUrl = "https://cutiecomics.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun popularMangaSelector() = "#dle-content > div.w25"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        with(element.selectFirst("strong.field-content > a")!!) {
            title = ownText()
            setUrlWithoutDomain(attr("href"))
        }
        thumbnail_url = element.selectFirst("a > img")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector() = ".navigation > a > i.fa-angle-right"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val id = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$baseUrl/$id"))
                .asObservableSuccess()
                .map(::searchMangaByIdParse)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response.asJsoup())
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        require(query.isNotBlank() && query.length >= 4) { "Invalid search! It should have at least 4 non-blank characters." }
        val body = FormBody.Builder()
            .add("do", "search")
            .add("subaction", "search")
            .add("full_search", "0")
            .add("search_start", "$page")
            .add("result_from", "${(page - 1) * 20 + 1}")
            .add("story", query)
            .build()
        return POST("$baseUrl/index.php?do=search", headers, body)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document): SManga {
        throw UnsupportedOperationException()
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException()
    }

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        throw UnsupportedOperationException()
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
