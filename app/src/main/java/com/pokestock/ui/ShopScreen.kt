package com.pokestock.ui

import android.content.Context
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokestock.data.AppState
import com.pokestock.data.Binder
import com.pokestock.data.Card
import com.pokestock.data.CardSet
import com.pokestock.data.Labels
import com.pokestock.data.pounds

/**
 * For card shops and trading: punch in a number and see at once whether you need it, what it's worth,
 * and 90/80/70% of that for haggling. Works offline.
 */
@Composable
fun ShopScreen() {
    val p = P
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    @Suppress("UNUSED_VARIABLE") val v = AppState.version
    val cat = AppState.catalog
    // null = any set: find the card by its printed number ("45/185") and/or name, across every set.
    var setKey by remember { mutableStateOf(prefs.getString("shopSet", null)?.takeIf { cat.setByKey[it] != null }) }
    var picking by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf("EN") }
    var matches by remember { mutableStateOf<List<Card>?>(null) }
    var lastQuery by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var card by remember { mutableStateOf<Card?>(null) }
    var variant by remember { mutableStateOf<String?>(null) }
    var keyboard by remember { mutableStateOf(false) }
    val say = toast()
    val set = setKey?.let { cat.setByKey[it] }

    fun anySet() { setKey = null; picking = false; card = null; matches = null; prefs.edit().remove("shopSet").apply() }
    fun chooseSet(s: CardSet) {
        setKey = s.key; picking = false; card = null; matches = null
        prefs.edit().putString("shopSet", s.key).putString("shopRecent", (listOf(s.key) + (prefs.getString("shopRecent", "") ?: "").split(',').filter { it.isNotEmpty() && it != s.key }).take(6).joinToString(",")).apply()
    }
    fun submit() {
        var t = input.trim()
        // "SSP 50" picks the set by its code too.
        Regex("^([A-Za-z]{2,4})\\s+([A-Za-z]*\\d+)$").find(t)?.let { m ->
            cat.sets.firstOrNull { !it.custom && it.abbr.equals(m.groupValues[1], true) }?.let { chooseSet(it); t = m.groupValues[2] }
        }
        val s = setKey?.let { cat.setByKey[it] }
        if (s == null) {
            if (t.isEmpty()) return
            val found = findAnySet(lang, t)
            lastQuery = t; input = ""
            when (found.size) {
                0 -> { matches = null; card = null; say("Nothing matches \"$t\". Try the number with the total, like 45/185.") }
                1 -> { matches = null; card = found[0]; variant = Binder.mainVariant(found[0]) }
                else -> { matches = found; card = null }
            }
            return
        }
        val u = t.uppercase().substringBefore('/')
        val c = s.cards.firstOrNull { it.localId.uppercase().trimStart('0') == u.trimStart('0') || it.localId.uppercase() == u }
            ?: s.cards.firstOrNull { Regex("^([A-Z]*)0*(\\d+)$").find(it.localId.uppercase())?.let { m -> m.groupValues[1] + m.groupValues[2] } == u.replace(Regex("^([A-Z]*)0*"), "$1") }
        if (c == null) { if (t.isNotEmpty()) say("No card $t in ${s.name}"); input = ""; return }
        card = c; variant = Binder.mainVariant(c); input = ""
    }

    if (picking) { ShopSetPicker(prefs, onBack = { picking = false }, onAny = { anySet() }) { chooseSet(it) }; return }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        PageHeader("Shop mode") { Nav.page = null }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(12.dp))
                .clickable { picking = true }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (set != null && set.symbol.isNotEmpty()) {
                    coil.compose.AsyncImage(set.symbol, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.width(28.dp).heightIn(max = 28.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Label("Set")
                    Text(set?.name ?: "Any set", style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("Change", color = p.want, fontWeight = FontWeight.SemiBold)
            }
            if (set == null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Segmented(listOf("EN" to "English", "JA" to "Japanese"), lang) { lang = it; matches = null } }
                Btn("Scan") { Nav.openPage("scan", from = "shop") }
            }
            val c = card
            val m = matches
            if (c == null && m != null) {
                Sub("${m.size} cards match \"$lastQuery\". Tap the one in your hand.")
                m.take(60).forEach { x -> ShopMatchRow(x) { card = x; variant = Binder.mainVariant(x); matches = null } }
                if (m.size > 60) Sub("Showing the first 60. Add the name or the total to narrow it down, e.g. pikachu 45/185.")
            }
            else if (c == null) Sub(if (set == null)
                "Type the number printed at the bottom with its total, like 45/185. The total tells sets apart, so you don't need to know the symbol. " +
                "Or tap ABC and type the name, with or without the number (e.g. \"umbreon 95\"). Pick a set above if you know it."
                else "Type the card number and tap Check. In keyboard mode you can type the set code too, like \"SSP 50\".")
            else {
                if (lastQuery.isNotEmpty() && set == null) Text("‹ Back to matches", color = p.want, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { val f = findAnySet(lang, lastQuery); if (f.size > 1) { matches = f; card = null } }.padding(4.dp))
                ShopResult(c, variant ?: Binder.mainVariant(c), { variant = it }) { k -> AppState.store.addOne(c.key, k); say("Added to your collection") }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                    if (keyboard) {
                        if (input.isEmpty()) Text(if (set == null) "e.g. 45/185 or umbreon" else "e.g. 45 or SSP 50", color = p.faint, fontSize = 18.sp)
                        BasicTextField(input, { input = it.take(14) }, singleLine = true, textStyle = TextStyle(color = p.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                            cursorBrush = SolidColor(p.accent), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }), modifier = Modifier.fillMaxWidth())
                    } else Text(input.ifEmpty { "Card number" }, color = if (input.isEmpty()) p.faint else p.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Btn(if (keyboard) "123" else "ABC") { keyboard = !keyboard }
            }
            if (!keyboard) NumPad(set?.let { numberPrefixes(it.cards) } ?: listOf("/", "TG", "GG", "SV"), onKey = { input = (input + it).take(10) },
                onDelete = { input = input.dropLast(1) }, onEnter = { submit() }, enterLabel = "Check")
        }
    }
}

