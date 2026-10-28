package com.pokestock.data

/** One pocket in a physical binder: a card version, and whether you have it. */
data class Pocket(val card: Card, val variant: String, val owned: Boolean)

object Binder {
    val MODES = listOf("base" to "Base set", "base_rh" to "Base + reverse", "master" to "Master set")
    fun modeName(m: String) = MODES.firstOrNull { it.first == m }?.second ?: "Base set"

    /** Part of the main numbered set (e.g. 1–182 of 182), not a secret rare. Sets without a count are all "base". */
    fun isBase(c: Card): Boolean {
        val total = c.set.total
        if (total <= 0) return true
        val n = c.localId.toIntOrNull() ?: return false
        return n in 1..total
    }

    /** The version a base-set binder holds: the normal (or holo) card, not reverses or stamped versions. */
    fun mainVariant(c: Card): String =
        c.variants.firstOrNull { !it.key.startsWith("reverse") && !it.key.contains('+') && !it.key.contains('~') }?.key
            ?: c.variants.first().key

    private fun has(c: Card, v: String) = AppState.store.qty(c.key, v) + AppState.store.slabsOf(c.key, v).size > 0

    fun pockets(set: CardSet, mode: String): List<Pocket> {
        val out = ArrayList<Pocket>()
        for (c in set.cards) {
            when (mode) {
                "master" -> AppState.versionsOf(c).forEach { v -> out.add(Pocket(c, v, has(c, v))) }
                "base_rh" -> if (isBase(c)) {
                    val main = mainVariant(c)
                    out.add(Pocket(c, main, has(c, main)))
                    if (c.variants.any { it.key == "reverse" }) out.add(Pocket(c, "reverse", has(c, "reverse")))
                }
                else -> if (isBase(c)) { val main = mainVariant(c); out.add(Pocket(c, main, has(c, main))) }
            }
        }
        return out
    }
}
