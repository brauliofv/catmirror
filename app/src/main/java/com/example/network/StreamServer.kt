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
    private val onClientDisconnected: (String) -> Unit = {}
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
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
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
        val bis = BufferedInputStream(socket.getInputStream())
        bis.mark(4)
        val b1 = bis.read()
        val b2 = bis.read()
        bis.reset()

        if (b1 == Protocol.MAGIC_BYTE_1.toInt() && b2 == Protocol.MAGIC_BYTE_2.toInt()) {
            // Android Binary Protocol
            val handler = BinaryClientHandler(socket, clientIp, bis)
            activeClients.add(handler)
            handler.run()
            activeClients.remove(handler)
        } else {
            // HTTP / Web / Chrome Extension Request
            handleHttpRequest(socket, clientIp, bis)
        }
    }

    private suspend fun handleHttpRequest(socket: Socket, clientIp: String, bis: BufferedInputStream) {
        try {
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
                        val action = uri.getQueryParameter("action")?.toIntOrNull() ?: 0
                        val x = uri.getQueryParameter("x")?.toFloatOrNull() ?: 0.5f
                        val y = uri.getQueryParameter("y")?.toFloatOrNull() ?: 0.5f

                        val a11y = CatMirrorAccessibilityService.getInstance()
                        if (action == 0 || action == 1) {
                            a11y?.injectTap(x, y)
                        } else if (action == 2) {
                            val startX = uri.getQueryParameter("sx")?.toFloatOrNull() ?: x
                            val startY = uri.getQueryParameter("sy")?.toFloatOrNull() ?: y
                            a11y?.injectDrag(startX, startY, x, y, 60)
                        }
                        sendTextResponse(out, "ok")
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
                dos = DataOutputStream(socket.getOutputStream())
                dis = DataInputStream(bis)

                val readJob = scope.launch(Dispatchers.IO) {
                    while (isActive && !socket.isClosed) {
                        try {
                            val b1 = dis.readByte()
                            val b2 = dis.readByte()
                            if (b1 != Protocol.MAGIC_BYTE_1 || b2 != Protocol.MAGIC_BYTE_2) break

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
                                        AppLogger.i(TAG, "Receptor Android $clientIp autenticado")
                                        onClientConnected(clientIp)
                                    } else {
                                        AppLogger.e(TAG, "PIN inválido desde $clientIp")
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
                        } catch (_: EOFException) {
                            break
                        } catch (_: Exception) {
                            break
                        }
                    }
                }

                while (scope.isActive && !socket.isClosed) {
                    val frame = latestFrame
                    if (frame != null && _auth) {
                        latestFrame = null
                        Protocol.writeFrame(dos, frame.width, frame.height, frame.timestamp, frame.jpegData)
                    } else {
                        delay(6)
                    }
                }
                readJob.cancel()
            } catch (_: Exception) {} finally {
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
    <title>catmirror — Web Remote Control</title>
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
        }
        header {
            width: 100%;
            background-color: #182230;
            padding: 12px 24px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid rgba(255, 158, 44, 0.3);
        }
        .logo { font-size: 20px; font-weight: bold; color: #FF9E2C; display: flex; align-items: center; gap: 8px; }
        .controls { display: flex; align-items: center; gap: 12px; }
        input {
            background: #0F141C; border: 1px solid #00E5FF; color: white;
            padding: 6px 12px; border-radius: 8px; font-size: 14px;
        }
        button {
            background: #FF9E2C; border: none; color: black; font-weight: bold;
            padding: 8px 16px; border-radius: 8px; cursor: pointer; transition: 0.2s;
        }
        button:hover { background: #FFBE76; }
        .viewport-container {
            position: relative; margin: 20px auto;
            border: 2px solid rgba(0, 229, 255, 0.4);
            border-radius: 16px; overflow: hidden; background: #000;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.7);
        }
        #stream-img { display: block; max-height: 80vh; max-width: 90vw; cursor: pointer; user-select: none; }
        .nav-bar {
            margin-top: 10px; margin-bottom: 24px;
            display: flex; gap: 16px; background: #182230; padding: 10px 20px;
            border-radius: 30px; border: 1px solid rgba(255, 158, 44, 0.4);
        }
        .nav-btn { background: #222F42; color: #FF9E2C; padding: 8px 18px; border-radius: 20px; font-size: 14px; }
        .nav-btn:hover { background: #FF9E2C; color: black; }
        .ripple {
            position: absolute; border: 2px solid #FF9E2C; border-radius: 50%;
            pointer-events: none; transform: translate(-50%, -50%);
            animation: rip 0.4s ease-out forwards;
        }
        @keyframes rip { from { width: 10px; height: 10px; opacity: 1; } to { width: 50px; height: 50px; opacity: 0; } }
    </style>
</head>
<body>
    <header>
        <div class="logo">🐱 catmirror Web Remote</div>
        <div class="controls">
            <input type="text" id="pin-input" placeholder="PIN de 6 dígitos" maxlength="6" />
            <button id="connect-btn">Conectar</button>
        </div>
    </header>

    <div class="viewport-container" id="viewport">
        <img id="stream-img" alt="Pantalla de catmirror" src="" />
    </div>

    <div class="nav-bar">
        <button class="nav-btn" onclick="sendKey(1)">◀ Atrás</button>
        <button class="nav-btn" onclick="sendKey(2)">⚪ Inicio</button>
        <button class="nav-btn" onclick="sendKey(3)">▦ Recientes</button>
    </div>

    <script>
        const img = document.getElementById('stream-img');
        const viewport = document.getElementById('viewport');
        const pinInput = document.getElementById('pin-input');
        const connectBtn = document.getElementById('connect-btn');

        function connect() {
            const pin = pinInput.value.trim();
            img.src = '/stream?pin=' + encodeURIComponent(pin) + '&t=' + Date.now();
        }

        connectBtn.onclick = connect;

        let isDown = false;
        let startX = 0, startY = 0;

        img.addEventListener('mousedown', (e) => {
            isDown = true;
            const rect = img.getBoundingClientRect();
            startX = (e.clientX - rect.left) / rect.width;
            startY = (e.clientY - rect.top) / rect.height;
            sendTouch(0, startX, startY);
            showRipple(e.clientX - rect.left, e.clientY - rect.top);
        });

        img.addEventListener('mousemove', (e) => {
            if (!isDown) return;
            const rect = img.getBoundingClientRect();
            const x = (e.clientX - rect.left) / rect.width;
            const y = (e.clientY - rect.top) / rect.height;
            sendTouch(2, x, y, startX, startY);
        });

        window.addEventListener('mouseup', (e) => {
            if (!isDown) return;
            isDown = false;
            const rect = img.getBoundingClientRect();
            const x = (e.clientX - rect.left) / rect.width;
            const y = (e.clientY - rect.top) / rect.height;
            sendTouch(1, x, y);
        });

        function sendTouch(action, x, y, sx = x, sy = y) {
            const pin = pinInput.value.trim();
            fetch('/touch?action=' + action + '&x=' + x.toFixed(4) + '&y=' + y.toFixed(4) + '&sx=' + sx.toFixed(4) + '&sy=' + sy.toFixed(4) + '&pin=' + encodeURIComponent(pin)).catch(()=>{});
        }

        function sendKey(code) {
            const pin = pinInput.value.trim();
            fetch('/key?code=' + code + '&pin=' + encodeURIComponent(pin)).catch(()=>{});
        }

        function showRipple(x, y) {
            const r = document.createElement('div');
            r.className = 'ripple';
            r.style.left = x + 'px';
            r.style.top = y + 'px';
            viewport.appendChild(r);
            setTimeout(() => r.remove(), 400);
        }

        connect();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
