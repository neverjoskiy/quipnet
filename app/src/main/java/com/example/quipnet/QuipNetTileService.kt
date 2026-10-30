package com.example.quipnet

import android.app.ActivityManager
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class QuipNetTileService : TileService() {
    private var tileReceiverRegistered = false
    private val recentManualStopWindowMs = 8_000L

    private val vpnStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == QuipNetVpnService.ACTION_VPN_STATE) {
                updateTile(intent.getStringExtra(QuipNetVpnService.EXTRA_STATE))
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        ensureReceiverRegistered()
        updateTile()
    }

    override fun onStopListening() {
        unregisterTileReceiver()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        LogManager.setAppContext(this)
        val clientData = ClientData(this)
        val currentState = resolveEffectiveState(clientData)

        if (currentState != QuipNetVpnService.STATE_STOPPED) {
            QuipNetTunnelControl.stop(this, "Плитка в шторке")
            
            val tile = qsTile
            tile.state = Tile.STATE_INACTIVE
            tile.label = "QuipNet"
            tile.updateTile()
        } else {
            QuipNetTunnelControl.start(this, "Плитка в шторке")

            val tile = qsTile
            tile.state = Tile.STATE_ACTIVE
            tile.label = "QuipNet"
            tile.updateTile()
        }
    }

    private fun updateTile(stateOverride: String? = null) {
        val tile = qsTile ?: return
        val clientData = ClientData(this)
        val effectiveState = resolveEffectiveState(clientData)
        val recentlyStopped = isRecentLocalStop(clientData)
        val hasNovaSystemVpn = hasActiveNovaSystemVpn(clientData)
        val serviceRunning = isNovaVpnServiceRunning()
        val hasRestartSession = clientData.getRestartSession() != null
        val pendingState = clientData.isTransientConnectingPending() || clientData.isSoftReapplyPending()
        val resolvedState = when {
            recentlyStopped -> QuipNetVpnService.STATE_STOPPED
            stateOverride.isNullOrBlank() -> effectiveState
            stateOverride == QuipNetVpnService.STATE_STOPPED && hasNovaSystemVpn -> effectiveState
            stateOverride == QuipNetVpnService.STATE_CONNECTING && recentlyStopped -> QuipNetVpnService.STATE_STOPPED
            stateOverride == QuipNetVpnService.STATE_CONNECTED && recentlyStopped -> QuipNetVpnService.STATE_STOPPED
            stateOverride == QuipNetVpnService.STATE_CONNECTED && !hasNovaSystemVpn -> effectiveState
            stateOverride == QuipNetVpnService.STATE_CONNECTING &&
                !hasNovaSystemVpn &&
                !serviceRunning &&
                !(hasRestartSession && pendingState) -> effectiveState
            else -> stateOverride
        }
        LogManager.log(
            "QS tile refresh: override=${stateOverride.orEmpty().ifBlank { "<none>" }}, " +
                "resolved=$resolvedState, persisted=${clientData.getServiceState()}, " +
                "vpn=$hasNovaSystemVpn, service=$serviceRunning"
        )
        tile.label = "QuipNet"
        tile.state = when (resolvedState) {
            QuipNetVpnService.STATE_CONNECTED -> Tile.STATE_ACTIVE
            QuipNetVpnService.STATE_CONNECTING -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    private fun resolveEffectiveState(clientData: ClientData): String {
        val persistedState = clientData.getServiceState()
        if (isRecentLocalStop(clientData)) {
            return QuipNetVpnService.STATE_STOPPED
        }
        val hasNovaSystemVpn = hasActiveNovaSystemVpn(clientData)
        val serviceRunning = isNovaVpnServiceRunning()
        val hasRestartSession = clientData.getRestartSession() != null
        val pendingState = clientData.isTransientConnectingPending() || clientData.isSoftReapplyPending()
        if (hasNovaSystemVpn) {
            val syncedState = if (persistedState == QuipNetVpnService.STATE_CONNECTED) {
                QuipNetVpnService.STATE_CONNECTED
            } else if (
                persistedState == QuipNetVpnService.STATE_CONNECTING ||
                pendingState
            ) {
                QuipNetVpnService.STATE_CONNECTING
            } else {
                QuipNetVpnService.STATE_CONNECTED
            }
            if (syncedState == QuipNetVpnService.STATE_CONNECTED && pendingState) {
                clientData.clearTransientConnectingPending()
                clientData.clearSoftReapplyPending()
            }
            if (persistedState != syncedState) {
                LogManager.log(
                    "QS tile sync: системный QuipNet VPN активен, persisted=$persistedState -> $syncedState"
                )
                clientData.saveServiceState(
                    syncedState,
                    clientData.getServiceBackend().ifBlank { QuipNetVpnService.BACKEND_WARP },
                )
            }
            return syncedState
        }

        // Сессия перезапуска плюс живая служба раньше означали «туннель поднимается».
        // Теперь служба может работать только ради раздачи, поэтому дополнительно
        // требуем, чтобы сохранённое состояние не было «остановлено».
        if (serviceRunning && hasRestartSession && persistedState != QuipNetVpnService.STATE_STOPPED) {
            return if (persistedState == QuipNetVpnService.STATE_CONNECTING || pendingState) {
                QuipNetVpnService.STATE_CONNECTING
            } else {
                QuipNetVpnService.STATE_CONNECTED
            }
        }

        if (!serviceRunning) {
            val ageMs = (System.currentTimeMillis() - clientData.getServiceStateUpdatedAt()).coerceAtLeast(0L)
            if (
                persistedState == QuipNetVpnService.STATE_CONNECTING &&
                hasRestartSession &&
                pendingState &&
                ageMs <= 1_500L
            ) {
                return QuipNetVpnService.STATE_CONNECTING
            }
            if (persistedState != QuipNetVpnService.STATE_STOPPED) {
                clientData.clearTransientConnectingPending()
                clientData.clearSoftReapplyPending()
                clientData.saveServiceState(QuipNetVpnService.STATE_STOPPED)
            }
            return QuipNetVpnService.STATE_STOPPED
        }

        if (persistedState == QuipNetVpnService.STATE_CONNECTING) {
            val ageMs = (System.currentTimeMillis() - clientData.getServiceStateUpdatedAt()).coerceAtLeast(0L)
            if (clientData.isTransientConnectingPending() || clientData.isSoftReapplyPending() || ageMs <= 15_000L) {
                return QuipNetVpnService.STATE_CONNECTING
            }
        }

        if (persistedState != QuipNetVpnService.STATE_STOPPED) {
            clientData.saveServiceState(QuipNetVpnService.STATE_STOPPED)
        }
        return QuipNetVpnService.STATE_STOPPED
    }

    private fun isRecentLocalStop(clientData: ClientData): Boolean {
        if (clientData.getServiceState() != QuipNetVpnService.STATE_STOPPED) return false
        val updatedAt = clientData.getServiceStateUpdatedAt()
        if (updatedAt <= 0L) return false
        val ageMs = (System.currentTimeMillis() - updatedAt).coerceAtLeast(0L)
        return ageMs <= recentManualStopWindowMs
    }

    private fun isNovaVpnServiceRunning(): Boolean {
        @Suppress("DEPRECATION")
        return (getSystemService(ActivityManager::class.java)?.getRunningServices(Int.MAX_VALUE) ?: emptyList())
            .any { service -> service.service?.className == QuipNetVpnService::class.java.name }
    }

    /**
     * Живая служба сама по себе больше не доказывает живой туннель.
     *
     * QuipNetVpnService остаётся в foreground, пока включена раздача, даже когда VPN
     * остановлен. Без этой оговорки плитка в шторке горела «подключено» при
     * выключенном VPN — достаточно было, чтобы в системе оказалась активна любая
     * VPN-сеть, хоть чужого приложения.
     */
    private fun tunnelIntended(clientData: ClientData): Boolean {
        val persisted = clientData.getServiceState()
        return persisted == QuipNetVpnService.STATE_CONNECTED ||
            persisted == QuipNetVpnService.STATE_CONNECTING
    }

    private fun hasActiveNovaSystemVpn(clientData: ClientData): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val serviceRunning = isNovaVpnServiceRunning() && tunnelIntended(clientData)
        val hasRestartSession = clientData.getRestartSession() != null
        fun isLikelyNovaVpnNetwork(network: Network): Boolean {
            val caps = cm.getNetworkCapabilities(network) ?: return false
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false
            if (isNovaVpnOwner(caps)) return true
            val transportLabel = extractVpnTransportLabel(caps)
            return transportLabel.contains("QuipNetVPN", ignoreCase = true) ||
                transportLabel.contains("QuipNetOperaVPN", ignoreCase = true)
        }

        val active = cm.activeNetwork
        if (active != null && isLikelyNovaVpnNetwork(active)) return true

        val anyVpnActive = cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
        if (!anyVpnActive) return false
        if (serviceRunning) {
            return true
        }

        return cm.allNetworks.any(::isLikelyNovaVpnNetwork)
    }

    private fun extractVpnTransportLabel(caps: NetworkCapabilities?): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ""
        val transportInfo = NetworkCapabilitiesCompat.getTransportInfo(caps) ?: return ""
        try {
            val sessionId = transportInfo.javaClass.methods
                .firstOrNull { it.name == "getSessionId" && it.parameterCount == 0 }
                ?.invoke(transportInfo) as? String
            if (!sessionId.isNullOrBlank()) {
                return sessionId
            }
        } catch (_: Throwable) {
        }
        return transportInfo.toString().orEmpty()
    }

    private fun isNovaVpnOwner(caps: NetworkCapabilities?): Boolean {
        return extractVpnOwnerUid(caps) == applicationInfo.uid
    }

    private fun extractVpnOwnerUid(caps: NetworkCapabilities?): Int? {
        if (caps == null) return null
        val reflectedOwnerUid = try {
            val ownerUid = caps.javaClass.methods
                .firstOrNull { it.name == "getOwnerUid" && it.parameterCount == 0 }
                ?.invoke(caps) as? Int
            ownerUid?.takeIf { it >= 0 }
        } catch (_: Throwable) {
            null
        }
        if (reflectedOwnerUid != null) return reflectedOwnerUid

        val capsDump = caps.toString()
        val legacyOwnerUid = Regex("(?:OwnerUid|EstablishingAppUid):\\s*(\\d+)")
            .find(capsDump)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it >= 0 }
        return legacyOwnerUid
    }

    private fun ensureReceiverRegistered() {
        if (tileReceiverRegistered) return
        val filter = IntentFilter(QuipNetVpnService.ACTION_VPN_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vpnStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(vpnStateReceiver, filter)
        }
        tileReceiverRegistered = true
    }

    private fun unregisterTileReceiver() {
        if (!tileReceiverRegistered) return
        try {
            unregisterReceiver(vpnStateReceiver)
        } catch (_: Exception) {
        }
        tileReceiverRegistered = false
    }
}
