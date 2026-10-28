package com.pokestock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokestock.data.AppState

/** The card picture filling the screen. Pinch to zoom, double-tap to zoom in or out, tap ✕ or Back to close. */
@Composable
fun FullImage(key: String) {
    val card = AppState.catalog.byKey[key] ?: run { Nav.fullImage = null; return }
    var scale by remember(key) { mutableFloatStateOf(1f) }
    var offset by remember(key) { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .pointerInput(key) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                offset = if (scale == 1f) Offset.Zero else offset + pan
            }
        }
        .pointerInput(key) {
            detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f })
        },
        contentAlignment = Alignment.Center) {
        CardArt(card, Modifier.fillMaxWidth().padding(16.dp).graphicsLayer {
            scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
        }, big = true)
        Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).size(48.dp).clip(RoundedCornerShape(24.dp))
            .background(Color(0x66000000)).clickable { Nav.fullImage = null }, contentAlignment = Alignment.Center) {
            Text("✕", color = Color.White, fontSize = 22.sp)
        }
        Text(card.name + " · " + card.set.name, color = Color(0xCCFFFFFF), fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}
