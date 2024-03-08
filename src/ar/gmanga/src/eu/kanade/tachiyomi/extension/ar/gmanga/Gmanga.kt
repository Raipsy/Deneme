package eu.kanade.tachiyomi.extension.ar.gmanga

import eu.kanade.tachiyomi.multisrc.gmanga.BrowseManga
import eu.kanade.tachiyomi.multisrc.gmanga.Gmanga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Response

class Gmanga : Gmanga(
    "GMANGA",
    "https://gmanga.org",
    "ar",
    "https://media.gmanga.me",
) {
    override val client = super.client.newBuilder()
        .rateLimit(4)
        .build()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val decMga = response.decryptAs<JsonObject>()
        val selectedManga = decMga["rows"]!!.jsonArray[0].jsonObject["rows"]!!.jsonArray
        val manags = selectedManga.map {
            json.decodeFromJsonElement<BrowseManga>(it.jsonArray[17])
        }

        val entries = manags.map { it.toSManga(::createThumbnail) }
            .distinctBy { it.url }

        return MangasPage(
            entries,
            hasNextPage = (manags.size >= 30),
        )
    }
}

/*

    override fun getCategoryFilter() =
        listOf(
            TagFilterData("1", "إثارة"),
            TagFilterData("2", "أكشن"),
            TagFilterData("3", "الحياة المدرسية"),
            TagFilterData("4", "الحياة اليومية"),
            TagFilterData("5", "آليات"),
            TagFilterData("6", "تاريخي"),
            TagFilterData("7", "تراجيدي"),
            TagFilterData("8", "جوسيه"),
            TagFilterData("9", "حربي"),
            TagFilterData("10", "خيال"),
            TagFilterData("11", "خيال علمي"),
            TagFilterData("12", "دراما"),
            TagFilterData("13", "رعب"),
            TagFilterData("14", "رومانسي"),
            TagFilterData("15", "رياضة"),
            TagFilterData("16", "ساموراي"),
            TagFilterData("17", "سحر"),
            TagFilterData("18", "سينين"),
            TagFilterData("19", "شوجو"),
            TagFilterData("20", "شونين"),
            TagFilterData("21", "عنف"),
            TagFilterData("22", "غموض"),
            TagFilterData("23", "فنون قتال"),
            TagFilterData("24", "قوى خارقة"),
            TagFilterData("25", "كوميدي"),
            TagFilterData("26", "لعبة"),
            TagFilterData("27", "مسابقة"),
            TagFilterData("28", "مصاصي الدماء"),
            TagFilterData("29", "مغامرات"),
            TagFilterData("30", "موسيقى"),
            TagFilterData("31", "نفسي"),
            TagFilterData("32", "نينجا"),
            TagFilterData("33", "وحوش"),
            TagFilterData("34", "حريم"),
            TagFilterData("35", "راشد"),
            TagFilterData("38", "ويب-تون"),
            TagFilterData("39", "زمنكاني"),
        )
 */
