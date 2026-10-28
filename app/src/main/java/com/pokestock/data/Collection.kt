package com.pokestock.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** A value with the time it was last changed, so two phones can merge (latest change wins). */
data class Stamped<T>(val v: T, val t: Long)

data class Item(val q: Int, val added: String, val t: Long)

data class Slab(
    val id: String, val card: String, val variant: String, val company: String, val grade: Double,
    val price: Int, // pence, -1 = not set
    val added: String, val t: Long, val deleted: Boolean = false,
)

data class CustomCard(
    val id: String, val lang: String, val set: String, val number: String, val name: String,
    val rarity: String, val dex: Int, val variants: List<String>, val t: Long, val deleted: Boolean = false,
) {
    val key get() = "CU:$id"
}

/**
 * Everything personal: owned copies, slabs, binder, wants, price overrides and custom cards.
 * This document is the backup file, the import format and the sync format.
 */
class CollectionDoc {
    val items = HashMap<String, Item>()                 // "cardKey|variant"
    val slabs = HashMap<String, Slab>()
    val binder = HashMap<Int, Stamped<String>>()        // dex -> cardKey ("" = empty)
    val slotWants = HashMap<String, Stamped<Boolean>>() // "dex|cardKey"
    val wish = HashMap<String, Stamped<Boolean>>()      // cardKey
    val overrides = HashMap<String, Stamped<Int>>()     // "cardKey|variant" -> pence, -1 = removed
    val custom = HashMap<String, CustomCard>()
    val trade = HashMap<String, Stamped<Int>>()          // "cardKey|variant" -> copies in the trade binder
    val tradeSlab = HashMap<String, Stamped<Boolean>>()  // slab id -> in the trade binder
    val trades = HashMap<String, Stamped<String>>()      // trade id -> record (JSON text)
    val binderMode = HashMap<String, Stamped<String>>()  // set key -> "base" | "base_rh" | "master" | "" (not a binder)
    val alerts = HashMap<String, Stamped<Int>>()         // "cardKey|variant" -> alert below this many pence (-1 = off)

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("format", "pokestock"); o.put("v", 1); o.put("saved", System.currentTimeMillis())
        o.put("items", JSONObject().apply { items.forEach { (k, i) -> put(k, JSONArray().put(i.q).put(i.added).put(i.t)) } })
        o.put("slabs", JSONObject().apply {
            slabs.forEach { (k, s) ->
                put(k, JSONObject().put("c", s.card).put("v", s.variant).put("co", s.company).put("gr", s.grade)
                    .put("p", s.price).put("a", s.added).put("t", s.t).put("d", if (s.deleted) 1 else 0))
            }
        })
        o.put("binder", JSONObject().apply { binder.forEach { (k, s) -> put(k.toString(), JSONArray().put(s.v).put(s.t)) } })
        o.put("slot", JSONObject().apply { slotWants.forEach { (k, s) -> put(k, JSONArray().put(if (s.v) 1 else 0).put(s.t)) } })
        o.put("wish", JSONObject().apply { wish.forEach { (k, s) -> put(k, JSONArray().put(if (s.v) 1 else 0).put(s.t)) } })
        o.put("ovr", JSONObject().apply { overrides.forEach { (k, s) -> put(k, JSONArray().put(s.v).put(s.t)) } })
        o.put("trade", JSONObject().apply { trade.forEach { (k, s) -> put(k, JSONArray().put(s.v).put(s.t)) } })
        o.put("tradeSlab", JSONObject().apply { tradeSlab.forEach { (k, s) -> put(k, JSONArray().put(if (s.v) 1 else 0).put(s.t)) } })
        o.put("trades", JSONObject().apply { trades.forEach { (k, s) -> put(k, JSONArray().put(s.v).put(s.t)) } })
        o.put("binderMode", JSONObject().apply { binderMode.forEach { (k, s) -> put(k, JSONArray().put(s.v).put(s.t)) } })
        o.put("alerts", JSONObject().apply { alerts.forEach { (k, s) -> put(k, JSONArray().put(s.v).put(s.t)) } })
        o.put("custom", JSONObject().apply {
            custom.forEach { (k, c) ->
                put(k, JSONObject().put("lang", c.lang).put("set", c.set).put("num", c.number).put("name", c.name)
                    .put("rar", c.rarity).put("dex", c.dex).put("vars", JSONArray(c.variants)).put("t", c.t).put("d", if (c.deleted) 1 else 0))
            }
        })
        return o
    }

    /** Merge another document in. For every entry the most recent change wins. Returns number of changes taken. */
    fun merge(other: CollectionDoc): Int {
        var n = 0
        fun <V> take(mine: HashMap<String, V>, theirs: Map<String, V>, t: (V) -> Long) {
            theirs.forEach { (k, v) -> val m = mine[k]; if (m == null || t(v) > t(m)) { mine[k] = v; n++ } }
        }
        take(items, other.items) { it.t }
        take(slabs, other.slabs) { it.t }
        take(slotWants, other.slotWants) { it.t }
        take(wish, other.wish) { it.t }
        take(overrides, other.overrides) { it.t }
        take(custom, other.custom) { it.t }
        take(trade, other.trade) { it.t }
        take(tradeSlab, other.tradeSlab) { it.t }
        take(trades, other.trades) { it.t }
        take(binderMode, other.binderMode) { it.t }
        take(alerts, other.alerts) { it.t }
        other.binder.forEach { (k, v) -> val m = binder[k]; if (m == null || v.t > m.t) { binder[k] = v; n++ } }
        return n
    }

    companion object {
        fun fromJson(o: JSONObject): CollectionDoc {
            require(o.optString("format") == "pokestock") { "This isn't a Pokestock file" }
            val d = CollectionDoc()
            o.optJSONObject("items")?.let { j -> j.keys().forEach { k -> val a = j.getJSONArray(k); d.items[k] = Item(a.getInt(0), a.optString(1), a.getLong(2)) } }
            o.optJSONObject("slabs")?.let { j ->
                j.keys().forEach { k ->
                    val s = j.getJSONObject(k)
                    d.slabs[k] = Slab(k, s.getString("c"), s.getString("v"), s.getString("co"), s.getDouble("gr"), s.optInt("p", -1),
                        s.optString("a"), s.getLong("t"), s.optInt("d") == 1)
                }
            }
            o.optJSONObject("binder")?.let { j -> j.keys().forEach { k -> val a = j.getJSONArray(k); d.binder[k.toInt()] = Stamped(a.getString(0), a.getLong(1)) } }
            o.optJSONObject("slot")?.let { j -> j.keys().forEach { k -> val a = j.getJSONArray(k); d.slotWants[k] = Stamped(a.getInt(0) == 1, a.getLong(1)) } }
            o.optJSONObject("wish")?.let { j -> j.keys().forEach { k -> val a = j.getJSONArray(k); d.wish[k] = Stamped(a.getInt(0) == 1, a.getLong(1)) } }
            o.optJSONObject("ovr")?.let { j -> j.keys().forEach { k -> val a = j.getJSONArray(k); d.overrides[k] = Stamped(a.getInt(0), a.getLong(1)) } }
            o.optJSONObject("trade")?.let { j -> j.keys().forEach { k -> val a = j.getJSONArray(k); d.trade[k] = Stamped(a.getInt(0), a.getLong(1)) } }
            o.optJSONObject("tradeSlab")?.let { j -> j.keys().forEach { k -> val a = j.getJSONArray(k); d.tradeSlab[k] = Stamped(a.getInt(0) == 1, a.getLong(1)) } }
            o.optJSONObject("trades")?.let { j -> j.keys().forEach { k -> val a = j.getJSONArray(k); d.trades[k] = Stamped(a.getString(0), a.getLong(1)) } }
            o.optJSONObject("binderMode")?.let { j -> j.keys().forEach { k -> val a = j.getJSONArray(k); d.binderMode[k] = Stamped(a.getString(0), a.getLong(1)) } }
            o.optJSONObject("alerts")?.let { j -> j.keys().forEach { k -> val a = j.getJSONArray(k); d.alerts[k] = Stamped(a.getInt(0), a.getLong(1)) } }
            o.optJSONObject("custom")?.let { j ->
                j.keys().forEach { k ->
                    val c = j.getJSONObject(k)
                    val vars = c.optJSONArray("vars")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: listOf("normal")
                    d.custom[k] = CustomCard(k, c.optString("lang", "EN"), c.optString("set"), c.optString("num"), c.optString("name"),
                        c.optString("rar"), c.optInt("dex"), vars, c.getLong("t"), c.optInt("d") == 1)
                }
            }
            return d
        }

        fun load(file: File): CollectionDoc =
            if (file.exists()) fromJson(JSONObject(file.readText())) else CollectionDoc()
    }
}

