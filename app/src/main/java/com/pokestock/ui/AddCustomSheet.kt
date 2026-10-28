package com.pokestock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokestock.data.AppState
import com.pokestock.data.CustomCard
import com.pokestock.data.Species

@Composable
private fun Field(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    val p = P
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Sub(label)
        Box(Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(10.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = p.faint, fontSize = 15.sp)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = p.fg, fontSize = 16.sp), cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** For cards the database doesn't have yet, e.g. prerelease cards from a set that isn't out. */
@Composable
fun AddCustomSheet() {
    val say = toast()
    var lang by remember { mutableStateOf("EN") }
    var set by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var rarity by remember { mutableStateOf("") }
    val versions = remember { mutableStateListOf("normal") }
    SheetFrame("Add a card", { Nav.addCustom = false }) {
        Sub("For cards that aren't in the list yet, like prerelease cards. They count in your portfolio and sets; set a price on the card if you want one.")
        Segmented(listOf("EN" to "English", "JA" to "Japanese", "ZH" to "Chinese"), lang) { lang = it }
        Field("Set", set, "e.g. Delta Reign") { set = it }
        Field("Card number", number, "e.g. 084/103") { number = it }
        Field("Name", name, "e.g. Mega Rayquaza ex") { name = it }
        Field("Rarity", rarity, "e.g. Double Rare") { rarity = it }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Sub("Versions you own")
            ChipRow {
                listOf("normal" to "Normal", "holo" to "Holo", "reverse" to "Reverse holo").forEach { (k, l) ->
                    Chip(l, k in versions) { if (k in versions) { if (versions.size > 1) versions.remove(k) } else versions.add(k) }
                }
            }
        }
        Btn("Add card", Modifier.fillMaxWidth(), primary = true) {
            if (name.isBlank() || set.isBlank()) { say("Add at least the set and the name"); return@Btn }
            val dex = (1..Species.count).firstOrNull { n -> name.contains(Species.name(n), ignoreCase = true) } ?: 0
            val id = "c" + System.currentTimeMillis().toString(36)
            val cc = CustomCard(id, lang, set.trim(), number.trim(), name.trim(), rarity.trim(), dex, versions.toList(), System.currentTimeMillis())
            AppState.store.addCustom(cc)
            AppState.catalog.addCustom(cc)
            versions.forEach { AppState.store.setQty(cc.key, it, 1) }
            Nav.addCustom = false
            Nav.openCard(cc.key)
            say("Card added")
        }
    }
}