@Composable
private fun ShopResult(c: Card, variant: String, onVariant: (String) -> Unit, onBought: (String) -> Unit) {
    val p = P
    val have = AppState.store.total(c.key)
    val haveThis = AppState.store.qty(c.key, variant) + AppState.store.slabsOf(c.key, variant).size
    val slotEmpty = c.dex > 0 && AppState.store.binderCard(c.dex) == null
    val wanted = AppState.store.isWish(c.key) || (c.dex > 0 && AppState.store.isSlotWant(c.dex, c.key))
    Row(verticalAlignment = Alignment.Top) {
        CardArt(c, Modifier.weight(0.4f).clickable { Nav.openCard(c.key, variant) }, big = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(0.6f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (haveThis == 0) "NEED IT" else "Have ×$haveThis", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = if (haveThis == 0) p.up else p.muted)
            Text(c.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = p.fg)
            Sub("${c.set.name} · ${c.numberLabel} · ${Labels.rarityShort(c.rarity)}")
            if (have > haveThis) Sub("You own ${have - haveThis} in other versions")
            if (wanted) Badge("On your Wanted list", "want")
            if (slotEmpty) Badge("Fills an empty Pokédex slot", "up")
        }
    }
    val versions = AppState.versionsOf(c)
    if (versions.size > 1) ChipRow {
        versions.forEach { k -> Chip(Labels.variantLabel(c, k), k == variant) { onVariant(k) } }
    }
    val price = AppState.unitPrice(c, variant)
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) { Label("Market price"); Text(pounds(price), style = Type.price, color = p.fg) }
            }
            if (price != null && price > 0) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(90, 80, 70).forEach { pct ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(p.surface2).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$pct%", color = p.muted, fontSize = 12.sp)
                        Text(pounds(Math.round(price * pct / 100.0).toInt()), color = p.fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn("I got it, add it", Modifier.weight(1f), primary = true) { onBought(variant) }
        Btn("Open card", Modifier.weight(1f)) { Nav.openCard(c.key, variant) }
    }
}

