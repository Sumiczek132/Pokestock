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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokestock.data.AppState
import com.pokestock.data.Binder
import com.pokestock.data.Card
import com.pokestock.data.Labels
import com.pokestock.data.Pocket
import com.pokestock.data.Species

private const val PER_PAGE = 9

/** A set's binder, laid out as 9-pocket pages in card-number order. */
@Composable
fun SetBinderScreen(setKey: String) {
    val p = P
    val set = AppState.catalog.setByKey[setKey] ?: run { Nav.page = null; return }
    val v = AppState.version
    val mode = AppState.store.binderMode(setKey).ifEmpty { "base" }
    var gapsOnly by remember { mutableStateOf(false) }
    val pockets = remember(v, setKey, mode) { Binder.pockets(set, mode) }
    val pages = pockets.chunked(PER_PAGE).mapIndexed { i, l -> i + 1 to l }
    val shown = if (gapsOnly) pages.filter { (_, l) -> l.any { !it.owned } } else pages
    val have = pockets.count { it.owned }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        PageHeader(set.name + " binder") { Nav.page = null }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Segmented(Binder.MODES, mode) { AppState.store.setBinderMode(setKey, it) }
                    Sub(when (mode) {
                        "master" -> "Every card and every version, secret rares included."
                        "base_rh" -> "Cards 1–${set.total.takeIf { it > 0 } ?: "end"}: the normal card plus its reverse holo where there is one."
                        else -> "Cards 1–${set.total.takeIf { it > 0 } ?: "end"}, one pocket each. Secret rares and reverses aren't included."
                    })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("$have/${pockets.size}", "pockets filled", Modifier.weight(1f))
                        Stat("${pages.size}", "pages", Modifier.weight(1f))
                        Stat("${pockets.size - have}", "to find", Modifier.weight(1f))
                    }
                    ProgressBar(if (pockets.isEmpty()) 0f else have.toFloat() / pockets.size)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(gapsOnly, { gapsOnly = it }, colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent))
                        Spacer(Modifier.width(8.dp))
                        Text("Only pages with gaps", color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("Stop tracking", color = p.muted, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { AppState.store.setBinderMode(setKey, ""); Nav.page = null }.padding(8.dp))
                    }
                }
            }
            items(shown, key = { it.first }) { (n, list) -> BinderPage(n, list.map { PocketView(it.card, it.variant, it.owned, it.card.localId, showVersion = mode != "base") }) }
        }
    }
}

/** The Pokédex binder as 16-pocket pages, slot #1 first. */
@Composable
fun DexBinderScreen() {
    val p = P
    val v = AppState.version
    var gapsOnly by remember { mutableStateOf(false) }
    val pockets = remember(v) {
        (1..Species.count).map { n ->
            val c = AppState.store.binderCard(n)?.let { AppState.catalog.byKey[it] }
            n to c
        }
    }
    // The Pokédex binder is a 16-pocket (4 × 4) binder.
    val pages = pockets.chunked(16).mapIndexed { i, l -> i + 1 to l }
    val shown = if (gapsOnly) pages.filter { (_, l) -> l.any { it.second == null } } else pages
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        PageHeader("Pokédex binder pages") { Nav.page = null }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(gapsOnly, { gapsOnly = it }, colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent))
                    Spacer(Modifier.width(8.dp))
                    Text("Only pages with gaps", color = p.muted, fontSize = 13.sp)
                }
            }
            items(shown, key = { it.first }) { (n, list) ->
                BinderPage(n, list.map { (dex, c) -> PocketView(c, null, c != null, dexNo(dex), name = Species.name(dex), dex = dex) }, columns = 4)
            }
        }
    }
}

private data class PocketView(val card: Card?, val variant: String?, val owned: Boolean, val number: String, val showVersion: Boolean = false, val name: String? = null, val dex: Int = 0)

@Composable
private fun BinderPage(n: Int, pockets: List<PocketView>, columns: Int = 3) {
    val p = P
    Panel(padding = 10.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text("Page $n", fontWeight = FontWeight.Bold, color = p.fg, modifier = Modifier.weight(1f))
                Sub("${pockets.count { it.owned }}/${pockets.size}")
            }
            pockets.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(if (columns > 3) 6.dp else 8.dp)) {
                    row.forEach { pk -> Box(Modifier.weight(1f)) { PocketCell(pk) } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PocketCell(pk: PocketView) {
    val p = P
    Column(Modifier.clip(RoundedCornerShape(6.dp)).clickable {
        when {
            pk.card != null -> Nav.openCard(pk.card.key, pk.variant)
            pk.dex > 0 -> Nav.slot = pk.dex
        }
    }, horizontalAlignment = Alignment.CenterHorizontally) {
        if (pk.card != null) CardArt(pk.card, Modifier.fillMaxWidth(), dim = !pk.owned)
        else Box(Modifier.fillMaxWidth().aspectRatio(63f / 88f).clip(RoundedCornerShape(4.dp)).border(1.dp, p.line, RoundedCornerShape(4.dp)).background(Color.Transparent),
            contentAlignment = Alignment.Center) { Text(pk.name ?: "", color = p.faint, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(4.dp)) }
        Text(pk.number + if (pk.showVersion && pk.card != null && pk.variant != null) " " + Labels.variantShort(pk.card, pk.variant) else "",
            style = Type.mono, color = if (pk.owned) p.fg else p.faint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}
