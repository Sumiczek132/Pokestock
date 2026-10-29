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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.clickable
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

/** Type card numbers from one set to see straight away whether you need them, and add them with one tap. */
@Composable
fun QuickAddScreen(setKey: String) {
    val p = P
    val set = AppState.catalog.setByKey[setKey] ?: run { Nav.page = null; return }
    @Suppress("UNUSED_VARIABLE") val v = AppState.version
    var input by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("main") }       // main | reverse
    var autoAdd by remember { mutableStateOf(true) }
    var keyboard by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf(false) }
    val session = remember { AddSession() }
    val prefixes = remember(setKey) { numberPrefixes(set.cards) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(keyboard) { if (keyboard) runCatching { focus.requestFocus() } }
    DisposableEffect(Unit) {
        Nav.pageBack = { if (session.total > 0 && !summary) { summary = true; true } else false }
        onDispose { Nav.pageBack = null }
    }

    fun findCard(num: String): Card? {
        val t = num.trim().uppercase().substringBefore('/')
        if (t.isEmpty()) return null
        val n = t.trimStart('0')
        return set.cards.firstOrNull { it.localId.uppercase().trimStart('0') == n || it.localId.uppercase() == t }
            ?: set.cards.firstOrNull { Regex("^([A-Z]*)0*(\\d+)$").find(it.localId.uppercase())?.let { m -> m.groupValues[1] + m.groupValues[2] } == t.replace(Regex("^([A-Z]*)0*"), "$1") }
    }
    val say = toast()
    fun submit() {
        val c = findCard(input) ?: run { if (input.isNotBlank()) say("No card ${input.trim()} in ${set.name}"); input = ""; return }
        val k = if (version == "reverse" && c.variants.any { it.key == "reverse" }) "reverse" else Binder.mainVariant(c)
        session.record(c, k, autoAdd)
        input = ""
    }

    if (summary) {
        SessionSummary(session, "Quick add · " + set.name, onMore = { summary = false }, onDone = { Nav.page = null })
        return
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        PageHeader("Quick add · " + set.name) { if (session.total > 0) summary = true else Nav.page = null }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(listOf("main" to "Normal / holo", "reverse" to "Reverse holo"), version) { version = it }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    val preview = findCard(input)
                    if (keyboard) {
                        if (input.isEmpty()) Text("Card number, e.g. 45", color = p.faint, fontSize = 18.sp)
                        BasicTextField(input, { input = it.take(10) }, singleLine = true, textStyle = TextStyle(color = p.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            cursorBrush = SolidColor(p.accent), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }), modifier = Modifier.fillMaxWidth().focusRequester(focus))
                    } else Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(input.ifEmpty { "Card number" }, color = if (input.isEmpty()) p.faint else p.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (preview != null) Text("  ${preview.name}", color = p.muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Btn(if (keyboard) "123" else "ABC") { keyboard = !keyboard }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Chip(if (autoAdd) "Adding to my collection ✓" else "Just checking", autoAdd) { autoAdd = !autoAdd } }
                if (session.total > 0) Text("${session.total} added · ${pounds(session.value)}", fontWeight = FontWeight.SemiBold, color = p.fg,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { summary = true }.padding(6.dp))
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.log.isEmpty()) item { Sub("Tap the card number and press ${if (autoAdd) "Add" else "Check"}. Green means you need it. Same card again? Type it again or tap +1.") }
            items(session.log, key = { it.id }) { e -> SessionRow(e, session) }
        }
        if (!keyboard) Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            NumPad(prefixes, onKey = { input = (input + it).take(10) }, onDelete = { input = input.dropLast(1) }, onEnter = { submit() },
                enterLabel = if (autoAdd) "Add" else "Check")
        }
    }
}
