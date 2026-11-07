package com.example.autodiag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autodiag.bluetooth.LiveReading
import com.example.autodiag.viewmodel.ConnectionState
import com.example.autodiag.viewmodel.MainViewModel

@Composable
fun ParametriScreen(vm: MainViewModel) {
    val state by vm.connectionState.collectAsStateWithLifecycle()
    val params by vm.params.collectAsStateWithLifecycle()
    val live by vm.liveRunning.collectAsStateWithLifecycle()
    val busy by vm.isBusy.collectAsStateWithLifecycle()
    val connected = state is ConnectionState.Connected

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Parametri in diretta", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Valori Mode 01 della centralina motore. Vengono chiesti solo i PID che l'auto dichiara di supportare.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                if (!connected) {
                    Text("Connetti l'adattatore dalla scheda Diagnosi.")
                } else if (live) {
                    OutlinedButton(
                        onClick = { vm.stopLive() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ferma lettura") }
                } else {
                    Button(
                        onClick = { vm.startLive() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Avvia lettura") }
                }
            }
        }

        if (params.isEmpty()) {
            Text(
                if (live) "In attesa della centralina…" else "Nessun valore ancora.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            params.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { reading ->
                        ParamTile(reading, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ParamTile(reading: LiveReading, modifier: Modifier = Modifier) {
    val featured = reading.pid == 0x0C || reading.pid == 0x0D
    Card(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(reading.name, style = MaterialTheme.typography.labelMedium)
            Text(
                reading.value,
                style = if (featured) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace
            )
            Text(reading.unit, style = MaterialTheme.typography.bodySmall)
        }
    }
}
