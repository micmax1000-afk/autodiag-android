package com.example.autodiag.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autodiag.bluetooth.DtcDatabase
import com.example.autodiag.viewmodel.ConnectionState
import com.example.autodiag.viewmodel.MainViewModel

private fun hasBtPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
    } else true
}

@Composable
fun DiagnosiScreen(
    vm: MainViewModel,
    requestPermissions: (() -> Unit) -> Unit,
    requestEnableBluetooth: () -> Unit
) {
    val context = LocalContext.current
    val state by vm.connectionState.collectAsStateWithLifecycle()
    val dtcs by vm.dtcs.collectAsStateWithLifecycle()
    val busy by vm.isBusy.collectAsStateWithLifecycle()
    val paired by vm.pairedDevices.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()

    var showDeviceDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier = Modifier.padding(16.dp)) {
                Text("Connessione", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val statusText = when (val s = state) {
                    is ConnectionState.Idle -> "Non connesso"
                    is ConnectionState.Connecting -> "Connessione…"
                    is ConnectionState.Connected -> "Connesso a ${s.deviceName}"
                    is ConnectionState.Error -> "Errore: ${s.message}"
                }
                val dotColor = when (state) {
                    is ConnectionState.Connected -> Color(0xFF33D17A)
                    is ConnectionState.Error -> Color(0xFFFF5C5C)
                    else -> Color.Gray
                }
                Text("● $statusText", color = dotColor)
                Spacer(Modifier.height(10.dp))

                if (state !is ConnectionState.Connected) {
                    Button(
                        onClick = {
                            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@Button
                            val proceed = {
                                if (!adapter.isEnabled) {
                                    requestEnableBluetooth()
                                } else {
                                    vm.loadPairedDevices()
                                    showDeviceDialog = true
                                }
                            }
                            if (hasBtPermissions(context)) proceed()
                            else requestPermissions(proceed)
                        },
                        enabled = state !is ConnectionState.Connecting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Connetti via Bluetooth") }
                } else {
                    OutlinedButton(
                        onClick = { vm.disconnect() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Disconnetti") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Errori memorizzati (DTC)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (dtcs.isEmpty()) {
                    Text("Nessun errore letto.", style = MaterialTheme.typography.bodySmall)
                } else {
                    dtcs.forEach { code ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                code,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                DtcDatabase.describe(code),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                val canUse = state is ConnectionState.Connected && !busy
                Button(
                    onClick = { vm.readDtcs() },
                    enabled = canUse,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Leggi errori") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.clearDtcs() },
                    enabled = canUse,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancella errori (Reset ECU)") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Log", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    log.takeLast(30).joinToString("\n").ifBlank { "Pronto." },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text("Scegli adattatore") },
            text = {
                if (paired.isEmpty()) {
                    Text("Nessun dispositivo accoppiato.\nAccoppia prima l'ELM327 dalle impostazioni Bluetooth di Android.")
                } else {
                    Column {
                        paired.forEach { dev ->
                            TextButton(
                                onClick = {
                                    showDeviceDialog = false
                                    vm.connect(dev)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(dev.name ?: "Sconosciuto")
                                    Text(dev.address, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceDialog = false }) { Text("Chiudi") }
            }
        )
    }
}
