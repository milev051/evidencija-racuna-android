package studio.room211.racuni

import android.content.Context

/**
 * Kupovina bez računa, ispričana svojim rečima.
 *
 * Diktiranje radi tastatura telefona, u isto polje u kom se i kuca. Ovde se
 * taj tekst samo pretvara u stavke, istim oblikom odgovora kao kod čitanja
 * slike, pa se dalje sve ponaša kao svaki drugi zapis.
 */
object Diktat {
    fun sredi(context: Context, tekst: String): CitanjeSlike.Procitano? {
        val kljuc = Podesavanja.geminiKljuc(context)
        if (kljuc.isBlank() || tekst.isBlank()) return null
        val uputstvo = buildString {
            append("Korisnik svojim rečima priča šta je kupio, bez računa. ")
            append("Izvuci pojedinačne artikle i cene u dinarima.\n\n")
            append("Pravila: ako cena nije rečena, ostavi prazno, nikada nagađaj. ")
            append("Ako je rečen datum, upiši ga u polje pfr_vreme u obliku ")
            append("17.9.2026. 20:37:48, inače ostavi prazno. ")
            append("U polje radnja ide mesto kupovine, ako je pomenuto.\n\n")
            append("Vrati isključivo JSON oblika:\n")
            append("{\"radnja\": \"\", \"pfr_vreme\": \"\", \"ukupan_iznos\": \"\", ")
            append("\"stavke\": [{\"naziv\": \"\", \"kolicina\": \"\", \"cena\": \"\", \"ukupno\": \"\"}]}")
            append("\n\nTekst:\n").append(tekst)
        }
        val odgovor = Gemini.odgovor(
            Podesavanja.geminiModel(context),
            kljuc,
            Gemini.telo(uputstvo),
        )
        return CitanjeSlike.procitajOdgovor(Gemini.tekstOdgovora(odgovor))
    }
}
