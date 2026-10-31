package com.example.network

import android.net.Uri
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
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class StreamServer(
    private val scope: CoroutineScope,
    private val port: Int = Protocol.DEFAULT_PORT,
    private val onClientConnected: (String) -> Unit = {},
    private val onClientDisconnected: (String) -> Unit = {},
    private val onToggleDim: ((Boolean) -> Unit)? = null
) {
    private val TAG = "CatMirrorServer"

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var statsJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    private var currentPin: String = ""
    private val activeClients = CopyOnWriteArrayList<ClientSession>()

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
                var bound = false
                var attempts = 0
                while (!bound && attempts < 3 && isRunning.get()) {
                    try {
                        attempts++
                        try { serverSocket?.close() } catch (_: Exception) {}
                        serverSocket = ServerSocket().apply {
                            reuseAddress = true
                            bind(java.net.InetSocketAddress(port))
                        }
                        bound = true
                    } catch (e: java.net.BindException) {
                        if (attempts >= 3) throw e
                        AppLogger.w(TAG, "Puerto $port ocupado temporalmente, reintentando en 350ms (intento $attempts)...")
                        delay(350)
                    }
                }
                AppLogger.i(TAG, "Servidor catmirror activo en puerto $port (TCP + Web/Chrome Extension)")

                while (isRunning.get() && isActive) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        socket.tcpNoDelay = true
                        val clientIp = socket.inetAddress?.hostAddress ?: "desconocido"
                        AppLogger.i(TAG, "Conexión entrante desde $clientIp")

                        scope.launch(Dispatchers.IO) {
                            handleConnection(socket, clientIp)
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            AppLogger.w(TAG, "Error en accept: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Fallo al iniciar ServerSocket", e)
            } finally {
                closeInternal()
            }
        }

        statsJob = scope.launch(Dispatchers.Default) {
            while (isRunning.get() && isActive) {
                delay(1000)
                val fps = framesSentInSecond.getAndSet(0)
                val bytes = bytesSentInSecond.getAndSet(0L)
                val kbps = (bytes * 8) / 1024
                val count = activeClients.count { it.isAuthenticated }

                _stats.value = _stats.value.copy(
                    fps = fps,
                    bitrateKbps = kbps,
                    activeClients = count,
                    framesTransmitted = totalFramesCounter.get()
                )
            }
        }
    }

    private suspend fun handleConnection(socket: Socket, clientIp: String) {
        val bis = BufferedInputStream(socket.getInputStream(), 8192)
        bis.mark(16)
        val b1 = bis.read()
        val b2 = bis.read()
        bis.reset()

        val magic1 = Protocol.MAGIC_BYTE_1.toInt() and 0xFF
        val magic2 = Protocol.MAGIC_BYTE_2.toInt() and 0xFF

        if (b1 == magic1 && b2 == magic2) {
            // Android Binary Protocol
            AppLogger.i(TAG, "Detectada conexión binaria Android CatMirror desde $clientIp")
            val handler = BinaryClientHandler(socket, clientIp, bis)
            activeClients.add(handler)
            try {
                handler.run()
            } finally {
                activeClients.remove(handler)
            }
        } else {
            // HTTP / Web / Chrome Extension Request
            AppLogger.i(TAG, "Detectada petición HTTP/Web desde $clientIp (b1=$b1, b2=$b2)")
            handleHttpRequest(socket, clientIp, bis)
        }
    }

    private suspend fun handleHttpRequest(socket: Socket, clientIp: String, bis: BufferedInputStream) {
        try {
            socket.soTimeout = 6000
            val reader = BufferedReader(InputStreamReader(bis, Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val fullUrl = parts[1]
            val uri = Uri.parse(fullUrl)
            val path = uri.path ?: "/"

            val out = socket.getOutputStream()

            if (method == "OPTIONS") {
                sendCorsOk(out)
                socket.close()
                return
            }

            when {
                path == "/stream" -> {
                    val reqPin = uri.getQueryParameter("pin") ?: ""
                    if (currentPin.isNotEmpty() && reqPin != currentPin) {
                        sendUnauthorized(out)
                        socket.close()
                        return
                    }

                    val webHandler = WebClientHandler(socket, clientIp)
                    activeClients.add(webHandler)
                    AppLogger.i(TAG, "Navegador/Extensión Chrome conectado desde $clientIp")
                    onClientConnected(clientIp)
                    webHandler.run(out)
                    activeClients.remove(webHandler)
                    onClientDisconnected(clientIp)
                }

                path == "/touch" -> {
                    val reqPin = uri.getQueryParameter("pin") ?: ""
                    if (currentPin.isEmpty() || reqPin == currentPin) {
                        val action = uri.getQueryParameter("action")?.toIntOrNull() ?: 1
                        val x = uri.getQueryParameter("x")?.toFloatOrNull() ?: 0.5f
                        val y = uri.getQueryParameter("y")?.toFloatOrNull() ?: 0.5f
                        val sx = uri.getQueryParameter("sx")?.toFloatOrNull() ?: x
                        val sy = uri.getQueryParameter("sy")?.toFloatOrNull() ?: y
                        val dur = uri.getQueryParameter("dur")?.toLongOrNull() ?: 150L

                        val a11y = CatMirrorAccessibilityService.getInstance()
                        if (a11y == null) {
                            AppLogger.w(TAG, "Toque recibido desde navegador pero CatMirrorAccessibilityService está inactivo en el teléfono")
                            sendTextResponse(out, "a11y_inactive")
                        } else {
                            val ok = when (action) {
                                0, 1 -> a11y.injectTap(x, y)
                                2 -> a11y.injectDrag(sx, sy, x, y, dur)
                                else -> a11y.injectTap(x, y)
                            }
                            sendTextResponse(out, if (ok) "ok" else "error")
                        }
                    } else {
                        sendUnauthorized(out)
                    }
                    socket.close()
                }

                path == "/key" -> {
                    val reqPin = uri.getQueryParameter("pin") ?: ""
                    if (currentPin.isEmpty() || reqPin == currentPin) {
                        val code = uri.getQueryParameter("code")?.toByteOrNull() ?: 1
                        CatMirrorAccessibilityService.getInstance()?.executeGlobal(code)
                        sendTextResponse(out, "ok")
                    } else {
                        sendUnauthorized(out)
                    }
                    socket.close()
                }

                path == "/dim" -> {
                    val reqPin = uri.getQueryParameter("pin") ?: ""
                    if (currentPin.isEmpty() || reqPin == currentPin) {
                        val state = uri.getQueryParameter("state")
                        val enable = if (state == "0") false else true
                        onToggleDim?.invoke(enable)
                        sendTextResponse(out, "ok")
                    } else {
                        sendUnauthorized(out)
                    }
                    socket.close()
                }

                path == "/ping" -> {
                    sendJsonResponse(out, "{\"status\":\"ok\",\"clients\":${activeClients.size},\"pinRequired\":${currentPin.isNotEmpty()}}")
                    socket.close()
                }

                else -> {
                    // Serve embedded HTML5 Web Remote Viewer
                    sendHtmlResponse(out, getEmbeddedWebRemoteHtml())
                    socket.close()
                }
            }
        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendCorsOk(out: OutputStream) {
        val resp = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n\r\n"
        out.write(resp.toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun sendTextResponse(out: OutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: ${bytes.size}\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun sendJsonResponse(out: OutputStream, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: ${bytes.size}\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun sendUnauthorized(out: OutputStream) {
        val msg = "PIN de seguridad incorrecto".toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 401 Unauthorized\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: ${msg.size}\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(msg)
        out.flush()
    }

    private fun sendHtmlResponse(out: OutputStream, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n\r\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    fun broadcastFrame(width: Int, height: Int, timestamp: Long, jpegData: ByteArray) {
        if (activeClients.isEmpty()) return

        for (client in activeClients) {
            if (client.isAuthenticated) {
                client.enqueue(width, height, timestamp, jpegData)
            }
        }

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
        for (client in activeClients) {
            client.close()
        }
        activeClients.clear()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    interface ClientSession {
        val isAuthenticated: Boolean
        fun enqueue(width: Int, height: Int, timestamp: Long, jpegData: ByteArray)
        fun close()
    }

    // Binary Protocol Client (Android App)
    private inner class BinaryClientHandler(
        private val socket: Socket,
        val clientIp: String,
        private val bis: BufferedInputStream
    ) : ClientSession {
        private var _auth = false
        override val isAuthenticated: Boolean get() = _auth
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var latestFrame: VideoFrame? = null

        override fun enqueue(width: Int, height: Int, timestamp: Long, jpegData: ByteArray) {
            latestFrame = VideoFrame(width, height, timestamp, jpegData)
        }

        suspend fun run() {
            var dos: DataOutputStream? = null
            var dis: DataInputStream? = null
            try {
                socket.soTimeout = 15000
                dos = DataOutputStream(socket.getOutputStream())
                dis = DataInputStream(bis)

                val readJob = scope.launch(Dispatchers.IO) {
                    try {
                        while (isActive && !socket.isClosed) {
                            val b1 = dis.readByte()
                            val b2 = dis.readByte()
                            if (b1 != Protocol.MAGIC_BYTE_1 || b2 != Protocol.MAGIC_BYTE_2) {
                                AppLogger.w(TAG, "Encabezado inválido de $clientIp: b1=$b1, b2=$b2")
                                break
                            }

                            val packetType = dis.readByte()
                            val payloadLen = dis.readInt()

                            when (packetType) {
                                Protocol.TYPE_AUTH -> {
                                    val pinBytes = ByteArray(payloadLen)
                                    dis.readFully(pinBytes)
                                    val receivedPin = String(pinBytes, Charsets.UTF_8)
                                    val valid = (currentPin.isEmpty() || receivedPin == currentPin)
                                    _auth = valid
                                    Protocol.writeAuthResponse(dos, valid)
                                    if (valid) {
                                        AppLogger.i(TAG, "Receptor Android $clientIp autenticado con éxito")
                                        onClientConnected(clientIp)
                                    } else {
                                        AppLogger.e(TAG, "PIN inválido desde $clientIp (recibido '$receivedPin', esperado '$currentPin')")
                                        break
                                    }
                                }
                                Protocol.TYPE_TOUCH -> {
                                    val action = dis.readByte()
                                    val xRatio = dis.readFloat()
                                    val yRatio = dis.readFloat()
                                    if (_auth) handleTouch(action, xRatio, yRatio)
                                }
                                Protocol.TYPE_GLOBAL_ACTION -> {
                                    val code = dis.readByte()
                                    if (_auth) CatMirrorAccessibilityService.getInstance()?.executeGlobal(code)
                                }
                                Protocol.TYPE_PING -> {
                                    val ts = dis.readLong()
                                    Protocol.writePong(dos, ts)
                                }
                                else -> {
                                    if (payloadLen > 0) dis.skipBytes(payloadLen)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (_auth) {
                            AppLogger.i(TAG, "Receptor Android $clientIp desconectado (${e.message ?: e.javaClass.simpleName})")
                        }
                    } finally {
                        close()
                    }
                }

                while (scope.isActive && !socket.isClosed) {
                    val frame = latestFrame
                    if (frame != null && _auth) {
                        latestFrame = null
                        try {
                            Protocol.writeFrame(dos, frame.width, frame.height, frame.timestamp, frame.jpegData)
                        } catch (e: Exception) {
                            AppLogger.i(TAG, "Error enviando frame a $clientIp: ${e.message}")
                            break
                        }
                    } else {
                        delay(6)
                    }
                }
                readJob.cancel()
            } catch (e: Exception) {
                AppLogger.w(TAG, "Sesión de $clientIp finalizada: ${e.message}")
            } finally {
                close()
            }
        }

        private fun handleTouch(action: Byte, xRatio: Float, yRatio: Float) {
            val a11y = CatMirrorAccessibilityService.getInstance() ?: return
            when (action) {
                Protocol.TOUCH_DOWN -> {
                    lastTouchX = xRatio
                    lastTouchY = yRatio
                    a11y.injectTap(xRatio, yRatio)
                }
                Protocol.TOUCH_MOVE -> {
                    val dist = Math.hypot((xRatio - lastTouchX).toDouble(), (yRatio - lastTouchY).toDouble())
                    if (dist > 0.02) {
                        a11y.injectDrag(lastTouchX, lastTouchY, xRatio, yRatio, 60)
                        lastTouchX = xRatio
                        lastTouchY = yRatio
                    }
                }
            }
        }

        override fun close() {
            if (_auth) onClientDisconnected(clientIp)
            _auth = false
            try { socket.close() } catch (_: Exception) {}
        }
    }

    // Web Client / Chrome Extension (MJPEG Stream)
    private inner class WebClientHandler(
        private val socket: Socket,
        val clientIp: String
    ) : ClientSession {
        override val isAuthenticated: Boolean = true
        private var latestJpeg: ByteArray? = null

        override fun enqueue(width: Int, height: Int, timestamp: Long, jpegData: ByteArray) {
            latestJpeg = jpegData
        }

        suspend fun run(out: OutputStream) {
            try {
                val boundary = "catframe"
                val mjpegHeader = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: multipart/x-mixed-replace; boundary=--$boundary\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                        "Connection: close\r\n\r\n"
                out.write(mjpegHeader.toByteArray(Charsets.UTF_8))
                out.flush()

                while (scope.isActive && !socket.isClosed) {
                    val frame = latestJpeg
                    if (frame != null) {
                        latestJpeg = null
                        val partHeader = "--$boundary\r\n" +
                                "Content-Type: image/jpeg\r\n" +
                                "Content-Length: ${frame.size}\r\n\r\n"
                        out.write(partHeader.toByteArray(Charsets.UTF_8))
                        out.write(frame)
                        out.write("\r\n".toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    delay(30)
                }
            } catch (_: Exception) {} finally {
                close()
            }
        }

        override fun close() {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun getEmbeddedWebRemoteHtml(): String {
        return """
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>catmirror — Control Remoto en Vivo</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background-color: #0F141C;
            color: #F8FAFC;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            display: flex;
            flex-direction: column;
            align-items: center;
            min-height: 100vh;
            user-select: none;
            overflow-x: hidden;
        }
        header {
            width: 100%;
            background-color: #182230;
            padding: 10px 20px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid rgba(255, 158, 44, 0.3);
            flex-wrap: wrap;
            gap: 10px;
            z-index: 10;
        }
        .logo { font-size: 17px; font-weight: bold; color: #FF9E2C; display: flex; align-items: center; gap: 8px; }
        .controls { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
        input {
            background: #0F141C; border: 1px solid #00E5FF; color: white;
            padding: 7px 10px; border-radius: 8px; font-size: 13px;
            font-family: monospace; letter-spacing: 2px; width: 130px; text-align: center;
        }
        button {
            background: #FF9E2C; border: none; color: black; font-weight: bold;
            padding: 8px 14px; border-radius: 8px; cursor: pointer; transition: 0.2s; font-size: 13px;
            display: inline-flex; align-items: center; gap: 4px;
        }
        button:hover { background: #FFBE76; }
        .btn-secondary {
            background: #222F42; color: #00E5FF; border: 1px solid rgba(0, 229, 255, 0.4);
        }
        .btn-secondary:hover {
            background: #00E5FF; color: #000;
        }
        .rot-pill {
            background: #182230; color: #94A3B8; border: 1px solid #334155;
            padding: 6px 10px; border-radius: 6px; font-size: 12px; cursor: pointer;
        }
        .rot-pill.active {
            background: #00E5FF; color: #0F141C; font-weight: bold; border-color: #00E5FF;
        }
        .viewport-wrapper {
            flex: 1;
            display: flex;
            justify-content: center;
            align-items: center;
            width: 100%;
            height: calc(100vh - 145px);
            padding: 8px;
            overflow: hidden;
            position: relative;
        }
        .viewport-container {
            position: relative;
            border: 2px solid rgba(0, 229, 255, 0.5);
            border-radius: 18px;
            overflow: hidden;
            background: #000;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.8), 0 0 20px rgba(0, 229, 255, 0.15);
            display: flex;
            justify-content: center;
            align-items: center;
            cursor: grab;
            transition: width 0.25s ease, height 0.25s ease;
        }
        .viewport-container:active {
            cursor: grabbing;
        }
        #stream-img {
            position: absolute;
            left: 50%;
            top: 50%;
            display: block;
            user-select: none;
            -webkit-user-drag: none;
            pointer-events: none;
            object-fit: fill;
            transform-origin: center center;
            transition: transform 0.25s ease, width 0.25s ease, height 0.25s ease;
        }
        .nav-bar {
            margin-top: 4px; margin-bottom: 10px;
            display: flex; gap: 10px; background: #182230; padding: 7px 18px;
            border-radius: 30px; border: 1px solid rgba(255, 158, 44, 0.4);
            flex-wrap: wrap; justify-content: center; z-index: 10;
        }
        .nav-btn { background: #222F42; color: #FF9E2C; padding: 7px 15px; border-radius: 20px; font-size: 13px; }
        .nav-btn:hover { background: #FF9E2C; color: black; }
        .nav-btn-scroll { background: #1B2E3D; color: #00E5FF; border: 1px solid rgba(0, 229, 255, 0.3); }
        .nav-btn-scroll:hover { background: #00E5FF; color: black; }
        .ripple {
            position: absolute; border: 2px solid #00E5FF; border-radius: 50%;
            pointer-events: none; transform: translate(-50%, -50%);
            animation: rip 0.35s ease-out forwards;
        }
        @keyframes rip { from { width: 6px; height: 6px; opacity: 1; } to { width: 44px; height: 44px; opacity: 0; } }
        .status-msg {
            font-size: 11px; color: #94A3B8; font-family: monospace; margin-bottom: 4px; text-align: center;
        }
    </style>
</head>
<body>
    <header>
        <div class="logo">🐱 catmirror Control Remoto</div>
        <div class="controls">
            <input type="text" id="pin-input" placeholder="PIN 6 dígitos" maxlength="6" />
            <button id="connect-btn">Conectar</button>
            <div style="display: flex; gap: 4px; align-items: center; background: #0F141C; padding: 3px 6px; border-radius: 8px; border: 1px solid #334155;">
                <span style="font-size: 11px; color: #94A3B8; margin-right: 4px;">Rotar:</span>
                <button class="rot-pill" id="rot-left" title="Girar -90° izquierda">↶ -90°</button>
                <button class="rot-pill" id="rot-right" title="Girar +90° derecha">↷ +90°</button>
                <button class="rot-pill active" id="rot-0" onclick="setRotation(0)">0°</button>
                <button class="rot-pill" id="rot-90" onclick="setRotation(90)">90°</button>
                <button class="rot-pill" id="rot-180" onclick="setRotation(180)">180°</button>
                <button class="rot-pill" id="rot-270" onclick="setRotation(270)">270°</button>
            </div>
            <button class="btn-secondary" id="fullscreen-btn" onclick="toggleFullscreen()" title="Pantalla completa">⛶ Pantalla Completa</button>
            <button class="btn-secondary" id="dim-btn" onclick="toggleDim()">🌙 Modo Ahorro</button>
        </div>
    </header>

    <div class="viewport-wrapper" id="stage">
        <div class="viewport-container" id="viewport">
            <img id="stream-img" alt="Pantalla de catmirror" src="" draggable="false" />
        </div>
    </div>

    <div class="status-msg" id="status-label">Rueda del ratón o arrastrar para scroll • Clic para tocar</div>

    <div class="nav-bar">
        <button class="nav-btn" onclick="sendKey(1)">◀ Atrás</button>
        <button class="nav-btn" onclick="sendKey(2)">⚪ Inicio</button>
        <button class="nav-btn" onclick="sendKey(3)">▦ Recientes</button>
        <span style="border-left: 1px solid rgba(255, 158, 44, 0.3); margin: 0 4px;"></span>
        <button class="nav-btn nav-btn-scroll" onclick="scrollPhone('up')">⬆ Subir (Scroll)</button>
        <button class="nav-btn nav-btn-scroll" onclick="scrollPhone('down')">⬇ Bajar (Scroll)</button>
    </div>

    <script>
        const img = document.getElementById('stream-img');
        const viewport = document.getElementById('viewport');
        const stage = document.getElementById('stage') || document.querySelector('.viewport-wrapper');
        const pinInput = document.getElementById('pin-input');
        const connectBtn = document.getElementById('connect-btn');
        const statusLabel = document.getElementById('status-label');

        let rotationAngle = 0;

        function updateLayout() {
            const stageW = (stage ? stage.clientWidth : window.innerWidth) - 24;
            const stageH = (stage ? stage.clientHeight : (window.innerHeight - 145)) - 24;
            if (stageW <= 0 || stageH <= 0) return;

            const natW = img.naturalWidth > 0 ? img.naturalWidth : 720;
            const natH = img.naturalHeight > 0 ? img.naturalHeight : 1560;

            const isSideways = (rotationAngle === 90 || rotationAngle === 270);

            // Relación de aspecto efectiva deseada en pantalla (ancho / alto):
            const effectiveAspect = isSideways ? (natH / natW) : (natW / natH);

            let containerW, containerH;
            if (effectiveAspect > (stageW / stageH)) {
                // Limitado por el ancho disponible del navegador
                containerW = stageW;
                containerH = stageW / effectiveAspect;
            } else {
                // Limitado por la altura disponible del navegador
                containerH = stageH;
                containerW = stageH * effectiveAspect;
            }

            viewport.style.width = Math.round(containerW) + 'px';
            viewport.style.height = Math.round(containerH) + 'px';

            if (!isSideways) {
                img.style.width = Math.round(containerW) + 'px';
                img.style.height = Math.round(containerH) + 'px';
            } else {
                // Dimensiones invertidas para que la imagen rotada 90/270deg llene exactamente el marco
                img.style.width = Math.round(containerH) + 'px';
                img.style.height = Math.round(containerW) + 'px';
            }

            img.style.position = 'absolute';
            img.style.left = '50%';
            img.style.top = '50%';
            img.style.transform = 'translate(-50%, -50%) rotate(' + rotationAngle + 'deg)';
            img.style.transformOrigin = 'center center';
        }

        img.addEventListener('load', updateLayout);
        window.addEventListener('resize', updateLayout);
        setTimeout(updateLayout, 150);

        let lastNatW = 0, lastNatH = 0;
        setInterval(() => {
            if (img.naturalWidth > 0 && (img.naturalWidth !== lastNatW || img.naturalHeight !== lastNatH)) {
                const wasLandscape = (lastNatW > lastNatH);
                const isNowLandscape = (img.naturalWidth > img.naturalHeight);
                lastNatW = img.naturalWidth;
                lastNatH = img.naturalHeight;

                if (lastNatW > 0 && wasLandscape !== isNowLandscape) {
                    if (isNowLandscape && (rotationAngle === 90 || rotationAngle === 270)) {
                        setRotation(0);
                    } else {
                        updateLayout();
                    }
                } else {
                    updateLayout();
                }
            }
        }, 350);

        function setRotation(deg) {
            rotationAngle = (deg % 360 + 360) % 360;
            [0, 90, 180, 270].forEach(a => {
                const pill = document.getElementById('rot-' + a);
                if (pill) pill.classList.toggle('active', a === rotationAngle);
            });
            const names = { 0: 'Vertical (0°)', 90: 'Horizontal (90°)', 180: 'Invertido (180°)', 270: 'Horizontal Invertido (270°)' };
            const isHorizStream = (img.naturalWidth > img.naturalHeight);
            statusLabel.textContent = (isHorizStream ? '📺 Pantalla Horizontal (' + img.naturalWidth + 'x' + img.naturalHeight + ')' : '📱 Pantalla Vertical (' + img.naturalWidth + 'x' + img.naturalHeight + ')') + ' • ' + (names[rotationAngle] || rotationAngle + '°');
            updateLayout();
        }

        document.getElementById('rot-left').onclick = () => setRotation(rotationAngle - 90);
        document.getElementById('rot-right').onclick = () => setRotation(rotationAngle + 90);

        function toggleFullscreen() {
            if (!document.fullscreenElement) {
                document.documentElement.requestFullscreen().catch(() => {});
            } else {
                document.exitFullscreen().catch(() => {});
            }
            setTimeout(updateLayout, 200);
        }
        document.addEventListener('fullscreenchange', () => setTimeout(updateLayout, 200));

        function connect() {
            const pin = pinInput.value.trim();
            img.src = '/stream?pin=' + encodeURIComponent(pin) + '&t=' + Date.now();
            statusLabel.textContent = 'Transmisión activa con rotación de ' + rotationAngle + '°';
            setTimeout(updateLayout, 200);
        }

        connectBtn.onclick = connect;

        const urlParams = new URLSearchParams(window.location.search);
        if (urlParams.get('pin')) {
            pinInput.value = urlParams.get('pin');
        }

        let isDimmed = false;
        function toggleDim() {
            isDimmed = !isDimmed;
            const pin = pinInput.value.trim();
            const btn = document.getElementById('dim-btn');
            btn.textContent = isDimmed ? '☀️ Restaurar Brillo' : '🌙 Modo Ahorro';
            statusLabel.textContent = isDimmed ? 'Pantalla del teléfono atenuada al mínimo para ahorrar batería' : 'Brillo normal restaurado en el teléfono';
            fetch('/dim?state=' + (isDimmed ? '1' : '0') + '&pin=' + encodeURIComponent(pin)).catch(()=>{});
        }

        function mapToPhone(relX, relY) {
            let px = relX;
            let py = relY;
            if (rotationAngle === 90) {
                px = relY;
                py = 1 - relX;
            } else if (rotationAngle === 180) {
                px = 1 - relX;
                py = 1 - relY;
            } else if (rotationAngle === 270) {
                px = 1 - relY;
                py = relX;
            }
            return {
                x: Math.max(0, Math.min(1, px)),
                y: Math.max(0, Math.min(1, py))
            };
        }

        let isMouseDown = false;
        let startClientX = 0, startClientY = 0;
        let downTime = 0;

        viewport.addEventListener('mousedown', (e) => {
            if (e.button !== 0) return;
            e.preventDefault();
            isMouseDown = true;
            downTime = Date.now();
            startClientX = e.clientX;
            startClientY = e.clientY;
            showRipple(e.clientX - viewport.getBoundingClientRect().left, e.clientY - viewport.getBoundingClientRect().top);
        });

        window.addEventListener('mouseup', (e) => {
            if (!isMouseDown) return;
            isMouseDown = false;
            const rect = viewport.getBoundingClientRect();
            const startRelX = Math.max(0, Math.min(1, (startClientX - rect.left) / rect.width));
            const startRelY = Math.max(0, Math.min(1, (startClientY - rect.top) / rect.height));
            const endRelX = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
            const endRelY = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height));

            const startP = mapToPhone(startRelX, startRelY);
            const endP = mapToPhone(endRelX, endRelY);

            const dist = Math.hypot(endP.x - startP.x, endP.y - startP.y);
            const duration = Math.min(500, Math.max(100, Date.now() - downTime));

            if (dist < 0.02) {
                sendTouch(1, endP.x, endP.y);
            } else {
                sendTouch(2, endP.x, endP.y, startP.x, startP.y, duration);
            }
        });

        function scrollPhone(direction) {
            const scrollDown = (direction === 'down');
            statusLabel.textContent = scrollDown ? 'Desplazando hacia abajo (scroll down)...' : 'Desplazando hacia arriba (scroll up)...';
            const startRelY = scrollDown ? 0.75 : 0.25;
            const endRelY = scrollDown ? 0.25 : 0.75;
            const startP = mapToPhone(0.5, startRelY);
            const endP = mapToPhone(0.5, endRelY);
            sendTouch(2, endP.x, endP.y, startP.x, startP.y, 250);
        }

        // Mouse wheel scroll support with gesture throttling
        let isWheelScrolling = false;
        let wheelAccumulator = 0;

        viewport.addEventListener('wheel', (e) => {
            e.preventDefault();
            wheelAccumulator += e.deltaY;

            if (isWheelScrolling) return;
            if (Math.abs(wheelAccumulator) < 20) return;

            const scrollDown = wheelAccumulator > 0;
            wheelAccumulator = 0;
            isWheelScrolling = true;

            const rect = viewport.getBoundingClientRect();
            const cursorRelX = Math.max(0.2, Math.min(0.8, (e.clientX - rect.left) / rect.width));
            const cursorRelY = Math.max(0.25, Math.min(0.75, (e.clientY - rect.top) / rect.height));

            const travel = 0.35;
            let startRelY, endRelY;
            if (scrollDown) {
                startRelY = Math.min(0.85, cursorRelY + travel / 2);
                endRelY = Math.max(0.15, startRelY - travel);
            } else {
                startRelY = Math.max(0.15, cursorRelY - travel / 2);
                endRelY = Math.min(0.85, startRelY + travel);
            }

            const startP = mapToPhone(cursorRelX, startRelY);
            const endP = mapToPhone(cursorRelX, endRelY);

            statusLabel.textContent = scrollDown ? 'Desplazando hacia abajo...' : 'Desplazando hacia arriba...';
            sendTouch(2, endP.x, endP.y, startP.x, startP.y, 220);

            setTimeout(() => {
                isWheelScrolling = false;
            }, 280);
        }, { passive: false });

        // Keyboard navigation shortcuts
        window.addEventListener('keydown', (e) => {
            if (e.target.tagName === 'INPUT') return;
            if (e.key === 'ArrowDown' || e.key === 'PageDown' || e.key === ' ') {
                e.preventDefault();
                scrollPhone('down');
            } else if (e.key === 'ArrowUp' || e.key === 'PageUp') {
                e.preventDefault();
                scrollPhone('up');
            } else if (e.key === 'Escape') {
                sendKey(1);
            }
        });

        // Touch event support for tablets & mobile browsers
        viewport.addEventListener('touchstart', (e) => {
            if (e.touches.length === 1) {
                const t = e.touches[0];
                isMouseDown = true;
                downTime = Date.now();
                startClientX = t.clientX;
                startClientY = t.clientY;
                showRipple(t.clientX - viewport.getBoundingClientRect().left, t.clientY - viewport.getBoundingClientRect().top);
            }
        }, { passive: true });

        viewport.addEventListener('touchend', (e) => {
            if (!isMouseDown) return;
            isMouseDown = false;
            const t = e.changedTouches[0];
            const rect = viewport.getBoundingClientRect();
            const startRelX = Math.max(0, Math.min(1, (startClientX - rect.left) / rect.width));
            const startRelY = Math.max(0, Math.min(1, (startClientY - rect.top) / rect.height));
            const endRelX = Math.max(0, Math.min(1, (t.clientX - rect.left) / rect.width));
            const endRelY = Math.max(0, Math.min(1, (t.clientY - rect.top) / rect.height));

            const startP = mapToPhone(startRelX, startRelY);
            const endP = mapToPhone(endRelX, endRelY);
            const dist = Math.hypot(endP.x - startP.x, endP.y - startP.y);
            const duration = Math.min(500, Math.max(100, Date.now() - downTime));
            if (dist < 0.02) {
                sendTouch(1, endP.x, endP.y);
            } else {
                sendTouch(2, endP.x, endP.y, startP.x, startP.y, duration);
            }
        }, { passive: true });

        function sendTouch(action, x, y, sx = x, sy = y, dur = 150) {
            const pin = pinInput.value.trim();
            statusLabel.textContent = (action === 2 ? 'Deslizando...' : 'Tap en ' + (x * 100).toFixed(0) + '%, ' + (y * 100).toFixed(0) + '%');
            fetch('/touch?action=' + action + '&x=' + x.toFixed(4) + '&y=' + y.toFixed(4) + '&sx=' + sx.toFixed(4) + '&sy=' + sy.toFixed(4) + '&dur=' + dur + '&pin=' + encodeURIComponent(pin))
                .then(r => r.text())
                .then(t => {
                    if (t === 'a11y_inactive') {
                        statusLabel.textContent = '⚠️ Toque no inyectado: Servicio de Accesibilidad desactivado en el teléfono';
                    } else if (t === 'ok') {
                        statusLabel.textContent = 'Toque ejecutado correctamente';
                    }
                })
                .catch(() => {
                    statusLabel.textContent = 'Error de conexión con el dispositivo';
                });
        }

        function sendKey(code) {
            const pin = pinInput.value.trim();
            const names = { 1: 'Atrás', 2: 'Inicio', 3: 'Recientes' };
            statusLabel.textContent = 'Botón: ' + (names[code] || code);
            fetch('/key?code=' + code + '&pin=' + encodeURIComponent(pin)).catch(()=>{});
        }

        function showRipple(x, y) {
            const r = document.createElement('div');
            r.className = 'ripple';
            r.style.left = x + 'px';
            r.style.top = y + 'px';
            viewport.appendChild(r);
            setTimeout(() => r.remove(), 350);
        }

        connect();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
