package com.pokestock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokestock.data.AppState
import com.pokestock.data.Card
import com.pokestock.data.Labels
import com.pokestock.data.pounds

data class Spare(val card: Card, val variant: String, val owned: Int, val inTrade: Int, val spare: Int, val each: Int)

object Duplicates {
    /** Ball and energy pattern reverses are worth trading even when cheap. */
    private fun isPattern(v: String) = v.startsWith("reverse-") || v.contains('+')
    /** Older cards are kept regardless of price. */
    private fun isVintage(c: Card) = c.set.date.isNotEmpty() && c.set.date < "2004-01-01"

    /**
     * Copies beyond the one you keep, not already in the trade binder. Commons, uncommons and plain holos
     * under £1 are left out, unless they're a pattern reverse or vintage.
     */
    fun find(): List<Spare> = AppState.holdings().mapNotNull { h ->
        val inTrade = AppState.store.tradeQty(h.card.key, h.variant)
        val spare = h.qty - 1 - inTrade
        if (spare <= 0) return@mapNotNull null
        val each = AppState.unitPrice(h.card, h.variant) ?: 0
        val bulk = Labels.group(h.card.rarity) in setOf("cu", "other") && each < 100 && !isPattern(h.variant) && !isVintage(h.card)
        if (bulk) null else Spare(h.card, h.variant, h.qty, inTrade, spare, each)
    }.sortedByDescending { it.each }
}

@Composable
fun DuplicatesScreen() {
    val p = P
    val say = toast()
    val v = AppState.version
    val list = remember(v, AppState.prices) { Duplicates.find() }
    val total = list.sumOf { it.each.toLong() * it.spare }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding().navigationBarsPadding()) {
        PageHeader("Duplicates") { Nav.page = null }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            item {
                Sub("Spare copies (you keep one of each) that aren't in the trade binder yet. Commons, uncommons and holos under £1 are left out, except pattern reverses and vintage.")
                Gap(10.dp)
                if (list.isNotEmpty()) Btn("Add all ${list.sumOf { it.spare }} spares to the trade binder (${pounds(total)})", Modifier.fillMaxWidth(), primary = true) {
                    list.forEach { s -> AppState.store.setTradeQty(s.card.key, s.variant, s.inTrade + s.spare) }
                    say("Added to the trade binder")
                } else EmptyState("No spares worth trading right now.")
                Gap(6.dp)
            }
            items(list, key = { it.card.key + "|" + it.variant }) { s ->
                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(12.dp)).clickable { Nav.openCard(s.card.key, s.variant) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CardArt(s.card, Modifier.width(44.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(s.card.name, style = Type.strong, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Sub("${s.card.set.name} · ${Labels.variantLabel(s.card, s.variant)}", maxLines = 1)
                        Sub("Own ×${s.owned}" + if (s.inTrade > 0) " · ${s.inTrade} in trade" else "")
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PriceText(s.each.takeIf { it > 0 })
                        Btn("+${s.spare} to trade") { AppState.store.setTradeQty(s.card.key, s.variant, s.inTrade + s.spare) }
                    }
                }
            }
        }
    }
}
