package com.pokestock.data

import android.util.JsonReader
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
) {
    val search: String = (name + " " + nativeName + " " + localId + " " + set.name + " " + set.abbr).lowercase()

    /** Card number as printed, e.g. 001/182 */
    val numberLabel: String
        get() = if (localId.all { it.isDigit() } && set.total > 0)
            localId.padStart(3, '0') + "/" + set.total.toString().padStart(3, '0') else localId

    fun imageUrl(high: Boolean = false): String? {
        if (custom || !hasImage) return null
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

    fun search(lang: String, query: String, limit: Int = 400): List<Card> {
        val words = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val out = ArrayList<Card>()
        for (s in sets) {
            if (s.lang != lang) continue
            for (c in s.cards) {
                if (words.all { w -> c.search.contains(w) || (c.dex > 0 && w == c.dex.toString()) }) {
                    out.add(c)
                    if (out.size >= limit) return out
                }
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
                        "built" -> built = r.nextString()
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
            var serieId = ""; var date = ""; var total = 0; var abbr = ""
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "k" -> k = r.nextString()
                    "lang" -> lang = r.nextString()
                    "id" -> id = r.nextString()
                    "name" -> name = r.nextString()
                    "nameEn" -> nameEn = r.nextString()
                    "series" -> series = r.nextString()
                    "serieId" -> serieId = r.nextString()
                    "date" -> date = r.nextString()
                    "total" -> total = r.nextInt()
                    "abbr" -> abbr = r.nextString()
                    else -> r.skipValue()
                }
            }
            r.endObject()
            return CardSet(idx, k, lang, id, name, nameEn, series, serieId, date, total, abbr)
        }

        private fun readCard(r: JsonReader, sets: List<CardSet>) {
            r.beginArray()
            val si = r.nextInt()
            val localId = r.nextString()
            val name = r.nextString()
            val rarity = r.nextString()
            val dex = r.nextInt()
            val cat = r.nextString()
            val vars = ArrayList<Variant>(3)
            r.beginArray()
            while (r.hasNext()) {
                r.beginArray()
                val k = r.nextString()
                val cm = r.nextInt()
                r.endArray()
                vars.add(Variant(k, cm))
            }
            r.endArray()
            val ja = r.nextString()
            val img = r.nextInt() == 1
            while (r.hasNext()) r.skipValue()
            r.endArray()
            val set = sets[si]
            set.cards.add(Card("${set.key}:$localId", set, localId, name, normaliseRarity(rarity), dex, cat, vars, ja, img))
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
