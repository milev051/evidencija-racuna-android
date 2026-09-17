package studio.room211.racuni

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Obaveštenje o uvozu slika.
 *
 * Uvoz više ne drži prozor preko aplikacije, pa se tok vidi i kada korisnik
 * ode na drugi ekran ili izađe iz aplikacije.
 */
object Obavestenja {
    const val KANAL_UVOZ = "uvoz-slika"
    private const val ID_UVOZ = 3001

    fun pripremi(context: Context) {
        val kanal = NotificationChannel(
            KANAL_UVOZ,
            "Uvoz slika",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Tok traženja fiskalnih QR kodova u izabranim slikama"
            setShowBadge(false)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(kanal)
    }

    fun napredak(context: Context, zavrseno: Int, ukupno: Int, pronadjeno: Int) {
        val gradnja = osnova(context)
            .setContentTitle("Skeniram slike")
            .setContentText(
                "Slika ${minOf(zavrseno + 1, ukupno)} od $ukupno • pronađeno: $pronadjeno"
            )
            .setProgress(ukupno, zavrseno, false)
            .setOngoing(true)
        posalji(context, gradnja.build())
    }

    fun kraj(context: Context, poruka: String) {
        val gradnja = osnova(context)
            .setContentTitle("Uvoz iz galerije je gotov")
            .setContentText(poruka)
            .setStyle(NotificationCompat.BigTextStyle().bigText(poruka))
            .setAutoCancel(true)
        posalji(context, gradnja.build())
    }

    fun ukloni(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_UVOZ)
    }

    private fun osnova(context: Context) = NotificationCompat.Builder(context, KANAL_UVOZ)
        .setSmallIcon(R.drawable.ic_obavestenje)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )

    /** Bez dozvole se obaveštenje tiho preskače; tok se i dalje vidi u aplikaciji. */
    @SuppressLint("MissingPermission")
    private fun posalji(context: Context, obavestenje: android.app.Notification) {
        if (!dozvoljeno(context)) return
        NotificationManagerCompat.from(context).notify(ID_UVOZ, obavestenje)
    }

    fun dozvoljeno(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
