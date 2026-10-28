package com.pokestock.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.pokestock.data.AppState
import com.pokestock.data.Card
import com.pokestock.data.pounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ShareItem(val card: Card, val line: String, val price: Int?)
data class ShareRequest(val title: String, val items: List<ShareItem>)

/** Bottom panel: share a list as a picture, a PDF or plain text. */
@Composable
fun ShareSheet(req: ShareRequest) {
    val p = P
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val say = toast()
    if (req.items.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(req) { say("Nothing missing to share. That one's complete!"); Nav.share = null }
        return
    }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { if (!busy) Nav.share = null }) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(p.bg)
            .clickable(enabled = false) {}.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(req.title, style = Type.title, color = p.fg)
            Sub("${req.items.size} cards")
            if (busy) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }
            else {
                Btn("Picture (JPEG)", Modifier.fillMaxWidth(), primary = true) { busy = true; scope.launch { val err = Export.share(ctx, req, "jpeg"); busy = false; Nav.share = null; err?.let { say(it) } } }
                Btn("PDF", Modifier.fillMaxWidth()) { busy = true; scope.launch { val err = Export.share(ctx, req, "pdf"); busy = false; Nav.share = null; err?.let { say(it) } } }
                Btn("Text", Modifier.fillMaxWidth()) { Export.shareText(ctx, req); Nav.share = null }
                Text("Pictures show card images you've already loaded; others appear as coloured cards.", color = p.faint, fontWeight = FontWeight.Normal)
            }
        }
    }
}

object Export {
    private const val BG = 0xFF0E131D.toInt(); private const val CARD_BG = 0xFF161D2B.toInt()
    private const val FG = 0xFFEDF1F8.toInt(); private const val MUTED = 0xFF9BA6BB.toInt(); private const val ACCENT = 0xFFF5C518.toInt()

