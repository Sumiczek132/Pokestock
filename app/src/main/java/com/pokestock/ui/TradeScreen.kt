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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Incoming(val cardKey: String, val variant: String, val qty: Int)

/** Trade state lives here so it survives opening a card on top of the trade screen. */
object TradeState {
    var selecting by mutableStateOf(false)
    val sel = mutableStateMapOf<String, Int>()       // "cardKey|variant" -> copies chosen
    val selSlabs = mutableStateListOf<String>()
    val incoming = mutableStateListOf<Incoming>()
    var picking by mutableStateOf(false)
    var confirming by mutableStateOf(false)
    fun reset() { selecting = false; sel.clear(); selSlabs.clear(); incoming.clear(); picking = false; confirming = false }
}

private val PERCENTS = listOf(95, 90, 85, 80, 75, 70)

@Composable
fun TradeScreen() {
    val p = P
    val say = toast()
    val v = AppState.version
    val raw = remember(v) { AppState.store.tradeRaw().mapNotNull { (k, q) -> AppState.catalog.byKey[k.substringBefore('|')]?.let { Triple(it, k.substringAfter('|'), q) } }
        .sortedByDescending { (c, vk, _) -> AppState.unitPrice(c, vk) ?: 0 } }
    val slabs = remember(v) { AppState.store.tradeSlabs().sortedByDescending { it.price } }
    val binderValue = raw.sumOf { (c, vk, q) -> (AppState.unitPrice(c, vk) ?: 0).toLong() * q } + slabs.sumOf { maxOf(0, it.price).toLong() }
    val giveValue = TradeState.sel.entries.sumOf { (k, q) -> AppState.catalog.byKey[k.substringBefore('|')]?.let { (AppState.unitPrice(it, k.substringAfter('|')) ?: 0).toLong() * q } ?: 0L } +
        TradeState.selSlabs.sumOf { id -> maxOf(0, AppState.store.doc.slabs[id]?.price ?: 0).toLong() }
    val getValue = TradeState.incoming.sumOf { inc -> AppState.catalog.byKey[inc.cardKey]?.let { (AppState.unitPrice(it, inc.variant) ?: 0).toLong() * inc.qty } ?: 0L }
    val pickedCount = TradeState.sel.values.sum() + TradeState.selSlabs.size

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            PageHeader(if (TradeState.selecting) "New trade" else "Trade binder") { if (TradeState.selecting) TradeState.reset() else Nav.page = null }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
                item {
                    Sub("${raw.sumOf { it.third } + slabs.size} cards · ${pounds(binderValue)}")
                    Gap(10.dp)
                    if (!TradeState.selecting) {
                        Btn("Trade now", Modifier.fillMaxWidth(), primary = true, enabled = raw.isNotEmpty() || slabs.isNotEmpty()) { TradeState.selecting = true }
                        Gap(6.dp)
                        Sub("Add cards to the trade binder from any card's page: open the card and set how many copies are up for trade.")
                    } else Sub("Pick the cards you're trading away.")
                    if (raw.isEmpty() && slabs.isEmpty()) EmptyState("Your trade binder is empty.")
                }
                items(raw, key = { (c, vk, _) -> c.key + "|" + vk }) { (c, vk, q) -> TradeRow(c, vk, q) }
                items(slabs, key = { it.id }) { s ->
                    val c = AppState.catalog.byKey[s.card] ?: return@items
                    SlabTradeRow(c, s.id, Labels.gradeName(s.company, s.grade), s.price)
                }
                if (TradeState.selecting) item {
                    SectionHead("Your side", "$pickedCount card${if (pickedCount == 1) "" else "s"}")
                    Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row { Text("Market value", color = p.muted, modifier = Modifier.weight(1f)); Text(pounds(giveValue), fontWeight = FontWeight.Bold, color = p.fg) }
                            PERCENTS.forEach { pc ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("$pc%", fontWeight = FontWeight.SemiBold, color = if (pc == 70) p.down else p.fg, modifier = Modifier.width(56.dp))
                                    Box(Modifier.weight(1f)) { ProgressBar(pc / 100f, height = 6.dp) }
                                    Spacer(Modifier.width(12.dp))
                                    Text(pounds(giveValue * pc / 100), fontWeight = FontWeight.SemiBold, color = p.fg, modifier = Modifier.width(96.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                }
                            }
                            Sub("70% is your floor.")
                        }
                    }
                    SectionHead("What you're getting", "optional")
                    TradeState.incoming.forEachIndexed { i, inc ->
                        val c = AppState.catalog.byKey[inc.cardKey]
                        if (c != null) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CardArt(c, Modifier.width(40.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Sub("${c.set.name} · ${Labels.variantLabel(c, inc.variant)}" + if (inc.qty > 1) " · ×${inc.qty}" else "", maxLines = 1)
                            }
                            PriceText(AppState.unitPrice(c, inc.variant)?.let { it * inc.qty })
                            Text("✕", color = p.muted, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { TradeState.incoming.removeAt(i) }.padding(10.dp))
                        }
                    }
                    Btn("Add a card you're getting", Modifier.fillMaxWidth()) { TradeState.picking = true }
                    if (TradeState.incoming.isNotEmpty() && giveValue > 0) {
                        Gap(10.dp)
                        val ratio = getValue.toDouble() / giveValue
                        Panel {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row { Text("You get", color = p.muted, modifier = Modifier.weight(1f)); Text(pounds(getValue), fontWeight = FontWeight.Bold, color = p.fg) }
                                Text(String.format("%.0f%% of your cards' value", ratio * 100), fontWeight = FontWeight.Bold,
                                    color = if (ratio >= 0.7) p.up else p.down)
                                if (ratio < 0.7) Text("Below your 70% limit.", color = p.down, fontSize = 13.sp)
                            }
                        }
                    }
                    Gap(14.dp)
                    if (!TradeState.confirming) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Cancel", Modifier.weight(1f)) { TradeState.reset() }
                        Btn("Accept trade", Modifier.weight(1f), primary = true, enabled = pickedCount > 0) { TradeState.confirming = true }
                    } else Panel {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Complete this trade?", fontWeight = FontWeight.Bold, color = p.fg)
                            Sub("$pickedCount card${if (pickedCount == 1) "" else "s"} (${pounds(giveValue)}) come out of your collection" +
                                if (TradeState.incoming.isNotEmpty()) " and ${TradeState.incoming.sumOf { it.qty }} card${if (TradeState.incoming.sumOf { it.qty } == 1) "" else "s"} (${pounds(getValue)}) go in." else ".")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Btn("Back", Modifier.weight(1f)) { TradeState.confirming = false }
                                Btn("Confirm trade", Modifier.weight(1f), primary = true) {
                                    completeTrade(giveValue, getValue)
                                    say("Trade done. Your collection is updated.")
                                    TradeState.reset()
                                }
                            }
                        }
                    }
                }
                if (!TradeState.selecting) item { PastTrades() }
            }
        }
        if (TradeState.picking) IncomingPicker()
    }
}

