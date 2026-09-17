package studio.room211.racuni

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
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
 * Sve se računa iz već sačuvanih računa i stavki: spisak računa, stavke po
 * mesecu, zbir po radnji, po vrsti radnje, po kategoriji i po oznaci zdravlja.
 * Filteri se slažu, pa „jul" i „LIDL" daju samo julske kupovine u Lidlu.
 *
 * Nije aktivnost, nego pogled unutar taba, pa se stanje filtera ne gubi kada
 * se pređe na drugi tab i nazad.
 */
class PregledPrikaz(
    private val aktivnost: AppCompatActivity,
    private val baza: Baza,
    /** Kartica računa se pravi u glavnom ekranu, da klik i brisanje rade isto. */
    private val karticaRacuna: (Racun) -> View,
) {
    private enum class Prikaz { RACUNI, STAVKE, RADNJE, KATEGORIJE, ZDRAVLJE, VRSTE }

    private val datumStavke = SimpleDateFormat("dd.MM.yyyy. HH:mm", Locale("sr", "RS"))
    private val kljucMeseca = SimpleDateFormat("yyyy-MM", Locale("sr", "RS"))
    private val imeMeseca = SimpleDateFormat("LLLL yyyy.", Locale("sr", "RS"))

    private val filteri = LinearLayout(aktivnost).apply { orientation = LinearLayout.VERTICAL }
    private val sadrzaj = LinearLayout(aktivnost).apply { orientation = LinearLayout.VERTICAL }

    val koren: View = ScrollView(aktivnost).apply {
        addView(
            LinearLayout(aktivnost).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(aktivnost.dp(14), aktivnost.dp(6), aktivnost.dp(14), aktivnost.dp(14))
                addView(TextView(aktivnost).apply {
                    text = "Pregled kupovina"
                    setTextAppearance(
                        com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall
                    )
                    setPadding(aktivnost.dp(4), 0, aktivnost.dp(4), aktivnost.dp(8))
                })
                addView(filteri)
                addView(sadrzaj)
            },
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private var prikaz = Prikaz.RACUNI
    private var mesec: String? = null
    private var radnja: String? = null
    private var vrsta: String? = null
    private var kategorija: String? = null
    private var zdravlje: String? = null
    private var poCeni = false
    private var sviRacuni: List<Racun> = emptyList()
    private var racuni: List<Racun> = emptyList()

    fun osvezi() {
        sviRacuni = baza.svi()
        // U zbirove ulaze samo obrađeni računi; kôd bez podataka nema šta da doprinese.
        racuni = sviRacuni.filter { it.stavke.isNotEmpty() || it.ukupanIznosPara != null }
        nacrtaj()
    }

    private fun nacrtaj() {
        nacrtajFiltere()
        sadrzaj.removeAllViews()

        if (prikaz == Prikaz.RACUNI) {
            if (sviRacuni.isEmpty()) {
                sadrzaj.addView(maliTekst(aktivnost, "Još nema sačuvanih računa."))
                return
            }
            val izabrani = sviRacuni.filter { uMesecu(it) }
            sadrzaj.addView(maliTekst(aktivnost, "Zapisa: ${izabrani.size}"))
            for (racun in izabrani) sadrzaj.addView(karticaRacuna(racun))
            return
        }

        val izabrani = izabraniRacuni()
        if (racuni.isEmpty()) {
            sadrzaj.addView(maliTekst(aktivnost, "Još nema obrađenih računa sa stavkama."))
            return
        }

        val ukupno = izabrani.sumOf { zbir(it) }
        sadrzaj.addView(vrednost(aktivnost, "Ukupno: ${dinari(ukupno)}"))
        sadrzaj.addView(
            maliTekst(
                aktivnost,
                "Računa: ${izabrani.size} • stavki: ${izabrani.sumOf { it.stavke.size }}",
            )
        )

        when (prikaz) {
            Prikaz.RACUNI -> Unit
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

    private fun uMesecu(racun: Racun): Boolean =
        mesec == null || kljucMeseca.format(Date(vreme(racun))) == mesec

    private fun nacrtajFiltere() {
        filteri.removeAllViews()

        val prikazi = ChipGroup(aktivnost).apply { isSingleSelection = true }
        for (mogucnost in Prikaz.values()) {
            prikazi.addView(cip(imePrikaza(mogucnost), prikaz == mogucnost) {
                prikaz = mogucnost
                nacrtaj()
            })
        }
        filteri.addView(prikazi)

        if (prikaz == Prikaz.STAVKE) {
            val redosled = ChipGroup(aktivnost).apply { isSingleSelection = true }
            redosled.addView(cip("Najnovije prvo", !poCeni) { poCeni = false; nacrtaj() })
            redosled.addView(cip("Najskuplje prvo", poCeni) { poCeni = true; nacrtaj() })
            filteri.addView(redosled)
        }

        val meseci = sviRacuni.map { kljucMeseca.format(Date(vreme(it))) }
            .distinct().sortedDescending()
        if (meseci.size > 1 || mesec != null) {
            filteri.addView(odeljak(aktivnost, "Mesec"))
            val grupa = ChipGroup(aktivnost).apply { isSingleSelection = true }
            grupa.addView(cip("Sve", mesec == null) { mesec = null; nacrtaj() })
            for (kljuc in meseci) {
                grupa.addView(cip(imeMeseca(kljuc), mesec == kljuc) { mesec = kljuc; nacrtaj() })
            }
            filteri.addView(grupa)
        }

        if (radnja != null || vrsta != null || kategorija != null || zdravlje != null) {
            val grupa = ChipGroup(aktivnost)
            radnja?.let { grupa.addView(cip("Radnja: $it", true) { radnja = null; nacrtaj() }) }
            vrsta?.let { grupa.addView(cip("Vrsta: $it", true) { vrsta = null; nacrtaj() }) }
            kategorija?.let {
                grupa.addView(cip("Kategorija: $it", true) { kategorija = null; nacrtaj() })
            }
            zdravlje?.let { grupa.addView(cip("Zdravlje: $it", true) { zdravlje = null; nacrtaj() }) }
            filteri.addView(maliTekst(aktivnost, "Klik na filter ga uklanja."))
            filteri.addView(grupa)
        }
    }

    private fun nacrtajStavke(izabrani: List<Racun>) {
        val saStavkama = izabrani
            .map { racun -> racun to racun.stavke.filter { uKategoriji(it) } }
            .filter { (_, stavke) -> stavke.isNotEmpty() }
            .let { spisak ->
                // Po ceni se gleda zbir prikazanih stavki, ne ceo račun.
                if (poCeni) spisak.sortedByDescending { (_, stavke) -> stavke.sumOf { it.ukupnoPara } }
                else spisak
            }
        if (saStavkama.isEmpty()) {
            sadrzaj.addView(maliTekst(aktivnost, "Za izabrani filter nema pojedinačnih stavki."))
            return
        }
        for ((racun, stavke) in saStavkama) {
            val (kartica, unutra) = kartica(aktivnost)
            unutra.addView(TextView(aktivnost).apply {
                text = datumStavke.format(Date(vreme(racun)))
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            })
            unutra.addView(maliTekst(aktivnost, LogikaRacuna.kratakNazivRadnje(nazivRadnje(racun))))
            val poredaneStavke = if (poCeni) stavke.sortedByDescending { it.ukupnoPara } else stavke
            for (stavka in poredaneStavke) {
                unutra.addView(vrednost(aktivnost, "• ${stavka.naziv}"))
                val opis = buildString {
                    append(stavka.kolicina).append(" × ").append(dinari(stavka.jedinicnaCenaPara))
                    append(" = ").append(dinari(stavka.ukupnoPara))
                    if (stavka.kategorija.isNotBlank()) {
                        append("\n").append(stavka.kategorija)
                        if (stavka.zdravlje.isNotBlank()) append(" • ").append(stavka.zdravlje)
                    }
                }
                unutra.addView(maliTekst(aktivnost, opis).apply { setPadding(aktivnost.dp(14), 0, 0, aktivnost.dp(6)) })
            }
            unutra.addView(maliTekst(aktivnost, "Račun: ${dinari(zbir(racun))}"))
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
            val (kartica, unutra) = kartica(aktivnost)
            unutra.addView(TextView(aktivnost).apply {
                text = naziv
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            })
            unutra.addView(vrednost(aktivnost, dinari(racuniGrupe.sumOf { zbir(it) })))
            unutra.addView(
                maliTekst(
                    aktivnost,
                    "Računa: ${racuniGrupe.size} • stavki: ${racuniGrupe.sumOf { it.stavke.size }}",
                )
            )
            kartica.isClickable = true
            kartica.isFocusable = true
            kartica.setOnClickListener {
                klik(naziv)
                nacrtaj()
            }
            sadrzaj.addView(kartica)
        }
    }

    private fun nacrtajZbiroveStavki(
        grupe: Map<String, List<Pair<Racun, Stavka>>>,
        klik: (String) -> Unit,
    ) {
        if (grupe.isEmpty()) {
            sadrzaj.addView(maliTekst(aktivnost, "Za izabrani filter nema stavki."))
            return
        }
        val poredak = grupe.entries.sortedByDescending { red -> red.value.sumOf { it.second.ukupnoPara } }
        for ((ime, stavke) in poredak) {
            val (kartica, unutra) = kartica(aktivnost)
            unutra.addView(TextView(aktivnost).apply {
                text = ime
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            })
            unutra.addView(vrednost(aktivnost, dinari(stavke.sumOf { it.second.ukupnoPara })))
            unutra.addView(maliTekst(aktivnost, "Stavki: ${stavke.size}"))
            kartica.isClickable = true
            kartica.isFocusable = true
            kartica.setOnClickListener {
                klik(ime)
                nacrtaj()
            }
            sadrzaj.addView(kartica)
        }
        if (poredak.any { it.key == NERAZVRSTANO }) {
            sadrzaj.addView(maliTekst(
                aktivnost,
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

    private fun cip(tekst: String, izabran: Boolean, klik: () -> Unit) = Chip(aktivnost).apply {
        text = tekst
        isCheckable = true
        isChecked = izabran
        setOnClickListener { klik() }
    }

    private fun imePrikaza(vrednost: Prikaz) = when (vrednost) {
        Prikaz.RACUNI -> "Računi"
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
