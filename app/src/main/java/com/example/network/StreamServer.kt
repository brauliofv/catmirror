package com.example.network

import android.util.Log
import com.example.model.StreamStats
import com.example.service.CatMirrorAccessibilityService
import com.example.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class StreamServer(
    private val scope: CoroutineScope,
    private val port: Int = Protocol.DEFAULT_PORT,
    private val onClientConnected: (String) -> Unit = {},
    private val onClientDisconnected: (String) -> Unit = {}
) {
    private val TAG = "CatMirrorServer"

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var statsJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    private var activeClient: ClientHandler? = null
    private var currentPin: String = ""

    private val _stats = MutableStateFlow(StreamStats())
    val stats: StateFlow<StreamStats> = _stats.asStateFlow()

    private val framesSentInSecond = AtomicInteger(0)
    private val bytesSentInSecond = AtomicLong(0L)
    private val totalFramesCounter = AtomicLong(0L)

    fun start(pin: String) {
        currentPin = pin
        stop()
        isRunning.set(true)

        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                AppLogger.i(TAG, "Servidor de streaming activo en puerto $port")

                while (isRunning.get() && isActive) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        clientSocket.tcpNoDelay = true
                        clientSocket.sendBufferSize = 64 * 1024
                        clientSocket.receiveBufferSize = 32 * 1024

                        val clientIp = clientSocket.inetAddress?.hostAddress ?: "desconocido"
                        AppLogger.i(TAG, "Conexión entrante desde $clientIp")

                        // Close previous client if exists (only 1 active receiver supported)
                        activeClient?.close()
                        val handler = ClientHandler(clientSocket, clientIp)
                        activeClient = handler
                        handler.start()
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            AppLogger.w(TAG, "Error en accept de socket: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error", e)
            } finally {
                closeInternal()
            }
        }

        // Stats ticker job
        statsJob = scope.launch(Dispatchers.Default) {
            while (isRunning.get() && isActive) {
                delay(1000)
                val fps = framesSentInSecond.getAndSet(0)
                val bytes = bytesSentInSecond.getAndSet(0L)
                val kbps = (bytes * 8) / 1024
                val activeCount = if (activeClient?.isAuthenticated?.get() == true) 1 else 0

                _stats.value = _stats.value.copy(
                    fps = fps,
                    bitrateKbps = kbps,
                    activeClients = activeCount,
                    framesTransmitted = totalFramesCounter.get()
                )
            }
        }
    }

    fun broadcastFrame(width: Int, height: Int, timestamp: Long, jpegData: ByteArray) {
        val client = activeClient
        if (client != null && client.isAuthenticated.get()) {
            client.enqueueFrame(width, height, timestamp, jpegData)
            framesSentInSecond.incrementAndGet()
            bytesSentInSecond.addAndGet(jpegData.size.toLong())
            totalFramesCounter.incrementAndGet()

            if (_stats.value.resolutionWidth != width || _stats.value.resolutionHeight != height) {
                _stats.value = _stats.value.copy(
                    resolutionWidth = width,
                    resolutionHeight = height
                )
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        acceptJob?.cancel()
        statsJob?.cancel()
        acceptJob = null
        statsJob = null
        closeInternal()
        _stats.value = StreamStats()
    }

    private fun closeInternal() {
        activeClient?.close()
        activeClient = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private inner class ClientHandler(
        private val socket: Socket,
        val clientIp: String
    ) {
        val isAuthenticated = AtomicBoolean(false)
        private var sendJob: Job? = null
        private var readJob: Job? = null
        private val frameQueue = ConcurrentLinkedQueue<VideoFrame>()

        private var lastTouchX = 0f
        private var lastTouchY = 0f

        fun start() {
            sendJob = scope.launch(Dispatchers.IO) {
                var out: DataOutputStream? = null
                try {
                    out = DataOutputStream(socket.getOutputStream())
                    val dis = DataInputStream(socket.getInputStream())

                    // Start reading incoming touch commands
                    startReader(dis, out)

                    while (isActive && !socket.isClosed) {
                        val frame = frameQueue.poll()
                        if (frame != null && isAuthenticated.get()) {
                            Protocol.writeFrame(out, frame.width, frame.height, frame.timestamp, frame.jpegData)
                        } else {
                            delay(5)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Client send loop ended: ${e.message}")
                } finally {
                    close()
                }
            }
        }

        private fun startReader(dis: DataInputStream, dos: DataOutputStream) {
            readJob = scope.launch(Dispatchers.IO) {
                try {
                    while (isActive && !socket.isClosed) {
                        val b1 = dis.readByte()
                        val b2 = dis.readByte()
                        if (b1 != Protocol.MAGIC_BYTE_1 || b2 != Protocol.MAGIC_BYTE_2) {
                            Log.w(TAG, "Invalid magic bytes from client")
                            break
                        }

                        val packetType = dis.readByte()
                        val payloadLen = dis.readInt()

                        when (packetType) {
                            Protocol.TYPE_AUTH -> {
                                val pinBytes = ByteArray(payloadLen)
                                dis.readFully(pinBytes)
                                val receivedPin = String(pinBytes, Charsets.UTF_8)
                                val isValid = (currentPin.isEmpty() || receivedPin == currentPin)
                                isAuthenticated.set(isValid)
                                Protocol.writeAuthResponse(dos, isValid)
                                if (isValid) {
                                    AppLogger.i(TAG, "Receptor $clientIp autenticado con éxito")
                                    onClientConnected(clientIp)
                                } else {
                                    AppLogger.e(TAG, "Receptor $clientIp rechazado: PIN incorrecto ($receivedPin)")
                                    break
                                }
                            }
                            Protocol.TYPE_TOUCH -> {
                                val action = dis.readByte()
                                val xRatio = dis.readFloat()
                                val yRatio = dis.readFloat()

                                if (isAuthenticated.get()) {
                                    handleTouch(action, xRatio, yRatio)
                                }
                            }
                            Protocol.TYPE_GLOBAL_ACTION -> {
                                val actionCode = dis.readByte()
                                if (isAuthenticated.get()) {
                                    CatMirrorAccessibilityService.getInstance()?.executeGlobal(actionCode)
                                }
                            }
                            Protocol.TYPE_PING -> {
                                val timestamp = dis.readLong()
                                Protocol.writePong(dos, timestamp)
                            }
                            Protocol.TYPE_DISCONNECT -> {
                                break
                            }
                            else -> {
                                // Skip unhandled payload
                                if (payloadLen > 0) {
                                    dis.skipBytes(payloadLen)
                                }
                            }
                        }
                    }
                } catch (_: EOFException) {
                    // Normal disconnect
                } catch (e: Exception) {
                    Log.w(TAG, "Client read loop error: ${e.message}")
                } finally {
                    close()
                }
            }
        }

        private fun handleTouch(action: Byte, xRatio: Float, yRatio: Float) {
            val a11y = CatMirrorAccessibilityService.getInstance()
            if (a11y == null) {
                Log.d(TAG, "Touch received but AccessibilityService is not bound!")
                return
            }

            when (action) {
                Protocol.TOUCH_DOWN -> {
                    lastTouchX = xRatio
                    lastTouchY = yRatio
                    a11y.injectTap(xRatio, yRatio)
                }
                Protocol.TOUCH_MOVE -> {
                    val dist = Math.hypot((xRatio - lastTouchX).toDouble(), (yRatio - lastTouchY).toDouble())
                    if (dist > 0.02) { // 2% minimum threshold to reduce jitter
                        a11y.injectDrag(lastTouchX, lastTouchY, xRatio, yRatio, 60)
                        lastTouchX = xRatio
                        lastTouchY = yRatio
                    }
                }
                Protocol.TOUCH_UP -> {
                    // Tap finalized
                }
            }
        }

        fun enqueueFrame(width: Int, height: Int, timestamp: Long, jpegData: ByteArray) {
            // Drop older frames if receiver cannot keep up to keep latency low!
            if (frameQueue.size > 2) {
                frameQueue.poll()
            }
            frameQueue.offer(VideoFrame(width, height, timestamp, jpegData))
        }

        fun close() {
            if (isAuthenticated.get()) {
                onClientDisconnected(clientIp)
            }
            isAuthenticated.set(false)
            sendJob?.cancel()
            readJob?.cancel()
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }
}
