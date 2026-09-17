package studio.room211.racuni

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.setPadding
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import studio.room211.racuni.Ui.dp
import studio.room211.racuni.Ui.dugme
import studio.room211.racuni.Ui.jednako
import studio.room211.racuni.Ui.kartica
import studio.room211.racuni.Ui.maliTekst
import studio.room211.racuni.Ui.polje
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var baza: Baza
    private lateinit var spisak: LinearLayout
    private lateinit var stanje: TextView
    private val datum = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("sr", "RS"))
    private val novac = NumberFormat.getCurrencyInstance(Locale("sr", "RS"))
    private val izvrsilacUvoza = Executors.newSingleThreadExecutor()

    private val skener = registerForActivityResult(ScanContract()) { rezultat ->
        val sadrzaj = rezultat.contents
        if (sadrzaj.isNullOrBlank()) {
            Toast.makeText(this, "Skeniranje je otkazano", Toast.LENGTH_SHORT).show()
        } else if (!SufRacun.podrzan(sadrzaj)) {
            Toast.makeText(
                this,
                "Kod je pročitan, ali nije fiskalni QR Poreske uprave",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            val dodat = baza.dodajQr(sadrzaj) != -1L
            if (dodat) ObradaRacuna.zakazi(this)
            osvezi()
            Toast.makeText(
                this,
                if (dodat) "QR je sačuvan lokalno" else "Ovaj račun je već sačuvan",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private val izborIzGalerije =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { slike ->
            if (slike.isNotEmpty()) uveziSlike(slike)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        baza = Baza(this)

        val koren = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14))
        }
        koren.addView(TextView(this).apply {
            setText(R.string.app_name)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)
            setPadding(dp(4), dp(4), dp(4), 0)
        })
        koren.addView(maliTekst(this, "Skeniraj sada, obradi kada se pojavi internet.").apply {
            setPadding(dp(4), 0, dp(4), dp(12))
        })
        koren.addView(dugme(this, "Skeniraj QR kôd", glavno = true) { pokreniSkener() })
        koren.addView(dugme(this, "Uvezi račun(e) iz galerije") {
            izborIzGalerije.launch(arrayOf("image/*"))
        })
        koren.addView(rucniUnos())

        stanje = maliTekst(this, "")
        stanje.setPadding(dp(4), dp(6), dp(4), dp(8))
        koren.addView(stanje)

        spisak = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        koren.addView(spisak)
        setContentView(ScrollView(this).apply {
            addView(koren, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(ObradaRacuna.IME_POSLA)
            .observe(this) { osvezi() }
        ObradaRacuna.zakazi(this)
        osvezi()
    }

    override fun onResume() {
        super.onResume()
        if (::spisak.isInitialized) osvezi()
    }

    override fun onDestroy() {
        baza.close()
        izvrsilacUvoza.shutdown()
        super.onDestroy()
    }

    private fun pokreniSkener() {
        skener.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Postavi QR kôd računa unutar okvira")
                setBeepEnabled(true)
                setOrientationLocked(false)
                setBarcodeImageEnabled(false)
            }
        )
    }

    private fun uveziSlike(slike: List<Uri>) {
        Toast.makeText(
            this,
            "Tražim fiskalne QR kodove u ${slike.size} izabranih slika…",
            Toast.LENGTH_SHORT,
        ).show()
        val kontekst = applicationContext
        izvrsilacUvoza.execute {
            var novi = 0
            var duplikati = 0
            var bezQr = 0
            var neispravne = 0
            var trebaMreza = false
            val bazaUvoza = Baza(kontekst)
            val citac = QrIzGalerije()
            try {
                for (slika in slike) {
                    try {
                        val kodovi = citac.procitaj(kontekst, slika)
                        if (kodovi.isEmpty()) {
                            bezQr++
                            continue
                        }
                        for (kod in kodovi) {
                            if (bazaUvoza.dodajQr(kod) == -1L) {
                                duplikati++
                            } else {
                                novi++
                                trebaMreza = true
                            }
                        }
                    } catch (_: Exception) {
                        neispravne++
                    }
                }
            } finally {
                citac.close()
                bazaUvoza.close()
            }

            if (trebaMreza) ObradaRacuna.zakazi(kontekst)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                osvezi()
                val poruka = buildString {
                    append("Uvezeno: ").append(novi)
                    if (duplikati > 0) append("\nVeć sačuvano: ").append(duplikati)
                    if (bezQr > 0) {
                        append("\nBez fiskalnog QR koda, preskočeno: ").append(bezQr)
                    }
                    if (neispravne > 0) append("\nNečitljive slike, preskočeno: ").append(neispravne)
                    if (novi == 0 && bezQr > 0) {
                        append("\n\nAko je QR vidljiv, iseci fotografiju oko njega i uvezi isečenu sliku.")
                    }
                }
                AlertDialog.Builder(this)
                    .setTitle("Uvoz iz galerije")
                    .setMessage(poruka)
                    .setPositiveButton("U redu", null)
                    .show()
            }
        }
    }

    private fun rucniUnos(): ViewGroup {
        val (kartica, unutra) = kartica(this)
        unutra.addView(TextView(this).apply {
            setText(R.string.manual_entry_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        })
        unutra.addView(maliTekst(this, "Upiši datum kupovine, šta je kupljeno, cenu ili napomenu."))
        val (okvir, unos) = polje(this, "Podaci o kupovini")
        okvir.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(10) }
        unutra.addView(okvir)
        unutra.addView(dugme(this, "Sačuvaj ručni unos") {
            val tekst = unos.text?.toString().orEmpty().trim()
            if (tekst.isBlank()) {
                okvir.error = "Unesi bar jednu informaciju"
            } else {
                okvir.error = null
                baza.dodajRucno(tekst)
                unos.text?.clear()
                osvezi()
                Toast.makeText(this, "Unos je sačuvan", Toast.LENGTH_SHORT).show()
            }
        })
        kartica.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) }
        return kartica
    }

    private fun osvezi() {
        val racuni = baza.svi()
        val ceka = racuni.count { it.stanje != LogikaRacuna.SACUVANO }
        stanje.text = when {
            racuni.isEmpty() -> "Još nema sačuvanih računa."
            ceka == 0 -> "Sačuvano: ${racuni.size} • sve obrađeno"
            else -> "Sačuvano: ${racuni.size} • čeka obradu: $ceka"
        }

        spisak.removeAllViews()
        for (racun in racuni) spisak.addView(redRacuna(racun))
    }

    private fun redRacuna(racun: Racun): ViewGroup {
        val (kartica, unutra) = kartica(this)
        val vreme = racun.datumRacuna ?: racun.nastao
        val naziv = racun.preduzece.ifBlank { racun.prodajnoMesto }.ifBlank { racun.naziv }
        val vremeINaziv = "${datum.format(Date(vreme))} — $naziv"
        unutra.addView(TextView(this).apply {
            text = vremeINaziv
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        })
        var opis = when (racun.stanje) {
            LogikaRacuna.CEKA_MREZU -> "Čeka internet da preuzme digitalni format"
            LogikaRacuna.GRESKA -> "Sačuvan QR; obrada će biti ponovljena"
            else -> if (racun.izvor == LogikaRacuna.IZVOR_QR) "QR obrađen i sačuvan" else "Ručni unos"
        }
        if (racun.ukupanIznosPara != null) opis += " • ${prikaziNovac(racun.ukupanIznosPara)}"
        if (racun.stavke.isNotEmpty()) opis += " • stavki: ${racun.stavke.size}"
        unutra.addView(maliTekst(this, opis))
        kartica.isClickable = true
        kartica.isFocusable = true
        kartica.setOnClickListener { prikaziDetalje(racun, vremeINaziv) }
        return kartica
    }

    private fun prikaziDetalje(racun: Racun, naslov: String) {
        val tekst = buildString {
            if (racun.preduzece.isNotBlank()) append("Preduzeće: ").append(racun.preduzece).append('\n')
            if (racun.prodajnoMesto.isNotBlank()) append("Prodajno mesto: ").append(racun.prodajnoMesto).append('\n')
            if (racun.adresa.isNotBlank()) append("Adresa: ").append(racun.adresa).append('\n')
            if (racun.grad.isNotBlank()) append("Grad: ").append(racun.grad).append('\n')
            if (racun.opstina.isNotBlank()) append("Opština: ").append(racun.opstina).append('\n')
            if (racun.pib.isNotBlank()) append("PIB: ").append(racun.pib).append('\n')
            if (racun.datumRacuna != null) append("Vreme računa: ").append(datum.format(Date(racun.datumRacuna))).append('\n')
            if (racun.ukupanIznosPara != null) append("Ukupno: ").append(prikaziNovac(racun.ukupanIznosPara)).append('\n')
            if (racun.brojRacuna.isNotBlank()) append("Broj računa: ").append(racun.brojRacuna).append('\n')
            if (racun.stavke.isNotEmpty()) {
                append("\nStavke:\n")
                for (stavka in racun.stavke) {
                    append("• ").append(stavka.naziv)
                        .append("\n  ").append(stavka.kolicina).append(" × ")
                        .append(prikaziNovac(stavka.jedinicnaCenaPara))
                        .append(" = ").append(prikaziNovac(stavka.ukupnoPara)).append('\n')
                }
            } else if (racun.tekst.isNotBlank()) {
                append(racun.tekst)
            }
            if (racun.qrSadrzaj.isNotBlank() && racun.qrSadrzaj != racun.tekst) {
                if (isNotEmpty()) append("\n\n")
                append("QR sadržaj:\n").append(racun.qrSadrzaj)
            }
            if (racun.greska.isNotBlank()) append("\n\nPoslednja greška:\n").append(racun.greska)
        }.ifBlank { "Podaci su sačuvani i čekaju obradu." }

        val dijalog = AlertDialog.Builder(this)
            .setTitle(naslov)
            .setView(ScrollView(this).apply {
                setPadding(dp(22))
                addView(jednako(this@MainActivity, tekst))
            })
            .setPositiveButton("Zatvori", null)

        if (LogikaRacuna.internetAdresa(racun.qrSadrzaj)) {
            dijalog.setNeutralButton("Otvori digitalni račun") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(racun.qrSadrzaj)))
            }
        }
        dijalog.show()
    }

    private fun prikaziNovac(para: Long): String = novac.format(para / 100.0)
}
