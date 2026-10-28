package com.pokestock.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokestock.data.AppState
import com.pokestock.data.Card
import com.pokestock.data.Labels
import com.pokestock.data.Slab
import com.pokestock.data.pounds
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun ebayUrl(card: Card, variant: String, grade: String?): String {
    val name = card.name.replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace(Regex("\\s+"), " ").trim()
    val parts = mutableListOf(name)
    if (card.localId.all { it.isDigit() } && card.set.total > 0) parts.add(card.numberLabel) else if (!card.custom) parts.add(card.localId)
    else parts.add(card.localId.substringBefore('/'))
    Labels.ebayWords(variant).takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    when (card.set.lang) { "JA" -> parts.add("japanese"); "ZH" -> parts.add("chinese") }
    if (grade != null) parts.add(grade) else parts.add("-psa -cgc -bgs -ace -graded -slab")
    if (card.set.lang == "EN") parts.add("-japanese -jpn -chinese -korean")
    val q = Uri.encode(parts.joinToString(" "))
    return "https://www.ebay.co.uk/sch/i.html?_nkw=$q&_sacat=183454&LH_Sold=1&LH_Complete=1&LH_PrefLoc=1&_sop=13&Graded=" + if (grade != null) "Yes" else "No"
}

fun parsePounds(s: String): Int? = s.replace("£", "").replace(",", "").trim().toDoubleOrNull()?.let { Math.round(it * 100).toInt() }

@Composable
fun MoneyField(value: String, placeholder: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = P
    Box(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(p.bg).border(1.dp, p.line, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, color = p.faint, fontSize = 15.sp)
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = p.fg, fontSize = 16.sp), cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SheetFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val p = P
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Text("‹", fontSize = 30.sp, color = p.fg) }
            Text(title, style = Type.title, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
        }
    }
}

