package com.pokestock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokestock.data.AppState
import com.pokestock.data.Card
import com.pokestock.data.CardSet
import com.pokestock.data.Labels
import com.pokestock.data.pounds
import com.pokestock.data.poundsRound
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class SetStats(val have: Int, val n: Int, val vHave: Int, val vTotal: Int, val value: Long)

fun statsOf(s: CardSet): SetStats {
    var have = 0; var vh = 0; var vt = 0; var value = 0L
    for (c in s.cards) {
        val own = AppState.store.ownedVariants(c.key)
        val slabs = AppState.store.slabsOf(c.key)
        val ks = AppState.versionsOf(c)
        vt += ks.size
        var any = false
        for (k in ks) {
            val q = (own[k] ?: 0)
            val sq = slabs.count { it.variant == k }
            if (q + sq > 0) { vh++; any = true }
            if (q > 0) value += (AppState.unitPrice(c, k) ?: 0).toLong() * q
        }
        for (sl in slabs) if (sl.price > 0) value += sl.price
        if (any) have++
    }
    return SetStats(have, s.cards.size, vh, vt, value)
}

@Composable
fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val p = P
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⌕", color = p.muted, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = p.faint, fontSize = 16.sp, maxLines = 1)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = p.fg, fontSize = 16.sp), cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) Text("✕", color = p.muted, modifier = Modifier.clickable { onChange("") }.padding(8.dp))
    }
}

@Composable
fun CollectionScreen() {
    when (val s = Nav.openSet) {
        null -> SetList()
        "slabs" -> SlabList()
        else -> AppState.catalog.setByKey[s]?.let { SetView(it) } ?: run { Nav.openSet = null }
    }
}

@Composable
private fun SetList() {
    val p = P
    val v = AppState.version
    val cat = AppState.catalog
    val prices = AppState.prices
    val lang = Nav.lang
    val q = Nav.colQuery.trim()
    val sets = remember(cat, lang) { cat.sets.filter { it.lang == lang } }
    val stats = remember(v, prices, sets) { sets.associate { it.key to statsOf(it) } }
    val hits = remember(cat, lang, q) { if (q.length >= 2) cat.search(lang, q) else emptyList() }
    val shown = remember(hits, v, Nav.searchOwned, Nav.ownedFirst) {
        hits.filter { c -> val o = AppState.store.total(c.key) > 0; when (Nav.searchOwned) { "owned" -> o; "missing" -> !o; else -> true } }
            .sortedWith(compareByDescending<Card> { Nav.ownedFirst && AppState.store.total(it.key) > 0 }.thenByDescending { AppState.displayPrice(it) ?: -1 })
            .take(200)
    }
    val slabs = remember(v) { AppState.store.allSlabs() }

    LazyColumn(state = Nav.setList, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            Text("Collection", style = Type.display, color = p.fg)
            Gap(10.dp)
            Segmented(listOf("EN" to "English", "JA" to "Japanese", "ZH" to "Chinese"), lang) { Nav.lang = it }
            Gap(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SearchField(Nav.colQuery, "Search every ${if (lang == "EN") "English" else if (lang == "JA") "Japanese" else "Chinese"} card or set") { Nav.colQuery = it } }
                Spacer(Modifier.width(8.dp))
                Btn("Scan") { Nav.page = "scan" }
            }
            if (cat.sets.isEmpty()) { Gap(); Panel { Text("The card list downloads the first time you're online. Open the Sync tab and tap Update.", color = p.muted) } }
        }
        if (q.length >= 2) {
            item {
                SectionHead("Cards", "${shown.size}${if (shown.size >= 200) "+" else ""} shown")
                ChipRow { listOf("all" to "All", "owned" to "Owned", "missing" to "Not owned").forEach { (k, l) -> Chip(l, Nav.searchOwned == k) { Nav.searchOwned = k } } }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(Nav.ownedFirst, { Nav.ownedFirst = it }, colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent))
                    Spacer(Modifier.width(8.dp))
                    Text("Show cards I own first", color = p.muted, fontSize = 13.sp)
                }
                Sub("Tip: add a card number, e.g. \"pikachu 58\" or \"058/102\".")
            }
            items(shown, key = { "h" + it.key }) { CardRow(it) }
        }
        if (q.isEmpty()) item {
            SectionHead("Graded")
            SetRowBox("Graded slabs", "${slabs.size} slabs · " + pounds(slabs.sumOf { maxOf(0, it.price).toLong() }), null) { Nav.openSet = "slabs" }
        }
        val binderSets = if (q.isEmpty()) sets.filter { AppState.store.binderMode(it.key).isNotEmpty() }.sortedByDescending { it.date } else emptyList()
        if (binderSets.isNotEmpty()) {
            item { SectionHead("Your set binders") }
            items(binderSets, key = { "b" + it.key }) { s ->
                val mode = AppState.store.binderMode(s.key)
                val pk = remember(v, s.key, mode) { com.pokestock.data.Binder.pockets(s, mode) }
                val have = pk.count { it.owned }
                SetRowBox(s.name, "${com.pokestock.data.Binder.modeName(mode)} · $have/${pk.size} pockets", if (pk.isEmpty()) null else have.toFloat() / pk.size, s.logo.ifEmpty { s.symbol }) {
                    Nav.page = "binder:" + s.key
                }
            }
        }
        if (lang == "ZH") item {
            Gap(); Panel { Text("Chinese set lists aren't in the card database yet, so this shows the Chinese cards you own. Full lists will follow when a source is found.", color = p.muted, fontSize = 14.sp) }
        }
        val visible = sets.filter { q.isEmpty() || it.name.contains(q, true) || it.nameEn.contains(q, true) || it.abbr.equals(q, true) }
        val real = visible.filter { !it.custom }.sortedByDescending { it.date }
        val groups = LinkedHashMap<String, MutableList<CardSet>>()
        real.forEach { groups.getOrPut(it.series) { ArrayList() }.add(it) }
        groups.forEach { (series, list) ->
            item(key = "g$series") { SectionHead(series) }
            items(list, key = { it.key }) { s -> SetRow(s, stats[s.key]) }
        }
        val custom = visible.filter { it.custom && it.cards.isNotEmpty() }.sortedBy { it.name }
        if (custom.isNotEmpty()) {
            item { SectionHead("Your other cards"); Sub("Cards not in the database yet, kept as you entered or imported them.") }
            items(custom, key = { it.key }) { s -> SetRow(s, stats[s.key]) }
        }
    }
}

