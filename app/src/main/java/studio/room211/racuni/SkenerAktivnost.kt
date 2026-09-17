package studio.room211.racuni

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import studio.room211.racuni.Ui.dp
import com.google.android.material.button.MaterialButton
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.Size

/**
 * Skener sa čistim kvadratnim okvirom.
 *
 * Ugrađeni ekran biblioteke ima crvenu liniju i red teksta pri dnu. Ovde se
 * koristi ista logika (CaptureManager), ali se crta samo kvadrat na sredini.
 */
class SkenerAktivnost : AppCompatActivity() {
    private lateinit var upravljac: CaptureManager
    private lateinit var prikaz: DecoratedBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val strana = stranaOkvira()

        prikaz = DecoratedBarcodeView(this).apply {
            statusView.visibility = View.GONE
            viewFinder.setLaserVisibility(false)
            // Zatamnjenje crta OkvirSkenera, da kvadrat i uglovi budu isti
            // bez obzira na verziju biblioteke.
            viewFinder.setMaskColor(Color.TRANSPARENT)
            barcodeView.setFramingRectSize(Size(strana, strana))
        }

        val koren = FrameLayout(this)
        koren.addView(
            prikaz,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        koren.addView(
            OkvirSkenera(this, strana),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        // Kamera ume da se otvori prva, pa mora da postoji vidljiv izlaz nazad.
        koren.addView(
            MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
                text = "Nazad u aplikaciju"
                setTextColor(Color.WHITE)
                strokeColor = android.content.res.ColorStateList.valueOf(Color.WHITE)
                setOnClickListener { finish() }
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(28)
            },
        )
        setContentView(koren)

        upravljac = CaptureManager(this, prikaz)
        upravljac.initializeFromIntent(intent, savedInstanceState)
        upravljac.decode()
    }

    /** Kvadrat zauzima veći deo kraće stranice, ali ne preko celog ekrana. */
    private fun stranaOkvira(): Int {
        val mere = resources.displayMetrics
        val kraca = minOf(mere.widthPixels, mere.heightPixels)
        val najvise = (340 * mere.density).toInt()
        return minOf((kraca * 0.74f).toInt(), najvise)
    }

    override fun onResume() {
        super.onResume()
        upravljac.onResume()
    }

    override fun onPause() {
        super.onPause()
        upravljac.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        upravljac.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        upravljac.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        upravljac.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