/** Reads and changes the collection. Every change bumps [version] so the screens redraw. */
class CollectionStore(private val file: File, val doc: CollectionDoc, private val onChange: () -> Unit) {

    private fun now() = System.currentTimeMillis()
    private fun today() = LocalDate.now().toString()

    fun qty(cardKey: String, variant: String): Int = doc.items["$cardKey|$variant"]?.q ?: 0

    private var ownIndex: HashMap<String, HashMap<String, Int>>? = null
    private var slabIndex: HashMap<String, MutableList<Slab>>? = null
    private fun buildIndex() {
        val o = HashMap<String, HashMap<String, Int>>()
        for ((k, i) in doc.items) if (i.q > 0) o.getOrPut(k.substringBefore('|')) { HashMap() }[k.substringAfter('|')] = i.q
        val s = HashMap<String, MutableList<Slab>>()
        for (x in doc.slabs.values) if (!x.deleted) s.getOrPut(x.card) { ArrayList() }.add(x)
        ownIndex = o; slabIndex = s
    }
    /** Raw copies you own of each version of a card. */
    fun ownedVariants(cardKey: String): Map<String, Int> { if (ownIndex == null) buildIndex(); return ownIndex!![cardKey] ?: emptyMap() }
    fun slabsOf(cardKey: String, variant: String? = null): List<Slab> {
        if (slabIndex == null) buildIndex()
        val l = slabIndex!![cardKey] ?: return emptyList()
        return if (variant == null) l else l.filter { it.variant == variant }
    }
    fun allSlabs(): List<Slab> = doc.slabs.values.filter { !it.deleted }

