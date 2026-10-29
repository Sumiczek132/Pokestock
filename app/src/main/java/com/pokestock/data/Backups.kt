package com.pokestock.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Automatic backups so a bad sync or a mis-tap can always be undone.
 * On the phone: one a day (kept 14 days), Mondays kept 12 weeks, plus one taken right before every sync.
 * In Google Drive (optional): a backup file overwritten once a week.
 */
object Backups {
    data class Entry(val file: File, val label: String, val cards: Int, val savedAt: Long)

    private fun dir(ctx: Context) = File(ctx.filesDir, "backups").apply { mkdirs() }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun write(f: File) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(AppState.store.exportJson())
        tmp.renameTo(f)
    }

    /** Today's backup, if not taken yet. Cheap to call often. */
    fun daily(ctx: Context) {
        runCatching {
            if (!AppState.ready) return
            val f = File(dir(ctx), "day-${LocalDate.now()}.json")
            if (!f.exists() && AppState.store.doc.items.isNotEmpty()) write(f)
            prune(ctx)
        }
    }

    /** Taken right before merging in another phone's collection (at most once every 10 minutes). */
    fun beforeSync(ctx: Context) {
        runCatching {
            val f = File(dir(ctx), "before-sync.json")
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 10 * 60_000) write(f)
        }
    }

    fun beforeRestore(ctx: Context) { runCatching { write(File(dir(ctx), "before-restore.json")) } }

    private fun prune(ctx: Context) {
        val today = LocalDate.now()
        dir(ctx).listFiles()?.forEach { f ->
            val d = Regex("day-(\\d{4}-\\d{2}-\\d{2})\\.json").find(f.name)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@forEach
            val age = ChronoUnit.DAYS.between(d, today)
            val keep = age <= 14 || (d.dayOfWeek == DayOfWeek.MONDAY && age <= 7 * 12)
            if (!keep) f.delete()
        }
    }

    suspend fun list(ctx: Context): List<Entry> = withContext(Dispatchers.IO) {
        val fmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
        (dir(ctx).listFiles() ?: emptyArray()).filter { it.name.endsWith(".json") }.mapNotNull { f ->
            runCatching {
                val doc = CollectionDoc.fromJson(JSONObject(f.readText()))
                val cards = doc.items.values.sumOf { it.q } + doc.slabs.values.count { !it.deleted }
                val label = if (f.name == "before-sync.json") "Just before the last sync"
                    else if (f.name == "before-restore.json") "Just before the last restore"
                    else LocalDate.parse(f.name.removePrefix("day-").removeSuffix(".json")).format(fmt)
                Entry(f, label, cards, f.lastModified())
            }.getOrNull()
        }.sortedByDescending { it.savedAt }
    }

    fun read(f: File): CollectionDoc = CollectionDoc.fromJson(JSONObject(f.readText()))

    // ---------- weekly copy in Google Drive ----------
    fun driveUri(ctx: Context): Uri? = prefs(ctx).getString("backupUri", null)?.let(Uri::parse)
    fun driveLast(ctx: Context): String = prefs(ctx).getString("backupLast", "") ?: ""

    fun chooseDrive(ctx: Context, uri: Uri) {
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        prefs(ctx).edit().putString("backupUri", uri.toString()).remove("backupAt").apply()
    }

    fun forgetDrive(ctx: Context) {
        driveUri(ctx)?.let { runCatching { ctx.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        prefs(ctx).edit().remove("backupUri").remove("backupLast").remove("backupAt").apply()
    }

    /** Writes the Drive backup if it's a week old (or [force]). Returns a message, or null if nothing was due. */
    suspend fun weeklyToDrive(ctx: Context, force: Boolean = false): String? = withContext(Dispatchers.IO) {
        val uri = driveUri(ctx) ?: return@withContext null
        if (!AppState.ready) return@withContext null
        val last = prefs(ctx).getLong("backupAt", 0)
        if (!force && System.currentTimeMillis() - last < 7L * 24 * 3600_000) return@withContext null
        try {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(AppState.store.exportJson().toByteArray()) } ?: error("can't write to the file")
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
            prefs(ctx).edit().putLong("backupAt", System.currentTimeMillis()).putString("backupLast", now).apply()
            "Backup saved to Drive"
        } catch (e: Exception) { "Drive backup failed: ${e.message ?: "no connection"}" }
    }
}
