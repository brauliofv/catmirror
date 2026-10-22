package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiscoveredDevice
import com.example.model.NetworkInfo
import com.example.ui.theme.CatAmberPrimary
import com.example.ui.theme.CatCyanSecondary
import com.example.ui.theme.CatNeonGreen
import com.example.ui.theme.CatSlateCard
import com.example.ui.theme.CatSlateSurface
import com.example.ui.theme.CatTextMuted
import com.example.ui.theme.CatTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiverScreen(
    networkInfo: NetworkInfo,
    discoveredDevices: List<DiscoveredDevice>,
    manualIp: String,
    manualPin: String,
    onUpdateManualIp: (String) -> Unit,
    onUpdateManualPin: (String) -> Unit,
    onConnect: (ip: String, port: Int, pin: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDeviceForPin by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var enteredPin by remember { mutableStateOf("") }

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Pantalla Receptor",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Ver y controlar otro dispositivo",
                            style = MaterialTheme.typography.bodySmall.copy(color = CatCyanSecondary)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("receiver_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tabs: Wi-Fi vs USB
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = CatSlateSurface,
                contentColor = CatCyanSecondary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detección Wi-Fi")
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Cable, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cable USB / Manual")
                        }
                    }
                )
            }

            if (selectedTab == 0) {
                // Wi-Fi Auto Discovery Tab
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CatCyanSecondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Buscando emisores catmirror en red local…",
                                style = MaterialTheme.typography.bodySmall.copy(color = CatTextSecondary)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (discoveredDevices.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = CatSlateSurface)
                        ) {
                            Column(
                                modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = CatTextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No se detectaron dispositivos aún",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Asegúrate de que el otro teléfono esté en la misma red Wi-Fi y tenga abierta la pantalla 'Pantalla Emisor'.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = CatTextSecondary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(onClick = { selectedTab = 1 }) {
                                    Text("O conectar manualmente por IP o USB →", color = CatCyanSecondary)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(discoveredDevices) { device ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedDeviceForPin = device
                                            enteredPin = ""
                                        }
                                        .testTag("device_item_${device.ip}"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = CatSlateSurface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CatCyanSecondary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(CatCyanSecondary.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PhoneAndroid,
                                                    contentDescription = null,
                                                    tint = CatCyanSecondary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = device.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                                Text(
                                                    text = "${device.ip}:${device.port}",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        color = CatTextSecondary
                                                    )
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                selectedDeviceForPin = device
                                                enteredPin = ""
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CatCyanSecondary)
                                        ) {
                                            Text("Conectar", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // USB / Manual Connection Tab
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CatSlateSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CatCyanSecondary.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "CONEXIÓN MANUAL O USB",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CatTextMuted,
                                    letterSpacing = 1.2.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = onUpdateManualIp,
                                label = { Text("Dirección IP del Emisor") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CatCyanSecondary,
                                    unfocusedBorderColor = CatTextMuted
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_ip_input")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = manualPin,
                                onValueChange = onUpdateManualPin,
                                label = { Text("PIN de Seguridad (6 dígitos)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CatCyanSecondary,
                                    unfocusedBorderColor = CatTextMuted
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manual_pin_input")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Quick presets
                            Text(
                                text = "Atajos rápidos de enlace:",
                                style = MaterialTheme.typography.labelSmall.copy(color = CatTextSecondary)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CatSlateCard,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onUpdateManualIp("127.0.0.1") }
                                ) {
                                    Text(
                                        text = "127.0.0.1 (ADB)",
                                        modifier = Modifier.padding(8.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = CatCyanSecondary
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CatSlateCard,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onUpdateManualIp("192.168.42.129") }
                                ) {
                                    Text(
                                        text = "192.168.42.x (USB)",
                                        modifier = Modifier.padding(8.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = CatCyanSecondary
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    onConnect(manualIp, 8888, manualPin)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("connect_manual_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CatCyanSecondary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cable,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Conectar Ahora",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // USB Connection Guide
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CatSlateCard)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = CatAmberPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "¿CÓMO CONECTAR POR CABLE USB?",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = CatAmberPrimary)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Método 1: Anclaje a red USB (Recomendado)\nConecta ambos dispositivos mediante cable USB OTG o a una PC y activa 'Compartir conexión por USB' en los Ajustes del emisor. La IP se asigna en el rango 192.168.42.x con latencia ultra-baja.",
                                style = MaterialTheme.typography.bodySmall.copy(color = CatTextSecondary, lineHeight = 16.sp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Método 2: ADB Forwarding\nSi ambos dispositivos están conectados a una PC con depuración USB:\n  adb forward tcp:8888 tcp:8888\nY conecta a la IP 127.0.0.1 con puerto 8888.",
                                style = MaterialTheme.typography.bodySmall.copy(color = CatTextSecondary, lineHeight = 16.sp)
                            )
                        }
                    }
                }
            }
        }
    }

    // PIN Input Dialog
    if (selectedDeviceForPin != null) {
        val targetDevice = selectedDeviceForPin!!
        AlertDialog(
            onDismissRequest = { selectedDeviceForPin = null },
            title = {
                Text(
                    text = "Vincular con ${targetDevice.name}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Introduce el PIN de 6 dígitos que aparece en la pantalla del emisor (${targetDevice.ip}):",
                        style = MaterialTheme.typography.bodyMedium.copy(color = CatTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { if (it.length <= 6) enteredPin = it },
                        label = { Text("PIN de 6 dígitos") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CatCyanSecondary,
                            unfocusedBorderColor = CatTextMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_dialog_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pinToUse = enteredPin
                        val dev = selectedDeviceForPin
                        selectedDeviceForPin = null
                        if (dev != null) {
                            onConnect(dev.ip, dev.port, pinToUse)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CatCyanSecondary)
                ) {
                    Text("Conectar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedDeviceForPin = null }) {
                    Text("Cancelar", color = CatTextSecondary)
                }
            },
            containerColor = CatSlateSurface
        )
    }
}
