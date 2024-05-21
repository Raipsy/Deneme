package eu.kanade.tachiyomi.extension.pt.hotcabaretscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class HotCabaretScan : Madara(
    "Hot Cabaret Scan",
    "https://hotcabaretscan.com",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::loginCheckIntercept)
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    private fun loginCheckIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.queryParameter("password-protected").isNullOrEmpty()) {
            return response
        }

        response.close()
        throw IOException(LOGIN_THROUGH_WEBVIEW_ERROR)
    }

    companion object {
        private const val LOGIN_THROUGH_WEBVIEW_ERROR = "Autentique-se pela WebView para usar a extensão."
    }

    override val useNewChapterEndpoint = true

}
