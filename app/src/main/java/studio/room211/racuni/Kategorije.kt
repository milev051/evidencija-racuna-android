package studio.room211.racuni

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Razvrstavanje proizvoda preko Gemini-ja.
 *
 * Šalju se samo nazivi proizvoda, nikada iznosi, radnja, PIB ni broj računa.
 * Svaki naziv se pita najviše jednom: odgovor se pamti u tabeli pojmova, pa
 * isti hleb ne troši nijedan novi zahtev.
 */
object Kategorije {
    const val NAJVISE_U_UPITU = 40
    private const val NAJVISE_UPITA = 3

    val SPISAK = listOf(
        "Voće i povrće",
        "Meso i riba",
        "Mlečni proizvodi i jaja",
        "Pekarski proizvodi",
        "Osnovne namirnice",
        "Slatkiši i grickalice",
        "Bezalkoholna pića",
        "Alkoholna pića",
        "Gotova i brza hrana",
        "Higijena i kupatilo",
        "Sredstva za čišćenje",
        "Kozmetika",
        "Lekovi i apoteka",
        "Kućne potrepštine",
        "Kućni ljubimci",
        "Tehnika",
        "Odeća, obuća i aksesoari",
        "Gorivo i automobil",
        "Ostalo",
    )

    val ZDRAVLJE = listOf("zdravo", "umereno", "nezdravo", "nije hrana")

    /**
     * Popunjava kategorije za naziv po naziv, u paketima. Vraća koliko je
     * proizvoda razvrstano u ovom prolazu.
     */
    fun dopuni(context: Context, baza: Baza): Int {
        val kljuc = Podesavanja.geminiKljuc(context)
        if (kljuc.isBlank()) return 0
        val model = Podesavanja.geminiModel(context)

        var razvrstano = 0
        for (krug in 0 until NAJVISE_UPITA) {
            val nazivi = baza.nekategorisaniNazivi(NAJVISE_U_UPITU)
            if (nazivi.isEmpty()) break

            // Naziv koji je model već video pod drugom šifrom ne ide na mrežu.
            val zaModel = mutableListOf<String>()
            for (naziv in nazivi) {
                val zapamceno = baza.kesPojma(LogikaRacuna.kljucProizvoda(naziv))
                if (zapamceno != null) {
                    baza.zapamtiKategoriju(naziv, zapamceno.kategorija, zapamceno.zdravlje)
                    razvrstano++
                } else {
                    zaModel += naziv
                }
            }
            if (zaModel.isEmpty()) continue

            val odgovor = try {
                procitajOdgovor(Gemini.odgovor(model, kljuc, upit(zaModel)))
            } catch (e: Exception) {
                Podesavanja.zapisiGresku(context, e.message ?: e.javaClass.simpleName)
                return razvrstano
            }

            var upisano = 0
            for (naziv in zaModel) {
                val red = odgovor[naziv] ?: continue
                baza.zapamtiKategoriju(naziv, red.kategorija, red.zdravlje)
                upisano++
                razvrstano++
            }
            Podesavanja.zapisiGresku(
                context,
                if (upisano == 0) "Model nije vratio nijedan poznat naziv proizvoda." else "",
            )
            if (upisano == 0) return razvrstano
        }
        return razvrstano
    }

    internal fun upit(nazivi: List<String>): JSONObject {
        val spisakProizvoda = nazivi.mapIndexed { redni, naziv -> "${redni + 1}. $naziv" }
            .joinToString("\n")
        val uputstvo = buildString {
            append("Razvrstaj proizvode sa srpskog fiskalnog računa.\n\n")
            append("Dozvoljene kategorije, koristi tačno ove nazive:\n")
            append(SPISAK.joinToString("\n") { "- $it" })
            append("\n\nDozvoljene oznake zdravlja: ")
            append(ZDRAVLJE.joinToString(", "))
            append(".\nOznaka \"nije hrana\" ide za sve što se ne jede i ne pije.\n\n")
            append("Vrati isključivo JSON niz, jedan red za svaki proizvod, oblika:\n")
            append("[{\"naziv\": \"tačan naziv iz spiska\", \"kategorija\": \"...\", \"zdravlje\": \"...\"}]\n\n")
            append("Proizvodi:\n").append(spisakProizvoda)
        }
        return Gemini.telo(uputstvo)
    }

    internal fun procitajOdgovor(json: String): Map<String, Pojam> {
        val niz = JSONArray(Gemini.tekstOdgovora(json))
        val rezultat = LinkedHashMap<String, Pojam>()
        for (i in 0 until niz.length()) {
            val red = niz.getJSONObject(i)
            val naziv = red.optString("naziv").trim()
            if (naziv.isBlank()) continue
            val kategorija = red.optString("kategorija").trim()
                .takeIf { it in SPISAK } ?: "Ostalo"
            val zdravlje = red.optString("zdravlje").trim().lowercase()
                .takeIf { it in ZDRAVLJE } ?: "nije hrana"
            rezultat[naziv] = Pojam(kategorija, zdravlje)
        }
        return rezultat
    }

    data class Pojam(val kategorija: String, val zdravlje: String)
}
