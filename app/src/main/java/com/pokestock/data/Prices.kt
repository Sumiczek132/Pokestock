package com.pokestock.data

import android.util.JsonReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/** Today's Cardmarket prices in pence, keyed by Cardmarket product id. */
class Prices(val date: String, private val map: HashMap<Int, IntArray>) {

    /** Market price for one version of a card, in pence, or null if Cardmarket has none. */
    fun market(card: Card, variantKey: String): Int? {
        val v = card.variants.firstOrNull { it.key == variantKey } ?: return null
        if (v.cm == 0) return null
        val p = map[v.cm] ?: return null
        val isReverse = variantKey.startsWith("reverse")
        // Reverse holos that share a product with the normal card are priced in Cardmarket's "holo" column.
        val shared = card.variants.any { it.cm == v.cm && !it.key.startsWith("reverse") }
        val trend = p[0]; val trendHolo = p[3]; val avg30 = p[1]; val avg30Holo = p[4]
        val value = if (isReverse && shared) firstPositive(trendHolo, avg30Holo)
        else if (isReverse) firstPositive(trendHolo, trend, avg30Holo, avg30)
        else firstPositive(trend, avg30)
        return value
    }

    /** 30-day average and lowest listing for the detail view. */
    fun extra(card: Card, variantKey: String): Pair<Int?, Int?> {
        val v = card.variants.firstOrNull { it.key == variantKey } ?: return null to null
        val p = map[v.cm] ?: return null to null
        val holo = variantKey.startsWith("reverse") && card.variants.any { it.cm == v.cm && !it.key.startsWith("reverse") }
        val a = if (holo) p[4] else p[1]
        val l = if (holo) p[5] else p[2]
        return (a.takeIf { it > 0 }) to (l.takeIf { it > 0 })
    }

    private fun firstPositive(vararg xs: Int): Int? = xs.firstOrNull { it > 0 }

    companion object {
        fun empty() = Prices("", HashMap())

        fun load(file: File): Prices {
            val map = HashMap<Int, IntArray>(40000)
            var date = ""
            GZIPInputStream(file.inputStream().buffered(1 shl 16)).use { gz ->
                val r = JsonReader(InputStreamReader(gz, Charsets.UTF_8))
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "date" -> date = r.nextString()
                        "prices" -> {
                            r.beginObject()
                            while (r.hasNext()) {
                                val id = r.nextName().toIntOrNull()
                                if (id == null) { r.skipValue(); continue }
                                val a = IntArray(6)
                                r.beginArray()
                                var i = 0
                                while (r.hasNext()) { val x = r.nextString().toDoubleOrNull()?.toInt() ?: 0; if (i < 6) a[i] = x; i++ }
                                r.endArray()
                                map[id] = a
                            }
                            r.endObject()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }
            return Prices(date, map)
        }
    }
}

/** Price history: up to 35 daily and 52 weekly points per product. */
class History(val days: List<String>, val weeks: List<String>, private val d: HashMap<Int, Array<IntArray>>, private val w: HashMap<Int, Array<IntArray>>) {

    /** Points (date, pence) for one version, oldest first, weekly then daily. */
    fun series(card: Card, variantKey: String): List<Pair<String, Int>> {
        val v = card.variants.firstOrNull { it.key == variantKey } ?: return emptyList()
        if (v.cm == 0) return emptyList()
        val shared = card.variants.any { it.cm == v.cm && !it.key.startsWith("reverse") }
        val rev = variantKey.startsWith("reverse")
        val hasHolo = (d[v.cm]?.get(1)?.any { it > 0 } == true) || (w[v.cm]?.get(1)?.any { it > 0 } == true)
        val col = if (rev && (shared || hasHolo)) 1 else 0
        val out = ArrayList<Pair<String, Int>>()
        val firstDay = days.firstOrNull() ?: "9999"
        w[v.cm]?.let { s -> for (i in weeks.indices) if (weeks[i] < firstDay && i < s[col].size && s[col][i] > 0) out.add(weeks[i] to s[col][i]) }
        d[v.cm]?.let { s -> for (i in days.indices) if (i < s[col].size && s[col][i] > 0) out.add(days[i] to s[col][i]) }
        return out
    }

    companion object {
        fun empty() = History(emptyList(), emptyList(), HashMap(), HashMap())

        fun load(file: File): History {
            val days = ArrayList<String>(); val weeks = ArrayList<String>()
            val d = HashMap<Int, Array<IntArray>>(); val w = HashMap<Int, Array<IntArray>>()
            GZIPInputStream(file.inputStream().buffered(1 shl 16)).use { gz ->
                val r = JsonReader(InputStreamReader(gz, Charsets.UTF_8))
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "days" -> { r.beginArray(); while (r.hasNext()) days.add(r.nextString()); r.endArray() }
                        "weeks" -> { r.beginArray(); while (r.hasNext()) weeks.add(r.nextString()); r.endArray() }
                        "d" -> readSeries(r, d)
                        "w" -> readSeries(r, w)
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }
            return History(days, weeks, d, w)
        }

        private fun readSeries(r: JsonReader, into: HashMap<Int, Array<IntArray>>) {
            r.beginObject()
            while (r.hasNext()) {
                val id = r.nextName().toIntOrNull()
                if (id == null) { r.skipValue(); continue }
                val cols = ArrayList<IntArray>(2)
                r.beginArray()
                while (r.hasNext()) {
                    val xs = ArrayList<Int>()
                    r.beginArray(); while (r.hasNext()) xs.add(r.nextString().toDoubleOrNull()?.toInt() ?: 0); r.endArray()
                    cols.add(xs.toIntArray())
                }
                r.endArray()
                while (cols.size < 2) cols.add(IntArray(0))
                into[id] = arrayOf(cols[0], cols[1])
            }
            r.endObject()
        }
    }
}
