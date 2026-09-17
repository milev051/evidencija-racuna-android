package studio.room211.racuni

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.DynamicColors
import studio.room211.racuni.Ui.dinari
import studio.room211.racuni.Ui.dp
import studio.room211.racuni.Ui.kartica
import studio.room211.racuni.Ui.maliTekst
import studio.room211.racuni.Ui.odeljak
import studio.room211.racuni.Ui.vrednost
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pregled onoga što je kupljeno, bez ijednog novog zahteva prema mreži.
 *
 * Sve se računa iz već sačuvanih računa i stavki: po mesecu, po radnji i po
 * vrsti radnje. Filteri se slažu, pa „jul" i „LIDL" daju samo julske kupovine
 * u Lidlu.
 */
class PregledAktivnost : AppCompatActivity() {
    private enum class Prikaz { STAVKE, RADNJE, VRSTE, KATEGORIJE, ZDRAVLJE }

    private lateinit var baza: Baza
    private lateinit var sadrzaj: LinearLayout
    private lateinit var filteri: LinearLayout
    private val datumStavke = SimpleDateFormat("dd.MM.yyyy. HH:mm", Locale("sr", "RS"))
    private val kljucMeseca = SimpleDateFormat("yyyy-MM", Locale("sr", "RS"))
    private val imeMeseca = SimpleDateFormat("LLLL yyyy.", Locale("sr", "RS"))

    private var prikaz = Prikaz.STAVKE
    private var mesec: String? = null
    private var radnja: String? = null
    private var vrsta: String? = null
    private var kategorija: String? = null
    private var zdravlje: String? = null
    private var racuni: List<Racun> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        baza = Baza(this)

