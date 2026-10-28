package com.pokestock

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pokestock.data.AppState
import com.pokestock.data.DriveSync
import com.pokestock.ui.PokestockRoot
import com.pokestock.ui.PokestockTheme
import com.pokestock.work.Schedule
import kotlinx.coroutines.launch

class PokestockApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        // Big disk cache so card pictures you've seen stay available offline.
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("cards")).maxSizeBytes(3L * 1024 * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .build()
}

@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openSync = intent?.getBooleanExtra("openSync", false) == true
        lifecycleScope.launch {
            if (!AppState.ready) AppState.init(applicationContext)
            Schedule.apply(applicationContext)
            // First run, or prices older than today: try an update straight away (silently does nothing offline).
            val age = AppState.priceAgeDays()
            if (AppState.catalog.sets.isEmpty() || age == null || age >= 1) AppState.update(applicationContext)
        }
        setContent { PokestockTheme { PokestockRoot(startOnSync = openSync) } }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { if (AppState.ready && DriveSync.fileUri(applicationContext) != null && AppState.isOnline(applicationContext)) DriveSync.sync(applicationContext) }
    }

    override fun onStop() {
        super.onStop()
        val ctx = applicationContext
        if (AppState.ready && DriveSync.fileUri(ctx) != null && AppState.isOnline(ctx))
            kotlinx.coroutines.GlobalScope.launch { DriveSync.sync(ctx) }
    }
}
