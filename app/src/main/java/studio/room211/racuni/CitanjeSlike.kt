package studio.room211.racuni

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Čitanje fiskalnog računa sa fotografije, kada QR nije čitljiv.
 *
 * Cilj nije da model zameni Poresku upravu, nego da pročita četiri polja koja
 * su dovoljna za zvaničnu proveru: ПФР број, бројач, укупан износ и ПФР време.
 * Ako su ta polja prekrivena ili mutna, pokušava bar da pročita šta je
 * kupljeno, da zapis ne ostane prazan.
 */
object CitanjeSlike {
    const val PROCITAN_PFR = "pfr"
    const val DELIMICNO = "delimicno"
    const val NECITLJIVO = "necitljivo"

    private const val NAJVECA_STRANICA = 1600

    data class Procitano(
        val pfrBroj: String,
        val brojac: String,
        val ukupanIznosPara: Long?,
        val vreme: Long?,
        val radnja: String,
        val stavke: List<Stavka>,
        val citljivost: String,
    ) {
        val upotrebljivo: Boolean
            get() = citljivost != NECITLJIVO &&
                (pfrBroj.isNotBlank() || stavke.isNotEmpty() || ukupanIznosPara != null)
    }

    /** Ime fajla se pamti uz račun, pa se zna koje su slike već obrađene. */
    fun imeFajla(context: Context, slika: Uri): String {
        val iz = runCatching {
            context.contentResolver.query(
                slika,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        return iz ?: slika.lastPathSegment.orEmpty()
    }

    fun procitaj(context: Context, slika: Uri): Procitano? {
        val kljuc = Podesavanja.geminiKljuc(context)
        if (kljuc.isBlank()) return null
        val base64 = uBase64(context, slika) ?: return null
        val odgovor = Gemini.odgovor(
            Podesavanja.geminiModel(context),
            kljuc,
            Gemini.telo(UPUTSTVO, JSONArray().put(Gemini.slika(base64))),
        )
        return procitajOdgovor(Gemini.tekstOdgovora(odgovor))
    }

    internal fun procitajOdgovor(tekst: String): Procitano {
        val red = JSONObject(tekst)
        val stavke = red.optJSONArray("stavke") ?: JSONArray()
        val procitane = (0 until stavke.length()).mapNotNull { i ->
            val s = stavke.optJSONObject(i) ?: return@mapNotNull null
            val naziv = s.optString("naziv").trim()
            if (naziv.isBlank()) return@mapNotNull null
            val ukupno = LogikaRacuna.uPare(s.optString("ukupno")) ?: 0L
            Stavka(
                naziv = naziv,
                kolicina = s.optString("kolicina").trim().ifBlank { "1" },
                jedinicnaCenaPara = LogikaRacuna.uPare(s.optString("cena")) ?: ukupno,
                ukupnoPara = ukupno,
                poreskaOsnovicaPara = 0L,
                pdvPara = 0L,
                poreskaOznaka = "",
                poreskaStopa = "",
            )
        }
        val pfrBroj = red.optString("pfr_broj").trim()
        val brojac = red.optString("brojac").trim()
        val iznos = LogikaRacuna.uPare(red.optString("ukupan_iznos"))
        val vreme = LogikaRacuna.uVreme(red.optString("pfr_vreme"))
        val sviPodaci = pfrBroj.isNotBlank() && brojac.isNotBlank() && iznos != null && vreme != null
        return Procitano(
            pfrBroj = pfrBroj,
            brojac = brojac,
            ukupanIznosPara = iznos,
            vreme = vreme,
            radnja = red.optString("radnja").trim(),
            stavke = procitane,
            citljivost = when {
                sviPodaci -> PROCITAN_PFR
                procitane.isNotEmpty() || iznos != null || pfrBroj.isNotBlank() -> DELIMICNO
                else -> NECITLJIVO
            },
        )
    }

    private fun uBase64(context: Context, slika: Uri): String? {
        val granice = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(slika)?.use {
            BitmapFactory.decodeStream(it, null, granice)
        }
        if (granice.outWidth <= 0 || granice.outHeight <= 0) return null

        var uzorak = 1
        while (
            granice.outWidth / uzorak > NAJVECA_STRANICA * 2 ||
            granice.outHeight / uzorak > NAJVECA_STRANICA * 2
        ) {
            uzorak *= 2
        }
        val ucitan = context.contentResolver.openInputStream(slika)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply { inSampleSize = uzorak },
            )
        } ?: return null

        val najduza = maxOf(ucitan.width, ucitan.height)
        val smanjen = if (najduza > NAJVECA_STRANICA) {
            val odnos = NAJVECA_STRANICA.toFloat() / najduza
            Bitmap.createScaledBitmap(
                ucitan,
                (ucitan.width * odnos).toInt().coerceAtLeast(1),
                (ucitan.height * odnos).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            ucitan
        }

        return try {
            ByteArrayOutputStream().use { izlaz ->
                smanjen.compress(Bitmap.CompressFormat.JPEG, 85, izlaz)
                Base64.encodeToString(izlaz.toByteArray(), Base64.NO_WRAP)
            }
        } finally {
            if (smanjen !== ucitan) smanjen.recycle()
            ucitan.recycle()
        }
    }

    private val UPUTSTVO = buildString {
        append("Na slici je srpski fiskalni račun. Pročitaj ga tačno, slovo po slovo.\n\n")
        append("Najvažnija su četiri polja pri dnu računa, jer se njima račun proverava ")
        append("na zvaničnoj stranici Poreske uprave:\n")
        append("- ПФР број рачуна, oblik XXXXXXXX-XXXXXXXX-1234\n")
        append("- Бројач рачуна, oblik 2078/2088ПП\n")
        append("- Укупан износ, broj sa zarezom, na primer 1619,99\n")
        append("- ПФР време, oblik 17.9.2026. 20:37:48\n\n")
        append("Ako neko polje nije čitljivo ili je prekriveno, stavi prazan string, ")
        append("nikada nagađaj. Slova i brojevi u ПФР броју se lako mešaju, pa ")
        append("prepiši tačno ono što vidiš.\n\n")
        append("Pročitaj i naziv radnje i artikle sa cenama, ako se vide.\n\n")
        append("Vrati isključivo JSON oblika:\n")
        append("{\"pfr_broj\": \"\", \"brojac\": \"\", \"ukupan_iznos\": \"\", ")
        append("\"pfr_vreme\": \"\", \"radnja\": \"\", ")
        append("\"stavke\": [{\"naziv\": \"\", \"kolicina\": \"\", \"cena\": \"\", \"ukupno\": \"\"}]}")
    }
}
