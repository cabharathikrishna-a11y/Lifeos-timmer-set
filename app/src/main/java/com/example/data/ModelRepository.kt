package com.example.data

import com.example.util.DeviceSpecs

enum class CompatibilityStatus {
    OPTIMAL,
    COMPATIBLE,
    COMPATIBLE_WITH_CAUTION,
    STORAGE_LOW,
    HARDWARE_UNSUPPORTED
}

data class CompatibilityResult(
    val status: CompatibilityStatus,
    val badgeLabel: String,
    val description: String,
    val storageDeficitGb: Double = 0.0,
    val isDownloadBlocked: Boolean = false,
    val requiresWarningDialog: Boolean = false,
    val showsCautionToast: Boolean = false
)

data class LocalAiModel(
    val id: String,
    val name: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeGb: Double,
    val minRamGb: Double,
    val recRamGb: Double,
    val minStorageGb: Double,
    val description: String,
    val category: String
) {
    fun evaluateCompatibility(deviceSpecs: DeviceSpecs): CompatibilityResult {
        // 1. Storage check first (Blocks download completely)
        if (deviceSpecs.freeStorageGb < minStorageGb) {
            val deficit = (minStorageGb - deviceSpecs.freeStorageGb * 10).let { Math.round(it) / 10.0 }
            val deficitText = String.format("%.1f", deficit)
            return CompatibilityResult(
                status = CompatibilityStatus.STORAGE_LOW,
                badgeLabel = "Insufficient Storage",
                description = "Requires $minStorageGb GB free storage. Need ${deficitText} GB more space.",
                storageDeficitGb = deficit,
                isDownloadBlocked = true,
                requiresWarningDialog = false
            )
        }

        // 2. RAM check for Hardware Unsupported (RAM < minRamGb)
        if (deviceSpecs.totalPhysicalRamGb < minRamGb) {
            return CompatibilityResult(
                status = CompatibilityStatus.HARDWARE_UNSUPPORTED,
                badgeLabel = "High Risk: Hardware Below Specs",
                description = "Device RAM (${deviceSpecs.totalPhysicalRamGb} GB) is below minimum required (${minRamGb} GB). Running this model may cause OOM crashes.",
                isDownloadBlocked = false,
                requiresWarningDialog = true
            )
        }

        // 3. 4GB Low-RAM Device caution handling (RAM <= 4.5 GB & model min RAM >= 3.5 GB)
        if (deviceSpecs.isLowRamDevice && minRamGb >= 3.5) {
            return CompatibilityResult(
                status = CompatibilityStatus.COMPATIBLE_WITH_CAUTION,
                badgeLabel = "Heavy for this Device",
                description = "Compatible with caution. Close background applications for optimal response speed.",
                isDownloadBlocked = false,
                showsCautionToast = true
            )
        }

        // 4. Optimal vs Compatible
        if (deviceSpecs.totalPhysicalRamGb >= recRamGb) {
            return CompatibilityResult(
                status = CompatibilityStatus.OPTIMAL,
                badgeLabel = "Recommended",
                description = "Device hardware exceeds recommended specs. Excellent performance expected.",
                isDownloadBlocked = false
            )
        }

        return CompatibilityResult(
            status = CompatibilityStatus.COMPATIBLE,
            badgeLabel = "Supported",
            description = "Device meets hardware minimums. Stable local execution guaranteed.",
            isDownloadBlocked = false
        )
    }
}

object ModelRepository {

    val catalog: List<LocalAiModel> = listOf(
        LocalAiModel(
            id = "smollm2_360m",
            name = "SmolLM2 360M (INT4)",
            fileName = "smollm2-360m-instruct-q4.gguf",
            downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q4_k_m.gguf",
            sizeGb = 0.35,
            minRamGb = 3.0,
            recRamGb = 4.0,
            minStorageGb = 0.8,
            description = "Ultra-fast lightweight model for basic tasks and older phones. Zero lag.",
            category = "Ultra-Light 4GB Tier"
        ),
        LocalAiModel(
            id = "qwen2_5_0_5b",
            name = "Qwen2.5 0.5B (INT4)",
            fileName = "qwen2.5-0.5b-instruct-q4.gguf",
            downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            sizeGb = 0.45,
            minRamGb = 3.5,
            recRamGb = 4.0,
            minStorageGb = 1.0,
            description = "Best balance of low memory usage and smart instruction following for 4GB devices.",
            category = "Ultra-Light 4GB Tier"
        ),
        LocalAiModel(
            id = "llama_3_2_1b",
            name = "Llama 3.2 1B (INT4)",
            fileName = "llama-3.2-1b-instruct-q4.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            sizeGb = 0.9,
            minRamGb = 4.0,
            recRamGb = 6.0,
            minStorageGb = 1.5,
            description = "High intelligence for 4GB phones. May run slower if background apps are open.",
            category = "Balanced Tier"
        ),
        LocalAiModel(
            id = "gemma_3_1b",
            name = "Gemma 3 1B (INT4)",
            fileName = "gemma-3-1b-it-q4.task",
            downloadUrl = "https://huggingface.co/google/gemma-3-1b-it-gguf/resolve/main/gemma-3-1b-it-q4_k_m.gguf",
            sizeGb = 1.2,
            minRamGb = 6.0,
            recRamGb = 8.0,
            minStorageGb = 1.5,
            description = "State-of-the-art Google compact model with multimodal intelligence.",
            category = "Compact Tier"
        ),
        LocalAiModel(
            id = "llama_3_2_3b",
            name = "Llama 3.2 3B (INT4)",
            fileName = "llama-3.2-3b-instruct-q4.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sizeGb = 2.2,
            minRamGb = 8.0,
            recRamGb = 12.0,
            minStorageGb = 3.5,
            description = "High-capability reasoning model for heavy analytical workloads.",
            category = "High Capability Tier"
        ),
        LocalAiModel(
            id = "phi_4_mini_3_8b",
            name = "Phi-4 Mini 3.8B (INT4)",
            fileName = "phi-4-mini-3.8b-instruct-q4.gguf",
            downloadUrl = "https://huggingface.co/microsoft/Phi-4-mini-instruct-GGUF/resolve/main/Phi-4-mini-instruct-q4_k_m.gguf",
            sizeGb = 2.6,
            minRamGb = 8.0,
            recRamGb = 12.0,
            minStorageGb = 4.0,
            description = "Microsoft's flagship small language model for advanced reasoning.",
            category = "High Capability Tier"
        )
    )

    fun getModelById(id: String): LocalAiModel? {
        return catalog.find { it.id == id }
    }
}
