package com.example.autodiag.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.autodiag.bluetooth.DtcDatabase

@Composable
fun CodiciScreen() {
    var query by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Cerca codice o parola (es. P0300, lambda)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        val q = query.trim()
        val results = if (q.isBlank()) emptyList() else
            DtcDatabase.codes.filter { (code, desc) ->
                code.contains(q, ignoreCase = true) || desc.contains(q, ignoreCase = true)
            }.toList().take(30)

        if (query.isNotBlank()) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    if (results.isEmpty()) Text("Nessun codice trovato nel database locale.")
                    else results.forEach { (code, desc) ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(code, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Text(desc, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Prefissi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("P — Powertrain (motore/trasmissione)")
                Text("B — Body (airbag, clima, serrature)")
                Text("C — Chassis (ABS, ESP, sospensioni)")
                Text("U — Network (CAN, comunicazione centraline)")
            }
        }
    }
}
