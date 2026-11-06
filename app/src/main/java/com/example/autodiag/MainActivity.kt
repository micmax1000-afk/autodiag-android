package com.example.autodiag

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autodiag.ui.CodiciScreen
import com.example.autodiag.ui.DiagnosiScreen
import com.example.autodiag.ui.ManutenzioneScreen
import com.example.autodiag.ui.theme.AutoDiagTheme
import com.example.autodiag.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private var pendingCallback: (() -> Unit)? = null
    private var retryAfterEnable: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val callback = pendingCallback
        pendingCallback = null
        if (grants.values.all { it }) callback?.invoke()
    }

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val retry = retryAfterEnable
        retryAfterEnable = null
        retry?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoDiagTheme {
                AutoDiagApp(
                    requestPermissions = { cb ->
                        pendingCallback = cb
                        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            arrayOf(
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                            )
                        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
                        permissionLauncher.launch(perms)
                    },
                    requestEnableBluetooth = { retry ->
                        retryAfterEnable = retry
                        enableBtLauncher.launch(
                            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDiagApp(
    requestPermissions: (() -> Unit) -> Unit,
    requestEnableBluetooth: (() -> Unit) -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    var tab by remember { mutableIntStateOf(0) }
    val labels = listOf("Diagnosi", "Codici", "Manutenzione")
    val icons = listOf(Icons.Default.Bluetooth, Icons.Default.Search, Icons.Default.Build)

    Scaffold(
        topBar = { TopAppBar(title = { Text("AutoDiag") }) },
        bottomBar = {
            NavigationBar {
                labels.forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> DiagnosiScreen(
                    vm = viewModel,
                    requestPermissions = requestPermissions,
                    requestEnableBluetooth = { requestEnableBluetooth {} }
                )
                1 -> CodiciScreen()
                2 -> ManutenzioneScreen(viewModel)
            }
        }
    }
}
