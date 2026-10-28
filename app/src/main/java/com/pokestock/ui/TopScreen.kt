package com.pokestock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pokestock.data.pounds

/** One owned copy group in the top list: a raw version, or a slab. */
private data class TopItem(val card: Card, val variant: String, val qty: Int, val each: Int, val label: String, val change: Double?)

@Composable
fun PageHeader(title: String, onBack: () -> Unit) {
    val p = P
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Text("‹", fontSize = 30.sp, color = p.fg) }
        Text(title, style = Type.title, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TopScreen() {
    val p = P
    val v = AppState.version
    var days by remember { mutableStateOf(7) }
    var sort by remember { mutableStateOf("value") }
    val all = remember(v, AppState.prices, AppState.history, days) {
        val raw = AppState.holdings().mapNotNull { h ->
            val each = AppState.unitPrice(h.card, h.variant) ?: return@mapNotNull null
            TopItem(h.card, h.variant, h.qty, each, Labels.variantLabel(h.card, h.variant), AppState.change(h.card, h.variant, days))
        }
        val slabs = AppState.store.allSlabs().mapNotNull { s ->
            val c = AppState.catalog.byKey[s.card] ?: return@mapNotNull null
            if (s.price <= 0) null else TopItem(c, s.variant, 1, s.price, Labels.gradeName(s.company, s.grade), null)
        }
        (raw + slabs).sortedByDescending { it.each }.take(100)
    }
    val list = when (sort) {
        "up" -> all.filter { (it.change ?: 0.0) > 0 }.sortedByDescending { it.change }
        "down" -> all.filter { (it.change ?: 0.0) < 0 }.sortedBy { it.change }
        else -> all
    }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        PageHeader("Top 100 cards") { Nav.page = null }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                Sub("Your 100 most valuable cards by price per copy. Change compares today's price with ${if (days == 7) "a week" else "30 days"} ago.")
                Gap(10.dp)
                ChipRow {
                    listOf("value" to "Most valuable", "up" to "Going up", "down" to "Going down").forEach { (k, l) -> Chip(l, sort == k) { sort = k } }
                    Spacer(Modifier.width(6.dp))
                    listOf(7 to "7 days", 30 to "30 days").forEach { (d, l) -> Chip(l, days == d) { days = d } }
                }
                Gap(8.dp)
                if (list.isEmpty()) EmptyState(if (sort == "up") "Nothing in your top 100 has gone up over this period." else if (sort == "down") "Nothing in your top 100 has gone down over this period." else "No priced cards yet.")
            }
            itemsIndexed(list, key = { _, it -> it.card.key + it.variant + it.label }) { i, it -> TopRow(i + 1, it) }
        }
    }
}

@Composable
private fun TopRow(rank: Int, it: TopItem) {
    val p = P
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp)).clickable { Nav.openCard(it.card.key, it.variant) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", style = Type.mono, color = p.faint, modifier = Modifier.width(26.dp))
        CardArt(it.card, Modifier.width(40.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(it.card.name, style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Sub("${it.card.set.name} · ${it.label}", maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(pounds(it.each), style = Type.strong, color = p.fg)
            val ch = it.change
            if (ch != null) Text((if (ch >= 0) "▲ " else "▼ ") + String.format("%.1f%%", Math.abs(ch * 100)), fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, color = if (ch >= 0) p.up else p.down)
            if (it.qty > 1) Badge("×${it.qty}")
        }
    }
}
