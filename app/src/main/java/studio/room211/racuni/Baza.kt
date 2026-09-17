package studio.room211.racuni

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Jedina trajna istina aplikacije. Sve se upisuje pre pokušaja mrežne obrade. */
class Baza(context: Context) : SQLiteOpenHelper(context, "racuni.db", null, 3) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE racun (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nastao INTEGER NOT NULL,
                izvor TEXT NOT NULL,
                naziv TEXT NOT NULL,
                qr_sadrzaj TEXT NOT NULL DEFAULT '',
                tekst TEXT NOT NULL DEFAULT '',
                stanje TEXT NOT NULL,
                greska TEXT NOT NULL DEFAULT '',
                datum_racuna INTEGER,
                pib TEXT NOT NULL DEFAULT '',
                preduzece TEXT NOT NULL DEFAULT '',
                prodajno_mesto TEXT NOT NULL DEFAULT '',
                adresa TEXT NOT NULL DEFAULT '',
                grad TEXT NOT NULL DEFAULT '',
                opstina TEXT NOT NULL DEFAULT '',
                ukupan_iznos_para INTEGER,
                broj_racuna TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX racun_nastao ON racun(nastao DESC)")
        db.execSQL("CREATE INDEX racun_stanje ON racun(stanje)")
        db.execSQL(
            "CREATE UNIQUE INDEX racun_jedinstveni_qr ON racun(qr_sadrzaj) " +
                "WHERE qr_sadrzaj <> ''"
        )
        napraviTabeluStavki(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Ako je stara verzija već imala duplikate, zadržava se prvi zapis.
            db.execSQL(
                """
                DELETE FROM racun
                WHERE qr_sadrzaj <> ''
                  AND id NOT IN (
                    SELECT MIN(id) FROM racun
                    WHERE qr_sadrzaj <> ''
                    GROUP BY qr_sadrzaj
                  )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX racun_jedinstveni_qr ON racun(qr_sadrzaj) " +
                    "WHERE qr_sadrzaj <> ''"
            )
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE racun ADD COLUMN datum_racuna INTEGER")
            db.execSQL("ALTER TABLE racun ADD COLUMN pib TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE racun ADD COLUMN preduzece TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE racun ADD COLUMN prodajno_mesto TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE racun ADD COLUMN adresa TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE racun ADD COLUMN grad TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE racun ADD COLUMN opstina TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE racun ADD COLUMN ukupan_iznos_para INTEGER")
            db.execSQL("ALTER TABLE racun ADD COLUMN broj_racuna TEXT NOT NULL DEFAULT ''")
            napraviTabeluStavki(db)
            // Linkovi koje je verzija 0.1/0.2 sačuvala samo kao običan tekst
            // ponovo prolaze kroz novi strukturisani analizator.
            db.execSQL(
                "UPDATE racun SET stanje = '${LogikaRacuna.CEKA_MREZU}', greska = '' " +
                    "WHERE qr_sadrzaj LIKE 'https://suf.purs.gov.rs/v/%'"
            )
        }
    }

    private fun napraviTabeluStavki(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS stavka (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                racun_id INTEGER NOT NULL REFERENCES racun(id) ON DELETE CASCADE,
                naziv TEXT NOT NULL,
                kolicina TEXT NOT NULL,
                jedinicna_cena_para INTEGER NOT NULL,
                ukupno_para INTEGER NOT NULL,
                poreska_osnovica_para INTEGER NOT NULL,
                pdv_para INTEGER NOT NULL,
                poreska_oznaka TEXT NOT NULL DEFAULT '',
                poreska_stopa TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS stavka_racun ON stavka(racun_id)")
    }

    fun dodajQr(sadrzaj: String, vreme: Long = System.currentTimeMillis()): Long {
        val jeAdresa = LogikaRacuna.internetAdresa(sadrzaj)
        return writableDatabase.insertWithOnConflict(
            "racun",
            null,
            ContentValues().apply {
                put("nastao", vreme)
                put("izvor", LogikaRacuna.IZVOR_QR)
                put("naziv", "QR račun")
                put("qr_sadrzaj", sadrzaj.trim())
                put("tekst", if (jeAdresa) "" else sadrzaj.trim())
                put("stanje", if (jeAdresa) LogikaRacuna.CEKA_MREZU else LogikaRacuna.SACUVANO)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    fun dodajRucno(tekst: String, vreme: Long = System.currentTimeMillis()): Long =
        writableDatabase.insertOrThrow(
            "racun",
            null,
            ContentValues().apply {
                put("nastao", vreme)
                put("izvor", LogikaRacuna.IZVOR_RUCNO)
                put("naziv", LogikaRacuna.rucniNaziv(tekst))
                put("tekst", tekst.trim())
                put("stanje", LogikaRacuna.SACUVANO)
            }
        )

    fun svi(): List<Racun> = citaj(
        POLJA_RACUNA + " FROM racun ORDER BY COALESCE(datum_racuna, nastao) DESC, id DESC",
        emptyArray(),
    )

    fun zaMreznuObradu(): List<Racun> = citaj(
        POLJA_RACUNA +
            "FROM racun WHERE stanje IN (?, ?) AND qr_sadrzaj LIKE 'http%' " +
            "ORDER BY nastao ASC",
        arrayOf(LogikaRacuna.CEKA_MREZU, LogikaRacuna.GRESKA),
    )

    fun sacuvajObradu(id: Long, tekst: String) {
        writableDatabase.update(
            "racun",
            ContentValues().apply {
                put("tekst", tekst)
                put("stanje", LogikaRacuna.SACUVANO)
                put("greska", "")
            },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun sacuvajAnalizu(id: Long, analiza: AnalizaRacuna) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.update(
                "racun",
                ContentValues().apply {
                    put("naziv", analiza.preduzece.ifBlank { analiza.prodajnoMesto }.ifBlank { "QR račun" })
                    put("tekst", analiza.kaoTekst())
                    put("stanje", LogikaRacuna.SACUVANO)
                    put("greska", "")
                    if (analiza.datumRacuna == null) putNull("datum_racuna")
                    else put("datum_racuna", analiza.datumRacuna)
                    put("pib", analiza.pib)
                    put("preduzece", analiza.preduzece)
                    put("prodajno_mesto", analiza.prodajnoMesto)
                    put("adresa", analiza.adresa)
                    put("grad", analiza.grad)
                    put("opstina", analiza.opstina)
                    if (analiza.ukupanIznosPara == null) putNull("ukupan_iznos_para")
                    else put("ukupan_iznos_para", analiza.ukupanIznosPara)
                    put("broj_racuna", analiza.brojRacuna)
                },
                "id = ?",
                arrayOf(id.toString()),
            )
            db.delete("stavka", "racun_id = ?", arrayOf(id.toString()))
            for (stavka in analiza.stavke) {
                db.insertOrThrow(
                    "stavka",
                    null,
                    ContentValues().apply {
                        put("racun_id", id)
                        put("naziv", stavka.naziv)
                        put("kolicina", stavka.kolicina)
                        put("jedinicna_cena_para", stavka.jedinicnaCenaPara)
                        put("ukupno_para", stavka.ukupnoPara)
                        put("poreska_osnovica_para", stavka.poreskaOsnovicaPara)
                        put("pdv_para", stavka.pdvPara)
                        put("poreska_oznaka", stavka.poreskaOznaka)
                        put("poreska_stopa", stavka.poreskaStopa)
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun sacuvajGresku(id: Long, poruka: String) {
        writableDatabase.update(
            "racun",
            ContentValues().apply {
                put("stanje", LogikaRacuna.GRESKA)
                put("greska", poruka.take(300))
            },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    private fun citaj(sql: String, argumenti: Array<String>): List<Racun> {
        val rezultat = ArrayList<Racun>()
        readableDatabase.rawQuery(sql, argumenti).use { c ->
            while (c.moveToNext()) {
                rezultat.add(
                    Racun(
                        id = c.getLong(0),
                        nastao = c.getLong(1),
                        izvor = c.getString(2),
                        naziv = c.getString(3),
                        qrSadrzaj = c.getString(4),
                        tekst = c.getString(5),
                        stanje = c.getString(6),
                        greska = c.getString(7),
                        datumRacuna = if (c.isNull(8)) null else c.getLong(8),
                        pib = c.getString(9),
                        preduzece = c.getString(10),
                        prodajnoMesto = c.getString(11),
                        adresa = c.getString(12),
                        grad = c.getString(13),
                        opstina = c.getString(14),
                        ukupanIznosPara = if (c.isNull(15)) null else c.getLong(15),
                        brojRacuna = c.getString(16),
                        stavke = citajStavke(c.getLong(0)),
                    )
                )
            }
        }
        return rezultat
    }

    private fun citajStavke(racunId: Long): List<Stavka> {
        val rezultat = ArrayList<Stavka>()
        readableDatabase.rawQuery(
            "SELECT naziv, kolicina, jedinicna_cena_para, ukupno_para, " +
                "poreska_osnovica_para, pdv_para, poreska_oznaka, poreska_stopa " +
                "FROM stavka WHERE racun_id = ? ORDER BY id",
            arrayOf(racunId.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                rezultat.add(
                    Stavka(
                        naziv = c.getString(0),
                        kolicina = c.getString(1),
                        jedinicnaCenaPara = c.getLong(2),
                        ukupnoPara = c.getLong(3),
                        poreskaOsnovicaPara = c.getLong(4),
                        pdvPara = c.getLong(5),
                        poreskaOznaka = c.getString(6),
                        poreskaStopa = c.getString(7),
                    )
                )
            }
        }
        return rezultat
    }

    private fun AnalizaRacuna.kaoTekst(): String = buildString {
        if (preduzece.isNotBlank()) append(preduzece).append('\n')
        if (prodajnoMesto.isNotBlank()) append(prodajnoMesto).append('\n')
        if (adresa.isNotBlank()) append(adresa).append('\n')
        if (grad.isNotBlank()) append(grad).append('\n')
        if (stavke.isNotEmpty()) {
            append("\nStavke:\n")
            for (stavka in stavke) append(stavka.naziv).append(" × ").append(stavka.kolicina).append('\n')
        }
    }.trim()

    companion object {
        private const val POLJA_RACUNA =
            "SELECT id, nastao, izvor, naziv, qr_sadrzaj, tekst, stanje, greska, " +
                "datum_racuna, pib, preduzece, prodajno_mesto, adresa, grad, opstina, " +
                "ukupan_iznos_para, broj_racuna "
    }
}
