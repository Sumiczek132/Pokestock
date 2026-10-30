package com.pokestock.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Data {
    const val BASE = "https://raw.githubusercontent.com/Sumiczek132/Pokestock/data/"
    const val RELEASES = "https://github.com/Sumiczek132/Pokestock/releases/latest"
}

/** Everything the screens read. Loaded once at start, refreshed by the morning update. */
object AppState {
    var ready by mutableStateOf(false)
    var catalog by mutableStateOf(Catalog.empty())
    var prices by mutableStateOf(Prices.empty())
    var history by mutableStateOf(History.empty())
    /** Bumped on every collection change so screens recompute. */
    var version by mutableIntStateOf(0)
    var status by mutableStateOf("")
    var updating by mutableStateOf(false)
    lateinit var store: CollectionStore
    private lateinit var dir: File
    private var prefs: android.content.SharedPreferences? = null
    var loadError by mutableStateOf("")
    private val lock = Mutex()

    val lastPriceDate: String get() = prices.date
    fun priceAgeDays(): Long? = prices.date.takeIf { it.isNotEmpty() }?.let { ChronoUnit.DAYS.between(LocalDate.parse(it), LocalDate.now()) }

    suspend fun init(ctx: Context) = withContext(Dispatchers.IO) {
        dir = ctx.filesDir
        prefs = ctx.getSharedPreferences("data", Context.MODE_PRIVATE)
        val doc = try { CollectionDoc.load(File(dir, "collection.json")) } catch (e: Exception) { CollectionDoc() }
        store = CollectionStore(File(dir, "collection.json"), doc) { version++ }
        loadLocal()
        ready = true
        Backups.daily(ctx)
    }

    private fun loadLocal() {
        val cf = File(dir, "catalog.json.gz")
        var cat = Catalog.empty()
        if (cf.exists()) {
            try { cat = Catalog.load(cf) } catch (e: Exception) { loadError = "Card list couldn't load (${e.message}). Tap Update to download it again." }
            if (cat.sets.isEmpty()) { cf.delete(); prefs?.edit()?.remove("catalogBuilt")?.apply() }
        }
        store.doc.custom.values.forEach { cat.addCustom(it) }
        catalog = cat
        val pf = File(dir, "prices.json.gz")
        prices = if (pf.exists()) try { Prices.load(pf) } catch (e: Exception) { pf.delete(); prefs?.edit()?.remove("pricesDate")?.apply(); Prices.empty() } else Prices.empty()
        val hf = File(dir, "history.json.gz")
        history = if (hf.exists()) try { History.load(hf) } catch (e: Exception) { History.empty() } else History.empty()
        version++
    }

    fun refreshCustom() {
        store.doc.custom.values.forEach { catalog.addCustom(it) }
        version++
    }

