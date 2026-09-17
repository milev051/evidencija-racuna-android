package studio.room211.racuni

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object Ui {
    fun Context.dp(vrednost: Int) = (vrednost * resources.displayMetrics.density).toInt()

    fun dugme(context: Context, tekst: String, glavno: Boolean = false, klik: () -> Unit) =
        MaterialButton(
            context,
            null,
            if (glavno) com.google.android.material.R.attr.materialButtonStyle
            else com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            text = tekst
            setOnClickListener { klik() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = context.dp(6) }
        }

    fun kartica(context: Context): Pair<MaterialCardView, LinearLayout> {
        val unutra = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = context.dp(16)
            setPadding(p, p, p, p)
        }
        val kartica = MaterialCardView(context).apply {
            radius = context.dp(18).toFloat()
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = context.dp(12) }
            addView(unutra)
        }
        return kartica to unutra
    }

    fun polje(context: Context, oznaka: String): Pair<TextInputLayout, TextInputEditText> {
        val unos = TextInputEditText(context).apply {
            minLines = 4
            maxLines = 8
            gravity = android.view.Gravity.TOP
        }
        val okvir = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle,
        ).apply {
            hint = oznaka
            addView(unos)
        }
        return okvir to unos
    }

    fun maliTekst(context: Context, tekst: String) = TextView(context).apply {
        this.text = tekst
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        alpha = 0.72f
    }

    fun jednako(context: Context, tekst: String) = TextView(context).apply {
        this.text = tekst
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextIsSelectable(true)
    }
}
