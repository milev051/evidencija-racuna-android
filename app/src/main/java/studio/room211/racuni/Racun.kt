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
    val brojac: String,
    val izvornaSlika: String,
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
    /** Popunjava se naknadno, kada model razvrsta proizvode. */
    val kategorija: String = "",
    val zdravlje: String = "",
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
    private val SRPSKI = java.util.Locale("sr", "RS")

    const val IZVOR_QR = "QR"
    const val IZVOR_RUCNO = "RUCNO"

    /** Račun pročitan sa fotografije, kada QR nije bio čitljiv. */
    const val IZVOR_SLIKA = "SLIKA"

    const val CEKA_MREZU = "CEKA_MREZU"
    const val SACUVANO = "SACUVANO"
    const val GRESKA = "GRESKA"

    /** Podaci su pročitani sa slike, ali ih još nije potvrdila Poreska uprava. */
    const val CEKA_PROVERU = "CEKA_PROVERU"

    /** Sa slike nije moglo da se pročita ništa upotrebljivo. */
    const val NECITLJIVO = "NECITLJIVO"

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

    private val PRAVNI_OBLICI = setOf(
        "DOO", "D.O.O.", "D.O.O", "OD", "KD", "AD", "A.D.", "DD", "PR", "LTD",
        "SZR", "STR", "SUR", "SZTR", "SRBIJA", "SERBIA", "KOMPANIJA",
    )

    /** „LIDL SRBIJA KD" i „DELHAIZE SERBIA DOO" su za spisak radnji LIDL i DELHAIZE. */
    fun kratakNazivRadnje(preduzece: String): String {
        val reci = preduzece.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        while (reci.size > 1 && reci.last().uppercase(SRPSKI).trimEnd(',') in PRAVNI_OBLICI) {
            reci.removeAt(reci.size - 1)
        }
        return reci.joinToString(" ").trimEnd(',', '-').ifBlank { preduzece.trim() }
    }

    private val VRSTE_RADNJI = listOf(
        "Marketi" to listOf(
            "LIDL", "MAXI", "MERCATOR", "IDEA", "UNIVEREXPORT", "AMAN", "DELHAIZE",
            "TEMPO", "RODA", "METRO", "GOMEX", "DIS", "TRGOCENTAR", "SUPERVERO",
            "MARKET", "MARKETI", "MINIMAX", "SHOP", "MEGA",
        ),
        "Pekare i poslastičarnice" to listOf(
            "PEKARA", "PEKARE", "PEKARSKA", "HLEB", "POSLASTIČARNICA", "TRPKOVIĆ",
        ),
        "Apoteke" to listOf("APOTEKA", "APOTEKE", "LILLY", "BENU", "MAX", "GALEN"),
        "Benzinske stanice" to listOf(
            "NIS", "GAZPROM", "OMV", "MOL", "LUKOIL", "SHELL", "PETROL", "EKO",
        ),
        "Kafići i restorani" to listOf(
            "KAFE", "CAFFE", "CAFE", "RESTORAN", "PICERIJA", "PIZZA", "BURGER",
            "MCDONALD", "KFC", "ROŠTILJ", "GRILL", "BAR",
        ),
        "Drogerije i kozmetika" to listOf("DM", "BIPA", "DROGERIE", "KOZMETIKA", "PARFI"),
        "Tehnika" to listOf(
            "GIGATRON", "TEHNOMANIJA", "WINWIN", "EMMEZETA", "COMTRADE", "TEHNIKA",
        ),
    )

    const val VRSTA_OSTALO = "Ostalo"

    /**
     * Gruba podela radnji, dovoljna za pregled potrošnje. Radi bez interneta,
     * pa nije potrebno da bilo šta napusti telefon.
     */
    fun vrstaRadnje(naziv: String): String {
        val veliko = naziv.uppercase(SRPSKI)
        for ((vrsta, kljucne) in VRSTE_RADNJI) {
            if (kljucne.any { rec -> celaRec(rec).containsMatchIn(veliko) }) return vrsta
        }
        return VRSTA_OSTALO
    }

    // (?U) je potrebno da bi Ć i Š bili slova, pa granica reči radi i na srpskom.
    private fun celaRec(rec: String) = Regex("(?U)\\b" + Regex.escape(rec) + "\\b")

    /**
     * Isti proizvod se na računima pojavljuje sa internim šifrom na kraju
     * („Sladoled štapić jagoda krisp/1013635"). Ključ sklanja šifru, pa se
     * jednom razvrstan proizvod nikada ne razvrstava ponovo.
     */
    fun kljucProizvoda(naziv: String): String = naziv
        // Sklanja se samo šifra na kraju, da „1/2 mleko" ostane čitavo.
        .replace(Regex("/\\s*\\d+\\s*$"), "")
        .replace(Regex("(?U)[^\\p{L}\\p{N} ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .uppercase(SRPSKI)

    /** Za proveru na zvaničnoj stranici potrebna su tačno ova četiri podatka. */
    fun spremanZaProveru(racun: Racun): Boolean =
        racun.brojRacuna.isNotBlank() &&
            racun.brojac.isNotBlank() &&
            racun.ukupanIznosPara != null &&
            racun.datumRacuna != null

    /** „1.619,99" i „1619,99" daju isti broj u parama. */
    fun uPare(tekst: String): Long? = runCatching {
        tekst.replace(".", "").replace(",", ".").replace(" ", "").trim()
            .toBigDecimal().movePointRight(2)
            .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()

    /** Vreme sa fiskalnog računa je uvek u beogradskoj zoni. */
    fun uVreme(tekst: String): Long? {
        if (tekst.isBlank()) return null
        for (oblik in listOf("d.M.yyyy. HH:mm:ss", "d.M.yyyy. HH:mm", "d.M.yyyy HH:mm:ss")) {
            val vreme = runCatching {
                java.text.SimpleDateFormat(oblik, java.util.Locale.ROOT).apply {
                    isLenient = false
                    timeZone = java.util.TimeZone.getTimeZone("Europe/Belgrade")
                }.parse(tekst.trim())?.time
            }.getOrNull()
            if (vreme != null) return vreme
        }
        return null
    }

    fun rucniNaziv(tekst: String): String = tekst
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?.take(60)
        ?: "Ručni unos"
}