@Composable
private fun SetRow(s: CardSet, st: SetStats?) {
    val d = s.date.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)).format(DateTimeFormatter.ofPattern("MMM yyyy")) }.getOrNull() }
    val (a, b, unit) = if (st == null) Triple(0, 0, "cards") else if (Nav.master) Triple(st.vHave, st.vTotal, "versions") else Triple(st.have, st.n, "cards")
    val sub = buildString {
        if (d != null) append("$d · ")
        if (s.custom) append("${s.cards.size} owned") else append("$a/$b $unit")
        if ((st?.value ?: 0) > 0) append(" · " + pounds(st!!.value))
    }
    SetRowBox(s.name, sub, if (s.custom || b == 0) null else a.toFloat() / b, s.logo.ifEmpty { s.symbol }) { Nav.openSet = s.key; Nav.setFilter = "all"; Nav.setGroup = "all" }
}

@Composable
private fun SetRowBox(title: String, sub: String, progress: Float?, logo: String? = null, onClick: () -> Unit) {
    val p = P
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (logo != null) {
                Box(Modifier.width(72.dp).height(36.dp), contentAlignment = Alignment.Center) {
                    if (logo.isNotEmpty()) coil.compose.AsyncImage(logo, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(36.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Sub(sub)
            }
            Text("›", color = p.faint, fontSize = 22.sp)
        }
        if (progress != null) { Gap(6.dp); ProgressBar(progress, height = 4.dp) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
}

@Composable
private fun BackLine(text: String, onBack: () -> Unit) {
    val p = P
    Row(Modifier.clickable(onClick = onBack).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", fontSize = 26.sp, color = p.fg)
        Spacer(Modifier.width(10.dp))
        Sub(text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetView(s: CardSet) {
    val p = P
    val v = AppState.version
    val prices = AppState.prices
    val st = remember(v, prices, s) { statsOf(s) }
    val list = remember(v, prices, s, Nav.setFilter, Nav.setGroup, Nav.setSort, Nav.master) {
        var l = s.cards.filter { c ->
            if (Nav.setGroup != "all" && Labels.group(c.rarity) != Nav.setGroup) return@filter false
            val ks = AppState.versionsOf(c)
            val ownedK = ks.count { k -> AppState.store.qty(c.key, k) + AppState.store.slabsOf(c.key, k).size > 0 }
            when (Nav.setFilter) {
                "owned" -> ownedK > 0
                "missing" -> if (Nav.master) ownedK < ks.size else ownedK == 0
                else -> true
            }
        }
        if (Nav.setSort == "price") l = l.sortedByDescending { AppState.bestPrice(it) ?: -1 }
        l
    }
    val (a, b, unit) = if (Nav.master) Triple(st.vHave, st.vTotal, "versions") else Triple(st.have, st.n, "cards")
    LazyColumn(state = Nav.setViewState(s.key), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            BackLine(if (s.custom) "Your other cards" else if (s.lang == "JA") "Japanese" else if (s.lang == "ZH") "Chinese" else "English") { Nav.openSet = null }
            val logo = s.logo.ifEmpty { s.symbol }
            if (logo.isNotEmpty()) { coil.compose.AsyncImage(logo, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.height(64.dp).fillMaxWidth(0.6f)); Gap(8.dp) }
            Text(s.name, style = Type.display, color = p.fg)
            if (s.lang == "JA" && s.nameEn != s.name) Sub(s.nameEn)
            val date = s.date.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy")) }.getOrNull() }
            Sub(listOfNotNull(date?.let { "Released $it" }, "${s.cards.size} cards" + if (s.total > 0 && s.total < s.cards.size) " (${s.total} + secret rares)" else "").joinToString(" · "))
            Gap(10.dp)
            val ctx = androidx.compose.ui.platform.LocalContext.current
            ChipRow {
                Chip("Binder pages", false) { if (AppState.store.binderMode(s.key).isEmpty()) AppState.store.setBinderMode(s.key, "base"); Nav.page = "binder:" + s.key }
                Chip("Quick add", false) { Nav.page = "quick:" + s.key }
                Chip("Share missing", false) {
                    val mode = AppState.store.binderMode(s.key).ifEmpty { if (Nav.master) "master" else "base" }
                    com.pokestock.data.ShareText.send(ctx, com.pokestock.data.ShareText.missing(s, mode), "Missing from ${s.name}")
                }
            }
            Gap()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("$a/$b", "$unit owned", Modifier.weight(1f))
                Stat("${b - a}", "missing", Modifier.weight(1f))
                Stat(poundsRound(st.value), "owned value", Modifier.weight(1f))
            }
            Gap(10.dp)
            ProgressBar(if (b > 0) a.toFloat() / b else 0f)
            Gap(10.dp)
            ChipRow {
                listOf("all" to "All", "owned" to "Owned", "missing" to "Missing").forEach { (k, l) -> Chip(l, Nav.setFilter == k) { Nav.setFilter = k } }
                Spacer(Modifier.width(6.dp))
                listOf("all" to "Any rarity", "cu" to "C / U", "other" to "Rare & holo", "ultra" to "Ultra", "art" to "IR / AR", "special" to "SIR / SAR")
                    .forEach { (k, l) -> Chip(l, Nav.setGroup == k) { Nav.setGroup = k } }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Switch(Nav.master, { Nav.master = it }, colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent))
                Spacer(Modifier.width(8.dp))
                Text("Count every version", color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Chip(if (Nav.setSort == "num") "Sort: number" else "Sort: price", false) { Nav.setSort = if (Nav.setSort == "num") "price" else "num" }
            }
            Sub("${list.size} shown")
            Gap(4.dp)
            if (list.isEmpty()) EmptyState(if (Nav.setFilter == "missing") "Nothing missing here. Set complete!" else "No cards match.")
        }
        items(list, key = { it.key }) { c -> SetCardRow(c) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetCardRow(c: Card) {
    @Suppress("UNUSED_VARIABLE") val redraw = AppState.version
    val p = P
    val ks = AppState.versionsOf(c)
    val slabs = AppState.store.slabsOf(c.key)
    val total = AppState.store.total(c.key)
    val wish = AppState.store.isWish(c.key)
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { Nav.openCard(c.key) }.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CardArt(c, Modifier.width(44.dp), dim = total == 0)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.localId, style = Type.mono, color = p.faint)
                    Spacer(Modifier.width(6.dp))
                    Text(c.name, style = Type.strong, color = if (total > 0) p.fg else p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val rarityShown = ks.any { Labels.variantShort(c, it) == Labels.rarityShort(c.rarity) }
                    if (!rarityShown) RarityTag(c.rarity)
                    ks.forEach { k ->
                        val q = AppState.store.qty(c.key, k)
                        VChip(Labels.variantShort(c, k) + if (q > 1) " ×$q" else "", q > 0)
                    }
                    slabs.forEach { VChip(Labels.gradeShort(it.company, it.grade), owned = true, slab = true) }
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                PriceText(AppState.displayPrice(c))
                if (total == 0 && (wish || (c.dex > 0 && AppState.store.isSlotWant(c.dex, c.key)))) { Gap(4.dp); Badge("Wanted", "want") }
            }
        }
        StarMark(wish) { AppState.store.toggleWish(c.key) }
    }
}

@Composable
private fun SlabList() {
    val p = P
    val v = AppState.version
    val slabs = remember(v) { AppState.store.allSlabs().sortedWith(compareBy<com.pokestock.data.Slab> { it.company }.thenByDescending { it.grade }.thenByDescending { it.price }) }
    val value = slabs.sumOf { maxOf(0, it.price).toLong() }
    val groups = slabs.groupBy { Labels.gradeName(it.company, it.grade) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        item {
            BackLine("Collection") { Nav.openSet = null }
            Text("Graded slabs", style = Type.display, color = p.fg)
            Gap()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("${slabs.size}", "slabs", Modifier.weight(1f))
                Stat(poundsRound(value), "value", Modifier.weight(1f))
                Stat("${slabs.count { it.price > 0 }}/${slabs.size}", "priced", Modifier.weight(1f))
            }
            Gap(8.dp)
            Sub("To grade a card you own, open it and tap Grade a copy.")
            if (slabs.isEmpty()) EmptyState("No slabs yet.")
        }
        groups.forEach { (g, list) ->
            item(key = "g$g") { SectionHead(g) }
            items(list, key = { it.id }) { SlabRow(it) }
        }
    }
}
