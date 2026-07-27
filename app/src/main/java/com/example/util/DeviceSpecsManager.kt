package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

data class DeviceSpecs(
    val totalPhysicalRamGb: Double,
    val availableRamGb: Double,
    val freeStorageGb: Double,
    val totalStorageGb: Double,
    val vulkanSupported: Boolean,
    val vulkanVersion: String,
    val apiLevel: Int,
    val isLowRamDevice: Boolean // RAM <= 4.5 GB
)

data class LowRamEngineConfig(
    val maxContextLength: Int = 1024,
    val useMmap: Boolean = true,
    val executionBackend: String = "CPU (4 Threads)",
    val cpuThreads: Int = 4,
    val description: String = "Low-Memory Safeguards Enabled: KV Cache reduced to 1024 tokens, mmap active, 4 CPU threads."
)

object DeviceSpecsManager {

    fun getDeviceSpecs(context: Context): DeviceSpecs {
        // 1. Physical & Available RAM
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val totalRamBytes = memoryInfo.totalMem
        val availRamBytes = memoryInfo.availMem

        val totalRamGb = (totalRamBytes / (1024.0 * 1024.0 * 1024.0) * 10).let { Math.round(it) / 10.0 }
        val availRamGb = (availRamBytes / (1024.0 * 1024.0 * 1024.0) * 10).let { Math.round(it) / 10.0 }

        // 2. Internal Free & Total Storage
        val dataDir: File = Environment.getDataDirectory()
        val statFs = StatFs(dataDir.path)
        val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val totalBytes = statFs.blockCountLong * statFs.blockSizeLong

        val freeStorageGb = (availableBytes / (1024.0 * 1024.0 * 1024.0) * 10).let { Math.round(it) / 10.0 }
        val totalStorageGb = (totalBytes / (1024.0 * 1024.0 * 1024.0) * 10).let { Math.round(it) / 10.0 }

        // 3. Vulkan Support & API Level
        val pm = context.packageManager
        val hasVulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val vulkanVersionStr = if (pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)) {
            "Vulkan 1.1+"
        } else if (hasVulkan) {
            "Vulkan 1.0"
        } else {
            "Not Supported (CPU Only)"
        }

        val isLowRam = totalRamGb <= 4.5

        return DeviceSpecs(
            totalPhysicalRamGb = totalRamGb,
            availableRamGb = availRamGb,
            freeStorageGb = freeStorageGb,
            totalStorageGb = totalStorageGb,
            vulkanSupported = hasVulkan,
            vulkanVersion = vulkanVersionStr,
            apiLevel = Build.VERSION.SDK_INT,
            isLowRamDevice = isLowRam
        )
    }

    fun getEngineConfigForSpecs(specs: DeviceSpecs): LowRamEngineConfig {
        return if (specs.isLowRamDevice) {
            LowRamEngineConfig(
                maxContextLength = 1024,
                useMmap = true,
                executionBackend = "CPU (4 Threads)",
                cpuThreads = 4,
                description = "4GB Low-Memory Mode: Context limit set to 1024 tokens to cut KV Cache RAM requirement in half; Memory Mapping enabled."
            )
        } else {
            LowRamEngineConfig(
                maxContextLength = 2048,
                useMmap = true,
                executionBackend = if (specs.vulkanSupported) "GPU (Vulkan) / Hybrid" else "CPU (8 Threads)",
                cpuThreads = 8,
                description = "Standard Engine Config: 2048 token context with memory mapping."
            )
        }
    }
}
