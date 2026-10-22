package com.example.model

enum class AppScreen {
    HOME,
    EMITTER,
    RECEIVER,
    REMOTE_VIEW
}

enum class ConnectionType {
    WIFI,
    USB
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    ERROR
}

data class DiscoveredDevice(
    val name: String,
    val ip: String,
    val port: Int,
    val pinRequired: Boolean = true,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

data class StreamStats(
    val fps: Int = 0,
    val bitrateKbps: Long = 0L,
    val latencyMs: Long = 0L,
    val framesTransmitted: Long = 0L,
    val activeClients: Int = 0,
    val resolutionWidth: Int = 0,
    val resolutionHeight: Int = 0
)

data class NetworkInfo(
    val ipAddress: String = "127.0.0.1",
    val isWifiConnected: Boolean = false,
    val ssid: String = "Desconectado",
    val isUsbTethered: Boolean = false
)