@Composable
fun CardSheet(key: String) {
    val card = AppState.catalog.byKey[key] ?: run { Nav.card = null; return }
    val p = P
    val ctx = LocalContext.current
    val say = toast()
    @Suppress("UNUSED_VARIABLE") val v = AppState.version
    val ks = AppState.versionsOf(card)
    val k = Nav.cardVariant?.takeIf { it in ks }
        ?: ks.firstOrNull { AppState.store.qty(card.key, it) > 0 }
        ?: ks.firstOrNull { AppState.unitPrice(card, it) != null } ?: ks.first()
    val price = AppState.unitPrice(card, k)
    val own = AppState.store.override(card.key, k)
    val raw = AppState.store.qty(card.key, k)
    val slabs = AppState.store.slabsOf(card.key)
    val online = AppState.isOnline(ctx)
    var grading by remember(key, k) { mutableStateOf(false) }
    var editPrice by remember(key, k) { mutableStateOf(false) }

    SheetFrame(card.name, { Nav.card = null }) {
        Row(verticalAlignment = Alignment.Bottom) {
            CardArt(card, Modifier.weight(0.42f), big = true)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(Labels.variantLabel(card, k))
                Text(pounds(price), style = Type.price, color = p.fg)
                Badge(if (own != null) "Your price" else if (price != null) "Cardmarket · ${AppState.prices.date.let { d -> runCatching { LocalDate.parse(d).format(DateTimeFormatter.ofPattern("d MMM")) }.getOrDefault("") }}" else "No price yet", "none")
                Sub("${card.set.name} · ${card.numberLabel}")
                Sub(listOf(card.rarity.ifEmpty { "—" }, when (card.set.lang) { "JA" -> "Japanese"; "ZH" -> "Chinese (Simplified)"; else -> "English" }).joinToString(" · "))
                if (card.nativeName.isNotEmpty()) Sub(card.nativeName)
            }
        }

        Column {
            Label("Versions")
            Gap(6.dp)
            ks.forEach { x ->
                val q = AppState.store.qty(card.key, x); val sq = AppState.store.slabsOf(card.key, x).size; val pp = AppState.unitPrice(card, x)
                val sel = x == k
                Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) p.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .border(1.dp, if (sel) p.line else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { Nav.cardVariant = x }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(44.dp)) { VChip(Labels.variantShort(card, x), q + sq > 0) }
                    Text(Labels.variantLabel(card, x), color = p.fg, modifier = Modifier.weight(1f), maxLines = 2)
                    Sub(if (q + sq > 0) "Own ${q + sq}" + (if (sq > 0) " · $sq graded" else "") else "Missing")
                    Spacer(Modifier.width(10.dp))
                    Text(pounds(pp), color = p.fg, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
        }

        PriceHistory(card, k)

        val (avg30, low) = AppState.prices.extra(card, k)
        if (avg30 != null || low != null) Sub(listOfNotNull(avg30?.let { "30-day average " + pounds(it) }, low?.let { "lowest listing " + pounds(it) }).joinToString(" · "))

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Raw copies · ${Labels.variantLabel(card, k)}", fontWeight = FontWeight.SemiBold, color = p.fg)
                    Sub(if (raw > 0) "$raw" + (price?.let { " × " + pounds(it) + " = " + pounds(it.toLong() * raw) } ?: "") else "None yet")
                }
                Stepper(raw, { AppState.store.setQty(card.key, k, raw - 1) }, { AppState.store.setQty(card.key, k, raw + 1) })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(if (online) "eBay sold" else "eBay needs signal", Modifier.weight(1f), enabled = online) {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ebayUrl(card, k, null))))
            }
            val wish = AppState.store.isWish(card.key)
            Btn(if (wish) "Wanted ✓" else "Add to wanted", Modifier.weight(1f), primary = !wish) {
                val on = AppState.store.toggleWish(card.key); say(if (on) "Added to wanted" else "Removed from wanted")
            }
        }
        Sub("eBay opens UK sold listings for this exact version, ungraded only.")

        if (card.dex > 0) {
            val inBinder = AppState.store.binderCard(card.dex) == card.key
            val filled = AppState.store.binderCard(card.dex) != null
            val dexNo = "#" + card.dex.toString().padStart(3, '0')
            if (AppState.store.total(card.key) > 0) Btn(if (inBinder) "In Pokédex binder $dexNo ✓" else "Put in Pokédex binder $dexNo", Modifier.fillMaxWidth()) {
                AppState.store.setBinder(card.dex, card.key); say("${card.name} is now in slot $dexNo")
            } else {
                val sw = AppState.store.isSlotWant(card.dex, card.key)
                Btn(if (sw) "Wanted for Pokédex $dexNo ✓" else if (filled) "Upgrade Pokédex $dexNo to this" else "Want for Pokédex $dexNo", Modifier.fillMaxWidth()) {
                    AppState.store.toggleSlotWant(card.dex, card.key)
                }
            }
        }

        // Own price
        if (!editPrice) Text(if (own != null) "Your price is set · Change or remove" else "Set your own price for this version",
            color = p.want, fontSize = 14.sp, modifier = Modifier.clickable { editPrice = true }.padding(vertical = 4.dp))
        else {
            var txt by remember(key, k) { mutableStateOf(own?.let { String.format("%.2f", it / 100.0) } ?: "") }
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sub("Your price in £ replaces the Cardmarket price for this version everywhere in the app.")
                    MoneyField(txt, "e.g. 140.00", { txt = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Use Cardmarket", Modifier.weight(1f)) { AppState.store.setOverride(card.key, k, null); editPrice = false }
                        Btn("Save", Modifier.weight(1f), primary = true) {
                            val pp = parsePounds(txt); if (pp == null) say("Type a price like 140.00") else { AppState.store.setOverride(card.key, k, pp); editPrice = false }
                        }
                    }
                }
            }
        }

        // Slabs
        if (slabs.isNotEmpty() || raw > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row { Label("Graded", Modifier.weight(1f)); Sub("${slabs.size} slab" + if (slabs.size == 1) "" else "s") }
                slabs.forEach { SlabBox(card, it, online) }
                if (grading) GradeForm(card, k) { grading = false }
                else if (raw > 0) Btn("Grade a copy · " + Labels.variantLabel(card, k), Modifier.fillMaxWidth()) { grading = true }
            }
        }

        if (card.dex > 0) {
            val others = AppState.catalog.bySpecies[card.dex].orEmpty().filter { it.key != card.key && AppState.store.total(it.key) > 0 }
            if (others.isNotEmpty()) Column {
                Label("Your other ${com.pokestock.data.Species.name(card.dex)} cards")
                others.take(10).forEach { CardRow(it) }
            }
        }
    }
}

