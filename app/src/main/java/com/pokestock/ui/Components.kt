package com.pokestock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.pokestock.data.Card
import com.pokestock.data.Labels
import com.pokestock.data.pounds
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun hueOf(card: Card): Float = if (card.dex > 0) ((card.dex * 47) % 360).toFloat() else (card.name.hashCode().mod(360)).toFloat()

/** Card picture, with a coloured placeholder while it loads or when offline and not yet cached. */
@Composable
fun CardArt(card: Card, modifier: Modifier = Modifier, big: Boolean = false, dim: Boolean = false) {
    val shape = RoundedCornerShape(if (big) 10.dp else 4.dp)
    Box(modifier.aspectRatio(63f / 88f).clip(shape)) {
        val url = card.imageUrl(high = big)
        if (url != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ArtPlaceholder(card, big) },
                error = { ArtPlaceholder(card, big) },
            )
        } else ArtPlaceholder(card, big)
        if (dim) Box(Modifier.fillMaxSize().background(Color(0x99000000)))
    }
}

@Composable
private fun ArtPlaceholder(card: Card, big: Boolean) {
    val h = hueOf(card)
    val special = Labels.group(card.rarity) in setOf("art", "special")
    val brush = if (special) Brush.linearGradient(listOf(Color.hsl(h, .6f, .55f), Color.hsl((h + 70) % 360, .6f, .6f), Color.hsl((h + 150) % 360, .55f, .55f)))
    else Brush.linearGradient(listOf(Color.hsl(h, .45f, .46f), Color.hsl((h + 30) % 360, .4f, .28f)))
    Box(Modifier.fillMaxSize().background(brush)) {
        Text(card.name, color = Color.White, fontSize = if (big) 14.sp else 8.sp, fontWeight = FontWeight.Bold, maxLines = 2,
            overflow = TextOverflow.Ellipsis, lineHeight = if (big) 16.sp else 9.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(if (big) 10.dp else 3.dp))
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = Type.label, color = P.muted, modifier = modifier)

@Composable
fun Sub(text: String, modifier: Modifier = Modifier, color: Color = P.muted, maxLines: Int = Int.MAX_VALUE) =
    Text(text, style = Type.small.copy(fontSize = 13.sp), color = color, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = P
    Box(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50))
            .background(if (selected) p.fg else p.surface)
            .border(1.dp, if (selected) p.fg else p.line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 13.sp, color = if (selected) p.bg else p.muted) }
}

@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** Small tag for a version: filled when owned, dashed-looking outline when missing, blue for slabs. */
@Composable
fun VChip(text: String, owned: Boolean, slab: Boolean = false) {
    val p = P
    val shape = RoundedCornerShape(5.dp)
    val bg = if (owned && !slab) p.accent else Color.Transparent
    val border = when { slab -> p.want; owned -> p.accent; else -> p.faint }
    val fg = when { slab -> p.want; owned -> p.onAccent; else -> p.faint }
    Box(Modifier.clip(shape).background(bg).border(1.dp, border, shape).padding(horizontal = 5.dp, vertical = 2.dp)) {
        Text(text, style = Type.mono.copy(fontWeight = if (owned || slab) FontWeight.Bold else FontWeight.Normal), color = fg)
    }
}

@Composable
fun RarityTag(rarity: String) {
    val s = Labels.rarityShort(rarity)
    if (s.isEmpty()) return
    val p = P
    Box(Modifier.border(1.dp, p.line, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
        Text(s, style = Type.mono, color = p.muted)
    }
}

@Composable
fun Badge(text: String, kind: String = "own") {
    val p = P
    val shape = RoundedCornerShape(6.dp)
    val (bg, fg, br) = when (kind) {
        "own" -> Triple(p.accent, p.onAccent, p.accent)
        "want" -> Triple(Color.Transparent, p.want, p.want)
        "slab" -> Triple(Color.Transparent, p.want, p.want)
        "up" -> Triple(Color.Transparent, p.up, p.up)
        else -> Triple(p.surface2, p.muted, p.surface2)
    }
    Box(Modifier.clip(shape).background(bg).border(1.dp, br, shape).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
fun PriceText(pence: Int?, style: TextStyle = Type.strong) {
    if (pence == null) Text("No price yet", fontSize = 12.sp, color = P.faint)
    else Text(pounds(pence), style = style, color = P.fg)
}

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 12.dp, content: @Composable () -> Unit) {
    val p = P
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(14.dp)).padding(padding)) { content() }
}

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val p = P
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.heightIn(min = 48.dp).clip(shape)
            .background(if (primary) p.accent else p.surface)
            .border(1.dp, if (primary) p.accent else p.line, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = when {
            !enabled -> p.faint; primary -> p.onAccent; else -> p.fg
        })
    }
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Panel(modifier, padding = 10.dp) {
        Column {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = P.fg, maxLines = 1)
            Text(label, fontSize = 12.sp, color = P.muted)
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val p = P
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(p.surface2)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(p.accent))
    }
}

