package studio.room211.racuni

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * Kvadratni okvir preko slike kamere.
 *
 * Crta se samo zatamnjenje oko kvadrata i četiri ugla, bez crvene linije i
 * bez teksta. Kvadrat je na sredini ekrana, isto kao oblast koju BarcodeView
 * dekodira, pa je ono što korisnik vidi tačno ono što se skenira.
 */
class OkvirSkenera(context: Context, private val strana: Int) : View(context) {
    private val zatamnjenje = Paint().apply { color = 0x99000000.toInt() }
    private val ivica = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = context.resources.displayMetrics.density * 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val uglovi = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sirina = width.toFloat()
        val visina = height.toFloat()
        val bocno = (sirina - strana) / 2f
        val gore = (visina - strana) / 2f
        val levo = maxOf(bocno, 0f)
        val vrh = maxOf(gore, 0f)
        val desno = sirina - levo
        val dno = visina - vrh

        canvas.drawRect(0f, 0f, sirina, vrh, zatamnjenje)
        canvas.drawRect(0f, vrh, levo, dno, zatamnjenje)
        canvas.drawRect(desno, vrh, sirina, dno, zatamnjenje)
        canvas.drawRect(0f, dno, sirina, visina, zatamnjenje)

        val duzina = (desno - levo) * 0.16f
        val poluprecnik = ivica.strokeWidth * 4f
        uglovi.reset()
        ugao(levo, vrh, 1f, 1f, duzina, poluprecnik)
        ugao(desno, vrh, -1f, 1f, duzina, poluprecnik)
        ugao(desno, dno, -1f, -1f, duzina, poluprecnik)
        ugao(levo, dno, 1f, -1f, duzina, poluprecnik)
        canvas.drawPath(uglovi, ivica)
    }

    private fun ugao(x: Float, y: Float, smerX: Float, smerY: Float, duzina: Float, poluprecnik: Float) {
        uglovi.moveTo(x, y + smerY * duzina)
        uglovi.lineTo(x, y + smerY * poluprecnik)
        uglovi.quadTo(x, y, x + smerX * poluprecnik, y)
        uglovi.lineTo(x + smerX * duzina, y)
    }
}
