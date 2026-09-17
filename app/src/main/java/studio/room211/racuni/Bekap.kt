package studio.room211.racuni

import org.json.JSONArray
import org.json.JSONObject

/**
 * Bekap je jedan čitljiv JSON fajl sa svim računima i stavkama.
 *
 * Namerno nije kopija SQLite fajla: ovako može da se otvori i pročita bilo
 * gde, i da se uveze i posle promene strukture baze. Podešavanja i Gemini
 * ključ nisu deo bekapa.
 */
object Bekap {
    const val VERZIJA = 1

    fun kaoJson(racuni: List<Racun>): String {
        val niz = JSONArray()
        for (racun in racuni) {
            val stavke = JSONArray()
            for (stavka in racun.stavke) {
                stavke.put(
                    JSONObject()
                        .put("naziv", stavka.naziv)
                        .put("kolicina", stavka.kolicina)
                        .put("jedinicna_cena_para", stavka.jedinicnaCenaPara)
                        .put("ukupno_para", stavka.ukupnoPara)
                        .put("poreska_osnovica_para", stavka.poreskaOsnovicaPara)
                        .put("pdv_para", stavka.pdvPara)
                        .put("poreska_oznaka", stavka.poreskaOznaka)
                        .put("poreska_stopa", stavka.poreskaStopa)
                        .put("kategorija", stavka.kategorija)
                        .put("zdravlje", stavka.zdravlje)
                )
            }
            niz.put(
                JSONObject()
                    .put("nastao", racun.nastao)
                    .put("izvor", racun.izvor)
                    .put("naziv", racun.naziv)
                    .put("qr_sadrzaj", racun.qrSadrzaj)
                    .put("tekst", racun.tekst)
                    .put("stanje", racun.stanje)
                    .put("greska", racun.greska)
                    .put("datum_racuna", racun.datumRacuna ?: JSONObject.NULL)
                    .put("pib", racun.pib)
                    .put("preduzece", racun.preduzece)
                    .put("prodajno_mesto", racun.prodajnoMesto)
                    .put("adresa", racun.adresa)
                    .put("grad", racun.grad)
                    .put("opstina", racun.opstina)
                    .put("ukupan_iznos_para", racun.ukupanIznosPara ?: JSONObject.NULL)
                    .put("broj_racuna", racun.brojRacuna)
                    .put("brojac", racun.brojac)
                    .put("izvorna_slika", racun.izvornaSlika)
                    .put("stavke", stavke)
            )
        }
        return JSONObject()
            .put("verzija", VERZIJA)
            .put("napravljen", System.currentTimeMillis())
            .put("racuni", niz)
            .toString(2)
    }

    /**
     * Spisak slika koje čekaju ruku ili jači model, u Markdown-u.
     *
     * Ide u isti folder u kom stoje fotografije, pa agent na računaru zna
     * tačno koju sliku da otvori i šta da uradi, a rezultat vraća kao bekap
     * koji aplikacija ume da uveze.
     */
    fun spisakZaObradu(racuni: List<Racun>, vreme: Long = System.currentTimeMillis()): String {
        val datum = java.text.SimpleDateFormat("dd.MM.yyyy. HH:mm", java.util.Locale("sr", "RS"))
        val cekaProveru = racuni.filter { it.stanje == LogikaRacuna.CEKA_PROVERU }
        val nepotpuni = racuni.filter { it.stanje == LogikaRacuna.NECITLJIVO }

        return buildString {
            append("# Računi koji čekaju obradu\n\n")
            append("Napravljeno: ").append(datum.format(java.util.Date(vreme))).append("\n\n")

            append("## Čeka zvaničnu proveru\n\n")
            if (cekaProveru.isEmpty()) append("Nema takvih zapisa.\n") else {
                append("Podaci su pročitani, treba ih samo poslati na `suf.purs.gov.rs/verify`.\n\n")
                for (racun in cekaProveru) append(red(racun)).append('\n')
            }

            append("\n## Nepotpuno pročitano sa slike\n\n")
            if (nepotpuni.isEmpty()) append("Nema takvih zapisa.\n") else {
                append("Sa ovih slika nedostaje bar jedno polje za proveru.\n\n")
                for (racun in nepotpuni) {
                    append(red(racun))
                    val fali = listOfNotNull(
                        "ПФР број".takeIf { racun.brojRacuna.isBlank() },
                        "бројач".takeIf { racun.brojac.isBlank() },
                        "износ".takeIf { racun.ukupanIznosPara == null },
                        "време".takeIf { racun.datumRacuna == null },
                    )
                    if (fali.isNotEmpty()) append(" (nedostaje: ").append(fali.joinToString(", ")).append(")")
                    append('\n')
                }
            }

            append("\n## Šta treba uraditi\n\n")
            append("1. Za svaku sliku pročitaj sa fotografije: ПФР број рачуна, ")
            append("бројач рачуна, укупан износ и ПФР време.\n")
            append("2. Na `https://suf.purs.gov.rs/verify` unesi ta četiri polja ")
            append("i otvori račun; stranica traži potvrdu da nisi robot, pa to ide ručno.\n")
            append("3. Sa otvorenog računa prepiši prodavnicu, iznos i sve stavke.\n")
            append("4. Rezultat upiši kao bekap fajl ovog oblika, pa ga u aplikaciji ")
            append("vrati preko „Vrati iz bekapa\":\n\n")
            append("```json\n")
            append("{\"verzija\": ").append(VERZIJA).append(", \"racuni\": [\n")
            append("  {\"nastao\": 0, \"izvor\": \"QR\", \"naziv\": \"LIDL\", ")
            append("\"qr_sadrzaj\": \"https://suf.purs.gov.rs/v/?vl=...\", \"stanje\": \"CEKA_MREZU\", ")
            append("\"izvorna_slika\": \"IMG_2031.jpg\", \"stavke\": []}\n")
            append("]}\n")
            append("```\n\n")
            append("Ako je poznat zvanični link računa, dovoljno je upisati ga u ")
            append("`qr_sadrzaj` uz stanje `CEKA_MREZU`: aplikacija sama preuzima ")
            append("prodavnicu, iznos i stavke čim dobije internet.\n")
        }
    }

