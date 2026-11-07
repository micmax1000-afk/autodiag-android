package com.example.autodiag.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autodiag.bluetooth.DtcDatabase
import com.example.autodiag.viewmodel.ClearReport
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
    requestEnableBluetooth: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val state by vm.connectionState.collectAsStateWithLifecycle()
    val stored by vm.storedDtcs.collectAsStateWithLifecycle()
    val pending by vm.pendingDtcs.collectAsStateWithLifecycle()
    val permanent by vm.permanentDtcs.collectAsStateWithLifecycle()
    val dtcNote by vm.dtcNote.collectAsStateWithLifecycle()
    val report by vm.clearReport.collectAsStateWithLifecycle()
    val busy by vm.isBusy.collectAsStateWithLifecycle()
    val paired by vm.pairedDevices.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()

    var showDeviceDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Connessione", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val statusText = when (val s = state) {
                    is ConnectionState.Idle -> "Non connesso"
                    is ConnectionState.Connecting -> "Connessione…"
                    is ConnectionState.Connected -> "Connesso a ${s.deviceName} · ${s.protocol}"
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
                                    requestEnableBluetooth {
                                        vm.loadPairedDevices()
                                        showDeviceDialog = true
                                    }
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
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Errori (DTC)", style = MaterialTheme.typography.titleMedium)
                DtcGroup(
                    title = "Memorizzati",
                    hint = "Hanno acceso la spia motore. Il Mode 04 li cancella.",
                    codes = stored,
                    color = MaterialTheme.colorScheme.error
                )
                DtcGroup(
                    title = "Pending",
                    hint = "Visti ma non ancora memorizzati. Si cancellano insieme ai memorizzati.",
                    codes = pending,
                    color = Color(0xFFF5C211)
                )
                DtcGroup(
                    title = "Permanenti",
                    hint = "Non si cancellano da qui. Restano finché il guasto è risolto e i controlli sono completi.",
                    codes = permanent,
                    color = Color(0xFFC58AF9)
                )
                if (dtcNote.isNotBlank()) {
                    Text(dtcNote, style = MaterialTheme.typography.bodySmall)
                }
                val canUse = state is ConnectionState.Connected && !busy
                Button(
                    onClick = { vm.readDtcs() },
                    enabled = canUse,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Leggi errori") }
                Button(
                    onClick = { showClearDialog = true },
                    enabled = canUse,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancella memorizzati e pending") }
            }
        }

        report?.let { ReportCard(it) }

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

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Cancellare i codici OBD?") },
            text = {
                Text(
                    "Toglie i DTC memorizzati e pending della centralina motore e spegne la spia motore. " +
                        "I permanenti restano. ABS, airbag e spia service non vengono toccati. " +
                        "Spegni il motore e lascia il quadro acceso. I monitor di collaudo si azzerano."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    vm.clearDtcs()
                }) { Text("Cancella", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Annulla") }
            }
        )
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

@Composable
private fun DtcGroup(title: String, hint: String, codes: List<String>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = color)
        Text(hint, style = MaterialTheme.typography.bodySmall)
        if (codes.isEmpty()) {
            Text("Nessuno.", style = MaterialTheme.typography.bodySmall)
        } else {
            codes.forEach { code ->
                Text(code, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                Text(DtcDatabase.describe(code), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ReportCard(report: ClearReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (report.accepted) "Cancellazione accettata" else "Cancellazione non confermata",
                style = MaterialTheme.typography.titleMedium,
                color = if (report.accepted) Color(0xFF33D17A) else MaterialTheme.colorScheme.error
            )
            Text(report.detail, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Tolti dai memorizzati: ${report.removedStored.joinToString().ifBlank { "nessuno" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Tolti dai pending: ${report.removedPending.joinToString().ifBlank { "nessuno" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Ancora memorizzati: ${report.stillStored.joinToString().ifBlank { "nessuno" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Ancora pending: ${report.stillPending.joinToString().ifBlank { "nessuno" }}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Permanenti (non cancellabili): ${report.permanent.joinToString().ifBlank { "nessuno" }}",
                style = MaterialTheme.typography.bodySmall
            )
            if (report.stillStored.isNotEmpty()) {
                Text(
                    "Se un memorizzato è tornato subito, il guasto è ancora presente.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
