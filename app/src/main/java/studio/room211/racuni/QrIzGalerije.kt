package studio.room211.racuni

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.math.min

/**
 * Čita fiskalne QR kodove iz fotografija potpuno lokalno.
 *
 * Jedan čitač se koristi za ceo grupni uvoz da se model ne otvara ponovo za
 * svaku fotografiju. ML Kit ujedno primenjuje rotaciju zapisanu u EXIF-u, što
 * je važno za fotografije računa napravljene telefonom.
 */
class QrIzGalerije : AutoCloseable {
    private companion object {
        const val MAKSIMALNO_PIKSELA = 8_000_000L
        const val MAKSIMALNA_STRANICA = 5_000
    }

    private val skener: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )

    fun procitaj(context: Context, uri: Uri): Set<String> {
        val slika = InputImage.fromFilePath(context, uri)
        val izCeleSlike = fiskalniKodovi(Tasks.await(skener.process(slika)))
        if (izCeleSlike.isNotEmpty()) return izCeleSlike

        // ML Kit internim skaliranjem cele velike fotografije može da učini
        // mali QR sa dna računa premalim. Zato se, samo kada prvi pokušaj ne
        // uspe, fotografija deli na preklapajuće delove i skenira ponovo.
        val bitmap = ucitajZaDelove(context, uri) ?: return emptySet()
        return try {
            procitajDelove(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun fiskalniKodovi(kodovi: List<Barcode>): Set<String> = kodovi
            .asSequence()
            .filter { it.format == Barcode.FORMAT_QR_CODE }
            .mapNotNull { kod -> kod.rawValue?.trim()?.takeIf(String::isNotEmpty) }
            // Na računima mogu postojati interni, reklamni i drugi kodovi.
            // Samo ovaj link vodi do kompletnog fiskalnog računa i stavki.
            .filter(SufRacun::podrzan)
            .toSet()

    private fun procitajDelove(bitmap: Bitmap): Set<String> {
        val kraca = min(bitmap.width, bitmap.height)
        val odnos = maxOf(bitmap.width, bitmap.height).toFloat() / kraca
        val velicina = if (odnos < 1.6f) (kraca * 0.72f).toInt() else kraca
        val xPozicije = pozicije(bitmap.width, velicina)
        val yPozicije = pozicije(bitmap.height, velicina)

        // Počinje od donjeg desnog dela, jer je fiskalni QR najčešće pri dnu
        // računa. Preklapanje sprečava da kod ostane presečen između dva dela.
        for (y in yPozicije.asReversed()) {
            for (x in xPozicije.asReversed()) {
                val deo = Bitmap.createBitmap(bitmap, x, y, velicina, velicina)
                try {
                    val rezultat = fiskalniKodovi(
                        Tasks.await(skener.process(InputImage.fromBitmap(deo, 0)))
                    )
                    if (rezultat.isNotEmpty()) return rezultat
                } finally {
                    if (deo !== bitmap) deo.recycle()
                }
            }
        }
        return emptySet()
    }

    private fun pozicije(duzina: Int, velicina: Int): List<Int> {
        if (duzina <= velicina) return listOf(0)
        val poslednja = duzina - velicina
        val korak = (velicina * 0.75f).toInt().coerceAtLeast(1)
        val izlaz = mutableListOf(0)
        var trenutna = korak
        while (trenutna < poslednja) {
            izlaz += trenutna
            trenutna += korak
        }
        if (izlaz.last() != poslednja) izlaz += poslednja
        return izlaz
    }

    private fun ucitajZaDelove(context: Context, uri: Uri): Bitmap? {
        val granice = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, granice)
        }
        if (granice.outWidth <= 0 || granice.outHeight <= 0) return null

        var uzorak = 1
        while (
            granice.outWidth / uzorak > MAKSIMALNA_STRANICA ||
            granice.outHeight / uzorak > MAKSIMALNA_STRANICA ||
            granice.outWidth.toLong() / uzorak * (granice.outHeight / uzorak) > MAKSIMALNO_PIKSELA
        ) {
            uzorak *= 2
        }
        val ucitan = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = uzorak
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        } ?: return null

        val rotacija = context.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotacija == 0f) return ucitan

        val okrenut = Bitmap.createBitmap(
            ucitan,
            0,
            0,
            ucitan.width,
            ucitan.height,
            Matrix().apply { postRotate(rotacija) },
            true,
        )
        if (okrenut !== ucitan) ucitan.recycle()
        return okrenut
    }

    override fun close() {
        skener.close()
    }
}
