package studio.room211.racuni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogikaRacunaTest {
    @Test fun prepoznajeInternetAdreseBezObziraNaVelikaSlova() {
        assertTrue(LogikaRacuna.internetAdresa(" HTTPS://suf.purs.gov.rs/v/123 "))
        assertTrue(LogikaRacuna.internetAdresa("http://primer.rs/racun"))
        assertFalse(LogikaRacuna.internetAdresa("Prodavnica; 1200 RSD"))
    }

    @Test fun nazivRucnogUnosaJePrviNeprazanRed() {
        assertEquals("Hleb i mleko", LogikaRacuna.rucniNaziv("\n Hleb i mleko \n 350 din"))
        assertEquals("Ručni unos", LogikaRacuna.rucniNaziv("  \n "))
    }

    @Test fun sufZaglavljeSePretvaraUStrukturisanaPolja() {
        val html = """
            <html><body>
            <script>
              viewModel.InvoiceNumber('ABC-ABC-123');
              viewModel.Token('kratkotrajni-token');
            </script>
            <span id="tinLabel">106884584</span>
            <span id="shopFullNameLabel">1056796-Prodavnica br. 0109</span>
            <span id="addressLabel">SAVE MAŠKOVIĆA 4</span>
            <span id="cityLabel">BEOGRAD</span>
            <span id="administrativeUnitLabel">Beograd-Voždovac</span>
            <span id="totalAmountLabel">1.269,99</span>
            <span id="sdcDateTimeLabel">9.9.2026. 16:54:58</span>
            <p><strong>Предузеће:</strong><span>LIDL SRBIJA KD</span></p>
            </body></html>
        """.trimIndent()

        val rezultat = SufRacun.parsirajZaglavlje(html)
        assertEquals("ABC-ABC-123", rezultat.brojRacuna)
        assertEquals("kratkotrajni-token", rezultat.token)
        assertEquals("LIDL SRBIJA KD", rezultat.analiza.preduzece)
        assertEquals("1056796-Prodavnica br. 0109", rezultat.analiza.prodajnoMesto)
        assertEquals(126999L, rezultat.analiza.ukupanIznosPara)
        assertTrue(rezultat.analiza.datumRacuna != null)
    }

    @Test fun prihvataSamoZvanicniHttpsLinkZaProveru() {
        assertTrue(SufRacun.podrzan("https://suf.purs.gov.rs/v/?vl=abc"))
        assertFalse(SufRacun.podrzan("http://suf.purs.gov.rs/v/?vl=abc"))
        assertFalse(SufRacun.podrzan("https://primer.rs/v/?vl=abc"))
    }

    @Test fun specifikacijaSePretvaraUStavkeBezGubitkaPara() {
        val json = """
            {"success":true,"items":[{
              "gtin":"","name":"Sladoled štapić jagoda krisp/1013635",
              "quantity":1,"total":69.99,"unitPrice":69.99,"label":"Ђ",
              "labelRate":20,"taxBaseAmount":58.32,"vatAmount":11.67
            }]}
        """.trimIndent()

        val stavka = SufRacun.parsirajStavke(json).single()
        assertEquals("Sladoled štapić jagoda krisp/1013635", stavka.naziv)
        assertEquals("1", stavka.kolicina)
        assertEquals(6999L, stavka.ukupnoPara)
        assertEquals(1167L, stavka.pdvPara)
        assertEquals("20", stavka.poreskaStopa)
    }
}
