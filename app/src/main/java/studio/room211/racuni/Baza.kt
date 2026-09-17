package studio.room211.racuni

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Jedina trajna istina aplikacije. Sve se upisuje pre pokušaja mrežne obrade. */
class Baza(context: Context) : SQLiteOpenHelper(context, "racuni.db", null, 5) {

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
                broj_racuna TEXT NOT NULL DEFAULT '',
                brojac TEXT NOT NULL DEFAULT '',
                izvorna_slika TEXT NOT NULL DEFAULT ''
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
        napraviTabeluPojmova(db)
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
        if (oldVersion < 5) {
            dodajKolonu(db, "racun", "brojac")
            dodajKolonu(db, "racun", "izvorna_slika")
        }
        if (oldVersion < 4) {
            // Baza sa verzije 3 već ima tabelu stavki, ali bez kolona za kategoriju.
            dodajKolonu(db, "stavka", "kategorija")
            dodajKolonu(db, "stavka", "zdravlje")
            napraviTabeluPojmova(db)
        }
    }

    private fun dodajKolonu(db: SQLiteDatabase, tabela: String, kolona: String) {
        val postoji = db.rawQuery("PRAGMA table_info($tabela)", null).use { c ->
            generateSequence { if (c.moveToNext()) c.getString(1) else null }.any { it == kolona }
        }
        if (!postoji) {
            db.execSQL("ALTER TABLE $tabela ADD COLUMN $kolona TEXT NOT NULL DEFAULT ''")
        }
    }

    /** Jednom razvrstan proizvod se pamti po ključu, pa se model ne pita ponovo. */
    private fun napraviTabeluPojmova(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pojam (
                kljuc TEXT PRIMARY KEY,
                kategorija TEXT NOT NULL,
                zdravlje TEXT NOT NULL
            )
            """.trimIndent()
        )
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
                poreska_stopa TEXT NOT NULL DEFAULT '',
                kategorija TEXT NOT NULL DEFAULT '',
                zdravlje TEXT NOT NULL DEFAULT ''
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

    /**
     * Račun pročitan sa fotografije. Ostaje u stanju „čeka proveru" dok se
     * podaci ne potvrde na zvaničnoj stranici, pa se odmah vidi šta je
     * pouzdano, a šta je samo pročitano sa slike.
     */
    fun dodajSaSlike(
        naziv: String,
        tekst: String,
        brojRacuna: String,
        brojac: String,
        ukupanIznosPara: Long?,
        datumRacuna: Long?,
        izvornaSlika: String,
        stanje: String,
        stavke: List<Stavka>,
        vreme: Long = System.currentTimeMillis(),
    ): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (brojRacuna.isNotBlank() && postojiBrojRacuna(db, brojRacuna)) return -1L
            if (izvornaSlika.isNotBlank() && postojiSlika(db, izvornaSlika)) return -1L
            val id = db.insertOrThrow(
                "racun",
                null,
                ContentValues().apply {
                    put("nastao", vreme)
                    put("izvor", LogikaRacuna.IZVOR_SLIKA)
                    put("naziv", naziv.ifBlank { "Račun sa slike" })
                    put("qr_sadrzaj", "")
                    put("tekst", tekst)
                    put("stanje", stanje)
                    put("greska", "")
                    if (datumRacuna == null) putNull("datum_racuna") else put("datum_racuna", datumRacuna)
                    if (ukupanIznosPara == null) putNull("ukupan_iznos_para")
                    else put("ukupan_iznos_para", ukupanIznosPara)
                    put("broj_racuna", brojRacuna)
                    put("brojac", brojac)
                    put("izvorna_slika", izvornaSlika)
                },
            )
            for (stavka in stavke) upisiStavku(db, id, stavka)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    /** Kategorija se uzima iz keša, osim kada stavka već nosi svoju iz bekapa. */
    private fun upisiStavku(
        db: SQLiteDatabase,
        racunId: Long,
        stavka: Stavka,
        koristiKes: Boolean = true,
    ) {
        val zapamceno = if (koristiKes) izKesa(db, LogikaRacuna.kljucProizvoda(stavka.naziv)) else null
        db.insertOrThrow(
            "stavka",
            null,
            ContentValues().apply {
                put("racun_id", racunId)
                put("naziv", stavka.naziv)
                put("kolicina", stavka.kolicina)
                put("jedinicna_cena_para", stavka.jedinicnaCenaPara)
                put("ukupno_para", stavka.ukupnoPara)
                put("poreska_osnovica_para", stavka.poreskaOsnovicaPara)
                put("pdv_para", stavka.pdvPara)
                put("poreska_oznaka", stavka.poreskaOznaka)
                put("poreska_stopa", stavka.poreskaStopa)
                put("kategorija", zapamceno?.kategorija ?: stavka.kategorija)
                put("zdravlje", zapamceno?.zdravlje ?: stavka.zdravlje)
            },
        )
    }

    private fun postojiBrojRacuna(db: SQLiteDatabase, broj: String): Boolean = db.rawQuery(
        "SELECT 1 FROM racun WHERE broj_racuna = ? LIMIT 1",
        arrayOf(broj),
    ).use { it.moveToFirst() }

    private fun postojiSlika(db: SQLiteDatabase, ime: String): Boolean = db.rawQuery(
        "SELECT 1 FROM racun WHERE izvorna_slika = ? LIMIT 1",
        arrayOf(ime),
    ).use { it.moveToFirst() }

    /** Ručni unos koji je model razložio na stavke. */
    fun dodajRucnoSaStavkama(
        naziv: String,
        tekst: String,
        ukupanIznosPara: Long?,
        datumRacuna: Long?,
        stavke: List<Stavka>,
        vreme: Long = System.currentTimeMillis(),
    ): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insertOrThrow(
                "racun",
                null,
                ContentValues().apply {
                    put("nastao", vreme)
                    put("izvor", LogikaRacuna.IZVOR_RUCNO)
                    put("naziv", naziv.ifBlank { LogikaRacuna.rucniNaziv(tekst) })
                    put("tekst", tekst.trim())
                    put("stanje", LogikaRacuna.SACUVANO)
                    if (datumRacuna == null) putNull("datum_racuna") else put("datum_racuna", datumRacuna)
                    if (ukupanIznosPara == null) putNull("ukupan_iznos_para")
                    else put("ukupan_iznos_para", ukupanIznosPara)
                },
            )
            for (stavka in stavke) upisiStavku(db, id, stavka)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
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

    fun obrisi(id: Long): Int =
        // Stavke odlaze zajedno sa računom, preko ON DELETE CASCADE.
        writableDatabase.delete("racun", "id = ?", arrayOf(id.toString()))

    /** Koliko je sačuvanih kodova koji ne mogu da dobiju nijedan podatak. */
    fun brojBezPodataka(): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM racun WHERE $USLOV_BEZ_PODATAKA",
            arrayOf(LogikaRacuna.IZVOR_QR),
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun obrisiBezPodataka(): Int =
        writableDatabase.delete("racun", USLOV_BEZ_PODATAKA, arrayOf(LogikaRacuna.IZVOR_QR))

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
            for (stavka in analiza.stavke) upisiStavku(db, id, stavka)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Nazivi proizvoda koje model još nije video, najviše koliko staje u jedan upit. */
    fun nekategorisaniNazivi(najvise: Int): List<String> =
        readableDatabase.rawQuery(
            "SELECT DISTINCT naziv FROM stavka WHERE kategorija = '' AND naziv <> '' " +
                "ORDER BY naziv LIMIT ?",
            arrayOf(najvise.toString()),
        ).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }

    fun brojStavki(): Int = jedanBroj("SELECT COUNT(*) FROM stavka")

    fun brojKategorisanih(): Int =
        jedanBroj("SELECT COUNT(*) FROM stavka WHERE kategorija <> ''")

    fun kesPojma(kljuc: String): Pojam? = izKesa(readableDatabase, kljuc)

    /** Upisuje odgovor modela: i u keš pojmova i u sve stavke sa tim nazivom. */
    fun zapamtiKategoriju(naziv: String, kategorija: String, zdravlje: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                "pojam",
                null,
                ContentValues().apply {
                    put("kljuc", LogikaRacuna.kljucProizvoda(naziv))
                    put("kategorija", kategorija)
                    put("zdravlje", zdravlje)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            db.update(
                "stavka",
                ContentValues().apply {
                    put("kategorija", kategorija)
                    put("zdravlje", zdravlje)
                },
                "naziv = ?",
                arrayOf(naziv),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Vraća koliko je zapisa dodato; postojeći se prepoznaju i preskaču. */
    fun uvezi(racuni: List<Racun>): Int {
        val db = writableDatabase
        var dodato = 0
        db.beginTransaction()
        try {
            for (racun in racuni) {
                if (vecPostoji(db, racun)) continue
                val id = db.insertOrThrow(
                    "racun",
                    null,
                    ContentValues().apply {
                        put("nastao", racun.nastao)
                        put("izvor", racun.izvor)
                        put("naziv", racun.naziv)
                        put("qr_sadrzaj", racun.qrSadrzaj)
                        put("tekst", racun.tekst)
                        put("stanje", racun.stanje)
                        put("greska", racun.greska)
                        if (racun.datumRacuna == null) putNull("datum_racuna")
                        else put("datum_racuna", racun.datumRacuna)
                        put("pib", racun.pib)
                        put("preduzece", racun.preduzece)
                        put("prodajno_mesto", racun.prodajnoMesto)
                        put("adresa", racun.adresa)
                        put("grad", racun.grad)
                        put("opstina", racun.opstina)
                        if (racun.ukupanIznosPara == null) putNull("ukupan_iznos_para")
                        else put("ukupan_iznos_para", racun.ukupanIznosPara)
                        put("broj_racuna", racun.brojRacuna)
                        put("brojac", racun.brojac)
                        put("izvorna_slika", racun.izvornaSlika)
                    },
                )
                for (stavka in racun.stavke) upisiStavku(db, id, stavka, koristiKes = false)
                dodato++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return dodato
    }

    private fun vecPostoji(db: SQLiteDatabase, racun: Racun): Boolean {
        val uslov = if (racun.qrSadrzaj.isNotBlank()) {
            db.rawQuery(
                "SELECT 1 FROM racun WHERE qr_sadrzaj = ? LIMIT 1",
                arrayOf(racun.qrSadrzaj),
            )
        } else {
            db.rawQuery(
                "SELECT 1 FROM racun WHERE qr_sadrzaj = '' AND nastao = ? AND tekst = ? LIMIT 1",
                arrayOf(racun.nastao.toString(), racun.tekst),
            )
        }
        return uslov.use { it.moveToFirst() }
    }

    private fun izKesa(db: SQLiteDatabase, kljuc: String): Pojam? = db.rawQuery(
        "SELECT kategorija, zdravlje FROM pojam WHERE kljuc = ? LIMIT 1",
        arrayOf(kljuc),
    ).use { c -> if (c.moveToFirst()) Pojam(c.getString(0), c.getString(1)) else null }

    private fun jedanBroj(sql: String): Int =
        readableDatabase.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    data class Pojam(val kategorija: String, val zdravlje: String)

    /** Posle uspešne provere zapis dobija zvanični link i ide na redovnu obradu. */
    fun postaviQr(id: Long, adresa: String) {
        writableDatabase.update(
            "racun",
            ContentValues().apply {
                put("qr_sadrzaj", adresa.trim())
                put("stanje", LogikaRacuna.CEKA_MREZU)
                put("greska", "")
            },
            "id = ?",
            arrayOf(id.toString()),
        )
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
                        brojac = c.getString(17),
                        izvornaSlika = c.getString(18),
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
                "poreska_osnovica_para, pdv_para, poreska_oznaka, poreska_stopa, " +
                "kategorija, zdravlje FROM stavka WHERE racun_id = ? ORDER BY id",
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
                        kategorija = c.getString(8),
                        zdravlje = c.getString(9),
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
        /** Isti uslov kao LogikaRacuna.bezKorisnihPodataka, samo u SQL-u. */
        private const val USLOV_BEZ_PODATAKA =
            "izvor = ? AND qr_sadrzaj <> '' AND qr_sadrzaj NOT LIKE 'http%' " +
                "AND preduzece = '' AND prodajno_mesto = '' " +
                "AND ukupan_iznos_para IS NULL " +
                "AND id NOT IN (SELECT racun_id FROM stavka)"

        private const val POLJA_RACUNA =
            "SELECT id, nastao, izvor, naziv, qr_sadrzaj, tekst, stanje, greska, " +
                "datum_racuna, pib, preduzece, prodajno_mesto, adresa, grad, opstina, " +
                "ukupan_iznos_para, broj_racuna, brojac, izvorna_slika "
    }
}
