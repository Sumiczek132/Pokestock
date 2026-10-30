package com.pokestock.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokestock.data.AppState
import com.pokestock.data.AppUpdate
import com.pokestock.data.Backups
import com.pokestock.data.CollectionDoc
import com.pokestock.data.Data
import com.pokestock.data.DriveSync
import com.pokestock.data.NearbySync
import com.pokestock.work.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SyncScreen() {
    val p = P
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val say = toast()
    val prefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var morning by remember { mutableStateOf(prefs.getBoolean("morningUpdate", true)) }
    var stale by remember { mutableStateOf(prefs.getBoolean("staleNotify", true)) }
    var driveUri by remember { mutableStateOf(DriveSync.fileUri(ctx)) }
    @Suppress("UNUSED_VARIABLE") val v = AppState.version

    LaunchedEffect(Unit) { AppUpdate.check(ctx) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }

    val nearbyPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) NearbySync.start(ctx) else say("Pokestock needs the Nearby devices permission to find the other phone.")
    }
    val createSync = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) { DriveSync.choose(ctx, uri); driveUri = uri; scope.launch { say(DriveSync.sync(ctx)) } }
    }
    val openSync = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { DriveSync.choose(ctx, uri); driveUri = uri; scope.launch { say(DriveSync.sync(ctx)) } }
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(AppState.store.exportJson().toByteArray()) } }.isSuccess
            withContext(Dispatchers.Main) { say(if (ok) "Backup saved" else "Couldn't save the backup") }
        }
    }
    var restoreAsk by remember { mutableStateOf<Pair<String, CollectionDoc>?>(null) }
    var backups by remember { mutableStateOf<List<Backups.Entry>?>(null) }
    var backupUri by remember { mutableStateOf(Backups.driveUri(ctx)) }
    val driveInstalled = remember { runCatching { ctx.packageManager.getPackageInfo("com.google.android.apps.docs", 0); true }.getOrDefault(false) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) { Backups.chooseDrive(ctx, uri); backupUri = uri; scope.launch { Backups.weeklyToDrive(ctx, force = true)?.let(say) } }
    }
    val restoreFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.toString(Charsets.UTF_8)
                val doc = CollectionDoc.fromJson(JSONObject(text))
                withContext(Dispatchers.Main) { restoreAsk = "the file you picked" to doc }
            } catch (e: Exception) { withContext(Dispatchers.Main) { say("Couldn't read that file: ${e.message}") } }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val msg = try {
                val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }.toString(Charsets.UTF_8)
                val doc = CollectionDoc.fromJson(JSONObject(text))
                withContext(Dispatchers.Main) { val n = AppState.store.mergeIn(doc); AppState.refreshCustom(); "Imported: $n changes added" }
            } catch (e: Exception) { "Couldn't import: ${e.message}" }
            withContext(Dispatchers.Main) { say(msg) }
        }
    }

    Column(Modifier.verticalScroll(Nav.syncScroll).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        Text("Sync & prices", style = Type.display, color = p.fg)

        if (AppUpdate.available != null) {
            Gap()
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${AppUpdate.available} is ready", fontWeight = FontWeight.Bold, color = p.fg)
                    Sub("It downloads here and Android asks you to confirm the install. Your collection stays.")
                    AppUpdate.progress?.let { ProgressBar(it) }
                    if (AppUpdate.error.isNotEmpty()) Sub(AppUpdate.error, color = p.down)
                    Btn(if (AppUpdate.progress != null) "Downloading…" else "Update now", Modifier.fillMaxWidth(), primary = true, enabled = AppUpdate.progress == null) {
                        scope.launch { AppUpdate.downloadAndInstall(ctx) }
                    }
                }
            }
        }

        SectionHead("Prices")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val d = AppState.prices.date.takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM yyyy")) }
                SettingText("Last update", (d?.let { "Prices from $it" } ?: "No prices yet") + ". Cardmarket prices converted to £ every morning.")
                if (AppState.status.isNotEmpty()) Sub(AppState.status)
                if (AppState.loadError.isNotEmpty()) Sub(AppState.loadError, color = p.down)
                Btn(if (AppState.updating) "Updating…" else "Update now", Modifier.fillMaxWidth(), primary = true, enabled = !AppState.updating) {
                    scope.launch { say(AppState.update(ctx)) }
                }
                SettingSwitch("Update every morning at 6:00", "Waits for signal if you're offline, then updates by itself", morning) {
                    morning = it; prefs.edit().putBoolean("morningUpdate", it).apply(); Schedule.apply(ctx)
                }
                SettingSwitch("Tell me when prices are a few days old", "A notification after 3 days without an update", stale) {
                    stale = it; prefs.edit().putBoolean("staleNotify", it).apply()
                }
            }
        }

        SectionHead("Phone to phone")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingText("Sync with a phone nearby", "No internet needed. Open this screen on both phones and tap Sync on both.")
                if (NearbySync.status.isNotEmpty()) Sub(NearbySync.status, color = p.fg)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(if (NearbySync.running) "Searching…" else "Sync", Modifier.weight(1f), primary = !NearbySync.running, enabled = !NearbySync.running) {
                        nearbyPerms.launch(nearbyPermissions())
                    }
                    if (NearbySync.running) Btn("Stop", Modifier.weight(1f)) { NearbySync.stop(); NearbySync.status = "" }
                }
            }
        }

        SectionHead("Google Drive")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (driveUri == null) {
                    SettingText("Sync through a shared file", "One phone creates the file in a Drive folder shared with the other person. The other phone picks that same file. After that, both phones sync whenever they open the app with signal.")
                    if (!driveInstalled) {
                        Sub("The Google Drive app isn't on this phone, so the file picker can only show phone storage. Install it and sign in, then come back here.", color = p.down)
                        Btn("Get Google Drive", Modifier.fillMaxWidth()) {
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.docs")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                .onFailure { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.docs"))) }
                        }
                    }
                    Panel(padding = 10.dp) {
                        Text("The picker opens on your phone's storage. Tap ☰ (top left) and choose Drive, then pick the folder. If Drive isn't in that list, open the Google Drive app once and sign in.",
                            color = p.fg, fontSize = 13.sp)
                    }
                    Btn("Create the sync file in Drive", Modifier.fillMaxWidth(), primary = true) { createSync.launch("pokestock-sync.json") }
                    Btn("Pick an existing sync file", Modifier.fillMaxWidth()) { openSync.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
                } else {
                    SettingText("Sync file chosen", DriveSync.lastSync(ctx).takeIf { it.isNotEmpty() }?.let { "Last synced $it" } ?: "Not synced yet")
                    if (DriveSync.status.isNotEmpty()) Sub(DriveSync.status)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn(if (DriveSync.busy) "Syncing…" else "Sync now", Modifier.weight(1f), primary = true, enabled = !DriveSync.busy) { scope.launch { say(DriveSync.sync(ctx)) } }
                        Btn("Change file", Modifier.weight(1f)) { DriveSync.forget(ctx); driveUri = null }
                    }
                }
            }
        }

        SectionHead("Your collection")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingText("Import", "Adds a Pokestock file (your Collectr import, a backup, or the other phone's export). Nothing gets deleted; the newest change of each card wins.")
                Btn("Import a file", Modifier.fillMaxWidth()) { importFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
                SettingText("Backup", "Saves everything (cards, slabs, binder, wanted, your prices) to a file you choose.")
                Btn("Save a backup", Modifier.fillMaxWidth()) { exportBackup.launch("pokestock-backup-${LocalDate.now()}.json") }
                Btn("Add a card that isn't listed yet", Modifier.fillMaxWidth()) { Nav.addCustom = true }
            }
        }

        SectionHead("Backups")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingText("Automatic backups", "One a day on this phone (kept 2 weeks, Mondays kept 3 months), plus one right before every sync. Restore one if a sync or a mis-tap goes wrong.")
                val list = backups
                if (list == null) Btn("Restore a backup", Modifier.fillMaxWidth()) { scope.launch { backups = Backups.list(ctx) } }
                else {
                    if (list.isEmpty()) Sub("No backups yet. The first one is made the next time the app opens.")
                    list.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(e.label, color = p.fg, fontSize = 15.sp); Sub("${String.format("%,d", e.cards)} cards") }
                            Btn("Restore") { scope.launch(Dispatchers.IO) {
                                val doc = runCatching { Backups.read(e.file) }.getOrNull()
                                withContext(Dispatchers.Main) { if (doc != null) restoreAsk = e.label.replaceFirstChar { it.lowercase() } to doc else say("That backup couldn't be read") }
                            } }
                        }
                    }
                    Btn("Hide backups", Modifier.fillMaxWidth()) { backups = null }
                }
                Btn("Restore from a file", Modifier.fillMaxWidth()) { restoreFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
                if (backupUri == null) {
                    SettingText("Weekly backup in Google Drive", "Keeps a copy off the phone too, updated once a week when the morning update runs. In the picker tap ☰ and choose Drive.")
                    Btn("Set up the Drive backup", Modifier.fillMaxWidth()) { createBackup.launch("pokestock-weekly-backup.json") }
                } else {
                    SettingText("Weekly backup in Google Drive", Backups.driveLast(ctx).takeIf { it.isNotEmpty() }?.let { "Last saved $it" } ?: "Not saved yet")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Back up now", Modifier.weight(1f)) { scope.launch { Backups.weeklyToDrive(ctx, force = true)?.let(say) } }
                        Btn("Stop", Modifier.weight(1f)) { Backups.forgetDrive(ctx); backupUri = null }
                    }
                }
            }
        }
        restoreAsk?.let { (what, doc) ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { restoreAsk = null },
                containerColor = p.surface,
                title = { Text("Restore this backup?", color = p.fg) },
                text = { Text("Your collection goes back to exactly how it was in $what (${String.format("%,d", doc.items.values.sumOf { it.q } + doc.slabs.values.count { !it.deleted })} cards). " +
                    "Anything changed since is undone, and the other phone gets the restore at the next sync. A backup of how things are now is saved first, so you can undo this.", color = p.muted) },
                confirmButton = { Btn("Restore", primary = true) {
                    Backups.beforeRestore(ctx); AppState.store.restoreFrom(doc); AppState.refreshCustom(); restoreAsk = null; backups = null; say("Restored")
                } },
                dismissButton = { Btn("Cancel") { restoreAsk = null } },
            )
        }

        SectionHead("Card database")
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val cat = AppState.catalog
                SettingText("On this phone", "${cat.sets.count { !it.custom && it.lang == "EN" }} English and ${cat.sets.count { !it.custom && it.lang == "JA" }} Japanese sets, ${String.format("%,d", cat.byKey.size)} cards")
                Sub("Card pictures download as you browse and stay available offline.")
            }
        }
        Gap()
        Sub("Pokestock ${appVersion(ctx)} · Card data from TCGdex · Prices from Cardmarket")
    }
}

@Composable
private fun SettingText(title: String, desc: String) {
    Column { Text(title, color = P.fg, fontSize = 15.sp); Sub(desc) }
}

@Composable
private fun SettingSwitch(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = P
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = p.fg, fontSize = 15.sp); Sub(desc) }
        Spacer(Modifier.width(10.dp))
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent))
    }
}

private fun nearbyPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_ADVERTISE); add(Manifest.permission.BLUETOOTH_CONNECT) }
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.ACCESS_FINE_LOCATION)
}.toTypedArray()

fun appVersion(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""

