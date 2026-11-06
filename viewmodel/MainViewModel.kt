package com.example.autodiag.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autodiag.bluetooth.Elm327Client
import com.example.autodiag.data.AppDatabase
import com.example.autodiag.data.ManutenzioneEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val client = Elm327Client()
    private val dao = AppDatabase.getDatabase(app).manutenzioneDao()
    private val prefs = app.getSharedPreferences("autodiag", Context.MODE_PRIVATE)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _dtcs = MutableStateFlow<List<String>>(emptyList())
    val dtcs: StateFlow<List<String>> = _dtcs.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    private val _currentKm = MutableStateFlow(prefs.getInt("currentKm", 0))
    val currentKm: StateFlow<Int> = _currentKm.asStateFlow()

    val maintenance: StateFlow<List<ManutenzioneEntity>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            if (dao.count() == 0) seedDefaults()
        }
    }

    private suspend fun seedDefaults() {
        val presets = listOf(
            ManutenzioneEntity(nome = "Cambio olio motore", kmIntervallo = 15000, mesiIntervallo = 12),
            ManutenzioneEntity(nome = "Filtro aria", kmIntervallo = 30000, mesiIntervallo = 24),
            ManutenzioneEntity(nome = "Filtro abitacolo", kmIntervallo = 20000, mesiIntervallo = 12),
            ManutenzioneEntity(nome = "Freni (pastiglie)", kmIntervallo = 40000),
            ManutenzioneEntity(nome = "Liquido freni", mesiIntervallo = 24),
            ManutenzioneEntity(nome = "Cinghia distribuzione", kmIntervallo = 120000, mesiIntervallo = 60)
        )
        presets.forEach { dao.insert(it) }
    }

    private fun addLog(msg: String) {
        _log.value = (_log.value + msg).takeLast(200)
    }

    fun loadPairedDevices() {
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        _pairedDevices.value = try {
            adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Connecting
            addLog("Connessione a ${device.name ?: device.address}…")
            try {
                client.connect(device)
                _connectionState.value = ConnectionState.Connected(device.name ?: "ELM327")
                addLog("✓ Connesso e inizializzato")
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Errore")
                addLog("✗ ${e.message}")
            }
        }
    }

    fun disconnect() {
        client.disconnect()
        _connectionState.value = ConnectionState.Idle
        _dtcs.value = emptyList()
        addLog("Disconnesso")
    }

    fun readDtcs() {
        viewModelScope.launch {
            _isBusy.value = true
            addLog("Lettura DTC…")
            try {
                val codes = client.readDtcs()
                _dtcs.value = codes
                addLog(if (codes.isEmpty()) "✓ Nessun errore" else "✓ ${codes.size} errori")
            } catch (e: Exception) {
                addLog("✗ ${e.message}")
            } finally { _isBusy.value = false }
        }
    }

    fun clearDtcs() {
        viewModelScope.launch {
            _isBusy.value = true
            addLog("Cancellazione DTC…")
            try {
                if (client.clearDtcs()) {
                    _dtcs.value = emptyList()
                    addLog("✓ Errori cancellati")
                } else addLog("✗ Cancellazione rifiutata")
            } catch (e: Exception) {
                addLog("✗ ${e.message}")
            } finally { _isBusy.value = false }
        }
    }

    fun setKm(km: Int) {
        prefs.edit().putInt("currentKm", km).apply()
        _currentKm.value = km
    }

    fun addMaint(nome: String, kmInt: Int, mesiInt: Int) {
        viewModelScope.launch {
            dao.insert(ManutenzioneEntity(nome = nome, kmIntervallo = kmInt, mesiIntervallo = mesiInt))
        }
    }

    fun markDone(item: ManutenzioneEntity) {
        viewModelScope.launch {
            val oggi = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            dao.update(item.copy(ultimoKm = _currentKm.value, ultimaData = oggi))
            addLog("Manutenzione aggiornata: ${item.nome}")
        }
    }

    fun deleteMaint(item: ManutenzioneEntity) {
        viewModelScope.launch { dao.deleteById(item.id) }
    }

    fun clearAllMaint() {
        viewModelScope.launch { dao.clearAll() }
    }

    override fun onCleared() {
        client.disconnect()
        super.onCleared()
    }
}
