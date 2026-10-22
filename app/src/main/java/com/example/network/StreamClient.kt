package com.example.network

import android.util.Log
import com.example.model.ConnectionStatus
import com.example.model.StreamStats
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
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class StreamClient(
    private val scope: CoroutineScope
) {
    private val TAG = "CatMirrorClient"

    private var socket: Socket? = null
    private var readJob: Job? = null
    private var writeJob: Job? = null
    private var pingJob: Job? = null
    private var statsJob: Job? = null

    private val isConnected = AtomicBoolean(false)
    private var outputStream: DataOutputStream? = null

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _currentFrame = MutableStateFlow<VideoFrame?>(null)
    val currentFrame: StateFlow<VideoFrame?> = _currentFrame.asStateFlow()

    private val _stats = MutableStateFlow(StreamStats())
    val stats: StateFlow<StreamStats> = _stats.asStateFlow()

    private val outgoingQueue = ConcurrentLinkedQueue<ByteArray>()
    private val framesReceivedInSecond = AtomicInteger(0)
    private val bytesReceivedInSecond = AtomicLong(0L)
    private val totalFramesCounter = AtomicLong(0L)
    private val currentLatencyMs = AtomicLong(0L)

    fun connect(host: String, port: Int = Protocol.DEFAULT_PORT, pin: String = "") {
        disconnect()
        _status.value = ConnectionStatus.CONNECTING
        AppLogger.i(TAG, "Iniciando conexión hacia $host:$port...")

        readJob = scope.launch(Dispatchers.IO) {
            try {
                val s = Socket()
                s.tcpNoDelay = true
                s.receiveBufferSize = 64 * 1024
                s.sendBufferSize = 32 * 1024
                s.connect(InetSocketAddress(host, port), 4000)
                socket = s
                AppLogger.i(TAG, "Socket TCP conectado a $host:$port. Enviando PIN...")

                val dis = DataInputStream(s.getInputStream())
                val dos = DataOutputStream(s.getOutputStream())
                outputStream = dos

                _status.value = ConnectionStatus.AUTHENTICATING
                Protocol.writeAuth(dos, pin)

                // Start outgoing writer job
                startWriter()
                startPingJob()
                startStatsJob()

                while (isActive && !s.isClosed) {
                    val b1 = dis.readByte()
                    val b2 = dis.readByte()
                    if (b1 != Protocol.MAGIC_BYTE_1 || b2 != Protocol.MAGIC_BYTE_2) {
                        AppLogger.w(TAG, "Encabezado de paquete inválido recibido del emisor")
                        break
                    }

                    val type = dis.readByte()
                    val payloadLen = dis.readInt()

                    when (type) {
                        Protocol.TYPE_AUTH_RESP -> {
                            val success = dis.readBoolean()
                            if (success) {
                                isConnected.set(true)
                                _status.value = ConnectionStatus.CONNECTED
                                AppLogger.i(TAG, "Autenticado con éxito. Recibiendo pantalla en tiempo real.")
                            } else {
                                AppLogger.e(TAG, "Error: Autenticación rechazada. Verifica el PIN.")
                                _status.value = ConnectionStatus.ERROR
                                break
                            }
                        }
                        Protocol.TYPE_FRAME -> {
                            val width = dis.readShort().toInt()
                            val height = dis.readShort().toInt()
                            val timestamp = dis.readLong()
                            val imageBytesLen = payloadLen - (2 + 2 + 8)

                            if (imageBytesLen > 0) {
                                val buffer = ByteArray(imageBytesLen)
                                dis.readFully(buffer)

                                val frame = VideoFrame(width, height, timestamp, buffer)
                                _currentFrame.value = frame

                                framesReceivedInSecond.incrementAndGet()
                                bytesReceivedInSecond.addAndGet(imageBytesLen.toLong())
                                totalFramesCounter.incrementAndGet()
                            }
                        }
                        Protocol.TYPE_PONG -> {
                            val originalTimestamp = dis.readLong()
                            val rtt = (System.currentTimeMillis() - originalTimestamp).coerceAtLeast(0)
                            currentLatencyMs.set(rtt)
                        }
                        else -> {
                            if (payloadLen > 0) {
                                dis.skipBytes(payloadLen)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Conexión interrumpida o cerrada: ${e.message}", e)
                if (_status.value != ConnectionStatus.DISCONNECTED) {
                    _status.value = ConnectionStatus.ERROR
                }
            } finally {
                cleanup()
            }
        }
    }

    private fun startWriter() {
        writeJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive && !socket!!.isClosed) {
                    val data = outgoingQueue.poll()
                    if (data != null && outputStream != null) {
                        outputStream?.write(data)
                        outputStream?.flush()
                    } else {
                        delay(2)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun startPingJob() {
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive && isConnected.get()) {
                delay(1500)
                try {
                    val out = outputStream
                    if (out != null) {
                        Protocol.writePing(out, System.currentTimeMillis())
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun startStatsJob() {
        statsJob = scope.launch(Dispatchers.Default) {
            while (isActive && isConnected.get()) {
                delay(1000)
                val fps = framesReceivedInSecond.getAndSet(0)
                val bytes = bytesReceivedInSecond.getAndSet(0L)
                val kbps = (bytes * 8) / 1024

                _stats.value = _stats.value.copy(
                    fps = fps,
                    bitrateKbps = kbps,
                    latencyMs = currentLatencyMs.get(),
                    framesTransmitted = totalFramesCounter.get(),
                    resolutionWidth = _currentFrame.value?.width ?: 0,
                    resolutionHeight = _currentFrame.value?.height ?: 0
                )
            }
        }
    }

    fun sendTouch(action: Byte, xRatio: Float, yRatio: Float) {
        if (!isConnected.get()) return
        scope.launch(Dispatchers.IO) {
            try {
                val out = outputStream ?: return@launch
                Protocol.writeTouch(out, action, xRatio, yRatio)
            } catch (_: Exception) {}
        }
    }

    fun sendGlobalAction(actionCode: Byte) {
        if (!isConnected.get()) return
        scope.launch(Dispatchers.IO) {
            try {
                val out = outputStream ?: return@launch
                Protocol.writeGlobalAction(out, actionCode)
            } catch (_: Exception) {}
        }
    }

    fun disconnect() {
        _status.value = ConnectionStatus.DISCONNECTED
        cleanup()
    }

    private fun cleanup() {
        isConnected.set(false)
        readJob?.cancel()
        writeJob?.cancel()
        pingJob?.cancel()
        statsJob?.cancel()
        readJob = null
        writeJob = null
        pingJob = null
        statsJob = null

        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        outputStream = null
        outgoingQueue.clear()
        _currentFrame.value = null
        _stats.value = StreamStats()
    }
}
