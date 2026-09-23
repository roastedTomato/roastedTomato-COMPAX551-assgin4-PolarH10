package com.example.polarh10

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.polarh10.polar.PolarH10Manager
import com.example.polarh10.ui.dashboard.DashboardScreen
import com.example.polarh10.ui.format.requiredBluetoothPermissions
import com.example.polarh10.ui.history.HistoryScreen
import com.example.polarh10.ui.theme.PolarDarkColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PolarH10App()
        }
    }
}

enum class AppPage {
    Dashboard,
    History
}

@Composable
private fun PolarH10App() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val manager = remember { PolarH10Manager(context, scope) }
    val state by manager.state.collectAsState()
    var page by remember { mutableStateOf(AppPage.Dashboard) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            manager.startScan()
        }
    }

    DisposableEffect(Unit) {
        onDispose { manager.shutdown() }
    }

    MaterialTheme(colorScheme = PolarDarkColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (page) {
                AppPage.Dashboard -> DashboardScreen(
                    state = state,
                    onScan = {
                        permissionLauncher.launch(requiredBluetoothPermissions())
                    },
                    onStopScan = manager::stopScan,
                    onConnect = manager::connect,
                    onDisconnect = manager::disconnect,
                    onStartHr = manager::startHrStream,
                    onStopHr = manager::stopHrStream,
                    onStartAcc = manager::startAccStream,
                    onStopAcc = manager::stopAccStream,
                    onStartEcg = manager::startEcgStream,
                    onStopEcg = manager::stopEcgStream,
                    onStartSession = manager::startSession,
                    onStopSession = manager::stopSession,
                    onOpenHistory = {
                        manager.refreshHistory()
                        page = AppPage.History
                    }
                )

                AppPage.History -> HistoryScreen(
                    state = state,
                    onBack = { page = AppPage.Dashboard },
                    onRefreshHistory = manager::refreshHistory,
                    onSelectSession = manager::loadHistoryDetail,
                    onImportSampleHistory = manager::importSampleHistory,
                    onWeightChange = manager::updateWeightKg
                )
            }
        }
    }
}
