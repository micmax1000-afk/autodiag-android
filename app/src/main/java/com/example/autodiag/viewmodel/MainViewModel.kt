package com.example.autodiag.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autodiag.bluetooth.Elm327Client
import com.example.autodiag.bluetooth.LiveReading
import com.example.autodiag.bluetooth.ObdPids
import com.example.autodiag.data.AppDatabase
import com.example.autodiag.data.ManutenzioneEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val deviceName: String, val protocol: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class ClearReport(
    val accepted: Boolean,
    val detail: String,
    val removedStored: List<String>,
    val removedPending: List<String>,
    val stillStored: List<String>,
    val stillPending: List<String>,
    val permanent: List<String>,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val client = Elm327Client()
    private val dao = AppDatabase.getDatabase(app).manutenzioneDao()
    private val prefs = app.getSharedPreferences("autodiag", Context.MODE_PRIVATE)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _stored = MutableStateFlow<List<String>>(emptyList())
    val storedDtcs: StateFlow<List<String>> = _stored.asStateFlow()

    private val _pending = MutableStateFlow<List<String>>(emptyList())
    val pendingDtcs: StateFlow<List<String>> = _pending.asStateFlow()

    private val _permanent = MutableStateFlow<List<String>>(emptyList())
    val permanentDtcs: StateFlow<List<String>> = _permanent.asStateFlow()

    private val _dtcNote = MutableStateFlow("")
    val dtcNote: StateFlow<String> = _dtcNote.asStateFlow()

    private val _clearReport = MutableStateFlow<ClearReport?>(null)
    val clearReport: StateFlow<ClearReport?> = _clearReport.asStateFlow()

    private val _params = MutableStateFlow<List<LiveReading>>(emptyList())
    val params: StateFlow<List<LiveReading>> = _params.asStateFlow()

    private val _liveRunning = MutableStateFlow(false)
    val liveRunning: StateFlow<Boolean> = _liveRunning.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    private val _currentKm = MutableStateFlow(prefs.getInt("currentKm", 0))
    val currentKm: StateFlow<Int> = _currentKm.asStateFlow()

    private var liveJob: Job? = null

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
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun connect(device: BluetoothDevice) {
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Connecting
            addLog("Connessione a ${device.name ?: device.address}…")
            try {
                val protocol = client.connect(device)
                _connectionState.value = ConnectionState.Connected(device.name ?: "ELM327", protocol)
                addLog("✓ Connesso · $protocol")
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(e.message ?: "Errore")
                addLog("✗ ${e.message}")
            }
        }
    }

    fun disconnect() {
        liveJob?.cancel()
        client.disconnect()
        _connectionState.value = ConnectionState.Idle
        _stored.value = emptyList()
        _pending.value = emptyList()
        _permanent.value = emptyList()
        _dtcNote.value = ""
        _clearReport.value = null
        _params.value = emptyList()
        addLog("Disconnesso")
    }

    fun readDtcs() {
        viewModelScope.launch {
            pauseLive {
                _isBusy.value = true
                _clearReport.value = null
                addLog("Lettura DTC: memorizzati, pending, permanenti…")
                try {
                    applyDtcRead()
                    addLog(
                        "✓ memorizzati ${_stored.value.size}, pending ${_pending.value.size}, permanenti ${_permanent.value.size}"
                    )
                } catch (e: Exception) {
                    addLog("✗ ${e.message}")
                } finally {
                    _isBusy.value = false
                }
            }
        }
    }

    fun clearDtcs() {
        viewModelScope.launch {
            pauseLive {
                _isBusy.value = true
                addLog("Cancellazione DTC (Mode 04)…")
                try {
                    val beforeStored = _stored.value
                    val beforePending = _pending.value
                    val outcome = client.clearDtcs()
                    delay(400)
                    applyDtcRead()
                    _clearReport.value = ClearReport(
                        accepted = outcome.accepted,
                        detail = outcome.detail,
                        removedStored = beforeStored.filter { it !in _stored.value },
                        removedPending = beforePending.filter { it !in _pending.value },
                        stillStored = _stored.value,
                        stillPending = _pending.value,
                        permanent = _permanent.value,
                    )
                    addLog(if (outcome.accepted) "✓ ${outcome.detail}" else "✗ ${outcome.detail}")
                } catch (e: Exception) {
                    addLog("✗ ${e.message}")
                } finally {
                    _isBusy.value = false
                }
            }
        }
    }

    fun startLive() {
        if (liveJob?.isActive == true) return
        if (_connectionState.value !is ConnectionState.Connected) return
        liveJob = viewModelScope.launch {
            _liveRunning.value = true
            addLog("Parametri in diretta")
            try {
                if (!client.hasSupportMap()) {
                    try {
                        client.discoverSupport()
                    } catch (_: Exception) {
                    }
                }
                val specs = ObdPids.all.filter { client.isPidSupported(it.pid) }.ifEmpty { ObdPids.fallback }
                _params.value = specs.map { LiveReading(it.pid, it.name, "—", it.unit) }
                while (isActive) {
                    for (spec in specs) {
                        if (!isActive) break
                        val value = try {
                            client.readPid(spec)
                        } catch (e: Exception) {
                            addLog("✗ Parametri: ${e.message}")
                            return@launch
                        }
                        if (value != null) {
                            _params.value = _params.value.map {
                                if (it.pid == spec.pid) it.copy(value = value) else it
                            }
                        }
                    }
                }
            } finally {
                _liveRunning.value = false
            }
        }
    }

    fun stopLive() {
        liveJob?.cancel()
    }

    private suspend fun applyDtcRead() {
        val stored = client.readStored()
        val pending = client.readPending()
        val permanent = client.readPermanent()
        _stored.value = stored.codes
        _pending.value = pending.codes
        _permanent.value = permanent.codes
        _dtcNote.value = listOfNotNull(
            stored.note?.let { "Memorizzati: $it" },
            pending.note?.let { "Pending: $it" },
            permanent.note?.let { "Permanenti: $it" },
        ).joinToString(" ")
    }

    private suspend fun pauseLive(block: suspend () -> Unit) {
        val restart = liveJob?.isActive == true
        liveJob?.cancel()
        liveJob?.join()
        try {
            block()
        } finally {
            if (restart && _connectionState.value is ConnectionState.Connected) startLive()
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
        liveJob?.cancel()
        client.disconnect()
        super.onCleared()
    }
}