        val koren = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14))
        }
        koren.addView(TextView(this).apply {
            text = "Pregled kupovina"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)
            setPadding(dp(4), dp(4), dp(4), dp(8))
        })

        filteri = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        koren.addView(filteri)

        sadrzaj = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        koren.addView(sadrzaj)

        setContentView(ScrollView(this).apply {
            addView(koren, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        ucitaj()
    }

    override fun onDestroy() {
        baza.close()
        super.onDestroy()
    }

    private fun ucitaj() {
        // U pregled ulaze samo obrađeni računi; kôd bez podataka nema šta da doprinese.
        racuni = baza.svi().filter { it.stavke.isNotEmpty() || it.ukupanIznosPara != null }
        osvezi()
    }

    private fun osvezi() {
        nacrtajFiltere()
        sadrzaj.removeAllViews()
        val izabrani = izabraniRacuni()

        if (racuni.isEmpty()) {
            sadrzaj.addView(maliTekst(this, "Još nema obrađenih računa sa stavkama."))
            return
        }

        val ukupno = izabrani.sumOf { zbir(it) }
        sadrzaj.addView(vrednost(this, "Ukupno: ${dinari(ukupno)}"))
        sadrzaj.addView(maliTekst(this, "Računa: ${izabrani.size} • stavki: ${izabrani.sumOf { it.stavke.size }}"))

        when (prikaz) {
            Prikaz.STAVKE -> nacrtajStavke(izabrani)
            Prikaz.RADNJE -> nacrtajZbirove(
                izabrani.groupBy { LogikaRacuna.kratakNazivRadnje(nazivRadnje(it)) },
                "Radnja nema naziv",
            ) { izabrana -> radnja = izabrana; prikaz = Prikaz.STAVKE }
            Prikaz.VRSTE -> nacrtajZbirove(
                izabrani.groupBy { LogikaRacuna.vrstaRadnje(nazivRadnje(it)) },
                LogikaRacuna.VRSTA_OSTALO,
            ) { izabrana -> vrsta = izabrana; prikaz = Prikaz.STAVKE }
            Prikaz.KATEGORIJE -> nacrtajZbiroveStavki(
                izabraneStavke().groupBy { it.second.kategorija.ifBlank { NERAZVRSTANO } }
            ) { izabrana -> kategorija = izabrana; prikaz = Prikaz.STAVKE }
            Prikaz.ZDRAVLJE -> nacrtajZbiroveStavki(
                izabraneStavke().groupBy { imeZdravlja(it.second.zdravlje) }
            ) { izabrano -> zdravlje = izabrano; prikaz = Prikaz.STAVKE }
        }
    }

    private fun nacrtajFiltere() {
        filteri.removeAllViews()

        val prikazi = ChipGroup(this).apply { isSingleSelection = true }
        for (mogucnost in Prikaz.values()) {
            prikazi.addView(cip(imePrikaza(mogucnost), prikaz == mogucnost) {
                prikaz = mogucnost
                osvezi()
            })
        }
        filteri.addView(prikazi)

        val meseci = racuni.map { kljucMeseca.format(Date(vreme(it))) }.distinct().sortedDescending()
        if (meseci.size > 1 || mesec != null) {
            filteri.addView(odeljak(this, "Mesec"))
            val grupa = ChipGroup(this).apply { isSingleSelection = true }
            grupa.addView(cip("Sve", mesec == null) { mesec = null; osvezi() })
            for (kljuc in meseci) {
                grupa.addView(cip(imeMeseca(kljuc), mesec == kljuc) { mesec = kljuc; osvezi() })
            }
            filteri.addView(grupa)
        }

        if (radnja != null || vrsta != null || kategorija != null || zdravlje != null) {
            val grupa = ChipGroup(this)
            radnja?.let { grupa.addView(cip("Radnja: $it", true) { radnja = null; osvezi() }) }
            vrsta?.let { grupa.addView(cip("Vrsta: $it", true) { vrsta = null; osvezi() }) }
            kategorija?.let {
                grupa.addView(cip("Kategorija: $it", true) { kategorija = null; osvezi() })
            }
            zdravlje?.let { grupa.addView(cip("Zdravlje: $it", true) { zdravlje = null; osvezi() }) }
            filteri.addView(maliTekst(this, "Klik na filter ga uklanja."))
            filteri.addView(grupa)
        }
    }

    private fun nacrtajStavke(izabrani: List<Racun>) {
        val saStavkama = izabrani
            .map { racun -> racun to racun.stavke.filter { uKategoriji(it) } }
            .filter { (_, stavke) -> stavke.isNotEmpty() }
        if (saStavkama.isEmpty()) {
            sadrzaj.addView(maliTekst(this, "Za izabrani filter nema pojedinačnih stavki."))
            return
        }
        for ((racun, stavke) in saStavkama) {
            val (kartica, unutra) = kartica(this)
            unutra.addView(TextView(this).apply {
                text = datumStavke.format(Date(vreme(racun)))
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            })
            unutra.addView(maliTekst(this, LogikaRacuna.kratakNazivRadnje(nazivRadnje(racun))))
            for (stavka in stavke) {
                unutra.addView(vrednost(this, "• ${stavka.naziv}"))
                val opis = buildString {
                    append(stavka.kolicina).append(" × ").append(dinari(stavka.jedinicnaCenaPara))
                    append(" = ").append(dinari(stavka.ukupnoPara))
                    if (stavka.kategorija.isNotBlank()) {
                        append("\n").append(stavka.kategorija)
                        if (stavka.zdravlje.isNotBlank()) append(" • ").append(stavka.zdravlje)
                    }
                }
                unutra.addView(maliTekst(this, opis).apply { setPadding(dp(14), 0, 0, dp(6)) })
            }
            unutra.addView(maliTekst(this, "Račun: ${dinari(zbir(racun))}"))
            sadrzaj.addView(kartica)
        }
    }

    private fun nacrtajZbirove(
        grupe: Map<String, List<Racun>>,
        praznoIme: String,
        klik: (String) -> Unit,
    ) {
        val poredak = grupe.entries.sortedByDescending { red -> red.value.sumOf { zbir(it) } }
        for ((ime, racuniGrupe) in poredak) {
            val naziv = ime.ifBlank { praznoIme }
            val (kartica, unutra) = kartica(this)
            unutra.addView(TextView(this).apply {
                text = naziv
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            })
            unutra.addView(vrednost(this, dinari(racuniGrupe.sumOf { zbir(it) })))
            unutra.addView(
                maliTekst(
                    this,
                    "Računa: ${racuniGrupe.size} • stavki: ${racuniGrupe.sumOf { it.stavke.size }}",
                )
            )
            kartica.isClickable = true
            kartica.isFocusable = true
            kartica.setOnClickListener {
                klik(naziv)
                osvezi()
            }
            sadrzaj.addView(kartica)
        }
    }

    private fun nacrtajZbiroveStavki(
        grupe: Map<String, List<Pair<Racun, Stavka>>>,
        klik: (String) -> Unit,
    ) {
        if (grupe.isEmpty()) {
            sadrzaj.addView(maliTekst(this, "Za izabrani filter nema stavki."))
            return
        }
        val poredak = grupe.entries.sortedByDescending { red -> red.value.sumOf { it.second.ukupnoPara } }
        for ((ime, stavke) in poredak) {
            val (kartica, unutra) = kartica(this)
            unutra.addView(TextView(this).apply {
                text = ime
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            })
            unutra.addView(vrednost(this, dinari(stavke.sumOf { it.second.ukupnoPara })))
            unutra.addView(maliTekst(this, "Stavki: ${stavke.size}"))
            kartica.isClickable = true
            kartica.isFocusable = true
            kartica.setOnClickListener {
                klik(ime)
                osvezi()
            }
            sadrzaj.addView(kartica)
        }
        if (poredak.any { it.key == NERAZVRSTANO }) {
            sadrzaj.addView(maliTekst(
                this,
                "Nerazvrstano čeka da se u podešavanjima unese Gemini ključ i da se pojavi internet.",
            ))
        }
    }

    private fun izabraneStavke(): List<Pair<Racun, Stavka>> =
        izabraniRacuni().flatMap { racun -> racun.stavke.map { racun to it } }

    private fun uKategoriji(stavka: Stavka): Boolean {
        val poKategoriji = kategorija == null ||
            stavka.kategorija.ifBlank { NERAZVRSTANO } == kategorija
        val poZdravlju = zdravlje == null || imeZdravlja(stavka.zdravlje) == zdravlje
        return poKategoriji && poZdravlju
    }

    private fun imeZdravlja(oznaka: String): String = oznaka.ifBlank { NERAZVRSTANO }
        .replaceFirstChar { it.uppercase(Locale("sr", "RS")) }

    private fun izabraniRacuni(): List<Racun> = racuni.filter { racun ->
        val uMesecu = mesec == null || kljucMeseca.format(Date(vreme(racun))) == mesec
        val uRadnji = radnja == null ||
            LogikaRacuna.kratakNazivRadnje(nazivRadnje(racun)) == radnja
        val uVrsti = vrsta == null || LogikaRacuna.vrstaRadnje(nazivRadnje(racun)) == vrsta
        uMesecu && uRadnji && uVrsti
    }

    private fun cip(tekst: String, izabran: Boolean, klik: () -> Unit) = Chip(this).apply {
        text = tekst
        isCheckable = true
        isChecked = izabran
        setOnClickListener { klik() }
    }

    private fun imePrikaza(vrednost: Prikaz) = when (vrednost) {
        Prikaz.STAVKE -> "Stavke"
        Prikaz.RADNJE -> "Radnje"
        Prikaz.VRSTE -> "Vrste radnji"
        Prikaz.KATEGORIJE -> "Kategorije"
        Prikaz.ZDRAVLJE -> "Zdravlje"
    }

    private fun imeMeseca(kljuc: String): String {
        val delovi = kljuc.split("-")
        val kalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, delovi[0].toInt())
            set(Calendar.MONTH, delovi[1].toInt() - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return imeMeseca.format(kalendar.time).replaceFirstChar { it.uppercase(Locale("sr", "RS")) }
    }

    private fun nazivRadnje(racun: Racun): String =
        racun.preduzece.ifBlank { racun.prodajnoMesto }.ifBlank { racun.naziv }

    /** Zbir sa računa je tačniji od sabiranja stavki, ali stavke su rezerva. */
    private fun zbir(racun: Racun): Long =
        racun.ukupanIznosPara ?: racun.stavke.sumOf { it.ukupnoPara }

    private fun vreme(racun: Racun): Long = racun.datumRacuna ?: racun.nastao

    private companion object {
        const val NERAZVRSTANO = "Nerazvrstano"
    }
}
