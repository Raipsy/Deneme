package eu.kanade.tachiyomi.extension.ja.ganma

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest

// source ID needed before class construction
// generated by running main() below
const val sourceId = 8045942616403978870
const val sourceName = "GANMA!"
const val sourceLang = "ja"
const val sourceVersionId = 1 // != extension version code
const val METADATA_PREF = "METADATA"

val json: Json = Injekt.get()
val preferences: SharedPreferences =
    Injekt.get<Application>().getSharedPreferences("source_$sourceId", 0x0000)

class GanmaFactory : SourceFactory {
    override fun createSources(): List<Source> {
        val source = try {
            val metadata = preferences.getString(METADATA_PREF, "")!!
                .also { if (it.isEmpty()) throw Exception() }
                .let { Base64.decode(it.toByteArray(), Base64.DEFAULT) }
            GanmaApp(json.decodeFromString(String(metadata)))
        } catch (e: Exception) {
            Ganma()
        }
        return listOf(source)
    }
}

fun main() {
    println(getSourceId()) // unfortunately there's no constexpr in Kotlin
}

fun getSourceId() = run { // copied from HttpSource
    val key = "${sourceName.lowercase()}/$sourceLang/$sourceVersionId"
    val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
    (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
}
