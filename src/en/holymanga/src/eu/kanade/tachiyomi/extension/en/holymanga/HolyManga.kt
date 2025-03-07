package eu.kanade.tachiyomi.extension.en.holymanga

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader

class HolyManga : FMReader(
    "HolyManga",
    "https://w34.holymanga.net",
    "en",
) {
    override val versionId = 3

    override val chapterUrlSelector = ""

    override fun chapterFromElement(element: Element, mangaTitle: String = ""): SChapter {
        return SChapter.create().apply {
            if (chapterUrlSelector != "") {
                element.select(chapterUrlSelector).first()!!.let {
                    setUrlWithoutDomain(it.attr("abs:href"))
                    name = it.text().substringAfter("$mangaTitle ")
                }
            } else {
                element.let {
                    setUrlWithoutDomain(it.attr("abs:href"))
                    name = element.attr(chapterNameAttrSelector).substringAfter("$mangaTitle ")
                }
            }
            date_upload = element.select(chapterTimeSelector).let { if (it.hasText()) parseAbsoluteDate (it.text()) else 0 }
        }
    }
}
