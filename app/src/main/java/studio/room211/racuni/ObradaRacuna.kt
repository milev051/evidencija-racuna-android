package studio.room211.racuni

import android.content.Context
import android.text.Html
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Obrada radi tek kada Android potvrdi mrežu. WorkManager čuva posao i posle
 * gašenja aplikacije ili telefona, tako da skenirani QR ne zavisi od interneta
 * u trenutku skeniranja.
 */
class ObradaRacuna(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val baza = Baza(applicationContext)
        var neuspeh = false
        for (racun in baza.zaMreznuObradu()) {
            try {
                if (SufRacun.podrzan(racun.qrSadrzaj)) {
                    baza.sacuvajAnalizu(racun.id, SufRacun.preuzmi(racun.qrSadrzaj))
                } else {
                    baza.sacuvajObradu(racun.id, preuzmiTekst(racun.qrSadrzaj))
                }
            } catch (e: Exception) {
                neuspeh = true
                baza.sacuvajGresku(racun.id, e.message ?: e.javaClass.simpleName)
            }
        }
        baza.close()
        return if (neuspeh) Result.retry() else Result.success()
    }

    private fun preuzmiTekst(adresa: String): String {
        require(LogikaRacuna.internetAdresa(adresa)) { "QR nije internet adresa" }
        val veza = URL(adresa).openConnection() as HttpURLConnection
        try {
            veza.connectTimeout = 12_000
            veza.readTimeout = 18_000
            veza.instanceFollowRedirects = true
            veza.setRequestProperty("User-Agent", "Racuni-Android/0.1")
            veza.setRequestProperty("Accept", "text/html,application/json,text/plain")

            val kod = veza.responseCode
            if (kod !in 200..299) error("Server je vratio HTTP $kod")

            val sirov = BufferedReader(InputStreamReader(veza.inputStream, Charsets.UTF_8)).use {
                val izlaz = StringBuilder()
                val bafer = CharArray(8_192)
                while (izlaz.length < MAKSIMALNO_ZNAKOVA) {
                    val broj = it.read(bafer, 0, minOf(bafer.size, MAKSIMALNO_ZNAKOVA - izlaz.length))
                    if (broj < 0) break
                    izlaz.append(bafer, 0, broj)
                }
                izlaz.toString()
            }

            val tip = veza.contentType.orEmpty().lowercase()
            val tekst = if ("html" in tip || sirov.contains("<html", ignoreCase = true)) {
                Html.fromHtml(sirov, Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                sirov
            }.replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()

            if (tekst.isBlank()) error("Digitalni račun nema čitljiv tekst")
            return tekst
        } finally {
            veza.disconnect()
        }
    }

    companion object {
        const val IME_POSLA = "obrada-digitalnih-racuna"
        private const val MAKSIMALNO_ZNAKOVA = 600_000

        fun zakazi(context: Context) {
            val uslovi = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val posao = OneTimeWorkRequestBuilder<ObradaRacuna>()
                .setConstraints(uslovi)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IME_POSLA,
                ExistingWorkPolicy.REPLACE,
                posao,
            )
        }
    }
}
