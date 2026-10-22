package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.example.model.NetworkInfo
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Prioritize wlan (Wi-Fi) and rndis/usb (USB Tethering)
            val sortedInterfaces = interfaces.sortedByDescending { iface ->
                when {
                    iface.name.startsWith("wlan", ignoreCase = true) -> 3
                    iface.name.startsWith("rndis", ignoreCase = true) || iface.name.startsWith("usb", ignoreCase = true) -> 2
                    iface.name.startsWith("eth", ignoreCase = true) -> 1
                    else -> 0
                }
            }

            for (iface in sortedInterfaces) {
                if (iface.isUp && !iface.isLoopback) {
                    val addresses = Collections.list(iface.inetAddresses)
                    for (addr in addresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val ip = addr.hostAddress ?: ""
                            if (ip.isNotEmpty() && !ip.startsWith("127.")) {
                                return ip
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun getNetworkInfo(context: Context): NetworkInfo {
        val ip = getLocalIpAddress()
        var isWifi = false
        var isUsb = false
        var ssid = "Red no identificada"

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)

        if (caps != null) {
            isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            isUsb = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || ip.startsWith("192.168.42.")
        }

        if (isWifi) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val rawSsid = wifiInfo?.ssid?.replace("\"", "") ?: ""
            ssid = if (rawSsid.isNotEmpty() && rawSsid != "<unknown ssid>") rawSsid else "Wi-Fi Conectado"
        } else if (isUsb) {
            ssid = "Enlace USB / Tethering"
        } else if (ip != "127.0.0.1") {
            ssid = "Red Local Directa"
        }

        return NetworkInfo(
            ipAddress = ip,
            isWifiConnected = isWifi,
            ssid = ssid,
            isUsbTethered = isUsb
        )
    }

    fun generatePin(): String {
        val randomNum = (100000..999999).random()
        return randomNum.toString()
    }
}
