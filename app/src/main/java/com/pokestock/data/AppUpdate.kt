package com.pokestock.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds a newer build on GitHub, downloads it inside the app and opens Android's installer.
 * Android always asks you to confirm an install for apps outside the Play Store; that one tap can't be skipped.
 */
object AppUpdate {
    var available by mutableStateOf<String?>(null)   // e.g. "Pokestock 0.1.7"
    var progress by mutableStateOf<Float?>(null)
    var error by mutableStateOf("")
    private var apkUrl: String? = null

    private fun currentBuild(ctx: Context): Long =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode }.getOrDefault(0L)

    suspend fun check(ctx: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(String(AppState.download("https://api.github.com/repos/Sumiczek132/Pokestock/releases/latest")))
            val n = json.optString("tag_name").removePrefix("build-").toLongOrNull() ?: return@runCatching null
            val assets = json.optJSONArray("assets")
            var url: String? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url")
            }
            if (n > currentBuild(ctx) && url != null) { apkUrl = url; json.optString("name", "build $n") } else null
        }.getOrNull().also { available = it }
    }

    /** Downloads the new version and opens the installer. */
    suspend fun downloadAndInstall(ctx: Context) {
        val url = apkUrl ?: run { check(ctx); apkUrl } ?: return
        error = ""
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            // First time only: Android needs you to allow Pokestock to install updates.
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            error = "Allow Pokestock to install apps, then come back and tap Update again."
            return
        }
        val file = withContext(Dispatchers.IO) {
            try {
                progress = 0f
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "Pokestock.apk")
                var c = URL(url).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = true
                // GitHub sends release files from a different host; follow that hop by hand if needed.
                var hops = 0
                while (c.responseCode in 300..399 && hops++ < 5) {
                    val next = c.getHeaderField("Location"); c.disconnect()
                    c = URL(next).openConnection() as HttpURLConnection
                }
                if (c.responseCode != 200) throw RuntimeException("server said ${c.responseCode}")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: 13_000_000L
                c.inputStream.use { input -> out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) { val r = input.read(buf); if (r < 0) break; o.write(buf, 0, r); done += r; progress = (done.toFloat() / total).coerceAtMost(1f) }
                } }
                c.disconnect()
                out
            } catch (e: Exception) { error = "Download failed: ${e.message}"; null } finally { progress = null }
        } ?: return
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
