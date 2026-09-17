package studio.room211.racuni

import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Strukturisano čitanje javne stranice za proveru fiskalnog računa. */
object SufRacun {
    private val brojRacunaRegex = Regex("viewModel\\.InvoiceNumber\\('([^']+)'\\)")
    private val tokenRegex = Regex("viewModel\\.Token\\('([^']+)'\\)")

    fun podrzan(adresa: String): Boolean = try {
        val uri = URI(adresa.trim())
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("suf.purs.gov.rs", ignoreCase = true) &&
            uri.path.startsWith("/v/")
    } catch (_: Exception) {
        false
    }

    fun preuzmi(adresa: String): AnalizaRacuna {
        require(podrzan(adresa)) { "Nije podržan link za proveru fiskalnog računa" }
        return izStranice(zahtev(adresa.trim()))
    }

    /** Ista stranica, samo kada je već otvorena u aplikaciji posle provere. */
    fun izStranice(html: String): AnalizaRacuna {
        val zaglavlje = parsirajZaglavlje(html)
        return zaglavlje.analiza.copy(
            stavke = zahtevZaStavke(zaglavlje.brojRacuna, zaglavlje.token)
        )
    }

    internal fun parsirajZaglavlje(html: String): ParsiranoZaglavlje {
        val dokument = Jsoup.parse(html)
        val skripte = dokument.select("script").joinToString("\n") { it.data() }
        val brojRacuna = brojRacunaRegex.find(skripte)?.groupValues?.get(1)
            ?: dokument.getElementById("invoiceNumberLabel")?.text()?.trim().orEmpty()
        val token = tokenRegex.find(skripte)?.groupValues?.get(1).orEmpty()
        require(brojRacuna.isNotBlank()) { "Stranica ne sadrži broj fiskalnog računa" }
        require(token.isNotBlank()) { "Stranica ne sadrži token za specifikaciju" }

        val datumTekst = vrednost(dokument, "sdcDateTimeLabel")
        return ParsiranoZaglavlje(
            brojRacuna = brojRacuna,
            token = token,
            analiza = AnalizaRacuna(
                datumRacuna = parsirajDatum(datumTekst),
                pib = vrednost(dokument, "tinLabel"),
                preduzece = vrednostPoredOznake(dokument, "Предузеће", "Preduzeće"),
                prodajnoMesto = vrednost(dokument, "shopFullNameLabel"),
                adresa = vrednost(dokument, "addressLabel"),
                grad = vrednost(dokument, "cityLabel"),
                opstina = vrednost(dokument, "administrativeUnitLabel"),
                ukupanIznosPara = uPareSrpski(vrednost(dokument, "totalAmountLabel")),
                brojRacuna = brojRacuna,
                stavke = emptyList(),
            ),
        )
    }

    internal fun parsirajStavke(json: String): List<Stavka> {
        val odgovor = JSONObject(json)
        require(odgovor.optBoolean("success")) { "Server nije vratio specifikaciju računa" }
        val niz = odgovor.optJSONArray("items") ?: return emptyList()
        return buildList {
            for (i in 0 until niz.length()) {
                val red = niz.getJSONObject(i)
                add(
                    Stavka(
                        naziv = red.optString("name").trim(),
                        kolicina = decimalniTekst(red, "quantity"),
                        jedinicnaCenaPara = uPareJson(red, "unitPrice"),
                        ukupnoPara = uPareJson(red, "total"),
                        poreskaOsnovicaPara = uPareJson(red, "taxBaseAmount"),
                        pdvPara = uPareJson(red, "vatAmount"),
                        poreskaOznaka = red.optString("label").trim(),
                        poreskaStopa = decimalniTekst(red, "labelRate"),
                    )
                )
            }
        }
    }

    private fun zahtevZaStavke(brojRacuna: String, token: String): List<Stavka> {
        val telo = "invoiceNumber=${kodiraj(brojRacuna)}&token=${kodiraj(token)}"
        val json = zahtev(
            "https://suf.purs.gov.rs/specifications",
            metod = "POST",
            telo = telo,
        )
        return parsirajStavke(json)
    }

    private fun zahtev(adresa: String, metod: String = "GET", telo: String? = null): String {
        val veza = URL(adresa).openConnection() as HttpURLConnection
        try {
            veza.connectTimeout = 12_000
            veza.readTimeout = 18_000
            veza.instanceFollowRedirects = true
            veza.requestMethod = metod
            veza.setRequestProperty("User-Agent", "Racuni-Android/0.5")
            veza.setRequestProperty("Accept", "text/html,application/json")
            if (telo != null) {
                veza.doOutput = true
                veza.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                veza.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(telo) }
            }
            val kod = veza.responseCode
            if (kod !in 200..299) error("Server je vratio HTTP $kod")
            return veza.inputStream.bufferedReader(Charsets.UTF_8).use { citac ->
                val izlaz = StringBuilder()
                val bafer = CharArray(8_192)
                while (izlaz.length < 1_500_000) {
                    val broj = citac.read(bafer, 0, minOf(bafer.size, 1_500_000 - izlaz.length))
                    if (broj < 0) break
                    izlaz.append(bafer, 0, broj)
                }
                izlaz.toString()
            }
        } finally {
            veza.disconnect()
        }
    }

    private fun vrednost(dokument: Document, id: String): String =
        dokument.getElementById(id)?.text()?.trim().orEmpty()

    private fun vrednostPoredOznake(dokument: Document, vararg oznake: String): String {
        val trazene = oznake.map { it.lowercase(Locale.ROOT) }.toSet()
        val jaka = dokument.select("strong").firstOrNull {
            it.text().trim().trimEnd(':').lowercase(Locale.ROOT) in trazene
        } ?: return ""
        return jaka.parent()?.selectFirst("span")?.text()?.trim().orEmpty()
    }

    // Isto čitanje datuma i iznosa koristi i čitanje računa sa fotografije.
    private fun parsirajDatum(tekst: String): Long? = LogikaRacuna.uVreme(tekst)

    private fun uPareSrpski(tekst: String): Long? = LogikaRacuna.uPare(tekst)

    private fun uPareJson(objekat: JSONObject, kljuc: String): Long =
        BigDecimal(objekat.get(kljuc).toString())
            .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()

    private fun decimalniTekst(objekat: JSONObject, kljuc: String): String =
        BigDecimal(objekat.get(kljuc).toString()).stripTrailingZeros().toPlainString()

    private fun kodiraj(vrednost: String) = URLEncoder.encode(vrednost, "UTF-8")

    internal data class ParsiranoZaglavlje(
        val brojRacuna: String,
        val token: String,
        val analiza: AnalizaRacuna,
    )
}
