package studio.room211.racuni

data class Racun(
    val id: Long,
    val nastao: Long,
    val izvor: String,
    val naziv: String,
    val qrSadrzaj: String,
    val tekst: String,
    val stanje: String,
    val greska: String,
    val datumRacuna: Long?,
    val pib: String,
    val preduzece: String,
    val prodajnoMesto: String,
    val adresa: String,
    val grad: String,
    val opstina: String,
    val ukupanIznosPara: Long?,
    val brojRacuna: String,
    val stavke: List<Stavka>,
)

data class Stavka(
    val naziv: String,
    val kolicina: String,
    val jedinicnaCenaPara: Long,
    val ukupnoPara: Long,
    val poreskaOsnovicaPara: Long,
    val pdvPara: Long,
    val poreskaOznaka: String,
    val poreskaStopa: String,
)

data class AnalizaRacuna(
    val datumRacuna: Long?,
    val pib: String,
    val preduzece: String,
    val prodajnoMesto: String,
    val adresa: String,
    val grad: String,
    val opstina: String,
    val ukupanIznosPara: Long?,
    val brojRacuna: String,
    val stavke: List<Stavka>,
)

object LogikaRacuna {
    const val IZVOR_QR = "QR"
    const val IZVOR_RUCNO = "RUCNO"
    const val CEKA_MREZU = "CEKA_MREZU"
    const val SACUVANO = "SACUVANO"
    const val GRESKA = "GRESKA"

    fun internetAdresa(sadrzaj: String): Boolean {
        val vrednost = sadrzaj.trim()
        return vrednost.startsWith("https://", ignoreCase = true) ||
            vrednost.startsWith("http://", ignoreCase = true)
    }

    /**
     * Kôd koji nikada neće dobiti podatke: nije internet adresa, pa nema
     * odakle da se preuzmu prodavnica, iznos i stavke. Takvi kodovi su
     * najčešće interne nalepnice radnje, a ne fiskalni račun.
     */
    fun bezKorisnihPodataka(racun: Racun): Boolean =
        racun.izvor == IZVOR_QR &&
            !internetAdresa(racun.qrSadrzaj) &&
            racun.preduzece.isBlank() &&
            racun.prodajnoMesto.isBlank() &&
            racun.ukupanIznosPara == null &&
            racun.stavke.isEmpty()

    fun rucniNaziv(tekst: String): String = tekst
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?.take(60)
        ?: "Ručni unos"
}
