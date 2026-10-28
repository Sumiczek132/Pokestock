package com.pokestock.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Sync through one file in Google Drive, picked with Android's own file picker.
 * No Google keys or passwords: the phone just remembers permission to that one file.
 */
object DriveSync {
    var status by mutableStateOf("")
    var busy by mutableStateOf(false)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun fileUri(ctx: Context): Uri? = prefs(ctx).getString("driveUri", null)?.let(Uri::parse)
    fun lastSync(ctx: Context): String = prefs(ctx).getString("driveLast", "") ?: ""

    fun choose(ctx: Context, uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { ctx.contentResolver.takePersistableUriPermission(uri, flags) } catch (_: Exception) {}
        prefs(ctx).edit().putString("driveUri", uri.toString()).apply()
    }

    fun forget(ctx: Context) {
        fileUri(ctx)?.let { try { ctx.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {} }
        prefs(ctx).edit().remove("driveUri").apply()
        status = ""
    }

    /** Read the shared file, merge it in, write the merged result back. */
    suspend fun sync(ctx: Context): String = withContext(Dispatchers.IO) {
        val uri = fileUri(ctx) ?: return@withContext "No sync file chosen yet."
        if (busy) return@withContext status
        busy = true
        try {
            val text = try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8) } catch (_: Exception) { null }
            var taken = 0
            if (!text.isNullOrBlank()) {
                val other = CollectionDoc.fromJson(JSONObject(text))
                withContext(Dispatchers.Main) { taken = AppState.store.mergeIn(other); AppState.refreshCustom() }
            }
            val out = AppState.store.exportJson().toByteArray()
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(out) } ?: throw RuntimeException("can't write to the file")
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
            prefs(ctx).edit().putString("driveLast", now).apply()
            status = if (taken > 0) "Synced $now: took $taken changes." else "Synced $now."
            status
        } catch (e: Exception) {
            status = "Drive sync failed: ${e.message ?: "no connection"}. Your changes are safe on this phone and will sync next time."
            status
        } finally { busy = false }
    }
}