    fun shareText(ctx: Context, req: ShareRequest) {
        val sb = StringBuilder(req.title).append('\n')
        req.items.forEach { sb.append("• ").append(it.card.name).append(" – ").append(it.line); it.price?.let { pr -> sb.append(" ~").append(pounds(pr)) }; sb.append('\n') }
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString()).putExtra(Intent.EXTRA_SUBJECT, req.title)
        ctx.startActivity(Intent.createChooser(i, req.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Makes the picture(s) or PDF and opens the share menu. Returns an error message, or null when it worked. */
    suspend fun share(ctx: Context, req: ShareRequest, kind: String): String? {
        val files = try { withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val stamp = LocalDate.now().toString()
            val safe = req.title.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(40)
            // Pictures are fetched one card at a time while drawing, so long lists don't run out of memory.
            val load: suspend (Card) -> Bitmap? = { c -> runCatching { kotlinx.coroutines.withTimeoutOrNull(6000) { loadBitmap(ctx, c) } }.getOrNull() }
            if (kind == "pdf") listOf(renderPdf(req, load, File(dir, "$safe-$stamp.pdf")))
            else req.items.indices.chunked(40).mapIndexed { n, idx ->
                renderJpeg(req, idx.map { req.items[it] }, load, n + 1, (req.items.size + 39) / 40, File(dir, "$safe-$stamp-${n + 1}.jpg"))
            }
        } } catch (e: Throwable) { return "Couldn't make the ${if (kind == "pdf") "PDF" else "picture"}: ${e.message ?: e.javaClass.simpleName}" }
        val uris = ArrayList(files.map { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", it) })
        val mime = if (kind == "pdf") "application/pdf" else "image/jpeg"
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).setType(mime).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_SUBJECT, req.title)
        ctx.startActivity(Intent.createChooser(i, req.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        return null
    }

    private suspend fun loadBitmap(ctx: Context, card: Card): Bitmap? {
        val url = card.imageUrl() ?: return null
        val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(url).allowHardware(false).size(240, 335)
            .memoryCachePolicy(coil.request.CachePolicy.DISABLED).build())
        return ((r as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = size; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun ellipsize(p: Paint, s: String, w: Float): String {
        if (p.measureText(s) <= w) return s
        var t = s
        while (t.length > 1 && p.measureText("$t…") > w) t = t.dropLast(1)
        return "$t…"
    }

    /** Draws one card cell: picture (or a coloured stand-in), name, details and price. */
    private fun drawCell(c: Canvas, x: Float, y: Float, w: Float, item: ShareItem, pic: Bitmap?, scale: Float) {
        val imgH = w * 88f / 63f
        val r = RectF(x, y, x + w, y + imgH)
        if (pic != null) c.drawBitmap(pic, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
        else {
            val h = hueOf(item.card)
            c.drawRoundRect(r, 8f * scale, 8f * scale, Paint().apply { color = AColor.HSVToColor(floatArrayOf(h, 0.45f, 0.55f)) })
            c.drawText(ellipsize(paint(AColor.WHITE, 14f * scale, true), item.card.name, w - 12f * scale), x + 6f * scale, y + imgH - 10f * scale, paint(AColor.WHITE, 14f * scale, true))
        }
        val name = paint(FG, 15f * scale, true); val small = paint(MUTED, 12f * scale); val price = paint(ACCENT, 14f * scale, true)
        var ty = y + imgH + 18f * scale
        c.drawText(ellipsize(name, item.card.name, w), x, ty, name); ty += 16f * scale
        c.drawText(ellipsize(small, item.line, w), x, ty, small); ty += 17f * scale
        item.price?.let { c.drawText(pounds(it), x, ty, price) }
    }

    private suspend fun renderJpeg(req: ShareRequest, items: List<ShareItem>, load: suspend (Card) -> Bitmap?, part: Int, parts: Int, out: File): File {
        val scale = 2f; val cols = 4; val margin = 24f * scale; val gap = 12f * scale
        val width = 1080
        val cellW = (width - 2 * margin - (cols - 1) * gap) / cols
        val cellH = cellW * 88f / 63f + 60f * scale
        val header = 90f * scale
        val rows = (items.size + cols - 1) / cols
        val height = (header + rows * (cellH + gap) + margin).toInt()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)
        c.drawText(req.title + if (parts > 1) "  ($part/$parts)" else "", margin, margin + 22f * scale, paint(FG, 22f * scale, true))
        c.drawText("${req.items.size} cards · ${LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy"))} · Pokestock", margin, margin + 44f * scale, paint(MUTED, 13f * scale))
        items.forEachIndexed { i, it ->
            val col = i % cols; val row = i / cols
            val pic = load(it.card)
            drawCell(c, margin + col * (cellW + gap), header + row * (cellH + gap), cellW, it, pic, scale * 0.85f)
        }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return out
    }

    private suspend fun renderPdf(req: ShareRequest, load: suspend (Card) -> Bitmap?, out: File): File {
        val doc = PdfDocument()
        val pw = 595; val ph = 842; val cols = 4; val rowsPerPage = 3
        val margin = 30f; val gap = 10f; val header = 56f
        val cellW = (pw - 2 * margin - (cols - 1) * gap) / cols
        val cellH = cellW * 88f / 63f + 52f
        val perPage = cols * rowsPerPage
        val pages = (req.items.size + perPage - 1) / perPage
        for (pg in 0 until maxOf(1, pages)) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pg + 1).create())
            val c = page.canvas
            c.drawColor(AColor.WHITE)
            c.drawText(req.title, margin, margin + 14f, paint(0xFF111826.toInt(), 16f, true))
            c.drawText("${req.items.size} cards · ${LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy"))} · page ${pg + 1} of ${maxOf(1, pages)}",
                margin, margin + 30f, paint(0xFF4F5A70.toInt(), 9f))
            for (k in 0 until perPage) {
                val i = pg * perPage + k
                if (i >= req.items.size) break
                val col = k % cols; val row = k / cols
                val x = margin + col * (cellW + gap); val y = header + row * (cellH + gap)
                drawPdfCell(c, x, y, cellW, req.items[i], load(req.items[i].card))
            }
            doc.finishPage(page)
        }
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun drawPdfCell(c: Canvas, x: Float, y: Float, w: Float, item: ShareItem, pic: Bitmap?) {
        val imgH = w * 88f / 63f
        val r = RectF(x, y, x + w, y + imgH)
        if (pic != null) c.drawBitmap(pic, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
        else c.drawRoundRect(r, 4f, 4f, Paint().apply { color = AColor.HSVToColor(floatArrayOf(hueOf(item.card), 0.35f, 0.75f)) })
        val name = paint(0xFF111826.toInt(), 8.5f, true); val small = paint(0xFF4F5A70.toInt(), 7f); val price = paint(0xFF7A5800.toInt(), 8f, true)
        var ty = y + imgH + 11f
        c.drawText(ellipsize(name, item.card.name, w), x, ty, name); ty += 10f
        c.drawText(ellipsize(small, item.line, w), x, ty, small); ty += 10f
        item.price?.let { c.drawText(pounds(it), x, ty, price) }
    }
}

object ShareLists {
    fun missing(set: com.pokestock.data.CardSet, mode: String): List<ShareItem> =
        com.pokestock.data.Binder.pockets(set, mode).filter { !it.owned }.map { pk ->
            ShareItem(pk.card, "${set.name} ${pk.card.numberLabel}" + if (mode != "base") " · " + com.pokestock.data.Labels.variantLabel(pk.card, pk.variant) else "",
                AppState.unitPrice(pk.card, pk.variant))
        }

    fun wanted(): List<ShareItem> {
        val cat = AppState.catalog
        val slot = AppState.store.doc.slotWants.filter { it.value.v }.keys.mapNotNull { cat.byKey[it.substringAfter('|')] }.filter { AppState.store.total(it.key) == 0 }
        val wish = AppState.store.wishList().mapNotNull { cat.byKey[it] }
        return (slot + wish).distinctBy { it.key }.sortedWith(compareBy({ it.set.name }, { it.localId })).map { c ->
            ShareItem(c, "${c.set.name} ${c.numberLabel}" + if (c.set.lang == "JA") " · Japanese" else "", AppState.lowPrice(c))
        }
    }
}
