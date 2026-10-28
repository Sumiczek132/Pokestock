package com.pokestock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokestock.data.AppState
import com.pokestock.data.Labels
import com.pokestock.data.pounds
import com.pokestock.data.poundsRound
import java.time.LocalDate

@Composable
fun PortfolioScreen() {
    val p = P
    val v = AppState.version
    val prices = AppState.prices
    val data = remember(v, prices) {
        val holds = AppState.holdings()
        val total = AppState.portfolioValue()
        val slabs = AppState.store.allSlabs().sortedByDescending { it.price }
        var count = 0; val byLang = HashMap<String, Int>()
        holds.forEach { count += it.qty; byLang[it.card.set.lang] = (byLang[it.card.set.lang] ?: 0) + it.qty }
        slabs.forEach { s -> count++; AppState.catalog.byKey[s.card]?.let { byLang[it.set.lang] = (byLang[it.set.lang] ?: 0) + 1 } }
        val priced = holds.map { it to (AppState.unitPrice(it.card, it.variant) ?: 0) }
        val top = priced.sortedByDescending { it.second }.take(10)
        val recent = holds.sortedWith(compareByDescending<AppState.Holding> { it.added }.thenByDescending { AppState.unitPrice(it.card, it.variant) ?: 0 }).take(8)
        // Value of what you owned on each day, at today's prices.
        val byDay = java.util.TreeMap<LocalDate, Long>()
        val today = LocalDate.now()
        priced.forEach { (h, pr) -> val d = runCatching { LocalDate.parse(h.added) }.getOrDefault(today); byDay[d] = (byDay[d] ?: 0L) + pr.toLong() * h.qty }
        slabs.forEach { s -> if (s.price > 0) { val d = runCatching { LocalDate.parse(s.added) }.getOrDefault(today); byDay[d] = (byDay[d] ?: 0L) + s.price } }
        val series = ArrayList<Pair<LocalDate, Double>>()
        if (byDay.isNotEmpty()) {
            var acc = 0L; var d = byDay.firstKey(); val it = byDay.entries.iterator(); var next = if (it.hasNext()) it.next() else null
            while (!d.isAfter(today)) { while (next != null && !next.key.isAfter(d)) { acc += next.value; next = if (it.hasNext()) it.next() else null }; series.add(d to acc / 100.0); d = d.plusDays(1) }
        }
        Snapshot(total, count, holds.map { it.card.key }.toSet().size + slabs.map { it.card }.toSet().size, byLang, top, recent, slabs, series)
    }
    val binder = remember(v) { AppState.store.doc.binder.count { it.value.v.isNotEmpty() } }
    val days = mapOf("1M" to 30L, "3M" to 91L, "6M" to 182L, "1Y" to 365L)[Nav.range]
    val cut = if (days == null) data.series else data.series.filter { !it.first.isBefore(LocalDate.now().minusDays(days)) }
    val start = cut.firstOrNull()?.second ?: 0.0
    val diff = data.total / 100.0 - start

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            if (com.pokestock.data.AppUpdate.available != null) {
                Panel(Modifier.padding(bottom = 12.dp).clickable { Nav.tab = "more" }) {
                    Text("${com.pokestock.data.AppUpdate.available} is ready. Tap to update.", color = p.fg, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("Collection value")
                Text(pounds(data.total), style = Type.value, color = p.fg)
                if (cut.size > 1) Text((if (diff >= 0) "▲ " else "▼ ") + pounds(Math.round(Math.abs(diff) * 100)) + " added over " + rangeWords(Nav.range),
                    color = if (diff >= 0) p.up else p.down, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (AppState.prices.date.isEmpty()) Sub("Prices arrive with the first update. Connect once and they download automatically.")
            }
            Gap(14.dp)
            ChipRow { listOf("1M", "3M", "6M", "1Y", "All").forEach { r -> Chip(r, Nav.range == r) { Nav.range = r } } }
            Gap(10.dp)
            if (cut.size > 1) LineChart(cut, { poundsRound(Math.round(it * 100)) })
            Gap(6.dp)
            Sub("Value of the cards you owned on each day, at today's prices.")
            Gap(16.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(String.format("%,d", data.count), "cards", Modifier.weight(1f))
                Stat(String.format("%,d", data.unique), "unique", Modifier.weight(1f))
                Stat("$binder/1025", "binder slots", Modifier.weight(1f))
            }
            Gap(8.dp)
            Sub("${String.format("%,d", data.byLang["EN"] ?: 0)} English · ${data.byLang["JA"] ?: 0} Japanese · ${data.byLang["ZH"] ?: 0} Chinese")
            SectionHead("Most valuable")
        }
        items(data.top, key = { "t" + it.first.card.key + it.first.variant }) { (h, pr) ->
            HoldingRow(h.card, h.variant, h.qty, pr.takeIf { it > 0 }, Labels.variantLabel(h.card, h.variant))
        }
        item { SectionHead("Recently added") }
        items(data.recent, key = { "r" + it.card.key + it.variant }) { h ->
            HoldingRow(h.card, h.variant, h.qty, AppState.unitPrice(h.card, h.variant), Labels.variantLabel(h.card, h.variant))
        }
        if (data.slabs.isNotEmpty()) {
            item { SectionHead("Graded slabs", "${data.slabs.size} · " + pounds(data.slabs.sumOf { maxOf(0, it.price).toLong() })) }
            items(data.slabs, key = { "s" + it.id }) { SlabRow(it) }
        }
    }
}

private data class Snapshot(
    val total: Long, val count: Int, val unique: Int, val byLang: Map<String, Int>,
    val top: List<Pair<AppState.Holding, Int>>, val recent: List<AppState.Holding>,
    val slabs: List<com.pokestock.data.Slab>, val series: List<Pair<LocalDate, Double>>,
)

private fun rangeWords(r: String) = when (r) { "1M" -> "the last month"; "3M" -> "the last 3 months"; "6M" -> "the last 6 months"; "1Y" -> "the last year"; else -> "all time" }
