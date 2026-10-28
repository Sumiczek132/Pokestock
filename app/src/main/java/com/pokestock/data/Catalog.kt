package com.pokestock.data

import android.util.JsonReader
import android.util.JsonToken
import java.io.File
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

data class CardSet(
    val idx: Int,
    val key: String,        // "EN:sv04"
    val lang: String,       // EN / JA / ZH (custom only)
    val id: String,
    val name: String,
    val nameEn: String,
    val series: String,
    val serieId: String,
    val date: String,
    val total: Int,
    val abbr: String,
    val custom: Boolean = false,
    val logo: String = "",
    val symbol: String = "",
) {
    val cards = ArrayList<Card>()
}

data class Variant(val key: String, val cm: Int)

class Card(
    val key: String,        // "EN:sv04:001"
    val set: CardSet,
    val localId: String,
    val name: String,
    val rarity: String,
    val dex: Int,
    val category: String,
    val variants: List<Variant>,
    val nativeName: String,
    val hasImage: Boolean,
    val custom: Boolean = false,
    /** Picture URLs from the second source (small, large), used when TCGdex has none. */
    val altImage: Array<String>? = null,
) {
    val search: String = (name + " " + nativeName + " " + localId + " " + set.name + " " + set.abbr).lowercase()

    /** Card number as printed, e.g. 001/182 */
    val numberLabel: String
        get() = if (localId.all { it.isDigit() } && set.total > 0)
            localId.padStart(3, '0') + "/" + set.total.toString().padStart(3, '0') else localId

    fun imageUrl(high: Boolean = false): String? {
        if (custom) return null
        altImage?.let { return if (high) it[1] else it[0] }
        if (!hasImage) return null
        val lang = if (set.lang == "JA") "ja" else "en"
        return "https://assets.tcgdex.net/$lang/${set.serieId}/${set.id}/$localId/${if (high) "high" else "low"}.webp"
    }
}

/** The whole card list, held in memory for instant offline search. */
class Catalog(val sets: MutableList<CardSet>, val built: String) {
    val byKey = HashMap<String, Card>()
    val bySpecies = HashMap<Int, MutableList<Card>>()
    val setByKey = HashMap<String, CardSet>()

    init {
        for (s in sets) {
            setByKey[s.key] = s
            for (c in s.cards) index(c)
        }
    }

    private fun index(c: Card) {
        byKey[c.key] = c
        if (c.dex > 0) bySpecies.getOrPut(c.dex) { ArrayList() }.add(c)
    }

    /** Cards entered by hand (not in the database yet) live in one "set" per name and language. */
    fun customSet(lang: String, name: String): CardSet {
        val key = "CU-$lang:$name"
        return setByKey[key] ?: CardSet(sets.size, key, lang, name, name, name, "Your other cards", "", "", 0, "", custom = true).also {
            sets.add(it); setByKey[key] = it
        }
    }

    fun addCustom(cc: CustomCard) {
        val old = byKey[cc.key]
        if (old != null) { old.set.cards.remove(old); byKey.remove(cc.key); bySpecies[old.dex]?.remove(old) }
        if (cc.deleted) return
        val set = customSet(cc.lang, cc.set.ifBlank { "Other cards" })
        val c = Card(cc.key, set, cc.number, cc.name, cc.rarity, cc.dex, "", cc.variants.map { Variant(it, 0) }, "", false, custom = true)
        set.cards.add(c)
        index(c)
    }

    /**
     * Words match the name, set or abbreviation. A number like "58" or "058/102" matches the card number
     * (the part before the slash); a plain number also matches the Pokédex number.
     */
    fun search(lang: String, query: String, limit: Int = 600): List<Card> {
        val words = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val numRe = Regex("^#?([a-z]{0,4})0*(\\d+)(/[a-z]{0,4}\\d+)?$")
        val out = ArrayList<Card>()
        for (s in sets) {
            if (s.lang != lang) continue
            for (c in s.cards) {
                val ok = words.all { w ->
                    val m = numRe.matchEntire(w)
                    if (m != null) {
                        val prefix = m.groupValues[1]; val n = m.groupValues[2]; val slash = m.groupValues[3].isNotEmpty()
                        val local = c.localId.lowercase().substringBefore('/')
                        val lm = Regex("^([a-z]*)0*(\\d+)").find(local)
                        val numberHit = lm != null && lm.groupValues[1] == prefix && lm.groupValues[2] == n
                        numberHit || (!slash && prefix.isEmpty() && (c.dex.toString() == n || c.search.contains(w)))
                    } else c.search.contains(w)
                }
                if (ok) { out.add(c); if (out.size >= limit) return out }
            }
        }
        return out
    }

