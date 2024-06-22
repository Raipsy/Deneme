package eu.kanade.tachiyomi.extension.ko.agitoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import rx.Observable
import uy.kohesive.injekt.injectLazy
import kotlin.math.min
import kotlin.random.Random

class AgiToon : HttpSource() {

    override val name = "아지툰"

    override val lang = "ko"

    private var currentBaseUrlHost = ""
    override val baseUrl = "https://agitoon.in"

    private val cdnUrl = "https://blacktoonimg.com/"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder().addInterceptor { chain ->
        if (currentBaseUrlHost.isBlank()) {
            noRedirectClient.newCall(GET(baseUrl, headers)).execute().use {
                currentBaseUrlHost = it.headers["location"]?.toHttpUrlOrNull()?.host
                    ?: throw IOException("unable to get updated url")
            }
        }

        val request = chain.request().newBuilder().apply {
            if (chain.request().url.toString().startsWith(baseUrl)) {
                url(
                    chain.request().url.newBuilder()
                        .host(currentBaseUrlHost)
                        .build(),
                )
            }
            header("Referer", "https://$currentBaseUrlHost/")
            header("Origin", "https://$currentBaseUrlHost")
        }.build()

        return@addInterceptor chain.proceed(request)
    }.build()

    private val noRedirectClient = network.cloudflareClient.newBuilder()
        .followRedirects(false)
        .build()

    private val json by injectLazy<Json>()

    private val db by lazy {
        val doc = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
        doc.select("script[src*=data/webtoon]").flatMap { scriptEl ->
            var listIdx: Int
            client.newCall(GET(scriptEl.absUrl("src"), headers))
                .execute().body.string()
                .also {
                    listIdx = it.substringBefore(" = ")
                        .substringAfter("data")
                        .toInt()
                }
                .substringAfter(" = ")
                .removeSuffix(";")
                .let { json.decodeFromString<List<SeriesItem>>(it) }
                .onEach { it.listIndex = listIdx }
        }
    }

    private fun List<SeriesItem>.getPageChunk(page: Int): MangasPage {
        return MangasPage(
            mangas = subList((page - 1) * 24, min(page * 24, size))
                .map { it.toSManga(cdnUrl) },
            hasNextPage = (page + 1) * 24 <= size,
        )
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(
            db.sortedByDescending { it.hot }.getPageChunk(page),
        )
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return Observable.just(
            db.sortedByDescending { it.updatedAt }.getPageChunk(page),
        )
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        var list = db

        if (query.isNotBlank()) {
            val stdQuery = query.replace(Regex("""\s+"""), "")
            list = list.filter { it.name.contains(stdQuery) || it.author.contains(stdQuery) }
        }

        filters.filterIsInstance<ListFilter>().forEach {
            list = it.applyFilter(list)
        }

        return Observable.just(
            list.getPageChunk(page),
        )
    }

    override fun getFilterList() = getFilters()

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response, manga)
            }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/azi_toon/${manga.url}.html", headers)
    }

    override fun getMangaUrl(manga: SManga): String {
        return buildString {
            if (currentBaseUrlHost.isBlank()) {
                append(baseUrl)
            } else {
                append("https://")
                append(currentBaseUrlHost)
            }
            append("/azi_toon/")
            append(manga.url)
            append(".html")
        }
    }

    private fun mangaDetailsParse(response: Response, manga: SManga): SManga {
        val document = response.asJsoup()
        return manga.apply {
            description = document.select("p.mt-2").last()?.text()
            initialized = true
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$baseUrl/data/toonlist/${manga.url}.js?v=${"%.17f".format(Random.nextDouble())}"

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaId = response.request.url.pathSegments.last().removeSuffix(".js")

        val data = response.body.string()
            .substringAfter(" = ")
            .removeSuffix(";")
            .let { json.decodeFromString<List<Chapter>>(it) }

        return data.map { it.toSChapter(mangaId) }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return buildString {
            if (currentBaseUrlHost.isBlank()) {
                append(baseUrl)
            } else {
                append("https://")
                append(currentBaseUrlHost)
            }
            append("/azi_toons/")
            append(chapter.url)
            append(".html")
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl/azi_toons/${chapter.url}.html", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("#toon_content_imgs img").map {
            Page(0, imageUrl = cdnUrl + it.attr("o_src"))
        }
    }

    // unused
    override fun popularMangaRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }
    override fun popularMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }
    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }
    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        throw UnsupportedOperationException()
    }
    override fun searchMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }
    override fun mangaDetailsParse(response: Response): SManga {
        throw UnsupportedOperationException()
    }
    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }
}
