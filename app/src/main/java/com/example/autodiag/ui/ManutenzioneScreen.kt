package com.example.autodiag.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.autodiag.data.ManutenzioneEntity
import com.example.autodiag.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

private fun mesiTra(daData: String, aData: Date): Int {
    if (daData.isBlank()) return Int.MAX_VALUE
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val da = fmt.parse(daData) ?: return Int.MAX_VALUE
        val cal1 = Calendar.getInstance().apply { time = da }
        val cal2 = Calendar.getInstance().apply { time = aData }
        (cal2.get(Calendar.YEAR) - cal1.get(Calendar.YEAR)) * 12 +
            (cal2.get(Calendar.MONTH) - cal1.get(Calendar.MONTH))
    } catch (e: Exception) {
        Int.MAX_VALUE
    }
}

@Composable
fun ManutenzioneScreen(vm: MainViewModel) {
    val km by vm.currentKm.collectAsStateWithLifecycle()
    val items by vm.maintenance.collectAsStateWithLifecycle()

    var kmInput by remember(km) { mutableStateOf(km.toString()) }
    var nome by remember { mutableStateOf("") }
    var kmInt by remember { mutableStateOf("") }
    var mesiInt by remember { mutableStateOf("") }
    var showClearAll by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Chilometraggio attuale", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = kmInput,
                        onValueChange = { kmInput = it.filter { c -> c.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { kmInput.toIntOrNull()?.let { vm.setKm(it) } }) {
                        Text("Salva")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Aggiungi intervento", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Descrizione (es. Cambio olio)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = kmInt,
                        onValueChange = { kmInt = it.filter { c -> c.isDigit() } },
                        label = { Text("Ogni (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = mesiInt,
                        onValueChange = { mesiInt = it.filter { c -> c.isDigit() } },
                        label = { Text("Ogni (mesi)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (nome.isNotBlank()) {
                            vm.addMaint(
                                nome,
                                kmInt.toIntOrNull() ?: 0,
                                mesiInt.toIntOrNull() ?: 0
                            )
                            nome = ""
                            kmInt = ""
                            mesiInt = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Aggiungi") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Scadenze", style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = { showClearAll = true },
                        enabled = items.isNotEmpty()
                    ) {
                        Text("Cancella tutto", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (items.isEmpty()) {
                    Text("Nessun intervento registrato.")
                } else {
                    val oggi = Date()
                    items.forEachIndexed { index, item ->
                        ManutenzioneItem(item, km, oggi, vm)
                        if (index < items.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }
    }

    if (showClearAll) {
        AlertDialog(
            onDismissRequest = { showClearAll = false },
            title = { Text("Cancellare tutti gli interventi?") },
            text = { Text("L'operazione è irreversibile.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllMaint()
                    showClearAll = false
                }) {
                    Text("Cancella tutto", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAll = false }) { Text("Annulla") }
            }
        )
    }
}

@Composable
private fun ManutenzioneItem(
    item: ManutenzioneEntity,
    km: Int,
    oggi: Date,
    vm: MainViewModel
) {
    val remKm = if (item.kmIntervallo > 0 && item.ultimoKm > 0)
        item.ultimoKm + item.kmIntervallo - km else null
    val remMesi = if (item.mesiIntervallo > 0 && item.ultimaData.isNotBlank())
        item.mesiIntervallo - mesiTra(item.ultimaData, oggi) else null

    val scaduto = (remKm != null && remKm <= 0) || (remMesi != null && remMesi <= 0)
    val inScadenza = (remKm != null && remKm <= 1500) || (remMesi != null && remMesi <= 1)

    val (label, color) = when {
        scaduto -> "SCADUTO" to MaterialTheme.colorScheme.error
        inScadenza -> "In scadenza" to androidx.compose.ui.graphics.Color(0xFFF5C211)
        else -> "In regola" to androidx.compose.ui.graphics.Color(0xFF33D17A)
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.nome,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(label, color = color, style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { vm.deleteMaint(item) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Elimina",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        val dettagli = buildList {
            if (item.kmIntervallo > 0) add("ogni ${item.kmIntervallo} km")
            if (item.mesiIntervallo > 0) add("ogni ${item.mesiIntervallo} mesi")
            remKm?.let { add(if (it > 0) "mancano $it km" else "scaduto di ${-it} km") }
            remMesi?.let { add(if (it > 0) "$it mesi rimanenti" else "scaduto di ${-it} mesi") }
        }
        Text(dettagli.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        Text(
            "Ultimo: ${if (item.ultimoKm > 0) "${item.ultimoKm} km" else "—"}" +
                if (item.ultimaData.isNotBlank()) " · ${item.ultimaData}" else "",
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = { vm.markDone(item) }) {
            Text("✓ Segna come eseguito (a $km km, oggi)")
        }
    }
}
