package studio.room211.racuni

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
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
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import studio.room211.racuni.Ui.dinari
import studio.room211.racuni.Ui.dp
import studio.room211.racuni.Ui.dugme
import studio.room211.racuni.Ui.jednako
import studio.room211.racuni.Ui.kartica
import studio.room211.racuni.Ui.maliTekst
import studio.room211.racuni.Ui.odeljak
import studio.room211.racuni.Ui.polje
import studio.room211.racuni.Ui.vrednost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var baza: Baza
    private lateinit var spisak: LinearLayout
    private lateinit var tok: LinearLayout
    private lateinit var ciscenje: LinearLayout
    private lateinit var stanje: TextView
    private val datum = SimpleDateFormat("dd.MM.yyyy. HH:mm", Locale("sr", "RS"))
    private val datumSaSekundama = SimpleDateFormat("dd.MM.yyyy. HH:mm:ss", Locale("sr", "RS"))
    private val imeFajla = SimpleDateFormat("yyyy-MM-dd", Locale("sr", "RS"))
    private val vremeZaProveru = SimpleDateFormat("d.M.yyyy. HH:mm:ss", Locale.ROOT)
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

    private val izvozBekapa =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { gde ->
            if (gde != null) sacuvajBekap(gde)
        }

    private val izvozSpiska =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { gde ->
            if (gde != null) sacuvajUFajl(gde) { Bekap.spisakZaObradu(it.svi()) }
        }

    private val vracanjeBekapa =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { odakle ->
            if (odakle != null) vratiBekap(odakle)
        }

    private val dozvolaZaObavestenja =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val izborIzGalerije =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { slike ->
            if (slike.isNotEmpty()) uveziSlike(slike)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        Obavestenja.pripremi(this)
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
        tok = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        koren.addView(tok)

        koren.addView(dugme(this, "Skeniraj QR kôd", glavno = true) { pokreniSkener() })
        koren.addView(dugme(this, "Uvezi račun(e) iz galerije") {
            zatraziObavestenja()
            izborIzGalerije.launch(arrayOf("image/*"))
        })
        koren.addView(dugme(this, "Unesi ПФР број sa računa") { unosPfrBroja() })
        koren.addView(dugme(this, "Pregled kupovina") {
            startActivity(Intent(this, PregledAktivnost::class.java))
        })
        koren.addView(dugme(this, "Podešavanja i bekap") { podesavanja() })
        koren.addView(rucniUnos())

        stanje = maliTekst(this, "")
        stanje.setPadding(dp(4), dp(6), dp(4), dp(8))
        koren.addView(stanje)

        ciscenje = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        koren.addView(ciscenje)

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

    /** Obaveštenje je jedini način da se tok vidi i van aplikacije. */
    private fun zatraziObavestenja() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Obavestenja.dozvoljeno(this)) {
            dozvolaZaObavestenja.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun pokreniSkener() {
        skener.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setCaptureActivity(SkenerAktivnost::class.java)
                setPrompt("")
                setBeepEnabled(true)
                setOrientationLocked(false)
                setBarcodeImageEnabled(false)
            }
        )
    }

    private fun uveziSlike(slike: List<Uri>) {
        // Uvoz ne blokira ekran: tok stoji na vrhu aplikacije i u obaveštenju,
        // pa spisak i dalje može da se pregleda dok skeniranje traje.
        val napredak = TokUvoza(slike.size)
        val kontekst = applicationContext
        izvrsilacUvoza.execute {
            var novi = 0
            var saSlike = 0
            var duplikati = 0
            var bezQr = 0
            var neispravne = 0
            var trebaMreza = false
            val bazaUvoza = Baza(kontekst)
            val citac = QrIzGalerije()
            try {
                for ((redni, slika) in slike.withIndex()) {
                    val nadjeno = novi
                    runOnUiThread { napredak.naSlici(redni, nadjeno) }
                    Obavestenja.napredak(kontekst, redni, slike.size, nadjeno)
                    try {
                        val kodovi = citac.procitaj(kontekst, slika)
                        if (kodovi.isEmpty()) {
                            // QR nije čitljiv: račun se pokušava pročitati sa same slike.
                            runOnUiThread { napredak.poruka("QR nije čitljiv, čitam sa slike…") }
                            when (saSlike(kontekst, bazaUvoza, slika)) {
                                Ishod.NOVI -> saSlike++
                                Ishod.POSTOJI -> duplikati++
                                Ishod.NISTA -> bezQr++
                            }
                            runOnUiThread { napredak.poruka("") }
                        } else {
                            for (kod in kodovi) {
                                if (bazaUvoza.dodajQr(kod) == -1L) {
                                    duplikati++
                                } else {
                                    novi++
                                    trebaMreza = true
                                }
                            }
                        }
                    } catch (_: Exception) {
                        neispravne++
                    }
                    val zavrseno = redni + 1
                    val ukupnoNadjeno = novi
                    runOnUiThread { napredak.zavrsenaSlika(zavrseno, ukupnoNadjeno) }
                }
            } finally {
                citac.close()
                bazaUvoza.close()
            }

            if (trebaMreza) ObradaRacuna.zakazi(kontekst)
            val poruka = buildString {
                append("Uvezeno: ").append(novi)
                if (saSlike > 0) append(" • pročitano sa slike: ").append(saSlike)
                if (duplikati > 0) append(" • već sačuvano: ").append(duplikati)
                if (bezQr > 0) append(" • bez fiskalnog QR koda: ").append(bezQr)
                if (neispravne > 0) append(" • nečitljivo: ").append(neispravne)
                if (novi == 0 && saSlike == 0 && bezQr > 0) {
                    append("\nAko je QR vidljiv, iseci fotografiju oko njega i uvezi isečenu sliku.")
                    if (Podesavanja.geminiKljuc(kontekst).isBlank()) {
                        append("\nSa Gemini ključem u podešavanjima aplikacija ume da pročita ")
                        append("ПФР број sa same slike i da račun proveri zvanično.")
                    }
                }
            }
            Obavestenja.kraj(kontekst, poruka)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                napredak.zavrsi(poruka)
                osvezi()
            }
        }
    }

    private enum class Ishod { NOVI, POSTOJI, NISTA }

    /**
     * Slika bez čitljivog QR koda. Model čita ПФР број i ostala polja, pa
     * zapis ostaje u stanju „čeka proveru" dok se ne potvrdi kod Poreske
     * uprave. Ako ni to ne uspe, pamti se bar šta je kupljeno.
     */
    private fun saSlike(kontekst: android.content.Context, baza: Baza, slika: Uri): Ishod {
        val procitano = runCatching { CitanjeSlike.procitaj(kontekst, slika) }
            .onFailure { Podesavanja.zapisiGresku(kontekst, it.message ?: it.javaClass.simpleName) }
            .getOrNull()
        if (procitano == null || !procitano.upotrebljivo) return Ishod.NISTA

        val ime = CitanjeSlike.imeFajla(kontekst, slika)
        val spreman = procitano.citljivost == CitanjeSlike.PROCITAN_PFR
        val opis = buildString {
            if (procitano.radnja.isNotBlank()) append(procitano.radnja).append('\n')
            append("Pročitano sa slike").append(if (ime.isBlank()) "" else ": $ime")
            if (!spreman) append("\nПФР број nije pročitan u celosti; podaci nisu provereni.")
        }
        val id = baza.dodajSaSlike(
            naziv = procitano.radnja.ifBlank { "Račun sa slike" },
            tekst = opis,
            brojRacuna = procitano.pfrBroj,
            brojac = procitano.brojac,
            ukupanIznosPara = procitano.ukupanIznosPara,
            datumRacuna = procitano.vreme,
            izvornaSlika = ime,
            stanje = if (spreman) LogikaRacuna.CEKA_PROVERU else LogikaRacuna.NECITLJIVO,
            stavke = procitano.stavke,
        )
        return if (id == -1L) Ishod.POSTOJI else Ishod.NOVI
    }

    /** Tok uvoza na vrhu ekrana, umesto prozora koji zaustavlja rad. */
    private inner class TokUvoza(private val ukupno: Int) {
        private val korak = TextView(this@MainActivity).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            text = tekstKoraka(0)
        }
        private val traka = LinearProgressIndicator(this@MainActivity).apply {
            // Kod jedne slike nema šta da se puni, pa traka samo pokazuje da rad traje.
            isIndeterminate = ukupno <= 1
            max = maxOf(ukupno, 1)
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) }
        }
        private val nalaz = maliTekst(this@MainActivity, "Pronađenih računa: 0").apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }
        private val dodatno = maliTekst(this@MainActivity, "").apply { visibility = View.GONE }
        private val unutra: LinearLayout

        init {
            val (kartica, sadrzaj) = kartica(this@MainActivity)
            sadrzaj.addView(korak)
            sadrzaj.addView(traka)
            sadrzaj.addView(nalaz)
            sadrzaj.addView(dodatno)
            unutra = sadrzaj
            tok.removeAllViews()
            tok.addView(kartica)
        }

        private fun tekstKoraka(redni: Int) =
            if (ukupno == 1) "Skeniram sliku…" else "Skeniram sliku ${redni + 1} od $ukupno…"

        fun naSlici(redni: Int, pronadjeno: Int) {
            if (isDestroyed) return
            korak.text = tekstKoraka(redni)
            nalaz.text = "Pronađenih računa: $pronadjeno"
        }

        fun zavrsenaSlika(zavrseno: Int, pronadjeno: Int) {
            if (isDestroyed) return
            if (!traka.isIndeterminate) traka.setProgressCompat(zavrseno, true)
            nalaz.text = "Pronađenih računa: $pronadjeno"
        }

        fun poruka(tekst: String) {
            if (isDestroyed) return
            dodatno.text = tekst
            dodatno.visibility = if (tekst.isBlank()) View.GONE else View.VISIBLE
        }

        fun zavrsi(poruka: String) {
            korak.text = "Uvoz iz galerije je gotov"
            traka.visibility = View.GONE
            nalaz.text = poruka
            unutra.addView(dugme(this@MainActivity, "U redu") {
                tok.removeAllViews()
                Obavestenja.ukloni(this@MainActivity)
            })
        }
    }

    /** Ispričana kupovina postaje zapis sa stavkama, bez otvaranja novog ekrana. */
    private fun razloziUnos(tekst: String, posleUspeha: () -> Unit) {
        val kontekst = applicationContext
        Toast.makeText(this, "Šaljem tekst Gemini-ju…", Toast.LENGTH_SHORT).show()
        izvrsilacUvoza.execute {
            val ishod = runCatching { Diktat.sredi(kontekst, tekst) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                ishod.onSuccess { procitano ->
                    if (procitano == null || procitano.stavke.isEmpty()) {
                        porukaOGresci(
                            "Nije razloženo",
                            IllegalStateException(
                                "Iz teksta nije izvučena nijedna stavka. Sačuvaj ga kao belešku."
                            ),
                        )
                        return@onSuccess
                    }
                    baza.dodajRucnoSaStavkama(
                        naziv = procitano.radnja.ifBlank { LogikaRacuna.rucniNaziv(tekst) },
                        tekst = tekst,
                        ukupanIznosPara = procitano.ukupanIznosPara
                            ?: procitano.stavke.sumOf { it.ukupnoPara }.takeIf { it > 0 },
                        datumRacuna = procitano.vreme,
                        stavke = procitano.stavke,
                    )
                    posleUspeha()
                    osvezi()
                    ObradaRacuna.zakazi(this)
                    Toast.makeText(
                        this,
                        "Sačuvano, stavki: ${procitano.stavke.size}",
                        Toast.LENGTH_LONG,
                    ).show()
                }.onFailure { porukaOGresci("Nije razloženo", it) }
            }
        }
    }

    private fun unosPfrBroja() {
        val stubac = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(4))
        }
        stubac.addView(maliTekst(
            this,
            "Prepiši četiri polja sa dna računa. Aplikacija otvara zvaničnu stranicu " +
                "Poreske uprave sa već popunjenim poljima, a odatle preuzima sve stavke.",
        ))
        val (okvirBroja, unosBroja) = polje(this, "ПФР број рачуна", redova = 1)
        val (okvirBrojaca, unosBrojaca) = polje(this, "Бројач рачуна, na primer 2078/2088ПП", redova = 1)
        val (okvirIznosa, unosIznosa) = polje(this, "Укупан износ, na primer 1619,99", redova = 1)
        val (okvirVremena, unosVremena) = polje(this, "ПФР време, 17.9.2026. 20:37:48", redova = 1)
        for (okvir in listOf(okvirBroja, okvirBrojaca, okvirIznosa, okvirVremena)) {
            stubac.addView(okvir)
        }

        AlertDialog.Builder(this)
            .setTitle("Унос ПФР броја")
            .setView(ScrollView(this).apply { addView(stubac) })
            .setPositiveButton("Otvori proveru") { _, _ ->
                val broj = unosBroja.text?.toString().orEmpty().trim()
                val brojac = unosBrojaca.text?.toString().orEmpty().trim()
                val iznos = unosIznosa.text?.toString().orEmpty().trim()
                val vreme = unosVremena.text?.toString().orEmpty().trim()
                if (broj.isBlank() || brojac.isBlank() || iznos.isBlank() || vreme.isBlank()) {
                    Toast.makeText(this, "Sva četiri polja su obavezna", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val id = baza.dodajSaSlike(
                    naziv = "Račun sa ПФР броја",
                    tekst = "Unet ПФР број, čeka zvaničnu proveru.",
                    brojRacuna = broj,
                    brojac = brojac,
                    ukupanIznosPara = LogikaRacuna.uPare(iznos),
                    datumRacuna = LogikaRacuna.uVreme(vreme),
                    izvornaSlika = "",
                    stanje = LogikaRacuna.CEKA_PROVERU,
                    stavke = emptyList(),
                )
                osvezi()
                if (id == -1L) {
                    Toast.makeText(this, "Ovaj račun je već sačuvan", Toast.LENGTH_SHORT).show()
                } else {
                    otvoriProveru(id, broj, brojac, iznos, vreme)
                }
            }
            .setNegativeButton("Odustani", null)
            .show()
    }

    private fun pokreniProveru(racun: Racun) {
        otvoriProveru(
            racun.id,
            racun.brojRacuna,
            racun.brojac,
            racun.ukupanIznosPara?.let { "%d,%02d".format(it / 100, it % 100) }.orEmpty(),
            racun.datumRacuna?.let { vremeZaProveru.format(Date(it)) }.orEmpty(),
        )
    }

    private fun otvoriProveru(
        id: Long,
        broj: String,
        brojac: String,
        iznos: String,
        vreme: String,
    ) {
        startActivity(
            Intent(this, ProveraAktivnost::class.java)
                .putExtra(ProveraAktivnost.ID_RACUNA, id)
                .putExtra(ProveraAktivnost.BROJ, broj)
                .putExtra(ProveraAktivnost.BROJAC, brojac)
                .putExtra(ProveraAktivnost.IZNOS, iznos)
                .putExtra(ProveraAktivnost.VREME, vreme)
        )
    }

    private fun podesavanja() {
        val stubac = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(4))
        }

        stubac.addView(odeljak(this, "Razvrstavanje proizvoda"))
        stubac.addView(maliTekst(
            this,
            "Nazivi proizvoda se šalju Gemini-ju da ih svrsta u kategorije i označi " +
                "kao zdravo, umereno ili nezdravo. Šalju se samo nazivi, nikada iznosi, " +
                "radnja, PIB ni broj računa. Svaki naziv se pita najviše jednom. " +
                "Ključ se pravi na aistudio.google.com i ostaje samo na ovom telefonu.",
        ))
        val (okvirKljuca, unosKljuca) = polje(this, "Gemini API ključ", redova = 1)
        unosKljuca.setText(Podesavanja.geminiKljuc(this))
        stubac.addView(okvirKljuca)
        val (okvirModela, unosModela) = polje(this, "Model", redova = 1)
        unosModela.setText(Podesavanja.geminiModel(this))
        stubac.addView(okvirModela)

        val ukupnoStavki = baza.brojStavki()
        val razvrstano = baza.brojKategorisanih()
        stubac.addView(maliTekst(this, "Razvrstano: $razvrstano od $ukupnoStavki stavki."))
        val greska = Podesavanja.greskaKategorija(this)
        if (greska.isNotBlank()) {
            stubac.addView(maliTekst(this, "Poslednja greška: $greska"))
        }

        stubac.addView(odeljak(this, "Bekap"))
        stubac.addView(maliTekst(
            this,
            "Bekap je jedan JSON fajl sa svim računima i stavkama. Pri čuvanju biraš " +
                "gde ide, na primer u Google Drive. Ključ i podešavanja nisu deo fajla.",
        ))
        stubac.addView(dugme(this, "Sačuvaj bekap") {
            izvozBekapa.launch("racuni-${imeFajla.format(Date())}.json")
        })
        stubac.addView(dugme(this, "Vrati iz bekapa") {
            vracanjeBekapa.launch(arrayOf("application/json", "text/plain", "*/*"))
        })

        stubac.addView(odeljak(this, "Slike koje čekaju obradu"))
        val cekaju = baza.svi().count {
            it.stanje == LogikaRacuna.CEKA_PROVERU || it.stanje == LogikaRacuna.NECITLJIVO
        }
        stubac.addView(maliTekst(
            this,
            "Spisak je Markdown fajl sa slikama koje aplikacija nije mogla da pročita " +
                "do kraja, i sa uputstvom šta sa njima. Sačuvaj ga u isti folder u kom " +
                "stoje fotografije, pa obradu možeš da uradiš na računaru. Rezultat se " +
                "vraća kroz „Vrati iz bekapa\". Čeka obradu: $cekaju.",
        ))
        stubac.addView(dugme(this, "Sačuvaj spisak za obradu") {
            izvozSpiska.launch("racuni-za-obradu-${imeFajla.format(Date())}.md")
        })

        AlertDialog.Builder(this)
            .setTitle("Podešavanja i bekap")
            .setView(ScrollView(this).apply { addView(stubac) })
            .setPositiveButton("Sačuvaj") { _, _ ->
                Podesavanja.sacuvajGemini(
                    this,
                    unosKljuca.text?.toString().orEmpty(),
                    unosModela.text?.toString().orEmpty(),
                )
                Podesavanja.zapisiGresku(this, "")
                // Razvrstavanje kreće čim ima mreže.
                ObradaRacuna.zakazi(this)
                Toast.makeText(this, "Podešavanja su sačuvana", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Zatvori", null)
            .show()
    }

    private fun sacuvajBekap(gde: Uri) = sacuvajUFajl(gde) { Bekap.kaoJson(it.svi()) }

    private fun sacuvajUFajl(gde: Uri, napravi: (Baza) -> String) {
        val kontekst = applicationContext
        izvrsilacUvoza.execute {
            val ishod = runCatching {
                val bazaIzvoza = Baza(kontekst)
                val tekst = try {
                    napravi(bazaIzvoza)
                } finally {
                    bazaIzvoza.close()
                }
                kontekst.contentResolver.openOutputStream(gde, "wt")
                    ?.use { it.write(tekst.toByteArray(Charsets.UTF_8)) }
                    ?: error("Nije moguće pisati u izabrani fajl")
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                ishod.onSuccess {
                    Toast.makeText(this, "Fajl je sačuvan", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    porukaOGresci("Fajl nije sačuvan", it)
                }
            }
        }
    }

    private fun vratiBekap(odakle: Uri) {
        val kontekst = applicationContext
        izvrsilacUvoza.execute {
            val ishod = runCatching {
                val tekst = kontekst.contentResolver.openInputStream(odakle)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("Fajl nije moguće pročitati")
                val racuni = Bekap.izJson(tekst)
                val bazaUvoza = Baza(kontekst)
                try {
                    bazaUvoza.uvezi(racuni) to racuni.size
                } finally {
                    bazaUvoza.close()
                }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                ishod.onSuccess { (dodato, ukupno) ->
                    if (dodato > 0) ObradaRacuna.zakazi(this)
                    osvezi()
                    Toast.makeText(
                        this,
                        "Vraćeno: $dodato • već postojalo: ${ukupno - dodato}",
                        Toast.LENGTH_LONG,
                    ).show()
                }.onFailure {
                    porukaOGresci("Bekap nije vraćen", it)
                }
            }
        }
    }

    private fun porukaOGresci(naslov: String, greska: Throwable) {
        AlertDialog.Builder(this)
            .setTitle(naslov)
            .setMessage(greska.message ?: greska.javaClass.simpleName)
            .setPositiveButton("U redu", null)
            .show()
    }

    private fun rucniUnos(): ViewGroup {
        val (kartica, unutra) = kartica(this)
        unutra.addView(TextView(this).apply {
            setText(R.string.manual_entry_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        })
        unutra.addView(maliTekst(
            this,
            "Upiši ili izdiktiraj mikrofonom na tastaturi šta je kupljeno, gde i po " +
                "kojoj ceni. Gemini od toga pravi stavke, pa kupovina bez računa ulazi " +
                "u iste preglede kao i sve ostalo.",
        ))
        val (okvir, unos) = polje(this, "Podaci o kupovini")
        okvir.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(10) }
        unutra.addView(okvir)
        unutra.addView(dugme(this, "Sačuvaj kao belešku") {
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
        unutra.addView(dugme(this, "Razloži na stavke preko Gemini-ja") {
            val tekst = unos.text?.toString().orEmpty().trim()
            when {
                tekst.isBlank() -> okvir.error = "Unesi bar jednu informaciju"
                Podesavanja.geminiKljuc(this).isBlank() -> {
                    okvir.error = null
                    Toast.makeText(
                        this,
                        "Prvo unesi Gemini ključ u „Podešavanja i bekap\"",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                else -> {
                    okvir.error = null
                    razloziUnos(tekst) { unos.text?.clear() }
                }
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

        ciscenje.removeAllViews()
        val prazni = baza.brojBezPodataka()
        if (prazni > 0) {
            ciscenje.addView(dugme(this, "Obriši kodove bez podataka ($prazni)") {
                potvrdi(
                    "Obriši kodove bez podataka?",
                    "Briše se $prazni sačuvanih kodova koji nisu fiskalni računi, " +
                        "pa iz njih nikada ne mogu da se dobiju prodavnica, iznos i stavke.",
                ) {
                    val obrisano = baza.obrisiBezPodataka()
                    osvezi()
                    Toast.makeText(this, "Obrisano: $obrisano", Toast.LENGTH_SHORT).show()
                }
            })
        }

        spisak.removeAllViews()
        for (racun in racuni) spisak.addView(redRacuna(racun))
    }

    /** Brisanje se uvek prvo potvrđuje, jer nema opoziva. */
    private fun potvrdi(naslov: String, poruka: String, radnja: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(naslov)
            .setMessage(poruka)
            .setPositiveButton("Obriši") { _, _ -> radnja() }
            .setNegativeButton("Odustani", null)
            .show()
    }

    private fun obrisiRacun(racun: Racun) {
        potvrdi(
            "Obriši ovaj zapis?",
            "${nazivRacuna(racun)}\n${datum.format(Date(racun.datumRacuna ?: racun.nastao))}",
        ) {
            baza.obrisi(racun.id)
            osvezi()
            Toast.makeText(this, "Zapis je obrisan", Toast.LENGTH_SHORT).show()
        }
    }

    /** U spisku stoje samo vreme, mesto i iznos; ostalo se otvara klikom. */
    private fun redRacuna(racun: Racun): ViewGroup {
        val (kartica, unutra) = kartica(this)
        unutra.addView(TextView(this).apply {
            text = datum.format(Date(racun.datumRacuna ?: racun.nastao))
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        })
        unutra.addView(vrednost(this, nazivRacuna(racun)))
        if (LogikaRacuna.bezKorisnihPodataka(racun)) {
            unutra.addView(maliTekst(this, "Kôd nije fiskalni račun; dugi pritisak briše zapis."))
        }
        val mesto = kratkaLokacija(racun)
        if (mesto.isNotBlank()) unutra.addView(maliTekst(this, mesto))

        val dodatno = buildString {
            if (racun.ukupanIznosPara != null) append(dinari(racun.ukupanIznosPara))
            if (racun.stavke.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append("stavki: ").append(racun.stavke.size)
            }
            val cekanje = when (racun.stanje) {
                LogikaRacuna.CEKA_MREZU -> "čeka internet"
                LogikaRacuna.GRESKA -> "obrada se ponavlja"
                LogikaRacuna.CEKA_PROVERU -> "čeka zvaničnu proveru"
                LogikaRacuna.NECITLJIVO -> "sa slike, nepotpuno"
                else -> ""
            }
            if (cekanje.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(cekanje)
            }
        }
        if (dodatno.isNotBlank()) {
            unutra.addView(maliTekst(this, dodatno).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) }
            })
        }

        kartica.isClickable = true
        kartica.isFocusable = true
        kartica.setOnClickListener { prikaziRacun(racun) }
        kartica.setOnLongClickListener {
            obrisiRacun(racun)
            true
        }
        return kartica
    }

    /** Klik na račun: prvo vreme, pa lokacija, pa kupljene stavke. */
    private fun prikaziRacun(racun: Racun) {
        val stubac = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), dp(4))
        }

        stubac.addView(odeljak(this, "Vreme"))
        stubac.addView(vrednost(this, datumSaSekundama.format(Date(racun.datumRacuna ?: racun.nastao))))
        if (racun.datumRacuna == null) {
            stubac.addView(maliTekst(this, "Vreme unosa u aplikaciju; vreme sa računa još nije preuzeto."))
        }

        stubac.addView(odeljak(this, "Lokacija"))
        val lokacija = punaLokacija(racun)
        if (lokacija.isNotBlank()) {
            stubac.addView(vrednost(this, lokacija))
        } else {
            stubac.addView(maliTekst(this, "Lokacija će se pojaviti kada račun bude obrađen."))
        }

        if (racun.stavke.isNotEmpty()) {
            stubac.addView(odeljak(this, "Stavke (${racun.stavke.size})"))
            for (stavka in racun.stavke) {
                stubac.addView(vrednost(this, "• ${stavka.naziv}"))
                stubac.addView(maliTekst(
                    this,
                    "${stavka.kolicina} × ${dinari(stavka.jedinicnaCenaPara)}" +
                        " = ${dinari(stavka.ukupnoPara)}",
                ).apply { setPadding(dp(14), 0, 0, dp(6)) })
            }
        } else if (racun.tekst.isNotBlank() && racun.izvor == LogikaRacuna.IZVOR_RUCNO) {
            stubac.addView(odeljak(this, "Beleška"))
            stubac.addView(vrednost(this, racun.tekst))
        } else {
            stubac.addView(odeljak(this, "Stavke"))
            stubac.addView(maliTekst(this, kadaStizuStavke(racun)))
        }

        if (racun.ukupanIznosPara != null) {
            stubac.addView(odeljak(this, "Ukupno"))
            stubac.addView(TextView(this).apply {
                text = dinari(racun.ukupanIznosPara)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
            })
        }

        if (LogikaRacuna.spremanZaProveru(racun) && racun.stanje != LogikaRacuna.SACUVANO) {
            stubac.addView(odeljak(this, "Zvanična provera"))
            stubac.addView(maliTekst(
                this,
                "Podaci su pročitani sa slike. Provera na stranici Poreske uprave " +
                    "donosi tačan iznos i sve stavke. Stranica sama traži potvrdu da " +
                    "nisi robot, pa se otvara u aplikaciji sa već popunjenim poljima.",
            ))
            stubac.addView(dugme(this, "Proveri preko ПФР броја") { pokreniProveru(racun) })
        }

        val tehnicki = jednako(this, tehnickiPodaci(racun)).apply {
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }
        val prekidac = dugme(this, "Prikaži detalje") {}
        prekidac.setOnClickListener {
            val bioVidljiv = tehnicki.visibility == View.VISIBLE
            tehnicki.visibility = if (bioVidljiv) View.GONE else View.VISIBLE
            prekidac.text = if (bioVidljiv) "Prikaži detalje" else "Sakrij detalje"
        }
        prekidac.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(18) }
        stubac.addView(prekidac)
        stubac.addView(tehnicki)

        val dijalog = AlertDialog.Builder(this)
            .setTitle(nazivRacuna(racun))
            .setView(ScrollView(this).apply { addView(stubac) })
            .setPositiveButton("Zatvori", null)
            .setNegativeButton("Obriši") { _, _ -> obrisiRacun(racun) }

        if (LogikaRacuna.internetAdresa(racun.qrSadrzaj)) {
            dijalog.setNeutralButton("Otvori digitalni račun") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(racun.qrSadrzaj)))
            }
        }
        dijalog.show()
    }

    private fun nazivRacuna(racun: Racun): String = racun.preduzece
        .ifBlank { racun.prodajnoMesto }
        .ifBlank { if (LogikaRacuna.bezKorisnihPodataka(racun)) "Kôd bez podataka" else racun.naziv }

    /** Jedan red za spisak: prodajno mesto i grad, bez ponavljanja naziva firme. */
    private fun kratkaLokacija(racun: Racun): String {
        val naziv = nazivRacuna(racun)
        return listOf(racun.prodajnoMesto, racun.grad)
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(naziv, ignoreCase = true) }
            .distinct()
            .joinToString(", ")
    }

    private fun punaLokacija(racun: Racun): String = buildString {
        val naziv = nazivRacuna(racun)
        if (racun.prodajnoMesto.isNotBlank() && !racun.prodajnoMesto.equals(naziv, ignoreCase = true)) {
            append(racun.prodajnoMesto).append('\n')
        }
        if (racun.adresa.isNotBlank()) append(racun.adresa).append('\n')
        val mesto = listOf(racun.grad, racun.opstina)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
        if (mesto.isNotBlank()) append(mesto)
    }.trim()

    private fun kadaStizuStavke(racun: Racun): String = when {
        racun.izvor == LogikaRacuna.IZVOR_SLIKA ->
            "Sa slike nisu pročitane pojedinačne stavke. Zvanična provera preko " +
                "ПФР броја ih donosi tačno onako kako ih vodi Poreska uprava."
        racun.izvor == LogikaRacuna.IZVOR_RUCNO -> "Ručni unos nema pojedinačne stavke."
        LogikaRacuna.bezKorisnihPodataka(racun) ->
            "Ovaj kôd nije fiskalni račun Poreske uprave, pa nema stavki. " +
                "Takvi kodovi se najčešće nalaze na internim nalepnicama radnje."
        racun.stanje == LogikaRacuna.GRESKA -> "Obrada nije uspela; biće ponovljena."
        else -> "Stavke stižu kada aplikacija dobije internet i preuzme digitalni račun."
    }

    /** Sve ostalo, iza dugmeta „Prikaži detalje". */
    private fun tehnickiPodaci(racun: Racun): String = buildString {
        if (racun.preduzece.isNotBlank()) append("Preduzeće: ").append(racun.preduzece).append('\n')
        if (racun.pib.isNotBlank()) append("PIB: ").append(racun.pib).append('\n')
        if (racun.brojRacuna.isNotBlank()) append("Broj računa: ").append(racun.brojRacuna).append('\n')
        append("Sačuvano u aplikaciji: ").append(datumSaSekundama.format(Date(racun.nastao))).append('\n')
        append("Izvor: ").append(
            when (racun.izvor) {
                LogikaRacuna.IZVOR_QR -> "QR kôd"
                LogikaRacuna.IZVOR_SLIKA -> "fotografija računa"
                else -> "ručni unos"
            }
        ).append('\n')
        append("Stanje: ").append(
            when (racun.stanje) {
                LogikaRacuna.CEKA_MREZU -> "čeka internet"
                LogikaRacuna.GRESKA -> "greška, obrada se ponavlja"
                LogikaRacuna.CEKA_PROVERU -> "pročitano sa slike, čeka zvaničnu proveru"
                LogikaRacuna.NECITLJIVO -> "pročitano sa slike, nepotpuno"
                else -> "obrađeno"
            }
        ).append('\n')
        if (racun.brojac.isNotBlank()) append("Brojač računa: ").append(racun.brojac).append('\n')
        if (racun.izvornaSlika.isNotBlank()) {
            append("Slika: ").append(racun.izvornaSlika).append('\n')
        }
        if (racun.stavke.isNotEmpty()) {
            append("\nPorezi po stavkama:\n")
            for (stavka in racun.stavke) {
                append("• ").append(stavka.naziv).append('\n')
                append("  osnovica ").append(dinari(stavka.poreskaOsnovicaPara))
                    .append(", PDV ").append(dinari(stavka.pdvPara))
                if (stavka.poreskaOznaka.isNotBlank() || stavka.poreskaStopa.isNotBlank()) {
                    append(" (").append(
                        listOf(stavka.poreskaOznaka, stavka.poreskaStopa)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    ).append(')')
                }
                append('\n')
            }
        }
        if (racun.tekst.isNotBlank() && racun.izvor != LogikaRacuna.IZVOR_RUCNO) {
            append("\nZapis računa:\n").append(racun.tekst).append('\n')
        }
        if (racun.qrSadrzaj.isNotBlank() && racun.qrSadrzaj != racun.tekst) {
            append("\nQR sadržaj:\n").append(racun.qrSadrzaj).append('\n')
        }
        if (racun.greska.isNotBlank()) append("\nPoslednja greška:\n").append(racun.greska)
    }.trim()

}
