package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppScreen
import com.example.model.ConnectionStatus
import com.example.model.DiscoveredDevice
import com.example.model.NetworkInfo
import com.example.model.StreamStats
import com.example.network.DiscoveryBroadcaster
import com.example.network.DiscoveryScanner
import com.example.network.NetworkUtils
import com.example.network.Protocol
import com.example.network.StreamClient
import com.example.network.StreamServer
import com.example.network.VideoFrame
import com.example.service.CatMirrorAccessibilityService
import com.example.service.ScreenStreamingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _networkInfo = MutableStateFlow(NetworkUtils.getNetworkInfo(application))
    val networkInfo: StateFlow<NetworkInfo> = _networkInfo.asStateFlow()

    // --- Emitter Mode State ---
    private val _emitterPin = MutableStateFlow(NetworkUtils.generatePin())
    val emitterPin: StateFlow<String> = _emitterPin.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(
        CatMirrorAccessibilityService.isAccessibilityEnabled(application)
    )
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    val isEmitterStreaming: StateFlow<Boolean> = ScreenStreamingService.isStreaming

    private val _emitterConnectedClient = MutableStateFlow<String?>(null)
    val emitterConnectedClient: StateFlow<String?> = _emitterConnectedClient.asStateFlow()

    private val streamServer = StreamServer(
        scope = viewModelScope,
        onClientConnected = { clientIp ->
            _emitterConnectedClient.value = clientIp
            viewModelScope.launch {
                Toast.makeText(getApplication(), "Receptor conectado: $clientIp", Toast.LENGTH_SHORT).show()
            }
        },
        onClientDisconnected = { clientIp ->
            _emitterConnectedClient.value = null
            viewModelScope.launch {
                Toast.makeText(getApplication(), "Receptor desconectado: $clientIp", Toast.LENGTH_SHORT).show()
            }
        }
    )
    val emitterStats: StateFlow<StreamStats> = streamServer.stats

    private val discoveryBroadcaster = DiscoveryBroadcaster(viewModelScope)

    // --- Receiver Mode State ---
    private val discoveryScanner = DiscoveryScanner(viewModelScope)
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = discoveryScanner.devices

    private val streamClient = StreamClient(viewModelScope)
    val receiverStatus: StateFlow<ConnectionStatus> = streamClient.status
    val receiverFrame: StateFlow<VideoFrame?> = streamClient.currentFrame
    val receiverStats: StateFlow<StreamStats> = streamClient.stats

    private val _manualIp = MutableStateFlow("192.168.1.100")
    val manualIp: StateFlow<String> = _manualIp.asStateFlow()

    private val _manualPin = MutableStateFlow("")
    val manualPin: StateFlow<String> = _manualPin.asStateFlow()

    private val _fitScreen = MutableStateFlow(true)
    val fitScreen: StateFlow<Boolean> = _fitScreen.asStateFlow()

    init {
        // Wire up frame pipeline from ScreenStreamingService to StreamServer
        ScreenStreamingService.onFrameCaptured = { width, height, timestamp, jpegData ->
            streamServer.broadcastFrame(width, height, timestamp, jpegData)
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        if (screen == AppScreen.RECEIVER) {
            discoveryScanner.start()
        } else if (screen != AppScreen.REMOTE_VIEW) {
            discoveryScanner.stop()
        }
    }

    fun refreshNetwork(context: Context) {
        _networkInfo.value = NetworkUtils.getNetworkInfo(context)
        _isAccessibilityEnabled.value = CatMirrorAccessibilityService.isAccessibilityEnabled(context)
    }

    fun regeneratePin() {
        val newPin = NetworkUtils.generatePin()
        _emitterPin.value = newPin
        if (isEmitterStreaming.value) {
            discoveryBroadcaster.start(newPin)
        }
    }

    fun startEmitter(context: Context, resultCode: Int = 0, intentData: Intent? = null) {
        val pin = _emitterPin.value
        streamServer.start(pin)
        discoveryBroadcaster.start(pin)

        val serviceIntent = Intent(context, ScreenStreamingService::class.java).apply {
            action = ScreenStreamingService.ACTION_START
            putExtra(ScreenStreamingService.EXTRA_RESULT_CODE, resultCode)
            if (intentData != null) {
                putExtra(ScreenStreamingService.EXTRA_DATA, intentData)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun stopEmitter(context: Context) {
        discoveryBroadcaster.stop()
        streamServer.stop()
        _emitterConnectedClient.value = null

        val serviceIntent = Intent(context, ScreenStreamingService::class.java).apply {
            action = ScreenStreamingService.ACTION_STOP
        }
        context.startService(serviceIntent)
    }

    fun updateManualIp(ip: String) {
        _manualIp.value = ip
    }

    fun updateManualPin(pin: String) {
        _manualPin.value = pin
    }

    fun connectToDevice(ip: String, port: Int = Protocol.DEFAULT_PORT, pin: String = "") {
        streamClient.connect(ip, port, pin)
        _currentScreen.value = AppScreen.REMOTE_VIEW
    }

    fun disconnectReceiver() {
        streamClient.disconnect()
        _currentScreen.value = AppScreen.RECEIVER
    }

    fun sendTouch(action: Byte, xRatio: Float, yRatio: Float) {
        streamClient.sendTouch(action, xRatio, yRatio)
    }

    fun sendGlobalAction(actionCode: Byte) {
        streamClient.sendGlobalAction(actionCode)
    }

    fun toggleFitScreen() {
        _fitScreen.value = !_fitScreen.value
    }

    override fun onCleared() {
        super.onCleared()
        discoveryBroadcaster.stop()
        discoveryScanner.stop()
        streamServer.stop()
        streamClient.disconnect()
    }
}
