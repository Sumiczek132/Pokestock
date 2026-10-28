package com.pokestock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokestock.data.AppState
import com.pokestock.data.Card
import com.pokestock.data.Labels
import com.pokestock.data.Slab
import com.pokestock.data.pounds

/** One row in a list of cards: picture, name, set and number, price, owned/wanted badge. */
@Composable
fun CardRow(card: Card, onClick: () -> Unit = { Nav.openCard(card.key) }) {
    @Suppress("UNUSED_VARIABLE") val redraw = AppState.version
    val p = P
    val owned = AppState.store.total(card.key)
    val wanted = AppState.store.isWish(card.key) || (card.dex > 0 && AppState.store.isSlotWant(card.dex, card.key))
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CardArt(card, Modifier.width(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(card.name, style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RarityTag(card.rarity)
                Sub(card.set.name, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Text(card.numberLabel, style = Type.mono, color = p.muted)
                if (card.set.lang != "EN") Text(card.set.lang, style = Type.mono, color = p.faint)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PriceText(AppState.displayPrice(card))
            when {
                owned > 0 -> Badge("×$owned")
                wanted -> Badge("Wanted", "want")
                else -> Badge("Missing", "none")
            }
        }
    }
}

/** A copy you own (one version of a card), as shown in the portfolio. */
@Composable
fun HoldingRow(card: Card, variant: String, qty: Int, price: Int?, tag: String) {
    @Suppress("UNUSED_VARIABLE") val redraw = AppState.version
    val p = P
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp)).clickable { Nav.openCard(card.key, variant) }.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CardArt(card, Modifier.width(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(card.name, style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Sub(card.set.name, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Text(card.numberLabel, style = Type.mono, color = p.muted)
            }
            Text(tag, style = Type.mono, color = p.faint, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PriceText(price)
            Badge("×$qty")
        }
    }
}

@Composable
fun SlabRow(slab: Slab) {
    @Suppress("UNUSED_VARIABLE") val redraw = AppState.version
    val card = AppState.catalog.byKey[slab.card] ?: return
    val p = P
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp)).clickable { Nav.openCard(card.key, slab.variant) }.padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        CardArt(card, Modifier.width(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(card.name, style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Sub(card.set.name, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Text(card.numberLabel, style = Type.mono, color = p.muted)
            }
            Text(Labels.variantLabel(card, slab.variant), style = Type.mono, color = p.faint)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (slab.price > 0) Text(pounds(slab.price), style = Type.strong, color = p.fg) else Text("No price", style = Type.small, color = p.faint)
            Badge(Labels.gradeShort(slab.company, slab.grade), "slab")
        }
    }
}

@Composable
fun SectionHead(title: String, right: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Label(title, Modifier.weight(1f))
        if (right != null) Sub(right)
    }
}

@Composable
fun EmptyState(text: String) {
    Text(text, color = P.muted, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp), fontWeight = FontWeight.Normal)
}
