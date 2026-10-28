package com.pokestock.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pokestock.MainActivity
import com.pokestock.R
import com.pokestock.data.AppState
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Morning price update. Waits for a connection, so offline mornings update as soon as signal returns. */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        AppState.update(applicationContext)
        if (com.pokestock.data.DriveSync.fileUri(applicationContext) != null) com.pokestock.data.DriveSync.sync(applicationContext)
        com.pokestock.data.AppUpdate.check(applicationContext)?.let { Schedule.notify(applicationContext, "$it is ready", "Open Pokestock and tap Update now.") }
        return Result.success()
    }
}

/** Runs without needing signal: nudges you when prices are getting old. */
class StaleWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("staleNotify", true)) return Result.success()
        if (!AppState.ready) AppState.init(applicationContext)
        val age = AppState.priceAgeDays() ?: return Result.success()
        if (age >= 3) Schedule.notify(applicationContext, "Prices are $age days old", "Open Pokestock and tap Update when you have signal.")
        return Result.success()
    }
}

object Schedule {
    private const val CHANNEL = "prices"

    private fun delayUntil(time: LocalTime): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMinutes()
    }

    fun apply(ctx: Context) {
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val wm = WorkManager.getInstance(ctx)
        if (prefs.getBoolean("morningUpdate", true)) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(LocalTime.of(6, 0)), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork("morning-update", ExistingPeriodicWorkPolicy.UPDATE, req)
        } else wm.cancelUniqueWork("morning-update")
        val stale = PeriodicWorkRequestBuilder<StaleWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntil(LocalTime.of(10, 0)), TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork("stale-check", ExistingPeriodicWorkPolicy.UPDATE, stale)
    }

    fun notify(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Price updates", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).putExtra("openSync", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(pi).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(1, n) } catch (_: SecurityException) {}
    }
}
