package com.pokestock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokestock.data.AppState
import com.pokestock.data.Card
import com.pokestock.data.Labels
import com.pokestock.data.Species
import com.pokestock.data.pounds

private val GENS = listOf(1 to 151, 152 to 251, 252 to 386, 387 to 493, 494 to 649, 650 to 721, 722 to 809, 810 to 905, 906 to 1025)
fun dexNo(n: Int) = "#" + n.toString().padStart(3, '0')

/** Cards you want for a slot that you don't own yet. */
fun slotWantsOpen(dex: Int): List<Card> = AppState.store.slotWants(dex).mapNotNull { AppState.catalog.byKey[it] }.filter { AppState.store.total(it.key) == 0 }

@Composable
fun DexScreen() {
    val p = P
    val v = AppState.version
    val cat = AppState.catalog
    val filled = remember(v, cat) { (1..Species.count).count { AppState.store.binderCard(it)?.let { k -> cat.byKey[k] } != null } }
    val ups = remember(v, cat) { (1..Species.count).count { AppState.store.binderCard(it) != null && slotWantsOpen(it).isNotEmpty() } }
    val worth = remember(v, cat, AppState.prices) { (1..Species.count).sumOf { n -> AppState.store.binderCard(n)?.let { cat.byKey[it] }?.let { AppState.displayPrice(it)?.toLong() } ?: 0L } }
    val slots = remember(v, cat, Nav.dexFilter, Nav.dexGen, Nav.dexQuery) {
        val (a, b) = if (Nav.dexGen > 0) GENS[Nav.dexGen - 1] else 1 to Species.count
        val q = Nav.dexQuery.trim().lowercase().removePrefix("#")
        (a..b).filter { n ->
            val has = AppState.store.binderCard(n) != null
            val w = slotWantsOpen(n).isNotEmpty()
            val ok = when (Nav.dexFilter) { "missing" -> !has; "filled" -> has; "wanted" -> w && !has; "upgrade" -> w && has; else -> true }
            ok && (q.isEmpty() || Species.name(n).lowercase().contains(q) || n.toString() == q.trimStart('0'))
        }
    }
    LazyVerticalGrid(GridCells.Fixed(3), state = Nav.dexGrid, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(span = { GridItemSpan(3) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pokédex binder", style = Type.display, color = p.fg, modifier = Modifier.weight(1f))
                    Btn("Pages") { Nav.page = "dexbinder" }
                }
                Sub("$filled of ${Species.count} filled · $ups upgrades planned · binder worth ${pounds(worth)}")
                Gap(10.dp)
                ProgressBar(filled.toFloat() / Species.count)
                Gap(10.dp)
                SearchField(Nav.dexQuery, "Find a Pokémon or number") { Nav.dexQuery = it }
                Gap(8.dp)
                ChipRow { listOf("all" to "All", "missing" to "Missing", "filled" to "In binder", "wanted" to "Wanted", "upgrade" to "Upgrades").forEach { (k, l) -> Chip(l, Nav.dexFilter == k) { Nav.dexFilter = k } } }
                Gap(6.dp)
                ChipRow { (0..GENS.size).forEach { g -> Chip(if (g == 0) "All gens" else "Gen $g", Nav.dexGen == g) { Nav.dexGen = g } } }
                Gap(6.dp)
                Sub("${slots.size} slots")
            }
        }
        items(slots, key = { it }) { n -> SlotTile(n) }
    }
}

@Composable
private fun SlotTile(n: Int) {
    @Suppress("UNUSED_VARIABLE") val redraw = AppState.version
    val p = P
    val card = AppState.store.binderCard(n)?.let { AppState.catalog.byKey[it] }
    val wants = slotWantsOpen(n).size
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.heightIn(min = 62.dp).clip(shape).background(if (card != null) p.surface else androidx.compose.ui.graphics.Color.Transparent)
        .border(1.dp, p.line, shape).clickable { Nav.slot = n }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (card != null) CardArt(card, Modifier.width(30.dp))
        else Box(Modifier.width(30.dp).aspectRatio(63f / 88f).border(1.dp, p.line, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(dexNo(n), style = Type.mono, color = p.faint)
            Text(Species.name(n), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                card != null && wants > 0 -> Text("↑ Upgrade", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = p.up)
                card != null -> Text(pounds(AppState.displayPrice(card)).takeIf { it != "—" } ?: "", fontSize = 11.sp, color = p.muted)
                wants > 0 -> Text("$wants wanted", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = p.want)
                else -> Text("Missing", fontSize = 11.sp, color = p.muted)
            }
        }
    }
}

