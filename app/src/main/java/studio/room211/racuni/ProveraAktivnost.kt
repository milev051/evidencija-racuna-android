package studio.room211.racuni

import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import studio.room211.racuni.Ui.dp
import studio.room211.racuni.Ui.maliTekst
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Provera računa preko ПФР броја, na zvaničnoj stranici Poreske uprave.
 *
 * Stranica je zaštićena reCAPTCHA-om, pa se obrazac ne može poslati sa strane;
 * zato se otvara prava stranica u aplikaciji, polja se popune, a proveru
 * potvrđuje sama stranica. Kada se pojavi račun, aplikacija ga pokupi i upiše
 * u isti zapis.
 */
class ProveraAktivnost : AppCompatActivity() {
    companion object {
        const val ADRESA = "https://suf.purs.gov.rs/verify"
        const val ID_RACUNA = "id_racuna"
        const val BROJ = "broj"
        const val BROJAC = "brojac"
        const val IZNOS = "iznos"
        const val VREME = "vreme"
    }

    private lateinit var pregledac: WebView
    private lateinit var poruka: TextView
    private val izvrsilac = Executors.newSingleThreadExecutor()
    private var idRacuna = 0L
    private var popunjeno = false
    private var zavrseno = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        idRacuna = intent.getLongExtra(ID_RACUNA, 0L)

        val koren = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        poruka = maliTekst(this, "Otvaram zvaničnu stranicu i popunjavam polja…").apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        koren.addView(poruka)

        pregledac = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Most(), "Racuni")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(prikaz: WebView, adresa: String) {
                    if (zavrseno) return
                    if (!popunjeno && adresa.startsWith(ADRESA)) {
                        popunjeno = true
                        prikaz.evaluateJavascript(popunjavanje(), null)
                    } else {
                        prikaz.evaluateJavascript(
                            "Racuni.stranica(window.location.href, document.documentElement.outerHTML)",
                            null,
                        )
                    }
                }
            }
        }
        koren.addView(
            pregledac,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setContentView(koren)
        pregledac.loadUrl(ADRESA)
    }

    override fun onDestroy() {
        izvrsilac.shutdown()
        pregledac.destroy()
        super.onDestroy()
    }

    /**
     * Polja se popunjavaju, a dugme se pritiska jednom. Ako reCAPTCHA ne
     * propusti automatski pritisak, obrazac ostaje popunjen na ekranu, pa je
     * dovoljno pritisnuti Пошаљи.
     */
    private fun popunjavanje(): String {
        val broj = navodnici(intent.getStringExtra(BROJ).orEmpty())
        val brojac = intent.getStringExtra(BROJAC).orEmpty().trim()
        val nastavak = brojac.takeLast(2).uppercase(Locale("sr", "RS"))
            .takeIf { it.any { slovo -> !slovo.isDigit() } }.orEmpty()
        val brojKasa = brojac.removeSuffix(nastavak)
        val iznos = navodnici(intent.getStringExtra(IZNOS).orEmpty())
        val vreme = navodnici(intent.getStringExtra(VREME).orEmpty())
        return """
            (function () {
              function postavi(izbor, vrednost) {
                var polje = document.querySelector(izbor);
                if (!polje || !vrednost) return;
                polje.value = vrednost;
                polje.dispatchEvent(new Event('input', { bubbles: true }));
                polje.dispatchEvent(new Event('change', { bubbles: true }));
              }
              postavi('#InvoiceNumberSe', $broj);
              postavi('#InvoiceCounter', ${navodnici(brojKasa)});
              postavi('#InvoiceCounterExtension', ${navodnici(nastavak)});
              postavi('#TotalAmount', $iznos);
              postavi('input[name="SdcDateTime"]', $vreme);
              setTimeout(function () {
                var obrazac = document.getElementById('verifyForm');
                if (!obrazac) return;
                var dugme = obrazac.querySelector('button[type="submit"], input[type="submit"]');
                if (dugme) dugme.click();
              }, 2500);
            })();
        """.trimIndent()
    }

    private fun navodnici(vrednost: String): String =
        "\"" + vrednost.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private inner class Most {
        @JavascriptInterface
        fun stranica(adresa: String, html: String) {
            if (zavrseno) return
            val jeRacun = html.contains("viewModel.InvoiceNumber(")
            val jeLink = adresa.contains("/v/") && adresa.contains("vl=")
            if (!jeRacun && !jeLink) return
            zavrseno = true
            runOnUiThread { poruka.text = "Račun je pronađen, preuzimam stavke…" }
            sacuvaj(adresa, html)
        }
    }

    private fun sacuvaj(adresa: String, html: String) {
        val kontekst = applicationContext
        izvrsilac.execute {
            val ishod = runCatching {
                val baza = Baza(kontekst)
                try {
                    if (SufRacun.podrzan(adresa)) {
                        // Zvanični link je najpouzdaniji: dalje radi redovna obrada.
                        baza.postaviQr(idRacuna, adresa)
                    } else {
                        baza.sacuvajAnalizu(idRacuna, SufRacun.izStranice(html))
                    }
                } finally {
                    baza.close()
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                ishod.onSuccess {
                    ObradaRacuna.zakazi(this)
                    Toast.makeText(this, "Račun je provereno upisan", Toast.LENGTH_LONG).show()
                    finish()
                }.onFailure {
                    zavrseno = false
                    poruka.text = "Nije upisano: ${it.message ?: it.javaClass.simpleName}"
                }
            }
        }
    }
}
