package com.pokestock.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokestock.data.AppState

/** Navigation state shared by every screen. */
object Nav {
    var tab by mutableStateOf("portfolio")
    var openSet by mutableStateOf<String?>(null)      // set key, or "slabs"
    var card by mutableStateOf<String?>(null)         // card key shown in the card view
    var cardVariant by mutableStateOf<String?>(null)
    var slot by mutableStateOf<Int?>(null)            // Pokédex slot shown
    var lang by mutableStateOf("EN")
    var colQuery by mutableStateOf("")
    var master by mutableStateOf(true)
    var setFilter by mutableStateOf("all")
    var setGroup by mutableStateOf("all")
    var setSort by mutableStateOf("num")
    var dexFilter by mutableStateOf("all")
    var dexGen by mutableIntStateOf(0)
    var dexQuery by mutableStateOf("")
    var range by mutableStateOf("1Y")
    var cardRange by mutableStateOf("3M")
    var addCustom by mutableStateOf(false)
    var fullImage by mutableStateOf<String?>(null)
    var page by mutableStateOf<String?>(null)          // "top" or "trade", shown over the tabs
    var searchOwned by mutableStateOf("all")
    var share by mutableStateOf<ShareRequest?>(null)
    var colView by mutableStateOf("sets")              // "sets" or "binders" in the Collection tab
    val collapsed = androidx.compose.runtime.mutableStateListOf<String>()   // folded series headings
    var collapsedLoaded = false
    var ownedFirst by mutableStateOf(true)    // card key shown full screen

    // Scroll positions, kept so going back returns to the same place.
    val setList = androidx.compose.foundation.lazy.LazyListState()
    val dexGrid = androidx.compose.foundation.lazy.grid.LazyGridState()
    val binderList = androidx.compose.foundation.lazy.LazyListState()
    private val setViews = HashMap<String, androidx.compose.foundation.lazy.LazyListState>()
    fun setViewState(key: String) = setViews.getOrPut(key) { androidx.compose.foundation.lazy.LazyListState() }

    /** Jump to a set's page in the Collection tab from anywhere. */
    fun goToSet(setKey: String, lang: String) {
        card = null; slot = null; this.lang = lang; tab = "collection"
        setFilter = "all"; setGroup = "all"; openSet = setKey
    }

    fun openCard(key: String, variant: String? = null) { card = key; cardVariant = variant }
}

@Composable
fun toast(): (String) -> Unit {
    val ctx = LocalContext.current
    return { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
}

@Composable
fun PokestockRoot(startOnSync: Boolean) {
    val p = P
    LaunchedEffect(startOnSync) { if (startOnSync) Nav.tab = "more" }
    Box(Modifier.fillMaxSize().background(p.bg)) {
        if (!AppState.ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }
            return@Box
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar()
            Box(Modifier.weight(1f)) {
                when (Nav.tab) {
                    "portfolio" -> PortfolioScreen()
                    "collection" -> CollectionScreen()
                    "dex" -> DexScreen()
                    "wanted" -> WantedScreen()
                    else -> SyncScreen()
                }
            }
            BottomTabs()
        }
        AnimatedVisibility(Nav.page != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            val pg = Nav.page ?: ""
            when {
                pg == "top" -> TopScreen()
                pg == "trade" -> TradeScreen()
                pg == "dupes" -> DuplicatesScreen()
                pg == "scan" -> ScanScreen()
                pg == "dexbinder" -> DexBinderScreen()
                pg.startsWith("binder:") -> SetBinderScreen(pg.removePrefix("binder:"))
                pg.startsWith("quick:") -> QuickAddScreen(pg.removePrefix("quick:"))
                else -> {}
            }
        }
        AnimatedVisibility(Nav.slot != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            Nav.slot?.let { SlotSheet(it) }
        }
        AnimatedVisibility(Nav.card != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
            Nav.card?.let { CardSheet(it) }
        }
        AnimatedVisibility(Nav.addCustom, enter = slideInVertically { it }, exit = slideOutVertically { it }) { AddCustomSheet() }
        if (Nav.fullImage != null) FullImage(Nav.fullImage!!)
        Nav.share?.let { ShareSheet(it) }
        BackHandler(Nav.share != null) { Nav.share = null }
        BackHandler(Nav.fullImage != null) { Nav.fullImage = null }
        BackHandler(Nav.addCustom && Nav.card == null) { Nav.addCustom = false }
        BackHandler(Nav.card != null && Nav.fullImage == null) { Nav.card = null }
        BackHandler(Nav.card == null && Nav.slot != null) { Nav.slot = null }
        BackHandler(Nav.card == null && Nav.slot == null && Nav.page != null) {
            if (Nav.page == "trade" && TradeState.picking) TradeState.picking = false
            else if (Nav.page == "trade" && TradeState.selecting) TradeState.reset() else Nav.page = null
        }
        BackHandler(Nav.card == null && Nav.slot == null && Nav.tab == "collection" && Nav.openSet != null) { Nav.openSet = null }
    }
}

@Composable
private fun TopBar() {
    val p = P
    val ctx = LocalContext.current
    val online = AppState.isOnline(ctx)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(p.accent))
        Spacer(Modifier.width(8.dp))
        Text("Pokestock", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = p.fg)
        Spacer(Modifier.weight(1f))
        val date = AppState.lastPriceDate.takeIf { it.isNotEmpty() }?.let { d -> "prices " + java.time.LocalDate.parse(d).format(java.time.format.DateTimeFormatter.ofPattern("d MMM")) } ?: "no prices yet"
        Row(Modifier.clip(RoundedCornerShape(50)).background(p.surface).border(1.dp, p.line, RoundedCornerShape(50))
            .clickable { Nav.tab = "more" }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (AppState.updating) CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 2.dp, color = p.accent)
            else Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) p.up else p.faint))
            Spacer(Modifier.width(6.dp))
            Text((if (online) "Online · " else "Offline · ") + date, fontSize = 12.sp, color = p.muted)
        }
    }
}

@Composable
private fun BottomTabs() {
    val p = P
    val tabs = listOf(
        Triple("portfolio", "Portfolio", Icons.Outlined.ShowChart),
        Triple("collection", "Collection", Icons.Outlined.CollectionsBookmark),
        Triple("dex", "Pokédex", Icons.Outlined.GridView),
        Triple("wanted", "Wanted", Icons.Outlined.StarOutline),
        Triple("more", "Sync", Icons.Outlined.Sync),
    )
    Column(Modifier.background(p.surface).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            tabs.forEach { (k, label, icon) -> TabItem(label, icon, Nav.tab == k, Modifier.weight(1f)) {
                if (Nav.tab == k && k == "collection") Nav.openSet = null
                Nav.tab = k
            } }
        }
    }
}

@Composable
private fun TabItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = P
    Column(modifier.heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, tint = if (selected) p.accentInk else p.muted, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, color = if (selected) p.fg else p.muted)
    }
}
