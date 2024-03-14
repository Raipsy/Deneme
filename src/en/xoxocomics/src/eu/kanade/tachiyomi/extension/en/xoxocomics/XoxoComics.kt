package eu.kanade.tachiyomi.extension.en.xoxocomics

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class XoxoComics : WPComics(
    "XOXO Comics",
    "https://xoxocomic.com",
    "en",
    SimpleDateFormat("MM/dd/yyyy", Locale.US),
    null,
) {
    override val searchPath = "search-comic"
    override val popularPath = "hot-comic"

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/comic-update?page=$page", headers)

    override fun latestUpdatesSelector() = "li.row"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("h3 a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").attr("data-original")
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.let { if (it.isEmpty()) getFilterList() else it }
        return if (query.isNotEmpty() || filterList.isEmpty()) {
            // Search won't work together with filter
            return GET("$baseUrl/$searchPath?keyword=$query&page=$page", headers)
        } else {
            val url = baseUrl.toHttpUrl().newBuilder()

            var genreFilter: UriPartFilter? = null
            var statusFilter: UriPartFilter? = null
            filterList.forEach { filter ->
                when (filter) {
                    is GenreFilter -> genreFilter = filter
                    is StatusFilter -> statusFilter = filter
                    else -> {}
                }
            }

            // Genre filter must come before status filter
            genreFilter?.toUriPart()?.let { url.addPathSegment(it) }
            statusFilter?.toUriPart()?.let { url.addPathSegment(it) }

            url.apply {
                addQueryParameter("page", page.toString())
                addQueryParameter("sort", "0")
            }

            GET(url.toString(), headers)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        // recursively add chapters from paginated chapter list
        fun parseChapters(document: Document) {
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            document.select("ul.pagination a[rel=next]").firstOrNull()?.let { a ->
                parseChapters(client.newCall(GET(a.attr("abs:href"), headers)).execute().asJsoup())
            }
        }

        parseChapters(response.asJsoup())
        return chapters
    }

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            date_upload = element.select("div.col-xs-3").text().toDate()
        }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + "${chapter.url}/all")

    override fun getStatusList(): List<Pair<String?, String>> =
        listOf(
            Pair(null, "All"),
            Pair("ongoing", "Ongoing"),
            Pair("completed", "Completed"),
        )

    override fun genresRequest() = GET("$baseUrl/comic-list", headers)

    override fun genresSelector() = ".genres h2:contains(Genres) + ul.nav li a"

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }
        return FilterList(
            Filter.Header(intl["FILTERS_IGNORE_QUERY"]),
            StatusFilter(intl["STATUS"], getStatusList()),
            if (genreList.isEmpty()) {
                Filter.Header("Tap 'Reset' to load genres")
            } else {
                GenreFilter(intl["GENRE"], genreList)
            },
        )
    }
}
