package eu.kanade.tachiyomi.extension.en.arvenscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class VortexScans : Iken(
    "Vortex Scans",
    "en",
    "https://vortexscans.org",
) {

    private val json by injectLazy<Json>()

    // regexImagesAfter = [{*[0-9]}]
    private val regexImagesAfter = "\\[\\{.*\\d\\}\\]".toRegex()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val images = document.selectFirst("script:containsData(images)")
            ?.data()
            ?.replace("""\"""", "\"")
            ?.let {
                json.parseToJsonElement(regexImagesAfter.find(it)!!.groups[0]!!.value).jsonArray
            } ?: throw Exception("Unable to find images")

        return images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img.jsonObject["url"]!!.jsonPrimitive.content)
        }
    }
}
