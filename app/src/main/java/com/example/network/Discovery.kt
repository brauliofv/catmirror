package com.example.network

import android.os.Build
import android.util.Log
import com.example.model.DiscoveredDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DiscoveryBroadcaster(
    private val scope: CoroutineScope,
    private val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    private val port: Int = Protocol.DEFAULT_PORT
) {
    private var job: Job? = null
    private var currentPin: String = ""

    fun start(pin: String) {
        currentPin = pin
        stop()
        job = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                val payload = "CATMIRROR_BEACON|$deviceName|$port|$pin"
                val bytes = payload.toByteArray(Charsets.UTF_8)
                val targetAddress = InetAddress.getByName("255.255.255.255")

                while (isActive) {
                    try {
                        val packet = DatagramPacket(bytes, bytes.size, targetAddress, Protocol.DISCOVERY_PORT)
                        socket.send(packet)
                    } catch (e: Exception) {
                        Log.w("DiscoveryBroadcaster", "Send failed: ${e.message}")
                    }
                    delay(1500)
                }
            } catch (e: Exception) {
                Log.e("DiscoveryBroadcaster", "Broadcaster error", e)
            } finally {
                socket?.close()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

class DiscoveryScanner(
    private val scope: CoroutineScope
) {
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private var listenJob: Job? = null
    private var pruneJob: Job? = null

    fun start() {
        stop()
        listenJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(Protocol.DISCOVERY_PORT)
                socket.broadcast = true
                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val senderIp = packet.address.hostAddress ?: continue

                    // Ignore self broadcasts if on same IP
                    if (senderIp == NetworkUtils.getLocalIpAddress()) {
                        continue
                    }

                    if (message.startsWith("CATMIRROR_BEACON|")) {
                        val parts = message.split("|")
                        if (parts.size >= 4) {
                            val name = parts[1]
                            val port = parts[2].toIntOrNull() ?: Protocol.DEFAULT_PORT
                            val hasPin = parts[3].isNotEmpty()

                            val device = DiscoveredDevice(
                                name = name,
                                ip = senderIp,
                                port = port,
                                pinRequired = hasPin,
                                lastSeenTimestamp = System.currentTimeMillis()
                            )

                            val currentList = _devices.value.toMutableList()
                            val index = currentList.indexOfFirst { it.ip == senderIp }
                            if (index >= 0) {
                                currentList[index] = device
                            } else {
                                currentList.add(device)
                            }
                            _devices.value = currentList
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("DiscoveryScanner", "Scanner stopped: ${e.message}")
            } finally {
                socket?.close()
            }
        }

        // Periodic pruning of devices inactive for > 5 seconds
        pruneJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(3000)
                val cutoff = System.currentTimeMillis() - 5000
                _devices.value = _devices.value.filter { it.lastSeenTimestamp >= cutoff }
            }
        }
    }

    fun stop() {
        listenJob?.cancel()
        pruneJob?.cancel()
        listenJob = null
        pruneJob = null
        _devices.value = emptyList()
    }
}
