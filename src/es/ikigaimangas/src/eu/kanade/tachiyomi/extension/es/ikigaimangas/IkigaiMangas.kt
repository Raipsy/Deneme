package eu.kanade.tachiyomi.extension.es.ikigaimangas

import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

class IkigaiMangas : HttpSource() {

    override val baseUrl: String = "https://visorikigai.net"
    private val apiBaseUrl: String = "https://panel.ikigaimangas.com"

    override val lang: String = "es"
    override val name: String = "Ikigai Mangas"

    override val supportsLatest: Boolean = true

    private val cookieInterceptor = CookieInterceptor(baseUrl.substringAfter("://"), "data-saving" to "0")

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 2)
        .rateLimitHost(apiBaseUrl.toHttpUrl(), 2, 1)
        .addNetworkInterceptor(cookieInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun popularMangaRequest(page: Int): Request {
        val apiUrl = "$apiBaseUrl/api/swf/series/ranking-list?type=total_ranking&series_type=comic"
        return GET(apiUrl, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<PayloadSeriesDto>(response.body.string())
        val mangaList = result.data.map { it.toSManga() }
        return MangasPage(mangaList, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val apiUrl = "$apiBaseUrl/api/swf/new-chapters?page=$page"
        return GET(apiUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = json.decodeFromString<PayloadLatestDto>(response.body.string())
        val mangaList = result.data.filter { it.type == "comic" }.map { it.toSManga() }
        return MangasPage(mangaList, result.hasNextPage())
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortByFilter = filters.firstInstanceOrNull<SortByFilter>()

        val apiUrl = "$apiBaseUrl/api/swf/series".toHttpUrl().newBuilder()

        if (query.isNotEmpty()) apiUrl.addQueryParameter("search", query)

        apiUrl.addQueryParameter("page", page.toString())
        apiUrl.addQueryParameter("type", "comic")

        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter(Genre::state)
            .map(Genre::id)
            .joinToString(",")

        val statuses = filters.firstInstanceOrNull<StatusFilter>()?.state.orEmpty()
            .filter(Status::state)
            .map(Status::id)
            .joinToString(",")

        if (genres.isNotEmpty()) apiUrl.addQueryParameter("genres", genres)
        if (statuses.isNotEmpty()) apiUrl.addQueryParameter("status", statuses)

        apiUrl.addQueryParameter("column", sortByFilter?.selected ?: "name")
        apiUrl.addQueryParameter("direction", if (sortByFilter?.state?.ascending == true) "asc" else "desc")

        return GET(apiUrl.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<PayloadSeriesDto>(response.body.string())
        val mangaList = result.data.filter { it.type == "comic" }.map { it.toSManga() }
        return MangasPage(mangaList, result.hasNextPage())
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url.substringBefore("#").replace("/series/comic-", "/series/")

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url
            .substringAfter("/series/comic-")
            .substringBefore("#")

        return GET("$apiBaseUrl/api/swf/series/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = json.decodeFromString<PayloadSeriesDetailsDto>(response.body.string())
        return result.series.toSMangaDetails()
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBefore("#")

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("/series/comic-").substringBefore("#")
        return GET("$apiBaseUrl/api/swf/series/$slug/chapters?page=1", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.toString()
            .substringAfter("/series/")
            .substringBefore("/chapters")
        var result = json.decodeFromString<PayloadChaptersDto>(response.body.string())
        val mangas = mutableListOf<SChapter>()
        mangas.addAll(result.data.map { it.toSChapter(dateFormat) })
        var page = 2
        while (result.meta.hasNextPage()) {
            val newResponse = client.newCall(GET("$apiBaseUrl/api/swf/series/$slug/chapters?page=$page", headers)).execute()
            result = json.decodeFromString<PayloadChaptersDto>(newResponse.body.string())
            mangas.addAll(result.data.map { it.toSChapter(dateFormat) })
            page++
        }
        return mangas
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET(baseUrl + chapter.url.substringBefore("#"), headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("section div.img > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        fetchFilters()

        val filters = mutableListOf<Filter<*>>(
            SortByFilter("Ordenar por", getSortProperties()),
        )

        filters += if (filtersState == FiltersState.FETCHED) {
            listOf(
                StatusFilter("Estados", getStatusFilters()),
                GenreFilter("Géneros", getGenreFilters()),
            )
        } else {
            listOf(
                Filter.Header("Presione 'Restablecer' para intentar cargar los filtros"),
            )
        }

        return FilterList(filters)
    }

    private fun getSortProperties(): List<SortProperty> = listOf(
        SortProperty("Nombre", "name"),
        SortProperty("Creado en", "created_at"),
        SortProperty("Actualización más reciente", "last_chapter_date"),
        SortProperty("Número de favoritos", "bookmark_count"),
        SortProperty("Número de valoración", "rating_count"),
        SortProperty("Número de vistas", "view_count"),
    )

    private fun getGenreFilters(): List<Genre> = genresList.map { Genre(it.first, it.second) }
    private fun getStatusFilters(): List<Status> = statusesList.map { Status(it.first, it.second) }

    private var genresList: List<Pair<String, Long>> = emptyList()
    private var statusesList: List<Pair<String, Long>> = emptyList()
    private var fetchFiltersAttempts = 0
    private var filtersState = FiltersState.NOT_FETCHED

    private fun fetchFilters() {
        if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
        filtersState = FiltersState.FETCHING
        fetchFiltersAttempts++
        thread {
            try {
                val response = client.newCall(GET("$apiBaseUrl/api/swf/filter-options", headers)).execute()
                val filters = json.decodeFromString<PayloadFiltersDto>(response.body.string())

                genresList = filters.data.genres.map { it.name.trim() to it.id }
                statusesList = filters.data.statuses.map { it.name.trim() to it.id }

                filtersState = FiltersState.FETCHED
            } catch (e: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    private inline fun <reified R> List<*>.firstInstanceOrNull(): R? =
        filterIsInstance<R>().firstOrNull()

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }
}
