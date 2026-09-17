package studio.room211.racuni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
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

    @Test fun prepoznajeKodBezPodatakaAliNeIRacunKojiCekaMrezu() {
        val nefiskalni = prazanRacun(qrSadrzaj = "010982439WCRX5439WCRX50000083423")
        val cekaMrezu = prazanRacun(qrSadrzaj = "https://suf.purs.gov.rs/v/?vl=abc")
        val obradjen = prazanRacun(
            qrSadrzaj = "010982439WCRX5439WCRX50000083423",
            preduzece = "LIDL SRBIJA KD",
        )

        assertTrue(LogikaRacuna.bezKorisnihPodataka(nefiskalni))
        assertFalse(LogikaRacuna.bezKorisnihPodataka(cekaMrezu))
        assertFalse(LogikaRacuna.bezKorisnihPodataka(obradjen))
        assertFalse(LogikaRacuna.bezKorisnihPodataka(nefiskalni.copy(izvor = LogikaRacuna.IZVOR_RUCNO)))
        assertFalse(LogikaRacuna.bezKorisnihPodataka(nefiskalni.copy(ukupanIznosPara = 12000L)))
    }

    @Test fun kratakNazivRadnjeSklanjaPravniOblik() {
        assertEquals("LIDL", LogikaRacuna.kratakNazivRadnje("LIDL SRBIJA KD"))
        assertEquals("DELHAIZE", LogikaRacuna.kratakNazivRadnje("DELHAIZE SERBIA DOO"))
        assertEquals("MERCATOR-S", LogikaRacuna.kratakNazivRadnje("MERCATOR-S DOO"))
        assertEquals("DOO", LogikaRacuna.kratakNazivRadnje("DOO"))
    }

    @Test fun radnjeSeSvrstavajuPoCelojReci() {
        assertEquals("Marketi", LogikaRacuna.vrstaRadnje("LIDL SRBIJA KD"))
        assertEquals("Marketi", LogikaRacuna.vrstaRadnje("MAXI"))
        assertEquals("Apoteke", LogikaRacuna.vrstaRadnje("APOTEKA JANKOVIĆ"))
        assertEquals("Benzinske stanice", LogikaRacuna.vrstaRadnje("NIS AD NOVI SAD"))
        assertEquals("Pekare i poslastičarnice", LogikaRacuna.vrstaRadnje("PEKARA TRPKOVIĆ"))
        assertEquals(LogikaRacuna.VRSTA_OSTALO, LogikaRacuna.vrstaRadnje("PARADIS KNJIŽARA"))
    }

    @Test fun kljucProizvodaSklanjaInternuSifru() {
        assertEquals(
            "SLADOLED ŠTAPIĆ JAGODA KRISP",
            LogikaRacuna.kljucProizvoda("Sladoled štapić jagoda krisp/1013635"),
        )
        // Kosa crta usred naziva nije šifra, pa se ništa ne gubi.
        assertEquals("1 2 MLEKO", LogikaRacuna.kljucProizvoda("1/2 mleko"))
        assertEquals("HLEB SOMUN", LogikaRacuna.kljucProizvoda("  Hleb  somun  "))
    }

    @Test fun odgovorModelaSePrihvataSamoSaPoznatimVrednostima() {
        val stavke = listOf(
            """{"naziv":"Sladoled krisp","kategorija":"Slatkiši i grickalice","zdravlje":"nezdravo"}""",
            """{"naziv":"Sapun","kategorija":"Izmišljena","zdravlje":"možda"}""",
        ).joinToString(",", prefix = "[", postfix = "]")
        val odgovor = JSONObject().put(
            "candidates",
            JSONArray().put(
                JSONObject().put(
                    "content",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", stavke))),
                )
            ),
        ).toString()

        val procitano = Kategorije.procitajOdgovor(odgovor)
        assertEquals("Slatkiši i grickalice", procitano["Sladoled krisp"]?.kategorija)
        assertEquals("nezdravo", procitano["Sladoled krisp"]?.zdravlje)
        // Nepoznata kategorija i oznaka ne smeju da uđu u bazu.
        assertEquals("Ostalo", procitano["Sapun"]?.kategorija)
        assertEquals("nije hrana", procitano["Sapun"]?.zdravlje)
    }

    @Test fun bekapPreziviPutUJsonINazad() {
        val racun = prazanRacun(qrSadrzaj = "https://suf.purs.gov.rs/v/?vl=abc").copy(
            preduzece = "LIDL SRBIJA KD",
            datumRacuna = 1_757_000_000_000L,
            ukupanIznosPara = 126999L,
            stavke = listOf(
                Stavka(
                    naziv = "Sladoled krisp",
                    kolicina = "1",
                    jedinicnaCenaPara = 6999L,
                    ukupnoPara = 6999L,
                    poreskaOsnovicaPara = 5832L,
                    pdvPara = 1167L,
                    poreskaOznaka = "Ђ",
                    poreskaStopa = "20",
                    kategorija = "Slatkiši i grickalice",
                    zdravlje = "nezdravo",
                )
            ),
        )

        val vraceni = Bekap.izJson(Bekap.kaoJson(listOf(racun)))
        assertEquals(1, vraceni.size)
        assertEquals(racun.copy(id = 0), vraceni.first())
    }

    @Test fun citanjeSaSlikeTraziSvaCetiriPoljaZaProveru() {
        val potpuno = CitanjeSlike.procitajOdgovor(
            """{"pfr_broj":"JLWDW4VM-JLWDW4VM-2088","brojac":"2078/2088ПП",""" +
                """"ukupan_iznos":"1.619,99","pfr_vreme":"17.9.2026. 20:37:48",""" +
                """"radnja":"LILLY DROGERIE","stavke":[]}"""
        )
        assertEquals(CitanjeSlike.PROCITAN_PFR, potpuno.citljivost)
        assertEquals(161999L, potpuno.ukupanIznosPara)
        assertTrue(potpuno.vreme != null)

        // Bez ПФР времена provera nije moguća, ali zapis i dalje ima smisla.
        val delimicno = CitanjeSlike.procitajOdgovor(
            """{"pfr_broj":"JLWDW4VM-JLWDW4VM-2088","brojac":"","ukupan_iznos":"1619,99",""" +
                """"pfr_vreme":"","radnja":"","stavke":[{"naziv":"Magnezijum","kolicina":"1",""" +
                """"cena":"1619,99","ukupno":"1619,99"}]}"""
        )
        assertEquals(CitanjeSlike.DELIMICNO, delimicno.citljivost)
        assertEquals(1, delimicno.stavke.size)
        assertTrue(delimicno.upotrebljivo)

        val prazno = CitanjeSlike.procitajOdgovor(
            """{"pfr_broj":"","brojac":"","ukupan_iznos":"","pfr_vreme":"","radnja":"","stavke":[]}"""
        )
        assertEquals(CitanjeSlike.NECITLJIVO, prazno.citljivost)
        assertFalse(prazno.upotrebljivo)
    }

    @Test fun iznosIVremeSaRacunaSeCitajuSrpskimOblikom() {
        assertEquals(161999L, LogikaRacuna.uPare("1.619,99"))
        assertEquals(161999L, LogikaRacuna.uPare("1619,99"))
        assertEquals(null, LogikaRacuna.uPare("nema"))
        assertTrue(LogikaRacuna.uVreme("17.9.2026. 20:37:48") != null)
        assertEquals(null, LogikaRacuna.uVreme(""))
    }

    private fun prazanRacun(qrSadrzaj: String, preduzece: String = ""): Racun = Racun(
        id = 1,
        nastao = 0,
        izvor = LogikaRacuna.IZVOR_QR,
        naziv = "QR račun",
        qrSadrzaj = qrSadrzaj,
        tekst = qrSadrzaj,
        stanje = LogikaRacuna.SACUVANO,
        greska = "",
        datumRacuna = null,
        pib = "",
        preduzece = preduzece,
        prodajnoMesto = "",
        adresa = "",
        grad = "",
        opstina = "",
        ukupanIznosPara = null,
        brojRacuna = "",
        brojac = "",
        izvornaSlika = "",
        stavke = emptyList(),
    )

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