@Composable
private fun ShopSetPicker(prefs: android.content.SharedPreferences, onBack: () -> Unit, onAny: () -> Unit, onPick: (CardSet) -> Unit) {
    val p = P
    var q by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("EN") }
    val cat = AppState.catalog
    val recent = remember { (prefs.getString("shopRecent", "") ?: "").split(',').mapNotNull { cat.setByKey[it] } }
    val sets = cat.sets.filter { !it.custom && it.lang == lang && (q.isBlank() || it.name.contains(q, true) || it.nameEn.contains(q, true) || it.abbr.equals(q.trim(), true)) }
        .sortedByDescending { it.date }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        PageHeader("Choose a set") { onBack() }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(listOf("EN" to "English", "JA" to "Japanese"), lang) { lang = it }
            SearchField(q, "Set name or code (e.g. SSP)") { q = it }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.surface).clickable { onAny() }.padding(12.dp)) {
                    Column { Text("Any set", color = p.fg, style = Type.strong); Sub("Don't know the set? Find the card by its number and total (45/185) or its name.") }
                }
                Gap(6.dp)
                Sub("Each set shows its symbol, so you can match the little icon on the card.")
            }
            if (q.isBlank() && recent.isNotEmpty()) {
                item { Label("Recent") }
                items(recent, key = { "r" + it.key }) { s -> SetPickRow(s) { onPick(s) } }
                item { Gap(8.dp); Label("All sets") }
            }
            items(sets, key = { it.key }) { s -> SetPickRow(s) { onPick(s) } }
        }
    }
}

@Composable
private fun SetPickRow(s: CardSet, onClick: () -> Unit) {
    val p = P
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
            if (s.symbol.isNotEmpty()) coil.compose.AsyncImage(s.symbol, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.width(26.dp).heightIn(max = 26.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, color = p.fg, style = Type.strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Sub(listOf(s.series, s.abbr).filter { it.isNotEmpty() }.joinToString(" · "), maxLines = 1)
        }
    }
}

/**
 * Finds a card without knowing its set. "45/185" matches card 45 in sets with 185 cards (the printed total),
 * "TG05/TG30" works for trainer galleries, and any words narrow it by name ("umbreon 95").
 */
fun findAnySet(lang: String, query: String): List<Card> {
    val words = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val numRe = Regex("^#?([a-z]{0,4})0*(\\d+)(?:/([a-z]{0,4})0*(\\d+))?$")
    var num: MatchResult? = null
    val names = ArrayList<String>()
    for (w in words) { val m = numRe.matchEntire(w); if (m != null && num == null) num = m else names.add(w) }
    val out = ArrayList<Card>()
    for (s in AppState.catalog.sets) {
        if (s.custom || s.lang != lang) continue
        val m = num
        val total = m?.groupValues?.get(4)?.takeIf { it.isNotEmpty() }?.toInt()
        val totalPrefix = m?.groupValues?.get(3) ?: ""
        if (total != null && totalPrefix.isEmpty() && s.total != total && s.cards.size != total) continue
        for (c in s.cards) {
            if (m != null) {
                val lm = Regex("^([a-z]*)0*(\\d+)").find(c.localId.lowercase()) ?: continue
                if (lm.groupValues[1] != m.groupValues[1] || lm.groupValues[2] != m.groupValues[2]) continue
            }
            if (names.any { !c.search.contains(it) }) continue
            out.add(c)
        }
    }
    return out.sortedByDescending { it.set.date }
}

@Composable
private fun ShopMatchRow(c: Card, onClick: () -> Unit) {
    val p = P
    val have = AppState.store.total(c.key)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CardArt(c, Modifier.width(54.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${c.localId} ${c.name}", style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (c.set.symbol.isNotEmpty()) {
                    coil.compose.AsyncImage(c.set.symbol, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.width(16.dp).heightIn(max = 16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Sub(c.set.name + " · " + c.set.date.take(4), maxLines = 1)
            }
            Text(if (have == 0) "NEED IT" else "Have ×$have", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = if (have == 0) p.up else p.muted)
        }
        PriceText(AppState.displayPrice(c))
    }
}
