package com.example.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.utils.BatterySpecs
import com.example.utils.CpuSpecs
import com.example.utils.PdfExporter
import com.example.utils.RamStatus
import com.example.utils.RootCheckResult
import com.example.utils.RootDetector
import com.example.utils.StoragePartition
import com.example.utils.SystemInfoProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MonitorUiState(
    val rootResult: RootCheckResult? = null,
    val cpuSpecs: CpuSpecs? = null,
    val batterySpecs: BatterySpecs? = null,
    val ramStatus: RamStatus? = null,
    val partitions: List<StoragePartition> = emptyList(),
    val systemTemperature: Float = 0.0f,
    val isScanning: Boolean = true,
    val exportStatus: ExportStatus? = null
)

sealed interface ExportStatus {
    object Idle : ExportStatus
    object Exporting : ExportStatus
    data class Success(val message: String, val openUri: Uri?, val isShare: Boolean) : ExportStatus
    data class Error(val message: String) : ExportStatus
}

class SystemMonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    private var monitorJob: Job? = null
    
    // Broadcast Receiver to listen to Battery status broadcasts
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && context != null) {
                val specs = SystemInfoProvider.getBatterySpecsFromIntent(intent, context)
                _uiState.update { current ->
                    current.copy(
                        batterySpecs = specs,
                        // Ensure temperature updates immediately when battery intent fires
                        systemTemperature = SystemInfoProvider.getSystemTemperature(specs.temperature)
                    )
                }
            }
        }
    }

    init {
        startHardwareScan()
    }

    fun startHardwareScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            
            val context = getApplication<Application>().applicationContext
            
            // 1. Initial Root Check Scan
            val rootCheck = RootDetector.detectRoot(context)
            
            // 2. Load Static CPU Specs
            val cpu = SystemInfoProvider.getCpuSpecs()

            // 3. First-run RAM and Disk readings
            val ram = SystemInfoProvider.getRamStatus(context)
            val storage = SystemInfoProvider.getStoragePartitions(context)

            _uiState.update { current ->
                current.copy(
                    rootResult = rootCheck,
                    cpuSpecs = cpu,
                    ramStatus = ram,
                    partitions = storage,
                    isScanning = false
                )
            }

            // Register battery status receiver
            try {
                androidx.core.content.ContextCompat.registerReceiver(
                    context,
                    batteryReceiver,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } catch (e: Exception) {
                // Ignore register errors on background/unit tests
            }

            // Start loop to update real-time monitors (RAM, Disks, Temperature)
            startPeriodicMonitoring()
        }
    }

    private fun startPeriodicMonitoring() {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            while (true) {
                // Read metrics
                val ram = SystemInfoProvider.getRamStatus(context)
                val disks = SystemInfoProvider.getStoragePartitions(context)
                
                // Get general physical temperature
                val batTemp = _uiState.value.batterySpecs?.temperature ?: 0.0f
                val systemTemp = SystemInfoProvider.getSystemTemperature(batTemp)

                _uiState.update { current ->
                    current.copy(
                        ramStatus = ram,
                        partitions = disks,
                        systemTemperature = systemTemp
                    )
                }
                
                // Update every 2 seconds for smooth but CPU-safe real-time feed
                delay(2000)
            }
        }
    }

    fun exportDocumentReport(context: Context, share: Boolean) {
        val currentState = _uiState.value
        val root = currentState.rootResult ?: return
        val cpu = currentState.cpuSpecs ?: return
        val battery = currentState.batterySpecs ?: return
        val ram = currentState.ramStatus ?: return
        val activeTemp = currentState.systemTemperature

        _uiState.update { it.copy(exportStatus = ExportStatus.Exporting) }

        viewModelScope.launch {
            // Give brief delay to represent action smoothly
            delay(400)
            PdfExporter.exportReport(
                context = context,
                rootResult = root,
                cpuSpecs = cpu,
                batterySpecs = battery,
                ramStatus = ram,
                partitions = currentState.partitions,
                currentTemp = activeTemp,
                shareAfterExport = share
            ) { success, message, uri ->
                _uiState.update { state ->
                    if (success) {
                        state.copy(exportStatus = ExportStatus.Success(message, uri, share))
                    } else {
                        state.copy(exportStatus = ExportStatus.Error(message))
                    }
                }
            }
        }
    }

    fun resetExportStatus() {
        _uiState.update { it.copy(exportStatus = null) }
    }

    override fun onCleared() {
        super.onCleared()
        monitorJob?.cancel()
        try {
            getApplication<Application>().applicationContext.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignored if wasn't registered
        }
    }
}
