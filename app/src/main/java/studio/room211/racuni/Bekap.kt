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
                    .put("stavke", stavke)
            )
        }
        return JSONObject()
            .put("verzija", VERZIJA)
            .put("napravljen", System.currentTimeMillis())
            .put("racuni", niz)
            .toString(2)
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
