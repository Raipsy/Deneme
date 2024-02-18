package eu.kanade.tachiyomi.extension.en.mangaowlus

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaOwlUs : Madara("MangaOwl.us (unoriginal)", "https://mangaowl.us", "en") {
    override val useNewChapterEndpoint = true
}
