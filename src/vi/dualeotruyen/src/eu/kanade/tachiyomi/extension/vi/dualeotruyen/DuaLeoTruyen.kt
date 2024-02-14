package eu.kanade.tachiyomi.extension.vi.dualeotruyen

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

class DuaLeoTruyen : ParsedHttpSource() {

    override val name = "Dưa Leo Truyện"

    override val baseUrl = "https://dualeotruyenme.com"

    override val lang = "vi"

    override val supportsLatest = false

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/truyen-tranh-moi.html?page=$page", headers)

    override fun popularMangaSelector() = "div.product-grid > div"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.selectFirst(".comics-item-title")!!.text()
        thumbnail_url = element.selectFirst("img.card-img-top")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector() = "ul.pagination li.page-item:contains(Next):not(.disabled)"

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addPathSegment("tim-kiem")
                addQueryParameter("search", query)
            } else {
                val genreFilter = filters.ifEmpty { getFilterList() }
                    .filterIsInstance<GenreFilter>()
                    .firstOrNull() ?: return popularMangaRequest(page)
                addPathSegments(genreFilter.genre[genreFilter.state].path)
            }

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val statusText = document.selectFirst(".card-body dt:contains(Trạng thái) + dd")?.text()

        title = document.selectFirst(".card-title")!!.text()
        description = document.selectFirst(".comics-description .inner")?.text()
        genre = document.select(".cate-item").joinToString { it.text() }
        status = when (statusText) {
            "Đang phát hành" -> SManga.ONGOING
            "Đã đủ bộ" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("img.img-fluid")?.absUrl("src")
    }

    override fun chapterListSelector() = ".list-chapters > .item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst(".episode-title a")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            name = it.text()
        }
        date_upload = element.selectFirst(".episode-date span")?.let { parseRelativeDate(it.text()) } ?: 0L
    }

    override fun pageListParse(document: Document): List<Page> {
        countView(document)

        return document.select("img.chapter-img").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("data-original"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Không dùng được khi tìm kiếm bằng tên truyện"),
        GenreFilter(getGenreList()),
    )

    private fun countView(document: Document) {
        val chapterId = document.selectFirst("input#chapter_id")!!.`val`()
        val comicsId = document.selectFirst("input#comics_id")!!.`val`()
        val token = document.selectFirst("meta[name=_token]")!!.attr("content")
        val form = FormBody.Builder()
            .add("_token", token)
            .add("comics_id", comicsId)
            .add("chapter_id", chapterId)
            .build()
        val request = POST("$baseUrl/ajax/increase-view-chapter", headers, form)

        client.newCall(request).execute().close()
    }

    private fun parseRelativeDate(date: String): Long {
        val dateParts = date.split(" ")

        val calendar = Calendar.getInstance().apply {
            val amount = -dateParts[0].toInt()
            val field = when (dateParts[1]) {
                "giây" -> Calendar.SECOND
                "phút" -> Calendar.MINUTE
                "giờ" -> Calendar.HOUR_OF_DAY
                "ngày" -> Calendar.DAY_OF_MONTH
                "tuần" -> Calendar.WEEK_OF_MONTH
                "tháng" -> Calendar.MONTH
                "năm" -> Calendar.YEAR
                else -> Calendar.SECOND
            }

            add(field, amount)
        }

        return calendar.timeInMillis
    }

    private class Genre(val name: String, val path: String)

    private class GenreFilter(val genre: List<Genre>) : Filter.Select<String>("Thể loại", genre.map { it.name }.toTypedArray())

    // copy([...document.querySelectorAll(".dropdown-menu .dropdown-item")].map((e) => `Genre("${e.textContent.trim()}", "${new URL(e).pathname.replace("/", "")}"),`).join("\n"))
    // "Tất cả" and "Truyện full" are custom genres that are lumped in to make my life easier.
    private fun getGenreList() = listOf(
        Genre("Tất cả", "truyen-tranh-moi.html"),
        Genre("Truyện full", "truyen-hoan-thanh.html"),
        Genre("18+", "the-loai/18-.html"),
        Genre("ABO", "the-loai/abo.html"),
        Genre("Bách Hợp", "the-loai/bach-hop.html"),
        Genre("BoyLove", "the-loai/boylove.html"),
        Genre("Chuyển Sinh", "the-loai/chuyen-sinh.html"),
        Genre("Cổ Đại", "the-loai/co-dai.html"),
        Genre("Doujinshi", "the-loai/doujinshi.html"),
        Genre("Drama", "the-loai/drama.html"),
        Genre("Đam Mỹ", "the-loai/dam-my.html"),
        Genre("Echi", "the-loai/echi.html"),
        Genre("GirlLove", "the-loai/girllove.html"),
        Genre("Hài Hước", "the-loai/hai-huoc.html"),
        Genre("Hành Động", "the-loai/hanh-dong.html"),
        Genre("Harem", "the-loai/harem.html"),
        Genre("Hentai", "the-loai/hentai.html"),
        Genre("Kịch Tính", "the-loai/kich-tinh.html"),
        Genre("Lãng Mạn", "the-loai/lang-man.html"),
        Genre("Manga", "the-loai/manga.html"),
        Genre("Manhua", "the-loai/manhua.html"),
        Genre("Manhwa", "the-loai/manhwa.html"),
        Genre("Người Thú", "the-loai/nguoi-thu.html"),
        Genre("Oneshot", "the-loai/oneshot.html"),
        Genre("Phiêu Lưu", "the-loai/phieu-luu.html"),
        Genre("Tình Cảm", "the-loai/tinh-cam.html"),
        Genre("Truyện Màu", "the-loai/truyen-mau.html"),
        Genre("Yaoi", "the-loai/yaoi.html"),
        Genre("Yuri", "the-loai/yuri.html"),
    )
}
