package com.motionsight.app.model

enum class MotionMode { FIXED, HANDHELD }

enum class TargetPreset { CRAWLING, FLYING }

enum class AppScreen { WELCOME, PERMISSION, SETUP, CAMERA, INFO }

enum class CameraStatus {
    IDLE,
    BINDING,
    WARMING_UP,
    ACTIVE,
    UNSTABLE,
    DEGRADED,
    ERROR,
}

enum class Sheet { NONE, CONTROLS, DIAGNOSTICS, PROFILE }

enum class FrameAcceptance {
    ACCEPTED,
    WARMING_UP,
    REJECTED_UNSTABLE,
    REJECTED_SCENE_CHANGE,
    REJECTED_EXPOSURE_FLASH,
}

data class MotionSettings(
    val sensitivity: Float,
    val minimumAreaPx: Int,
    val persistenceFrames: Int,
    val learningRate: Double,
    val instabilityThreshold: Float,
)

object PresetResolver {
    fun resolve(mode: MotionMode, preset: TargetPreset): MotionSettings = when (mode to preset) {
        MotionMode.FIXED to TargetPreset.CRAWLING -> MotionSettings(
            sensitivity = 0.56f,
            minimumAreaPx = 80,
            persistenceFrames = 5,
            learningRate = 0.002,
            instabilityThreshold = 0.45f,
        )
        MotionMode.FIXED to TargetPreset.FLYING -> MotionSettings(
            sensitivity = 0.68f,
            minimumAreaPx = 30,
            persistenceFrames = 2,
            learningRate = 0.008,
            instabilityThreshold = 0.42f,
        )
        MotionMode.HANDHELD to TargetPreset.CRAWLING -> MotionSettings(
            sensitivity = 0.48f,
            minimumAreaPx = 120,
            persistenceFrames = 4,
            learningRate = 0.004,
            instabilityThreshold = 0.58f,
        )
        MotionMode.HANDHELD to TargetPreset.FLYING -> MotionSettings(
            sensitivity = 0.58f,
            minimumAreaPx = 55,
            persistenceFrames = 2,
            learningRate = 0.01,
            instabilityThreshold = 0.62f,
        )
        else -> error("Unsupported motion profile: $mode / $preset")
    }
}

object FrameRatePolicy {
    fun targetFps(mode: MotionMode, preset: TargetPreset): Int = when {
        preset == TargetPreset.FLYING && mode == MotionMode.FIXED -> 20
        preset == TargetPreset.FLYING -> 18
        else -> 15
    }

    fun minimumIntervalNanos(mode: MotionMode, preset: TargetPreset): Long =
        1_000_000_000L / targetFps(mode, preset)
}

data class FrameMetrics(
    val fps: Float = 0f,
    val processingMs: Float = 0f,
    val medianMs: Float = 0f,
    val p95Ms: Float = 0f,
    val motionPercentage: Float = 0f,
    val stabilizationConfidence: Float? = null,
    val width: Int = 0,
    val height: Int = 0,
    val rejectedFrames: Long = 0,
    val resolutionFallback: Boolean = false,
)

data class MotionFrameResult(
    val frame: RenderFrame,
    val acceptance: FrameAcceptance,
    val metrics: FrameMetrics,
)

data class RenderFrame(
    val grayscale: ByteArray,
    val mask: ByteArray,
    val width: Int,
    val height: Int,
)

data class AppUiState(
    val screen: AppScreen = AppScreen.WELCOME,
    val returnFromInfo: AppScreen = AppScreen.SETUP,
    val sheet: Sheet = Sheet.NONE,
    val mode: MotionMode = MotionMode.FIXED,
    val preset: TargetPreset = TargetPreset.CRAWLING,
    val settings: MotionSettings = PresetResolver.resolve(MotionMode.FIXED, TargetPreset.CRAWLING),
    val cameraStatus: CameraStatus = CameraStatus.IDLE,
    val frame: RenderFrame? = null,
    val metrics: FrameMetrics = FrameMetrics(),
    val torchEnabled: Boolean = false,
    val torchAvailable: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
)