    /** Raw copies plus slabs, all versions. */
    fun total(cardKey: String): Int = ownedVariants(cardKey).values.sum() + slabsOf(cardKey).size

    fun setQty(cardKey: String, variant: String, q: Int) {
        val k = "$cardKey|$variant"
        val old = doc.items[k]
        doc.items[k] = Item(maxOf(0, q), old?.added?.takeIf { it.isNotEmpty() } ?: today(), now())
        changed()
    }

    fun grade(cardKey: String, variant: String, company: String, grade: Double, price: Int) {
        val q = qty(cardKey, variant)
        if (q <= 0) return
        setQtyNoSave(cardKey, variant, q - 1)
        val id = "s" + now().toString(36) + (0..999).random()
        doc.slabs[id] = Slab(id, cardKey, variant, company, grade, price, today(), now())
        changed()
    }

    fun crack(slabId: String) {
        val s = doc.slabs[slabId] ?: return
        doc.slabs[slabId] = s.copy(deleted = true, t = now())
        setQtyNoSave(s.card, s.variant, qty(s.card, s.variant) + 1)
        changed()
    }

    fun setSlabPrice(slabId: String, pence: Int) {
        val s = doc.slabs[slabId] ?: return
        doc.slabs[slabId] = s.copy(price = pence, t = now())
        changed()
    }

    private fun setQtyNoSave(cardKey: String, variant: String, q: Int) {
        val k = "$cardKey|$variant"
        val old = doc.items[k]
        doc.items[k] = Item(maxOf(0, q), old?.added ?: today(), now())
    }

    fun binderCard(dex: Int): String? = doc.binder[dex]?.v?.takeIf { it.isNotEmpty() }
    fun setBinder(dex: Int, cardKey: String?) { doc.binder[dex] = Stamped(cardKey ?: "", now()); changed() }

    fun isSlotWant(dex: Int, cardKey: String) = doc.slotWants["$dex|$cardKey"]?.v == true
    fun slotWants(dex: Int): List<String> = doc.slotWants.filter { it.value.v && it.key.startsWith("$dex|") }.map { it.key.substringAfter('|') }
    fun toggleSlotWant(dex: Int, cardKey: String) { val k = "$dex|$cardKey"; doc.slotWants[k] = Stamped(doc.slotWants[k]?.v != true, now()); changed() }