@Composable
fun SlotSheet(n: Int) {
    val p = P
    @Suppress("UNUSED_VARIABLE") val v = AppState.version
    var showAll by remember(n) { mutableStateOf(false) }
    var showAllOther by remember(n) { mutableStateOf(false) }
    val all = AppState.catalog.bySpecies[n].orEmpty()
    val pri = { c: Card -> when (Labels.group(c.rarity)) { "special" -> 0; "art" -> 1; "ultra" -> 2; else -> 3 } }
    val mine = all.filter { AppState.store.total(it.key) > 0 }.sortedWith(compareBy<Card> { pri(it) }.thenBy { AppState.lowPrice(it) ?: Int.MAX_VALUE })
    val cur = AppState.store.binderCard(n)?.let { AppState.catalog.byKey[it] }
    val wanted = AppState.store.slotWants(n).toSet()
    var cand = all.filter { AppState.store.total(it.key) == 0 && (it.key in wanted || Labels.group(it.rarity) in setOf("special", "art", "ultra")) }
        .sortedWith(compareByDescending<Card> { it.key in wanted }.thenBy { pri(it) }.thenBy { AppState.lowPrice(it) ?: Int.MAX_VALUE })
    val more = cand.size > 15 && !showAll
    if (more) cand = cand.take(15)
    // Everything else for this Pokémon (promos, holos, older sets...), most valuable first, so nothing is hidden.
    val fancyKeys = cand.map { it.key }.toSet()
    val otherAll = all.filter { AppState.store.total(it.key) == 0 && it.key !in fancyKeys && it.key !in wanted &&
            Labels.group(it.rarity) !in setOf("special", "art", "ultra") }
        .sortedWith(compareByDescending<Card> { AppState.lowPrice(it) ?: -1 }.thenBy { it.set.lang != "EN" })
    val otherShown = if (showAllOther) otherAll else otherAll.take(if (cand.size < 5) 12 else 6)

    SheetFrame(dexNo(n) + " " + Species.name(n), { Nav.slot = null }) {
        if (cur != null) Row(verticalAlignment = Alignment.Bottom) {
            CardArt(cur, Modifier.weight(0.42f), big = true)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("In your binder")
                Text(cur.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = p.fg)
                Sub("${cur.set.name} · ${cur.numberLabel}")
                Text(pounds(AppState.displayPrice(cur)), style = Type.price, color = p.fg)
                Btn("Open card", Modifier.fillMaxWidth()) { Nav.openCard(cur.key) }
            }
        } else Panel { Text("Empty slot. Pick one of your cards below, or tick the cards you'd take so they show up in Wanted.", color = p.muted) }

        Column {
            Label("Choose from your cards")
            Gap(4.dp)
            if (mine.isEmpty()) Sub("You don't own a ${Species.name(n)} card yet.")
            mine.forEach { c -> OptionRow(c, selected = cur?.key == c.key, radio = true) { AppState.store.setBinder(n, c.key) } }
            if (mine.isNotEmpty()) Row(Modifier.fillMaxWidth().clickable { AppState.store.setBinder(n, null) }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(cur == null, { AppState.store.setBinder(n, null) }, colors = RadioButtonDefaults.colors(selectedColor = p.accent))
                Text("Leave slot empty", color = p.fg)
            }
        }
        Column {
            Row { Label(if (cur != null) "Upgrade to one of these" else "I want one of these", Modifier.weight(1f)); Sub("${slotWantsOpen(n).size} ticked") }
            if (cur != null) Sub("Ticking a card marks this slot ↑ Upgrade in the Pokédex.")
            Gap(4.dp)
            if (cand.isEmpty()) Sub("No illustration or ultra rares for this Pokémon yet. Pick from the other cards below.")
            cand.forEach { c -> OptionRow(c, selected = c.key in wanted, radio = false) { AppState.store.toggleSlotWant(n, c.key) } }
            if (more) Btn("Show all options", Modifier.padding(top = 6.dp)) { showAll = true }
            Gap(6.dp)
            Sub("Illustration rares first, then cheapest.")
        }
        if (otherAll.isNotEmpty()) Column {
            Row { Label("Other ${Species.name(n)} cards", Modifier.weight(1f)); Sub("${otherAll.size} cards") }
            Sub("Promos, holos and older cards, most valuable first.")
            Gap(4.dp)
            otherShown.forEach { c -> OptionRow(c, selected = c.key in wanted, radio = false) { AppState.store.toggleSlotWant(n, c.key) } }
            if (otherShown.size < otherAll.size) Btn("Show all ${otherAll.size}", Modifier.padding(top = 6.dp)) { showAllOther = true }
        }
    }
}

@Composable
private fun OptionRow(c: Card, selected: Boolean, radio: Boolean, onClick: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val redraw = AppState.version
    val p = P
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (radio) RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = p.accent))
        else Checkbox(selected, { onClick() }, colors = CheckboxDefaults.colors(checkedColor = p.accent, checkmarkColor = p.onAccent))
        CardArt(c, Modifier.width(34.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, fontWeight = FontWeight.SemiBold, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Sub("${c.set.name} · ${Labels.rarityShort(c.rarity).ifEmpty { "—" }} · ${c.set.lang}", maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            PriceText(if (AppState.store.total(c.key) > 0) AppState.displayPrice(c) else AppState.lowPrice(c))
            Text("Open", color = p.want, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { Nav.openCard(c.key) }.padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
}