    private fun red(racun: Racun): String = buildString {
        append("- ").append(racun.izvornaSlika.ifBlank { "bez slike" })
        val podaci = listOfNotNull(
            racun.brojRacuna.takeIf { it.isNotBlank() }?.let { "ПФР број $it" },
            racun.brojac.takeIf { it.isNotBlank() }?.let { "бројач $it" },
            racun.ukupanIznosPara?.let { "износ %d,%02d".format(it / 100, it % 100) },
        )
        if (podaci.isNotEmpty()) append(" — ").append(podaci.joinToString(", "))
    }

    fun izJson(tekst: String): List<Racun> {
        val koren = JSONObject(tekst)
        require(koren.optInt("verzija") == VERZIJA) {
            "Fajl nije bekap ove aplikacije ili je novijeg oblika"
        }
        val niz = koren.optJSONArray("racuni") ?: return emptyList()
        return buildList {
            for (i in 0 until niz.length()) {
                val red = niz.getJSONObject(i)
                val stavke = red.optJSONArray("stavke") ?: JSONArray()
                add(
                    Racun(
                        id = 0,
                        nastao = red.optLong("nastao"),
                        izvor = red.optString("izvor", LogikaRacuna.IZVOR_QR),
                        naziv = red.optString("naziv"),
                        qrSadrzaj = red.optString("qr_sadrzaj"),
                        tekst = red.optString("tekst"),
                        stanje = red.optString("stanje", LogikaRacuna.SACUVANO),
                        greska = red.optString("greska"),
                        datumRacuna = if (red.isNull("datum_racuna")) null else red.optLong("datum_racuna"),
                        pib = red.optString("pib"),
                        preduzece = red.optString("preduzece"),
                        prodajnoMesto = red.optString("prodajno_mesto"),
                        adresa = red.optString("adresa"),
                        grad = red.optString("grad"),
                        opstina = red.optString("opstina"),
                        ukupanIznosPara = if (red.isNull("ukupan_iznos_para")) null
                        else red.optLong("ukupan_iznos_para"),
                        brojRacuna = red.optString("broj_racuna"),
                        brojac = red.optString("brojac"),
                        izvornaSlika = red.optString("izvorna_slika"),
                        stavke = (0 until stavke.length()).map { j ->
                            val s = stavke.getJSONObject(j)
                            Stavka(
                                naziv = s.optString("naziv"),
                                kolicina = s.optString("kolicina"),
                                jedinicnaCenaPara = s.optLong("jedinicna_cena_para"),
                                ukupnoPara = s.optLong("ukupno_para"),
                                poreskaOsnovicaPara = s.optLong("poreska_osnovica_para"),
                                pdvPara = s.optLong("pdv_para"),
                                poreskaOznaka = s.optString("poreska_oznaka"),
                                poreskaStopa = s.optString("poreska_stopa"),
                                kategorija = s.optString("kategorija"),
                                zdravlje = s.optString("zdravlje"),
                            )
                        },
                    )
                )
            }
        }
    }
}