/** Line chart with a faint grid, area fill, highlighted last point, and touch to read a value. */
@Composable
fun LineChart(points: List<Pair<LocalDate, Double>>, format: (Double) -> String, modifier: Modifier = Modifier) {
    val p = P
    if (points.size < 2) return
    var touch by remember(points) { mutableStateOf<Int?>(null) }
    val lo0 = points.minOf { it.second }; val hi0 = points.maxOf { it.second }
    val span = (hi0 - lo0).takeIf { it > 1e-9 } ?: maxOf(1.0, hi0 * 0.1)
    val lo = maxOf(0.0, lo0 - span * 0.12); val hi = hi0 + span * 0.12
    val t0 = points.first().first.toEpochDay().toDouble(); val t1 = points.last().first.toEpochDay().toDouble().let { if (it == t0) t0 + 1 else it }
    val fmtMonth = DateTimeFormatter.ofPattern("MMM yy")
    Panel(modifier, padding = 10.dp) {
        Column {
            val sel = touch?.let { points[it] }
            Text(if (sel != null) sel.first.format(DateTimeFormatter.ofPattern("d MMM yyyy")) + " · " + format(sel.second) else " ",
                fontSize = 12.sp, color = p.fg, modifier = Modifier.padding(bottom = 4.dp))
            Row {
                Canvas(Modifier.weight(1f).height(150.dp).pointerInput(points) {
                    fun pick(x: Float) { val f = (x / size.width).coerceIn(0f, 1f); val t = t0 + f * (t1 - t0); touch = points.indices.minByOrNull { kotlin.math.abs(points[it].first.toEpochDay() - t) } }
                    detectTapGestures(onPress = { pick(it.x); tryAwaitRelease(); })
                }.pointerInput(points) {
                    fun pick(x: Float) { val f = (x / size.width).coerceIn(0f, 1f); val t = t0 + f * (t1 - t0); touch = points.indices.minByOrNull { kotlin.math.abs(points[it].first.toEpochDay() - t) } }
                    detectDragGestures(onDragEnd = { }, onDrag = { change, _ -> pick(change.position.x) })
                }) {
                    val w = size.width; val h = size.height
                    fun x(d: LocalDate) = ((d.toEpochDay() - t0) / (t1 - t0) * w).toFloat()
                    fun y(v: Double) = (h - (v - lo) / (hi - lo) * h).toFloat()
                    for (i in 1..3) { val yy = h * i / 4f; drawLine(p.line, Offset(0f, yy), Offset(w, yy), 1f) }
                    val path = Path(); val area = Path()
                    points.forEachIndexed { i, pt -> val px = x(pt.first); val py = y(pt.second); if (i == 0) { path.moveTo(px, py); area.moveTo(px, h); area.lineTo(px, py) } else { path.lineTo(px, py); area.lineTo(px, py) } }
                    area.lineTo(x(points.last().first), h); area.close()
                    drawPath(area, Brush.verticalGradient(listOf(p.accent.copy(alpha = .28f), p.accent.copy(alpha = 0f))))
                    drawPath(path, p.accent, style = Stroke(width = 2.dp.toPx()))
                    val last = points.last()
                    drawCircle(p.surface, 6.dp.toPx(), Offset(x(last.first), y(last.second)))
                    drawCircle(p.accent, 4.dp.toPx(), Offset(x(last.first), y(last.second)))
                    touch?.let { i ->
                        val pt = points[i]; val px = x(pt.first)
                        drawLine(p.muted, Offset(px, 0f), Offset(px, h), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                        drawCircle(p.fg, 5.dp.toPx(), Offset(px, y(pt.second)))
                    }
                }
                Column(Modifier.width(56.dp).height(150.dp).padding(start = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(format(hi - (hi - lo) * .12), fontSize = 10.sp, color = p.muted, maxLines = 1)
                    Text(format((hi + lo) / 2), fontSize = 10.sp, color = p.muted, maxLines = 1)
                    Text(format(lo + (hi - lo) * .12), fontSize = 10.sp, color = p.muted, maxLines = 1)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, end = 56.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(points.first().first.format(fmtMonth), fontSize = 10.sp, color = p.muted)
                Text(points.last().first.format(fmtMonth), fontSize = 10.sp, color = p.muted)
            }
        }
    }
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

@Composable
fun StarMark(on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = P
    Box(modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(if (on) "★" else "☆", fontSize = 22.sp, color = if (on) p.want else p.faint)
    }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val p = P
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(12.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (k, label) ->
            val on = k == selected
            Box(Modifier.weight(1f).heightIn(min = 38.dp).clip(RoundedCornerShape(9.dp)).background(if (on) p.fg else Color.Transparent)
                .clickable { onSelect(k) }, contentAlignment = Alignment.Center) {
                Text(label, fontWeight = FontWeight.SemiBold, color = if (on) p.bg else p.muted)
            }
        }
    }
}

val Modifier.dimIf: Modifier get() = this
fun Modifier.maxW(): Modifier = this.widthIn(max = 700.dp)
