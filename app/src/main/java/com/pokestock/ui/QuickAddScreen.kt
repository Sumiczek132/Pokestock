package com.pokestock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokestock.data.AppState
import com.pokestock.data.Binder
import com.pokestock.data.Card
import com.pokestock.data.Labels
import com.pokestock.data.pounds

private data class Checked(val id: Long, val card: Card, val variant: String, val hadBefore: Int, val added: Int)

/** Type card numbers from one set to see straight away whether you need them, and add them with one tap. */
@Composable
fun QuickAddScreen(setKey: String) {
    val p = P
    val set = AppState.catalog.setByKey[setKey] ?: run { Nav.page = null; return }
    @Suppress("UNUSED_VARIABLE") val v = AppState.version
    var input by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("main") }       // main | reverse
    var autoAdd by remember { mutableStateOf(false) }
    val log = remember { mutableStateListOf<Checked>() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun findCard(num: String): Card? {
        val t = num.trim().uppercase().substringBefore('/')
        if (t.isEmpty()) return null
        val n = t.trimStart('0')
        return set.cards.firstOrNull { it.localId.uppercase().trimStart('0') == n || it.localId.uppercase() == t }
            ?: set.cards.firstOrNull { Regex("^([A-Z]*)0*(\\d+)$").find(it.localId.uppercase())?.let { m -> m.groupValues[1] + m.groupValues[2] } == t.replace(Regex("^([A-Z]*)0*"), "$1") }
    }
    fun submit() {
        val c = findCard(input) ?: run { input = ""; return }
        val k = if (version == "reverse" && c.variants.any { it.key == "reverse" }) "reverse" else Binder.mainVariant(c)
        if (autoAdd) AppState.store.addOne(c.key, k)
        // Typing the same card again moves it back to the top and counts it again, instead of a new row.
        val i = log.indexOfFirst { it.card.key == c.key && it.variant == k }
        if (i >= 0) { val e = log.removeAt(i); log.add(0, e.copy(added = e.added + if (autoAdd) 1 else 0)) }
        else {
            val before = AppState.store.qty(c.key, k) + AppState.store.slabsOf(c.key, k).size - if (autoAdd) 1 else 0
            log.add(0, Checked(System.nanoTime(), c, k, before, if (autoAdd) 1 else 0))
        }
        input = ""
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        PageHeader("Quick add · " + set.name) { Nav.page = null }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Sub("Type a card number and press enter. Green means you need it.")
            Segmented(listOf("main" to "Normal / holo", "reverse" to "Reverse holo"), version) { version = it }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    if (input.isEmpty()) Text("Card number, e.g. 45", color = p.faint, fontSize = 18.sp)
                    BasicTextField(input, { input = it.take(10) }, singleLine = true, textStyle = TextStyle(color = p.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(p.accent), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }), modifier = Modifier.fillMaxWidth().focusRequester(focus))
                }
                Btn("Check", primary = true) { submit() }
            }
            Chip(if (autoAdd) "Adding each card to my collection ✓" else "Just checking (tap to add as I go)", autoAdd) { autoAdd = !autoAdd }
            val total = log.sumOf { it.added }
            if (total > 0) {
                val value = log.sumOf { (AppState.unitPrice(it.card, it.variant) ?: 0).toLong() * it.added }
                Text("Added this session: $total card${if (total == 1) "" else "s"} · ${pounds(value)}", fontWeight = FontWeight.SemiBold, color = p.fg)
            }
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(log, key = { it.id }) { e ->
                QuickRow(e,
                    onAdd = { AppState.store.addOne(e.card.key, e.variant); val i = log.indexOfFirst { it.id == e.id }; if (i >= 0) log[i] = log[i].copy(added = log[i].added + 1) },
                    onRemove = {
                        val i = log.indexOfFirst { it.id == e.id }
                        if (i >= 0 && log[i].added > 0) {
                            AppState.store.setQty(e.card.key, e.variant, AppState.store.qty(e.card.key, e.variant) - 1)
                            log[i] = log[i].copy(added = log[i].added - 1)
                        }
                    })
            }
        }
    }
}

@Composable
private fun QuickRow(e: Checked, onAdd: () -> Unit, onRemove: () -> Unit) {
    val p = P
    val need = e.hadBefore == 0
    Panel(padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).heightIn(min = 52.dp).clip(RoundedCornerShape(3.dp)).background(if (need) p.up else p.faint))
            Spacer(Modifier.width(10.dp))
            CardArt(e.card, Modifier.width(38.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (need) "NEED IT" else "Have ×${e.hadBefore}", fontWeight = FontWeight.ExtraBold, color = if (need) p.up else p.muted, fontSize = 13.sp)
                Text("${e.card.localId} ${e.card.name}", style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Sub(Labels.variantLabel(e.card, e.variant) + " · " + pounds(AppState.unitPrice(e.card, e.variant)), maxLines = 1)
                if (e.added > 0) Text("Added ×${e.added}", color = p.up, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            if (e.added > 0) { Btn("−") { onRemove() }; Spacer(Modifier.width(6.dp)) }
            Btn(if (e.added > 0) "+1" else "Add", primary = e.added == 0) { onAdd() }
        }
    }
}
