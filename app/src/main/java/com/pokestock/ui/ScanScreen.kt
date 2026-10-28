package com.pokestock.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pokestock.data.AppState
import com.pokestock.data.Card
import java.util.concurrent.Executors

/**
 * Reads the set code and card number printed at the bottom of a card ("PAR EN 045/182", "GG35/GG70")
 * and lists the matching cards. Works offline: the text recognition runs on the phone.
 */
object CardReader {
    private val numRe = Regex("(?<![A-Z0-9])([A-Z]{0,3}) ?0*(\\d{1,3}) ?/ ?([A-Z]{0,3}) ?0*(\\d{1,3})(?!\\d)")
    private val wordRe = Regex("\\b[A-Z]{2,4}\\b")

    fun match(text: String): List<Card> {
        val up = text.uppercase()
        val cat = AppState.catalog
        val abbrs = HashSet<String>()
        wordRe.findAll(up).forEach { abbrs.add(it.value) }
        val out = LinkedHashMap<String, Pair<Card, Int>>()
        for (m in numRe.findAll(up)) {
            val prefix = m.groupValues[1]; val n = m.groupValues[2].toInt(); val total = m.groupValues[4].toInt()
            for (s in cat.sets) {
                if (s.custom || (s.lang != "EN" && s.lang != "JA")) continue
                val abbrHit = s.abbr.isNotEmpty() && s.abbr.uppercase() in abbrs
                val totalHit = s.total == total || (prefix.isNotEmpty() && m.groupValues[3] == prefix)
                if (!totalHit && !abbrHit) continue
                for (c in s.cards) {
                    val lm = Regex("^([A-Z]*)0*(\\d+)$").find(c.localId.uppercase()) ?: continue
                    if (lm.groupValues[1] == prefix && lm.groupValues[2].toInt() == n) {
                        val score = (if (abbrHit) 4 else 0) + (if (s.total == total) 2 else 0) + (if (s.lang == "EN") 1 else 0)
                        val old = out[c.key]
                        if (old == null || old.second < score) out[c.key] = c to score
                    }
                }
            }
        }
        return out.values.sortedWith(compareByDescending<Pair<Card, Int>> { it.second }.thenByDescending { it.first.set.date }).map { it.first }.take(6)
    }
}

@Composable
fun ScanScreen() {
    val p = P
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    var found by remember { mutableStateOf<List<Card>>(emptyList()) }
    var lastText by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            val previewView = remember { PreviewView(ctx) }
            AndroidView({ previewView }, Modifier.fillMaxSize())
            DisposableEffect(Unit) {
                val exec = Executors.newSingleThreadExecutor()
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                var busy = false
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(exec) { proxy ->
                        val media = proxy.image
                        if (media == null || busy) { proxy.close(); return@setAnalyzer }
                        busy = true
                        recognizer.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { r ->
                                val hits = CardReader.match(r.text)
                                if (hits.isNotEmpty()) { found = hits; lastText = r.text.lines().firstOrNull { CardReaderHint.looksLikeNumber(it) } ?: "" }
                            }
                            .addOnCompleteListener { busy = false; proxy.close() }
                    }
                    runCatching {
                        provider.unbindAll()
                        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                onDispose {
                    runCatching { providerFuture.get().unbindAll() }
                    recognizer.close(); exec.shutdown()
                }
            }
        } else Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pokestock needs the camera to read cards.", color = Color.White)
            Gap()
            Btn("Allow camera", primary = true) { ask.launch(Manifest.permission.CAMERA) }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            PageHeaderOnDark("Scan a card") { Nav.page = null }
            Text("Point at the bottom corner of the card so the set code and number (e.g. PAR 045/182) are sharp.",
                color = Color.White, modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x88000000)).padding(8.dp))
        }
        if (found.isNotEmpty()) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp)
            .clip(RoundedCornerShape(16.dp)).background(p.bg).padding(8.dp)) {
            Sub(if (lastText.isNotEmpty()) "Read: $lastText" else "Matches", modifier = Modifier.padding(4.dp))
            found.forEach { c -> CardRow(c) }
        }
    }
}

object CardReaderHint {
    fun looksLikeNumber(s: String) = Regex("\\d{1,3}\\s*/\\s*\\d{1,3}").containsMatchIn(s)
}

@Composable
private fun PageHeaderOnDark(title: String, onBack: () -> Unit) {
    Box(Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x88000000)).clickable(onClick = onBack)
        .padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text("‹  $title", color = Color.White, style = Type.title)
    }
}