    /** Downloads whatever is newer than what the phone has. Safe to call any time; does nothing offline. */
    suspend fun update(ctx: Context, force: Boolean = false): String = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!::dir.isInitialized) init(ctx)
            updating = true
            try {
                val manifest = JSONObject(String(download(Data.BASE + "manifest.json")))
                val prefs = ctx.getSharedPreferences("data", Context.MODE_PRIVATE)
                var changed = false
                var pricesChanged = false
                val built = manifest.optString("catalogBuilt")
                if (force || built != prefs.getString("catalogBuilt", "") || !File(dir, "catalog.json.gz").exists()) {
                    status = "Downloading card list…"
                    save("catalog.json.gz", download(Data.BASE + "catalog.json.gz"))
                    prefs.edit().putString("catalogBuilt", built).apply(); changed = true
                }
                val pd = manifest.optString("pricesBuilt").ifEmpty { manifest.optString("pricesDate") }
                if (force || pd != prefs.getString("pricesDate", "") || !File(dir, "prices.json.gz").exists()) {
                    status = "Downloading prices…"
                    save("prices.json.gz", download(Data.BASE + "prices.json.gz"))
                    prefs.edit().putString("pricesDate", pd).apply(); changed = true; pricesChanged = true
                    val lastHist = prefs.getLong("historyAt", 0)
                    val week = 7L * 24 * 3600 * 1000
                    if (force || !isMetered(ctx) || System.currentTimeMillis() - lastHist > week || !File(dir, "history.json.gz").exists()) {
                        status = "Downloading price history…"
                        save("history.json.gz", download(Data.BASE + "history.json.gz"))
                        prefs.edit().putLong("historyAt", System.currentTimeMillis()).apply()
                    }
                }
                // Price movement on what you own, at the same cards, for the widget (new cards aren't counted as a rise).
                val before = if (pricesChanged && prices.date.isNotEmpty()) portfolioValue() else null
                if (changed) { loadError = ""; loadLocal() }
                if (before != null) runCatching { WidgetData.priceMove(ctx, portfolioValue() - before) }
                runCatching { Alerts.check(ctx) }
                runCatching { WidgetData.publish(ctx) }
                if (catalog.sets.none { !it.custom }) throw RuntimeException(loadError.ifEmpty { "the card list didn't load" })
                prefs.edit().putLong("checkedAt", System.currentTimeMillis()).apply()
                status = if (changed) "Updated: prices from ${prices.date}" else "Already up to date (prices from ${prices.date})"
                status
            } catch (e: Exception) {
                status = "Couldn't update: ${e.message ?: "no connection"}"
                status
            } finally {
                updating = false
            }
        }
    }

    private fun save(name: String, bytes: ByteArray) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(File(dir, name))
    }

    fun download(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (c.responseCode != 200) throw RuntimeException("server said ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isMetered(ctx: Context): Boolean = ctx.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered

    // ---------- money ----------

    /** Price for one copy of a card version, in pence: your own price if set, else Cardmarket. */
    fun unitPrice(card: Card, variant: String): Int? = store.override(card.key, variant) ?: prices.market(card, variant)

    /** Highest known price across a card's versions. */
    fun bestPrice(card: Card): Int? = versionsOf(card).mapNotNull { unitPrice(card, it) }.maxOrNull()
    /** The price to show in lists: what your own copies are worth if you own it, otherwise the highest version. */
    fun displayPrice(card: Card): Int? {
        val owned = store.ownedVariants(card.key).keys + store.slabsOf(card.key).map { it.variant }
        if (owned.isNotEmpty()) owned.mapNotNull { unitPrice(card, it) }.maxOrNull()?.let { return it }
        return bestPrice(card)
    }
    fun lowPrice(card: Card): Int? = versionsOf(card).mapNotNull { unitPrice(card, it) }.minOrNull()

    /** The card's versions plus any version you own that the list doesn't have. */
    fun versionsOf(card: Card): List<String> {
        val ks = card.variants.map { it.key }.toMutableList()
        for (v in store.ownedVariants(card.key).keys) if (v !in ks) ks.add(v)
        for (s in store.slabsOf(card.key)) if (s.variant !in ks) ks.add(s.variant)
        return ks
    }

    /**
     * Price change of one version over the last [days] days, as a fraction (0.12 = +12%).
     * Uses the app's own daily history when it reaches back far enough, otherwise Cardmarket's rolling averages.
     */
    fun change(card: Card, variant: String, days: Int): Double? {
        if (store.override(card.key, variant) != null) return null
        val now = prices.market(card, variant)?.toDouble() ?: return null
        val since = java.time.LocalDate.now().minusDays(days.toLong()).toString()
        val hist = history.series(card, variant)
        val then = hist.lastOrNull { it.first <= since }?.second?.toDouble()
            ?: prices.averages(card, variant).firstOrNull { it.first == days }?.second?.toDouble()
            ?: return null
        if (then <= 0) return null
        return (now - then) / then
    }

    fun portfolioValue(): Long {
        var v = 0L
        for ((k, item) in store.doc.items) {
            if (item.q <= 0) continue
            val card = catalog.byKey[k.substringBefore('|')] ?: continue
            v += (unitPrice(card, k.substringAfter('|')) ?: 0).toLong() * item.q
        }
        for (s in store.allSlabs()) if (s.price > 0) v += s.price
        return v
    }

    data class Holding(val card: Card, val variant: String, val qty: Int, val added: String)

    fun holdings(): List<Holding> = store.doc.items.mapNotNull { (k, i) ->
        if (i.q <= 0) null else catalog.byKey[k.substringBefore('|')]?.let { Holding(it, k.substringAfter('|'), i.q, i.added) }
    }

    fun ownedCards(): List<Card> {
        val keys = HashSet<String>()
        store.doc.items.forEach { (k, i) -> if (i.q > 0) keys.add(k.substringBefore('|')) }
        store.allSlabs().forEach { keys.add(it.card) }
        return keys.mapNotNull { catalog.byKey[it] }
    }
}

fun pounds(pence: Int?): String = if (pence == null) "—" else "£" + String.format("%,.2f", pence / 100.0)
fun pounds(pence: Long): String = "£" + String.format("%,.2f", pence / 100.0)
fun poundsRound(pence: Long): String = "£" + String.format("%,d", Math.round(pence / 100.0))
