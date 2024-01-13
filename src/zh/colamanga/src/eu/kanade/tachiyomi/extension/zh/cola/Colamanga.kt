package eu.kanade.tachiyomi.extension.zh.colamanga

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class Colamanga : ParsedHttpSource() {

    override val name = "Cola漫画"

    override val baseUrl = "https://www.colamanga.com"

    override val lang = "zh"

    override val supportsLatest = true

    override val client: OkHttpClient =
        network.cloudflareClient
            .newBuilder()
            .addNetworkInterceptor(DecryptImageInterceptor)
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build()

    override fun headersBuilder() =
        super.headersBuilder().add("Referer", baseUrl).add("Origin", baseUrl)

    override fun popularMangaSelector() = "li.fed-list-item"

    override fun latestUpdatesSelector() = "li.fed-list-item"

    override fun searchMangaSelector() = "div.fed-main-info dl"

    override fun chapterListSelector() = "div.all_data_list li.fed-padding"

    override fun popularMangaNextPageSelector() = "div.fed-page-info"

    override fun latestUpdatesNextPageSelector() = "div.fed-page-info"

    override fun searchMangaNextPageSelector() = "div.fed-page-info"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/show?page=$page", headers)

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/show?orderBy=update&page=$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/search?type=1&page=$page&searchString=$query", headers)

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url, headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.select("dt a").attr("href")
            title = element.select("h1").text().trim()
            thumbnail_url = element.select("a.fed-list-pics").attr("data-original")
        }
    }

    private fun mangaFromElement(element: Element): SManga {
        Log.i("ColaManga", "mangaFromElement: $element")
        val manga = SManga.create()
        manga.url = element.select("a.fed-list-title").attr("href")
        manga.title = element.select("a.fed-list-title").text().trim()
        manga.thumbnail_url = element.select("a.fed-list-pics").attr("data-original")
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        Log.i("ColaManga", "chapterFromElement: $element")
        return SChapter.create().apply {
            name = element.select("a.fed-btns-info").text().trim()
            url = element.select("a.fed-btns-info").attr("href")
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        Log.i("ColaManga", "mangaDetailsParse: document")
        return SManga.create().apply {
            thumbnail_url =
                document.select("dt.fed-deta-images a.fed-list-pics").attr("data-original")
            description = document.select("p.fed-part-both").text().trim()
            title = document.select("h1").text().trim()
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        Log.i("Colamanga", "pageListParse: $document")
        val cDataRegex = """C_DATA='(.+)';""".toRegex()
        val cDataMatch = cDataRegex.find(document.toString())

        if (cDataMatch == null) {
            Log.e("Colamanga", "cDataMatch == null")
            return listOf()
        }

        val cData = cDataMatch.groupValues[1]
        val data = decryptCData(cData)
        Log.i("Colamanga", "data: $data")
        val encCodeRegex =
            """enc_code1:\"(.+?)\".+enc_code2:\"(.+?)\".+domain:\"(.+?)\".+use_server:\"(.*?)\".+keyType:\"(.*?)\",imgKey:\"(.*?)\"""".toRegex()
        val encCodeMatch = encCodeRegex.find(data)

        if (encCodeMatch == null) {
            Log.e("ColaManga", "encCodeMatch == null")
            return listOf()
        }

        val encCode1 = encCodeMatch.groupValues[1]
        val encCode2 = encCodeMatch.groupValues[2]
        var domain = encCodeMatch.groupValues[3]
        val useServer = encCodeMatch.groupValues[4]
        val keyType = encCodeMatch.groupValues[5]
        val encImgKey = encCodeMatch.groupValues[6]

        if (useServer.isNotEmpty()) {
            domain = domain.replace("img", "img$useServer")
        }

        Log.i("Colamanga", "encCode1: $encCode1")
        Log.i("Colamanga", "encCode2: $encCode2")
        Log.i("Colamanga", "domain: $domain")
        Log.i("Colamanga", "useServer: $useServer")
        Log.i("Colamanga", "keyType: $keyType")
        Log.i("Colamanga", "encImgKey: $encImgKey")

        val pageNumber = decryptPageNumber(encCode1)
        val pageUrl = decryptPageUrl(encCode2)
        val imgKey = getImgKey(keyType, encImgKey)

        Log.i("Colamanga", "pageNumber: $pageNumber")
        Log.i("Colamanga", "pageUrl: $pageUrl")
        Log.i("Colamanga", "imgKey: $imgKey")

        var finalUrl: String
        // 通过 queryParameter 传递 imgKey
        // 这样就算网址被缓存，也不会出现 imgKey 丢失的问题
        if (encImgKey.isEmpty()) {
            finalUrl = "https://$domain/comic/${pageUrl}0001.jpg?imgKey=$imgKey"
        } else {
            finalUrl = "https://$domain/comic/${pageUrl}0001.enc.webp?imgKey=$imgKey"
        }

        Log.i("ColaManga", "finalUrl: $finalUrl")

        // https://img.colamanga.com/comic/18735/SUwwSUdlUjZGeEQveU01SWd2enA2TEVpQ0MwMFpLa25kMmZOaEpyeEdpaz0=/0001.enc.webp
        return (1..pageNumber.toInt()).map {
            Page(it, "", finalUrl.replace("0001", it.toString().padStart(4, '0')))
        }
    }

    override fun imageUrlParse(document: Document) = ""
}