private fun completeTrade(giveValue: Long, getValue: Long) {
    val rec = JSONObject().put("date", LocalDate.now().toString()).put("giveValue", giveValue).put("getValue", getValue)
    val give = JSONArray(); val get = JSONArray()
    TradeState.sel.forEach { (k, q) -> if (q > 0) {
        val c = AppState.catalog.byKey[k.substringBefore('|')]
        give.put(JSONObject().put("k", k).put("q", q).put("name", c?.name ?: k).put("each", c?.let { AppState.unitPrice(it, k.substringAfter('|')) } ?: 0))
    } }
    TradeState.selSlabs.forEach { id -> AppState.store.doc.slabs[id]?.let { s ->
        give.put(JSONObject().put("slab", id).put("name", (AppState.catalog.byKey[s.card]?.name ?: s.card) + " " + Labels.gradeShort(s.company, s.grade)).put("each", s.price))
    } }
    TradeState.incoming.forEach { inc ->
        val c = AppState.catalog.byKey[inc.cardKey]
        get.put(JSONObject().put("k", inc.cardKey + "|" + inc.variant).put("q", inc.qty).put("name", c?.name ?: inc.cardKey).put("each", c?.let { AppState.unitPrice(it, inc.variant) } ?: 0))
    }
    rec.put("give", give).put("get", get)
    AppState.store.applyTrade(
        TradeState.sel.filter { it.value > 0 }.map { it.key to it.value },
        TradeState.selSlabs.toList(),
        TradeState.incoming.map { "${it.cardKey}|${it.variant}" to it.qty },
        rec,
    )
}

