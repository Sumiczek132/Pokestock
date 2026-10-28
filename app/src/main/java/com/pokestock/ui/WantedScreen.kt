package com.pokestock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokestock.data.AppState
import com.pokestock.data.Species
import com.pokestock.data.pounds

@Composable
fun WantedScreen() {
    val p = P
    val v = AppState.version
    val cat = AppState.catalog
    val data = remember(v, cat, AppState.prices) {
        val empty = ArrayList<Int>(); val ups = ArrayList<Int>()
        for (n in 1..Species.count) if (slotWantsOpen(n).isNotEmpty()) (if (AppState.store.binderCard(n) != null) ups else empty).add(n)
        val wish = AppState.store.wishList().mapNotNull { cat.byKey[it] }.sortedByDescending { AppState.bestPrice(it) ?: -1 }
        Triple(empty, ups, wish)
    }
    val (empty, ups, wish) = data
    fun cost(list: List<Int>) = list.sumOf { n -> slotWantsOpen(n).mapNotNull { AppState.lowPrice(it) }.minOrNull()?.toLong() ?: 0L }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wanted", style = Type.display, color = p.fg, modifier = Modifier.weight(1f))
                Btn("Share list") { Nav.share = ShareRequest("My want list", ShareLists.wanted()) }
            }
            Sub("Check here before you buy. It works without signal.")
            SectionHead("Empty binder slots", "${empty.size} · from ${pounds(cost(empty))}")
            if (empty.isEmpty()) EmptyState("Open a missing slot in the Pokédex and tick the cards you would take.")
        }
        items(empty, key = { "e$it" }) { SlotWantRow(it, false) }
        item {
            SectionHead("Upgrades", "${ups.size} · from ${pounds(cost(ups))}")
            if (ups.isEmpty()) EmptyState("Open a filled slot in the Pokédex and tick the cards you would upgrade to.")
        }
        items(ups, key = { "u$it" }) { SlotWantRow(it, true) }
        item {
            val priced = wish.sumOf { AppState.lowPrice(it)?.toLong() ?: 0L }
            SectionHead("Wishlist", "${wish.size} card${if (wish.size == 1) "" else "s"}" + if (priced > 0) " · ${pounds(priced)}" else "")
            if (wish.isEmpty()) EmptyState("Tap the star next to any card in a set, or Add to wanted on a card, to start your list.")
        }
        items(wish, key = { "w" + it.key }) { CardRow(it) }
    }
}

@Composable
private fun SlotWantRow(n: Int, upgrade: Boolean) {
    val p = P
    val opts = slotWantsOpen(n).sortedBy { AppState.lowPrice(it) ?: Int.MAX_VALUE }
    val c = opts.firstOrNull() ?: return
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp)).clickable { Nav.slot = n }.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CardArt(c, Modifier.width(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${dexNo(n)} ${Species.name(n)}", style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val from = if (upgrade) AppState.store.binderCard(n)?.let { AppState.catalog.byKey[it]?.name }?.let { "Upgrade from $it · " } ?: "" else ""
            Sub(from + "${opts.size} option${if (opts.size > 1) "s" else ""}", maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PriceText(AppState.lowPrice(c))
            Badge(if (upgrade) "Upgrade" else "Slot", if (upgrade) "up" else "want")
        }
    }
}
