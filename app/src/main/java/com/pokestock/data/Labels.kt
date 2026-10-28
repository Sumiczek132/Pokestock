package com.pokestock.data

object Labels {
    private val ART = setOf("Illustration Rare", "Art Rare", "AR", "Character Rare", "Trainer Gallery Rare Holo")
    private val SPECIAL = setOf("Special Illustration Rare", "SAR", "Special Art Rare", "Character Super Rare")
    private val PLAIN = setOf("Common", "Uncommon", "Rare", "Holo Rare", "Rare Holo", "Promo", "", "Pikachu Rare", "Classic Collection", "None")

    /** Groups used by rarity filters: cu, other, ultra, art, special */
    fun group(rarity: String): String = when {
        rarity in SPECIAL -> "special"
        rarity in ART -> "art"
        rarity == "Common" || rarity == "Uncommon" -> "cu"
        rarity in PLAIN -> "other"
        else -> "ultra"
    }

    private val SHORT = mapOf(
        "Common" to "C", "Uncommon" to "U", "Rare" to "R", "Holo Rare" to "H", "Rare Holo" to "H",
        "Double Rare" to "RR", "Ultra Rare" to "UR", "Illustration Rare" to "IR", "Special Illustration Rare" to "SIR",
        "Art Rare" to "AR", "AR" to "AR", "SAR" to "SAR", "Special Art Rare" to "SAR", "Secret Rare" to "SR",
        "Hyper Rare" to "HR", "Super Rare" to "SR", "RRR" to "RRR", "Promo" to "PR", "ACE SPEC Rare" to "ACE",
        "Mega Hyper Rare" to "MHR", "Mega Attack Rare" to "MAR", "Radiant Rare" to "RAD", "Shiny Rare" to "S",
        "Shiny Ultra Rare" to "SSR", "Shiny Holo Rare" to "S", "Black White Rare" to "BWR", "Amazing Rare" to "AMZ",
        "Classic Collection" to "CLC", "Rare BREAK" to "BRK", "Rare Holo V" to "V", "Rare Holo VMAX" to "VMAX",
        "Rare Holo VSTAR" to "VSTAR", "Rare Holo GX" to "GX", "Rare Holo EX" to "EX", "Full Art Trainer" to "FA",
        "Rare Ultra" to "UR", "Rare Secret" to "SR", "Rare Rainbow" to "RBW", "Character Rare" to "CHR",
        "Character Super Rare" to "CSR", "Crown" to "CR",
    )

    fun rarityShort(r: String): String = SHORT[r] ?: r.split(" ").filter { it.isNotEmpty() }.joinToString("") { it.take(1) }.take(4)

    private val VLABEL = mapOf(
        "normal" to "Normal", "holo" to "Holo", "reverse" to "Reverse holo",
        "reverse-pokeball" to "Poké Ball", "reverse-masterball" to "Master Ball", "reverse-energy" to "Energy reverse",
        "reverse-duskball" to "Dusk Ball", "reverse-loveball" to "Love Ball", "reverse-friendball" to "Friend Ball",
        "reverse-quickball" to "Quick Ball", "reverse-team-rocket" to "Rocket reverse", "holo-cosmos" to "Cosmos holo",
        "reverse-cosmos" to "Cosmos reverse", "holo-gold" to "Gold", "lenticular" to "Lenticular", "metal" to "Metal",
        "metal-gold" to "Gold metal", "1st-edition" to "1st Edition", "promo-stamp" to "Promo stamp",
        "holo-galaxy" to "Galaxy holo", "reverse-galaxy" to "Galaxy reverse", "holo-tinsel" to "Tinsel holo",
        "reverse-tinsel" to "Tinsel reverse", "reverse-cracked-ice" to "Cracked ice", "normal-league" to "League",
        "reverse-league" to "League reverse",
    )
    private val VSHORT = mapOf(
        "normal" to "N", "holo" to "H", "reverse" to "RH", "reverse-pokeball" to "PB", "reverse-masterball" to "MB",
        "reverse-energy" to "E", "reverse-duskball" to "DB", "reverse-loveball" to "LB", "reverse-friendball" to "FB",
        "reverse-quickball" to "QB", "reverse-team-rocket" to "TR", "holo-cosmos" to "CH", "reverse-cosmos" to "CR",
        "holo-gold" to "G", "1st-edition" to "1E",
    )
    private val STAMP = mapOf(
        "set-logo" to "set stamp", "staff" to "staff", "player-rewards-program" to "Play! Points",
        "professor-program" to "Professor", "pre-release" to "prerelease",
    )

    private fun base(k: String) = k.substringBefore('+').substringBefore('~')

    /** A version that is just the card's normal foil, named by its rarity (SIR, IR, UR...). */
    private fun isRarityFinish(card: Card, k: String) = base(k) == "holo" && card.rarity !in PLAIN

    fun variantLabel(card: Card, k: String): String {
        val b = base(k)
        var s = if (isRarityFinish(card, k)) card.rarity else VLABEL[b] ?: b.replace('-', ' ').replaceFirstChar { it.uppercase() }
        val stamps = k.split('+').drop(1).map { it.substringBefore('~') }
        if (stamps.isNotEmpty()) s += " (" + stamps.joinToString(", ") { STAMP[it] ?: it.replace('-', ' ') } + ")"
        if (k.contains("~jumbo")) s += " (jumbo)"
        return s
    }

    fun variantShort(card: Card, k: String): String {
        val b = base(k)
        var s = if (isRarityFinish(card, k)) rarityShort(card.rarity) else VSHORT[b] ?: b.take(2).uppercase()
        if (k.contains('+')) s += "★"
        if (k.contains("~jumbo")) s += "J"
        return s
    }

    /** Words added to an eBay search for this version. */
    fun ebayWords(k: String): String {
        val b = base(k)
        val w = when (b) {
            "reverse" -> "reverse holo"
            "reverse-pokeball" -> "poke ball"
            "reverse-masterball" -> "master ball"
            "reverse-energy" -> "energy reverse"
            "reverse-duskball" -> "dusk ball"
            "reverse-loveball" -> "love ball"
            "reverse-friendball" -> "friend ball"
            "reverse-quickball" -> "quick ball"
            "holo-cosmos" -> "cosmos"
            "1st-edition" -> "1st edition"
            else -> ""
        }
        val stamp = if (k.contains("+set-logo")) " stamped" else if (k.contains("+staff")) " staff" else ""
        return (w + stamp).trim()
    }

    val GRADE_NAMES = mapOf(10 to "GEM MINT", 9 to "MINT", 8 to "NM-MT", 7 to "NM", 6 to "EX-MT", 5 to "EX", 4 to "VG-EX", 3 to "VG", 2 to "GOOD", 1 to "POOR")
    fun gradeName(company: String, grade: Double): String {
        val g = if (grade % 1.0 == 0.0) grade.toInt().toString() else grade.toString()
        val n = GRADE_NAMES[grade.toInt()]
        return if (n != null && grade % 1.0 == 0.0) "$company $g $n" else "$company $g"
    }
    fun gradeShort(company: String, grade: Double): String =
        "$company " + if (grade % 1.0 == 0.0) grade.toInt().toString() else grade.toString()
}