@Composable
private fun TradeRow(c: Card, vk: String, inBinder: Int) {
    @Suppress("UNUSED_VARIABLE") val redraw = AppState.version
    val p = P
    val k = c.key + "|" + vk
    val picked = TradeState.sel[k] ?: 0
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp))
        .clickable { if (TradeState.selecting) TradeState.sel[k] = if (picked > 0) 0 else 1 else Nav.openCard(c.key, vk) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (TradeState.selecting) Checkbox(picked > 0, { on -> TradeState.sel[k] = if (on) 1 else 0 }, colors = CheckboxDefaults.colors(checkedColor = p.accent, checkmarkColor = p.onAccent))
        CardArt(c, Modifier.width(44.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(c.name, style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Sub("${c.set.name} · ${c.numberLabel}", maxLines = 1)
            Text(Labels.variantLabel(c, vk), style = Type.mono, color = p.faint, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PriceText(AppState.unitPrice(c, vk))
            if (TradeState.selecting && inBinder > 1 && picked > 0) Stepper(picked, { TradeState.sel[k] = picked - 1 }, { if (picked < inBinder) TradeState.sel[k] = picked + 1 })
            else Badge("×$inBinder")
        }
    }
}

@Composable
private fun SlabTradeRow(c: Card, id: String, grade: String, price: Int) {
    val p = P
    val picked = id in TradeState.selSlabs
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp))
        .clickable { if (TradeState.selecting) { if (picked) TradeState.selSlabs.remove(id) else TradeState.selSlabs.add(id) } else Nav.openCard(c.key) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (TradeState.selecting) Checkbox(picked, { on -> if (on) TradeState.selSlabs.add(id) else TradeState.selSlabs.remove(id) },
            colors = CheckboxDefaults.colors(checkedColor = p.accent, checkmarkColor = p.onAccent))
        CardArt(c, Modifier.width(44.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Sub("${c.set.name} · $grade", maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (price > 0) Text(pounds(price), style = Type.strong, color = p.fg) else Text("No price", fontSize = 12.sp, color = p.faint)
            Badge("Slab", "slab")
        }
    }
}

@Composable
private fun PastTrades() {
    val p = P
    val v = AppState.version
    val list = remember(v) {
        AppState.store.doc.trades.values.mapNotNull { runCatching { JSONObject(it.v) }.getOrNull() }.sortedByDescending { it.optString("date") }
    }
    if (list.isEmpty()) return
    SectionHead("Past trades")
    list.take(30).forEach { r ->
        val give = r.optLong("giveValue"); val get = r.optLong("getValue")
        val names = { a: JSONArray? -> if (a == null) "" else (0 until a.length()).joinToString(", ") { i -> a.getJSONObject(i).let { o -> o.optString("name") + if (o.optInt("q", 1) > 1) " ×" + o.optInt("q") else "" } } }
        Panel(Modifier.padding(bottom = 8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(runCatching { LocalDate.parse(r.optString("date")).format(DateTimeFormatter.ofPattern("d MMM yyyy")) }.getOrDefault(""), fontWeight = FontWeight.SemiBold, color = p.fg, modifier = Modifier.weight(1f))
                    if (give > 0 && get > 0) Text(String.format("%.0f%%", get * 100.0 / give), fontWeight = FontWeight.Bold, color = if (get * 10 >= give * 7) p.up else p.down)
                }
                Sub("Gave ${pounds(give)}: " + names(r.optJSONArray("give")), maxLines = 3)
                if ((r.optJSONArray("get")?.length() ?: 0) > 0) Sub("Got ${pounds(get)}: " + names(r.optJSONArray("get")), maxLines = 3)
            }
        }
    }
}

/** Search for the card you're getting, pick the version and how many. */
@Composable
private fun IncomingPicker() {
    val p = P
    var lang by remember { mutableStateOf("EN") }
    var q by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<Card?>(null) }
    var variant by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf(1) }
    val hits = remember(lang, q, AppState.catalog) { if (q.trim().length >= 2) AppState.catalog.search(lang, q, 120) else emptyList() }
    SheetFrame("Add a card you're getting", { if (chosen != null) chosen = null else TradeState.picking = false }) {
        val c = chosen
        if (c == null) {
            Segmented(listOf("EN" to "English", "JA" to "Japanese"), lang) { lang = it }
            SearchField(q, "Name, set or number") { q = it }
            hits.forEach { card -> CardRow(card) { chosen = card; variant = AppState.versionsOf(card).first(); qty = 1 } }
            if (q.trim().length >= 2 && hits.isEmpty()) Sub("No cards match.")
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                CardArt(c, Modifier.weight(0.4f), big = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(0.6f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(c.name, style = Type.title, color = p.fg)
                    Sub("${c.set.name} · ${c.numberLabel}")
                    Text(pounds(AppState.unitPrice(c, variant)), style = Type.price, color = p.fg)
                }
            }
            Label("Version")
            ChipRow { AppState.versionsOf(c).forEach { k -> Chip(Labels.variantLabel(c, k), variant == k) { variant = k } } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("How many", color = p.fg, modifier = Modifier.weight(1f))
                Stepper(qty, { if (qty > 1) qty-- }, { qty++ })
            }
            Btn("Add to trade", Modifier.fillMaxWidth(), primary = true) {
                TradeState.incoming.add(Incoming(c.key, variant, qty)); TradeState.picking = false
            }
        }
    }
}
