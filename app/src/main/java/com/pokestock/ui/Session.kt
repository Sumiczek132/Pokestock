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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokestock.data.AppState
import com.pokestock.data.Card
import com.pokestock.data.Labels
import com.pokestock.data.pounds

/** One card version looked up or added during a quick add / scan session. */
data class SessionEntry(val id: Long, val card: Card, val variant: String, val hadBefore: Int, val added: Int)

/** The running list for a quick add or scanning session, with the count of what went in. */
class AddSession {
    val log = mutableStateListOf<SessionEntry>()
    val total get() = log.sumOf { it.added }
    val value get() = log.sumOf { (AppState.unitPrice(it.card, it.variant) ?: 0).toLong() * it.added }

    /** Records a card; [add] also puts a copy in the collection. Repeats move back to the top and count again. */
    fun record(c: Card, variant: String, add: Boolean): SessionEntry {
        if (add) AppState.store.addOne(c.key, variant)
        val i = log.indexOfFirst { it.card.key == c.key && it.variant == variant }
        val e = if (i >= 0) log.removeAt(i).let { it.copy(added = it.added + if (add) 1 else 0) }
        else SessionEntry(System.nanoTime(), c, variant,
            AppState.store.qty(c.key, variant) + AppState.store.slabsOf(c.key, variant).size - if (add) 1 else 0, if (add) 1 else 0)
        log.add(0, e)
        return e
    }

    fun plus(id: Long) {
        val i = log.indexOfFirst { it.id == id }; if (i < 0) return
        AppState.store.addOne(log[i].card.key, log[i].variant)
        log[i] = log[i].copy(added = log[i].added + 1)
    }

    fun minus(id: Long) {
        val i = log.indexOfFirst { it.id == id }; if (i < 0 || log[i].added == 0) return
        val e = log[i]
        AppState.store.setQty(e.card.key, e.variant, AppState.store.qty(e.card.key, e.variant) - 1)
        log[i] = e.copy(added = e.added - 1)
    }
}

@Composable
fun SessionRow(e: SessionEntry, session: AddSession) {
    val p = P
    val need = e.hadBefore == 0
    val haptic = LocalHapticFeedback.current
    Panel(padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).heightIn(min = 52.dp).clip(RoundedCornerShape(3.dp)).background(if (need) p.up else p.faint))
            Spacer(Modifier.width(10.dp))
            CardArt(e.card, Modifier.width(38.dp).clickable { Nav.openCard(e.card.key, e.variant) })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (need) "NEED IT" else "Had ×${e.hadBefore}", fontWeight = FontWeight.ExtraBold, color = if (need) p.up else p.muted, fontSize = 13.sp)
                Text("${e.card.localId} ${e.card.name}", style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Sub(Labels.variantLabel(e.card, e.variant) + " · " + pounds(AppState.unitPrice(e.card, e.variant)), maxLines = 1)
                if (e.added > 0) Text("Added ×${e.added}", color = p.up, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            if (e.added > 0) { Btn("−") { session.minus(e.id) }; Spacer(Modifier.width(6.dp)) }
            Btn(if (e.added > 0) "+1" else "Add", primary = e.added == 0) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); session.plus(e.id)
            }
        }
    }
}

/** Big phone-style number pad, so the keyboard isn't popping up and down between cards. */
@Composable
fun NumPad(prefixes: List<String>, onKey: (String) -> Unit, onDelete: () -> Unit, onEnter: () -> Unit, enterLabel: String = "Add") {
    val p = P
    val haptic = LocalHapticFeedback.current
    @Composable
    fun Key(label: String, modifier: Modifier, accent: Boolean = false, action: () -> Unit) {
        Box(modifier.height(54.dp).clip(RoundedCornerShape(12.dp)).background(if (accent) p.accent else p.surface2)
            .border(1.dp, if (accent) p.accent else p.line, RoundedCornerShape(12.dp))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); action() }, contentAlignment = Alignment.Center) {
            Text(label, fontSize = if (label.length > 2) 17.sp else 24.sp, fontWeight = FontWeight.Bold, color = if (accent) p.onAccent else p.fg)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (prefixes.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            prefixes.take(5).forEach { Key(it, Modifier.weight(1f)) { onKey(it) } }
        }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { d -> Key(d, Modifier.weight(1f)) { onKey(d) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Key("⌫", Modifier.weight(1f)) { onDelete() }
            Key("0", Modifier.weight(1f)) { onKey("0") }
            Key(enterLabel, Modifier.weight(1f), accent = true) { onEnter() }
        }
    }
}

/** Letter prefixes used for card numbers in a set (e.g. TG, GG, SV), for the number pad's top row. */
fun numberPrefixes(cards: List<Card>): List<String> =
    cards.mapNotNull { Regex("^([A-Za-z]+)\\d").find(it.localId)?.groupValues?.get(1)?.uppercase() }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }.map { it.key }

/** Shown when leaving a quick add or scanning session: what went in and the best hits. */
@Composable
fun SessionSummary(session: AddSession, title: String, onMore: () -> Unit, onDone: () -> Unit) {
    val p = P
    val added = session.log.filter { it.added > 0 }
    val newCards = added.count { it.hadBefore == 0 }
    val dexFill = added.map { it.card }.filter { it.dex > 0 && AppState.store.binderCard(it.dex) == null }.map { it.dex }.distinct().size
    val wantedHits = added.count { e -> AppState.store.isWish(e.card.key) || (e.card.dex > 0 && AppState.store.isSlotWant(e.card.dex, e.card.key)) }
    val best = added.sortedByDescending { AppState.unitPrice(it.card, it.variant) ?: 0 }.take(10)
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        PageHeader(title) { onDone() }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Label("Added this session")
                Text(pounds(session.value), style = Type.value, color = p.fg)
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(String.format("%,d", session.total), "cards", Modifier.weight(1f))
                    Stat("$newCards", "new to you", Modifier.weight(1f))
                    Stat("$dexFill", "empty dex slots", Modifier.weight(1f))
                }
                if (wantedHits > 0) { Gap(8.dp); Text("★ $wantedHits from your Wanted list", color = p.want, fontWeight = FontWeight.SemiBold) }
                if (dexFill > 0) Sub("Pokédex slots still empty that these cards could fill. Open the slot in the Pokédex to put them in.")
                SectionHead("Best hits")
            }
            items(best, key = { it.id }) { e ->
                HoldingRow(e.card, e.variant, e.added, AppState.unitPrice(e.card, e.variant), Labels.variantLabel(e.card, e.variant))
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Keep adding", Modifier.weight(1f)) { onMore() }
            Btn("Done", Modifier.weight(1f), primary = true) { onDone() }
        }
    }
}
