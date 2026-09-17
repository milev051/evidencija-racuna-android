package studio.room211.racuni

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Jedno mesto za razgovor sa Gemini-jem: i za tekst i za slike. */
object Gemini {
    fun odgovor(model: String, kljuc: String, telo: JSONObject): String {
        val adresa =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val veza = URL(adresa).openConnection() as HttpURLConnection
        try {
            veza.connectTimeout = 15_000
            veza.readTimeout = 60_000
            veza.requestMethod = "POST"
            veza.doOutput = true
            veza.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            veza.setRequestProperty("x-goog-api-key", kljuc)
            veza.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(telo.toString()) }

            val kod = veza.responseCode
            if (kod !in 200..299) {
                val opis = veza.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                error("Gemini je vratio HTTP $kod. ${opis.orEmpty().take(200)}")
            }
            return veza.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            veza.disconnect()
        }
    }

    /** Model ume da obmota JSON u ```json blok, pa se to sklanja pre čitanja. */
    fun tekstOdgovora(json: String): String {
        val kandidati = JSONObject(json).optJSONArray("candidates")
            ?: error("Odgovor nema sadržaj; proveri ključ i naziv modela.")
        if (kandidati.length() == 0) error("Model nije vratio odgovor.")
        val delovi = kandidati.getJSONObject(0)
            .optJSONObject("content")
            ?.optJSONArray("parts")
            ?: error("Odgovor nema tekst.")
        return buildString {
            for (i in 0 until delovi.length()) append(delovi.getJSONObject(i).optString("text"))
        }.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    fun telo(uputstvo: String, delovi: JSONArray = JSONArray()): JSONObject {
        val sviDelovi = JSONArray().put(JSONObject().put("text", uputstvo))
        for (i in 0 until delovi.length()) sviDelovi.put(delovi.get(i))
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", sviDelovi)))
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0).put("responseMimeType", "application/json"),
            )
    }

    fun slika(base64: String): JSONObject = JSONObject().put(
        "inline_data",
        JSONObject().put("mime_type", "image/jpeg").put("data", base64),
    )
}