    fun isWish(cardKey: String) = doc.wish[cardKey]?.v == true
    fun wishList(): List<String> = doc.wish.filter { it.value.v }.keys.toList()
    fun toggleWish(cardKey: String): Boolean { val on = !isWish(cardKey); doc.wish[cardKey] = Stamped(on, now()); changed(); return on }

    fun override(cardKey: String, variant: String): Int? = doc.overrides["$cardKey|$variant"]?.v?.takeIf { it >= 0 }
    fun setOverride(cardKey: String, variant: String, pence: Int?) { doc.overrides["$cardKey|$variant"] = Stamped(pence ?: -1, now()); changed() }

    fun addCustom(c: CustomCard) { doc.custom[c.id] = c; changed() }

    // ---------- set binders and alerts ----------
    fun binderMode(setKey: String): String = doc.binderMode[setKey]?.v ?: ""
    fun setBinderMode(setKey: String, mode: String) { doc.binderMode[setKey] = Stamped(mode, now()); changed() }
    fun alert(cardKey: String, variant: String): Int? = doc.alerts["$cardKey|$variant"]?.v?.takeIf { it > 0 }
    fun setAlert(cardKey: String, variant: String, pence: Int?) { doc.alerts["$cardKey|$variant"] = Stamped(pence ?: -1, now()); changed() }
    fun activeAlerts(): List<Pair<String, Int>> = doc.alerts.filter { it.value.v > 0 }.map { it.key to it.value.v }
    fun addOne(cardKey: String, variant: String) = setQty(cardKey, variant, qty(cardKey, variant) + 1)

    // ---------- trade binder ----------
    /** Copies of a version in the trade binder (never more than you own raw). */
    fun tradeQty(cardKey: String, variant: String): Int = minOf(doc.trade["$cardKey|$variant"]?.v ?: 0, qty(cardKey, variant))
    fun setTradeQty(cardKey: String, variant: String, q: Int) { doc.trade["$cardKey|$variant"] = Stamped(maxOf(0, q), now()); changed() }
    fun isTradeSlab(id: String) = doc.tradeSlab[id]?.v == true && doc.slabs[id]?.deleted == false
    fun setTradeSlab(id: String, on: Boolean) { doc.tradeSlab[id] = Stamped(on, now()); changed() }
    /** Everything in the trade binder: ("cardKey|variant", copies) for raw cards. */
    fun tradeRaw(): List<Pair<String, Int>> = doc.trade.keys.mapNotNull { k ->
        val q = tradeQty(k.substringBefore('|'), k.substringAfter('|')); if (q > 0) k to q else null
    }
    fun tradeSlabs(): List<Slab> = allSlabs().filter { isTradeSlab(it.id) }

    /**
     * Completes a trade: removes the cards you gave (raw copies and slabs), adds what you got,
     * and keeps a record. Raw copies given also come out of the trade binder.
     */
    fun applyTrade(give: List<Pair<String, Int>>, giveSlabs: List<String>, get: List<Pair<String, Int>>, record: JSONObject) {
        val t = now()
        for ((k, n) in give) {
            val card = k.substringBefore('|'); val v = k.substringAfter('|')
            val have = qty(card, v)
            doc.items[k] = Item(maxOf(0, have - n), doc.items[k]?.added ?: today(), t)
            val inBinder = doc.trade[k]?.v ?: 0
            doc.trade[k] = Stamped(maxOf(0, inBinder - n), t)
        }
        for (id in giveSlabs) doc.slabs[id]?.let { doc.slabs[id] = it.copy(deleted = true, t = t); doc.tradeSlab[id] = Stamped(false, t) }
        for ((k, n) in get) {
            val old = doc.items[k]
            doc.items[k] = Item((old?.q ?: 0) + n, old?.added?.takeIf { it.isNotEmpty() } ?: today(), t)
        }
        val id = "t" + t.toString(36)
        doc.trades[id] = Stamped(record.toString(), t)
        changed()
    }

    fun mergeIn(other: CollectionDoc): Int { val n = doc.merge(other); if (n > 0) changed(); return n }
    fun invalidate() { ownIndex = null; slabIndex = null }

    fun exportJson(): String = doc.toJson().toString()

    fun save() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(doc.toJson().toString())
        tmp.renameTo(file)
    }

    private fun changed() { ownIndex = null; slabIndex = null; save(); onChange() }
}
