package studio.room211.racuni

import android.content.Context

/**
 * Podešavanja stoje u privatnom prostoru aplikacije, do kog druge aplikacije
 * nemaju pristup. Ključ se nikada ne upisuje u bazu, ni u bekap fajl, ni u
 * repozitorijum: unosi ga korisnik i ostaje samo na telefonu.
 */
object Podesavanja {
    private const val FAJL = "podesavanja"
    private const val KLJUC_MODELA = "gemini_model"
    private const val KLJUC_PRISTUPA = "gemini_kljuc"
    private const val KLJUC_GRESKE = "kategorije_greska"
    private const val KLJUC_KAMERE = "kamera_odmah"

    const val PODRAZUMEVANI_MODEL = "gemini-2.5-flash"

    private fun prostor(context: Context) =
        context.applicationContext.getSharedPreferences(FAJL, Context.MODE_PRIVATE)

    fun geminiKljuc(context: Context): String =
        prostor(context).getString(KLJUC_PRISTUPA, "").orEmpty().trim()

    fun geminiModel(context: Context): String =
        prostor(context).getString(KLJUC_MODELA, "").orEmpty().trim()
            .ifBlank { PODRAZUMEVANI_MODEL }

    fun sacuvajGemini(context: Context, kljuc: String, model: String) {
        prostor(context).edit()
            .putString(KLJUC_PRISTUPA, kljuc.trim())
            .putString(KLJUC_MODELA, model.trim())
            .apply()
    }

    fun kameraOdmah(context: Context): Boolean =
        prostor(context).getBoolean(KLJUC_KAMERE, false)

    fun sacuvajKameraOdmah(context: Context, ukljuceno: Boolean) {
        prostor(context).edit().putBoolean(KLJUC_KAMERE, ukljuceno).apply()
    }

    fun greskaKategorija(context: Context): String =
        prostor(context).getString(KLJUC_GRESKE, "").orEmpty()

    fun zapisiGresku(context: Context, poruka: String) {
        prostor(context).edit().putString(KLJUC_GRESKE, poruka.take(300)).apply()
    }
}
