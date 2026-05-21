package com.example.utils

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.io.InputStream
import java.io.BufferedReader
import java.io.FileReader

data class CpuSpecs(
    val architecture: String,
    val model: String,
    val revision: String,
    val hardware: String,
    val manufacturer: String,
    val coresCount: Int,
    val instructionSets: List<String>
)

data class BatterySpecs(
    val health: String,
    val level: Int,
    val scale: Int,
    val voltage: Int, // mV
    val temperature: Float, // °C
    val technology: String,
    val status: String,
    val pluggedSource: String,
    val capacityAh: Double // Approximate capacity if available
)

data class RamStatus(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val utilizationPercentage: Float,
    val isLowMemory: Boolean
)

data class StoragePartition(
    val name: String,
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val path: String,
    val isExternal: Boolean,
    val mountedState: String
)

object SystemInfoProvider {

    fun getCpuSpecs(): CpuSpecs {
        val arch = System.getProperty("os.arch") ?: "Unknown"
        val supportedAbis = Build.SUPPORTED_ABIS.toList()

        var cpuModel = "Unknown Processor"
        var cpuHardware = Build.HARDWARE ?: "Unknown"
        var cpuRevision = "r0p0"

        // Search in /proc/cpuinfo
        try {
            val reader = BufferedReader(FileReader("/proc/cpuinfo"))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.split(":").map { it.trim() }
                if (parts.size >= 2) {
                    val key = parts[0].lowercase()
                    val value = parts[1]
                    if (key.contains("model name") || key.contains("processor")) {
                        if (cpuModel == "Unknown Processor" || cpuModel.contains("Processor")) {
                            cpuModel = value
                        }
                    } else if (key.contains("hardware")) {
                        cpuHardware = value
                    } else if (key.contains("cpu revision")) {
                        cpuRevision = value
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            // Ignored
        }

        if (cpuModel == "Unknown Processor" || cpuModel.trim().isEmpty()) {
            cpuModel = if (Build.BOARD != "unknown") Build.BOARD else Build.HARDWARE
        }

        return CpuSpecs(
            architecture = arch,
            model = cpuModel,
            revision = cpuRevision,
            hardware = cpuHardware,
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            coresCount = Runtime.getRuntime().availableProcessors(),
            instructionSets = supportedAbis
        )
    }

    fun getRamStatus(context: Context): RamStatus {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val total = memoryInfo.totalMem
        val available = memoryInfo.availMem
        val used = total - available
        val percentage = if (total > 0) (used.toFloat() / total.toFloat()) * 100f else 0f

        return RamStatus(
            totalBytes = total,
            availableBytes = available,
            usedBytes = used,
            utilizationPercentage = percentage,
            isLowMemory = memoryInfo.lowMemory
        )
    }

    fun getStoragePartitions(context: Context): List<StoragePartition> {
        val partitions = mutableListOf<StoragePartition>()

        // 1. Internal Storage Partition
        val internalPath = Environment.getDataDirectory().path
        try {
            val stat = StatFs(internalPath)
            val total = stat.blockCountLong * stat.blockSizeLong
            val available = stat.availableBlocksLong * stat.blockSizeLong
            val used = total - available
            partitions.add(
                StoragePartition(
                    name = "Internal Partition",
                    totalBytes = total,
                    availableBytes = available,
                    usedBytes = used,
                    path = internalPath,
                    isExternal = false,
                    mountedState = Environment.MEDIA_MOUNTED
                )
            )
        } catch (e: Exception) {
            // Error mapping partition
        }

        // 2. Main Emulated / External Storage Path
        val externalState = Environment.getExternalStorageState()
        if (externalState == Environment.MEDIA_MOUNTED || externalState == Environment.MEDIA_MOUNTED_READ_ONLY) {
            val extPath = Environment.getExternalStorageDirectory().path
            try {
                val stat = StatFs(extPath)
                val total = stat.blockCountLong * stat.blockSizeLong
                val available = stat.availableBlocksLong * stat.blockSizeLong
                val used = total - available

                // Only add if it differs from internal path, or represents the emulated SD card path which is nice to list separation-wise.
                if (extPath != internalPath) {
                    partitions.add(
                        StoragePartition(
                            name = "External (Primary SD)",
                            totalBytes = total,
                            availableBytes = available,
                            usedBytes = used,
                            path = extPath,
                            isExternal = true,
                            mountedState = externalState
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignored
            }
        }

        // 3. Search other auxiliary directories returned by the system for physical SD Cards/Partitions
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (idx in 1 until externalDirs.size) {
                val dir = externalDirs[idx]
                if (dir != null) {
                    val path = dir.path
                    // Find actual secondary root partition from standard android package path
                    // /storage/XXXX-XXXX/Android/data/... -> /storage/XXXX-XXXX/
                    val rootPath = path.substringBefore("/Android/data/")
                    val partitionFile = File(rootPath)
                    if (partitionFile.exists() && partitionFile.canRead()) {
                        val stat = StatFs(rootPath)
                        val total = stat.blockCountLong * stat.blockSizeLong
                        val available = stat.availableBlocksLong * stat.blockSizeLong
                        val used = total - available

                        // Prevent duplicate paths
                        if (partitions.none { it.path == rootPath }) {
                            partitions.add(
                                StoragePartition(
                                    name = "External Partition (SD Card)",
                                    totalBytes = total,
                                    availableBytes = available,
                                    usedBytes = used,
                                    path = rootPath,
                                    isExternal = true,
                                    mountedState = Environment.MEDIA_MOUNTED
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Catch SD scan failures safely
        }

        return partitions
    }

    // Gathers system thermal zones if readable, or falls back to battery health
    fun getSystemTemperature(batteryTempCelsius: Float): Float {
        val paths = arrayOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone1/temp"
        )

        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val reader = BufferedReader(FileReader(file))
                    val line = reader.readLine()
                    reader.close()
                    if (line != null) {
                        val rawTemp = line.toFloatOrNull()
                        if (rawTemp != null) {
                            // Some devices write temperatures in millidegrees (e.g. 43000 for 43°C), others directly (e.g. 43.0 or 43)
                            val finalTemp = if (rawTemp > 200 || rawTemp < -20) rawTemp / 1000f else rawTemp
                            if (finalTemp in 0.0f..115.0f) {
                                return finalTemp
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Try next
            }
        }

        // Default or logical fallback is battery temp
        return if (batteryTempCelsius > 0) batteryTempCelsius else 32.5f
    }

    fun getBatterySpecsFromIntent(intent: Intent, context: Context): BatterySpecs {
        val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown (Fair)"
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val temperature = if (tempRaw > 0) tempRaw / 10f else 0.0f
        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

        val statusInt = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val status = when (statusInt) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }

        val pluggedInt = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val plugged = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Cable"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Unplugged (Battery)"
        }

        // Grab static design battery capacity via platform Profile API if accessible, or estimate depending on typical devices
        var capacity = 4500.0 // Default mAh estimation for standard Android phones
        try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfileConstructor = powerProfileClass.getConstructor(Context::class.java)
            val powerProfileInstance = powerProfileConstructor.newInstance(context)
            val getBatteryCapacityMethod = powerProfileClass.getMethod("getBatteryCapacity")
            val cap = getBatteryCapacityMethod.invoke(powerProfileInstance) as Double
            if (cap > 0) {
                capacity = cap
            }
        } catch (e: Exception) {
            // Fallback estimation
        }

        return BatterySpecs(
            health = health,
            level = level,
            scale = scale,
            voltage = voltage,
            temperature = temperature,
            technology = technology,
            status = status,
            pluggedSource = plugged,
            capacityAh = capacity
        )
    }
}