    companion object {
        fun empty() = Catalog(ArrayList(), "")

        fun load(file: File): Catalog {
            val sets = ArrayList<CardSet>()
            var built = ""
            GZIPInputStream(file.inputStream().buffered(1 shl 16)).use { gz ->
                val r = JsonReader(InputStreamReader(gz, Charsets.UTF_8))
                r.beginObject()
                while (r.hasNext()) {
                    when (r.nextName()) {
                        "built" -> built = str(r)
                        "sets" -> {
                            r.beginArray()
                            while (r.hasNext()) sets.add(readSet(r, sets.size))
                            r.endArray()
                        }
                        "cards" -> {
                            r.beginArray()
                            while (r.hasNext()) readCard(r, sets)
                            r.endArray()
                        }
                        else -> r.skipValue()
                    }
                }
                r.endObject()
            }
            return Catalog(sets, built)
        }

        private fun readSet(r: JsonReader, idx: Int): CardSet {
            var k = ""; var lang = ""; var id = ""; var name = ""; var nameEn = ""; var series = ""
            var serieId = ""; var date = ""; var total = 0; var abbr = ""; var logo = ""; var symbol = ""
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "k" -> k = str(r)
                    "lang" -> lang = str(r)
                    "id" -> id = str(r)
                    "name" -> name = str(r)
                    "nameEn" -> nameEn = str(r)
                    "series" -> series = str(r)
                    "serieId" -> serieId = str(r)
                    "date" -> date = str(r)
                    "total" -> total = num(r)
                    "abbr" -> abbr = str(r)
                    "logo" -> logo = str(r)
                    "symbol" -> symbol = str(r)
                    else -> r.skipValue()
                }
            }
            r.endObject()
            return CardSet(idx, k, lang, id, name, nameEn, series, serieId, date, total, abbr, logo = logo, symbol = symbol)
        }

        // Lenient readers: one odd value must never stop the whole card list loading.
        private fun str(r: JsonReader): String = when (r.peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> r.nextString()
            JsonToken.BOOLEAN -> r.nextBoolean().toString()
            else -> { r.skipValue(); "" }
        }
        private fun num(r: JsonReader): Int = when (r.peek()) {
            JsonToken.NUMBER, JsonToken.STRING -> r.nextString().toDoubleOrNull()?.toInt() ?: 0
            else -> { r.skipValue(); 0 }
        }

        private fun readCard(r: JsonReader, sets: List<CardSet>) {
            r.beginArray()
            val si = num(r)
            val localId = str(r)
            val name = str(r)
            val rarity = str(r)
            val dex = num(r)
            val cat = str(r)
            val vars = ArrayList<Variant>(3)
            if (r.peek() == JsonToken.BEGIN_ARRAY) {
                r.beginArray()
                while (r.hasNext()) {
                    if (r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); continue }
                    r.beginArray()
                    val k = str(r)
                    val cm = if (r.hasNext()) num(r) else 0
                    while (r.hasNext()) r.skipValue()
                    r.endArray()
                    if (k.isNotEmpty()) vars.add(Variant(k, cm))
                }
                r.endArray()
            } else r.skipValue()
            val ja = if (r.hasNext()) str(r) else ""
            var img = true
            var alt: Array<String>? = null
            if (r.hasNext()) {
                if (r.peek() == JsonToken.BEGIN_ARRAY) {
                    r.beginArray()
                    val urls = ArrayList<String>(2)
                    while (r.hasNext()) urls.add(str(r))
                    r.endArray()
                    if (urls.isNotEmpty() && urls[0].isNotEmpty()) alt = arrayOf(urls[0], urls.getOrElse(1) { urls[0] })
                    img = alt != null
                } else img = num(r) == 1
            }
            while (r.hasNext()) r.skipValue()
            r.endArray()
            val set = sets.getOrNull(si) ?: return
            if (name.isEmpty() || name == "undefined") return
            if (vars.isEmpty()) vars.add(Variant("normal", 0))
            set.cards.add(Card("${set.key}:$localId", set, localId, name, normaliseRarity(rarity), dex, cat, vars, ja, img, altImage = alt))
        }

        private val RARITY_FIX = mapOf(
            "Illustration rare" to "Illustration Rare",
            "Special illustration rare" to "Special Illustration Rare",
            "Double rare" to "Double Rare",
            "Hyper rare" to "Hyper Rare",
            "Shiny rare" to "Shiny Rare",
            "None" to "",
        )
        fun normaliseRarity(r: String) = RARITY_FIX[r] ?: r
    }
}
