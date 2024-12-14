package eu.kanade.tachiyomi.extension.pt.brmangastop

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class BRMangasTop : Madara(
    "BR Mangás",
    "https://brmangas.top",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMM 'de' yyyy", Locale("pt", "BR")),
) {
    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
