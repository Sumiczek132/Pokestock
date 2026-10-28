package com.pokestock.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Phone-to-phone sync over Bluetooth / Wi-Fi Direct, no internet needed.
 * Both phones tap Sync; they find each other, swap their collections, and each keeps the newest change of everything.
 */
object NearbySync {
    private const val SERVICE = "com.pokestock.sync"
    var status by mutableStateOf("")
    var running by mutableStateOf(false)

    private var client: ConnectionsClient? = null
    private var peer: String? = null
    private var sent = false
    private var received = false

    fun start(ctx: Context) {
        if (running) return
        val c = Nearby.getConnectionsClient(ctx.applicationContext)
        client = c; running = true; sent = false; received = false; peer = null
        status = "Looking for the other phone… open Sync on it and tap the same button."
        val name = android.os.Build.MODEL ?: "Phone"
        c.startAdvertising(name, SERVICE, lifecycle, AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
            .addOnFailureListener { fail("Couldn't start: ${it.message}") }
        c.startDiscovery(SERVICE, discovery, DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
            .addOnFailureListener { fail("Couldn't search: ${it.message}") }
    }

    fun stop() {
        client?.apply { stopAdvertising(); stopDiscovery(); stopAllEndpoints() }
        running = false
    }

    private fun fail(msg: String) { status = msg; stop() }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            if (peer != null) return
            status = "Found ${info.endpointName}, connecting…"
            client?.requestConnection(android.os.Build.MODEL ?: "Phone", id, lifecycle)
                ?.addOnFailureListener { /* the other phone may have connected first; that's fine */ }
        }
        override fun onEndpointLost(id: String) {}
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            client?.acceptConnection(id, payloads)
        }
        override fun onConnectionResult(id: String, res: ConnectionResolution) {
            if (res.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                peer = id
                client?.stopAdvertising(); client?.stopDiscovery()
                status = "Connected, swapping collections…"
                val bytes = AppState.store.exportJson().toByteArray()
                client?.sendPayload(id, Payload.fromStream(ByteArrayInputStream(bytes)))
                sent = true
            } else if (peer == null) status = "Connection failed, trying again…"
        }
        override fun onDisconnected(id: String) {
            if (!received) status = "The other phone disconnected before finishing. Try again."
            running = false
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            val input = payload.asStream()?.asInputStream() ?: return
            // Read the stream as it arrives; it ends when the other phone has sent everything.
            Thread {
                val main = android.os.Handler(android.os.Looper.getMainLooper())
                try {
                    val text = input.use { it.readBytes() }.toString(Charsets.UTF_8)
                    val other = CollectionDoc.fromJson(JSONObject(text))
                    main.post {
                        val n = AppState.store.mergeIn(other)
                        AppState.refreshCustom()
                        received = true
                        status = if (n > 0) "Synced: took $n changes from the other phone." else "Synced: both phones already match."
                        // Give the other phone a moment to finish reading ours, then hang up.
                        main.postDelayed({ stop() }, 4000)
                    }
                } catch (e: Exception) {
                    main.post { fail("Sync failed: ${e.message}") }
                }
            }.start()
        }
        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {}
    }
}
