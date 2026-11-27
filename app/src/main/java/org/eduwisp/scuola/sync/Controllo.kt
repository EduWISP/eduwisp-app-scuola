// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Michele Salerno e i contributori di EduWISP
package org.eduwisp.scuola.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.eduwisp.scuola.MainActivity
import org.eduwisp.scuola.R
import org.eduwisp.scuola.Sessione
import org.eduwisp.scuola.api.Esito
import java.util.concurrent.TimeUnit

/**
 * Controllo periodico delle richieste di app in attesa (ADR-0022): nessun servizio di notifica
 * proprietario. La notifica non contiene nomi di studenti, perché compare sulla schermata di blocco.
 */
class ControlloWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val sessione = Sessione(applicationContext)
        if (!sessione.connesso) return Result.success()
        return when (val e = sessione.api().riepilogo()) {
            is Esito.Ok -> {
                if (nuoveDaNotificare(e.valore, sessione.ultimeInAttesa)) Controllo.notifica(applicationContext, e.valore)
                sessione.ultimeInAttesa = e.valore
                Result.success()
            }
            is Esito.Errore -> Result.success()
            is Esito.Rete -> Result.retry()
        }
    }
}

/** Si notifica solo quando le richieste in attesa aumentano. */
fun nuoveDaNotificare(adesso: Int, prima: Int) = adesso > prima

object Controllo {
    private const val CANALE = "richieste"

    fun pianifica(context: Context) {
        val richiesta = PeriodicWorkRequestBuilder<ControlloWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("eduwisp-scuola-controllo", ExistingPeriodicWorkPolicy.KEEP, richiesta)
    }

    fun notifica(context: Context, quante: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANALE, context.getString(R.string.canale_richieste), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val apri = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(context, CANALE)
            .setSmallIcon(R.drawable.ic_eduwisp)
            .setContentTitle(context.getString(R.string.notifica_titolo))
            .setContentText(context.resources.getQuantityString(R.plurals.notifica_testo, quante, quante))
            .setContentIntent(apri)
            .setAutoCancel(true)
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE)
            .build()
        nm.notify(1, n)
    }
}
