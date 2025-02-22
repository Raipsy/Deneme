package eu.kanade.tachiyomi.multisrc.makaru
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import org.jsoup.nodes.Element

object MakaruUtils {
    fun Element.textWithNewlines() = run {
        select("p, br").prepend("\\n")
        text().replace("\\n", "\n").replace("\n ", "\n")
    }

    fun Element.imgAttr(): String = when {
        hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
        hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
        hasAttr("data-src") -> absUrl("data-src").substringBefore(" ")
        hasAttr("srcset") -> absUrl("srcset").substringBefore(" ")
        else -> absUrl("src")
    }
}