@Composable
fun Stepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = P
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(p.surface2).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)).background(p.bg).clickable(enabled = value > 0, onClick = onMinus), contentAlignment = Alignment.Center) { Text("−", fontSize = 22.sp, color = p.fg) }
        Text("$value", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = p.fg, modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(9.dp)).background(p.bg).clickable(onClick = onPlus), contentAlignment = Alignment.Center) { Text("+", fontSize = 22.sp, color = p.fg) }
    }
}

@Composable
private fun PriceHistory(card: Card, k: String) {
    val p = P
    val hist = AppState.history
    val points = remember(hist, card.key, k) {
        hist.series(card, k).mapNotNull { (d, v) -> runCatching { LocalDate.parse(d) to v / 100.0 }.getOrNull() }
    }
    val days = mapOf("1M" to 30L, "3M" to 91L, "6M" to 182L, "1Y" to 365L)[Nav.cardRange] ?: 365L
    val cut = points.filter { !it.first.isBefore(LocalDate.now().minusDays(days)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipRow {
            listOf("1M", "3M", "6M", "1Y").forEach { r -> Chip(r, Nav.cardRange == r) { Nav.cardRange = r } }
            if (cut.size >= 2) {
                val ch = (cut.last().second - cut.first().second) / cut.first().second * 100
                Text((if (ch >= 0) "▲ " else "▼ ") + String.format("%.1f%%", Math.abs(ch)), color = if (ch >= 0) p.up else p.down,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterVertically).padding(start = 6.dp))
            }
        }
        if (cut.size >= 2) LineChart(cut, { pounds(Math.round(it * 100).toInt()) })
        else Panel { Text("Price history builds up day by day from when the price service started. The graph appears once there are a few days of prices.", color = p.muted, fontSize = 14.sp) }
    }
}

@Composable
private fun SlabBox(card: Card, s: Slab, online: Boolean) {
    val p = P
    val ctx = LocalContext.current
    val say = toast()
    var txt by remember(s.id) { mutableStateOf(if (s.price > 0) String.format("%.2f", s.price / 100.0) else "") }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Labels.gradeName(s.company, s.grade), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = p.fg)
                    Sub(Labels.variantLabel(card, s.variant) + (if (s.added.isNotEmpty()) " · added " + s.added else ""))
                }
                Badge(Labels.gradeShort(s.company, s.grade), "slab")
            }
            Sub("Slab price in £")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MoneyField(txt, "Not set", { txt = it }, Modifier.weight(1f))
                Btn("Save") { val pp = parsePounds(txt); if (pp == null) say("Type a price like 45.00") else { AppState.store.setSlabPrice(s.id, pp); say("Slab price saved") } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(if (online) "eBay sold ${Labels.gradeShort(s.company, s.grade)}" else "eBay needs signal", Modifier.weight(1f), enabled = online) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ebayUrl(card, s.variant, Labels.gradeShort(s.company, s.grade)))))
                }
                Btn("Back to raw", Modifier.weight(1f)) { AppState.store.crack(s.id); say("Moved back to raw copies") }
            }
        }
    }
}

@Composable
private fun GradeForm(card: Card, k: String, done: () -> Unit) {
    val p = P
    val say = toast()
    var company by remember { mutableStateOf("ACE") }
    var grade by remember { mutableStateOf(10) }
    var txt by remember { mutableStateOf("") }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("Grade one ${Labels.variantLabel(card, k)} copy")
            Sub("Company")
            ChipRow { listOf("ACE", "PSA", "CGC", "BGS", "TAG").forEach { c -> Chip(c, company == c) { company = c } } }
            Sub("Grade")
            ChipRow { (10 downTo 1).forEach { g -> Chip("$g ${Labels.GRADE_NAMES[g]}", grade == g) { grade = g } } }
            Sub("Slab price in £ (optional, check eBay sold for this grade)")
            MoneyField(txt, "e.g. 45.00", { txt = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Cancel", Modifier.weight(1f), onClick = done)
                Btn("Save slab", Modifier.weight(1f), primary = true) {
                    AppState.store.grade(card.key, k, company, grade.toDouble(), parsePounds(txt) ?: -1)
                    say("${card.name} graded $company $grade"); done()
                }
            }
        }
    }
}
