package com.motionsight.app.viewmodel

import android.app.Application
import androidx.camera.core.ImageProxy
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motionsight.app.camera.LuminanceFrameReader
import com.motionsight.app.model.AppScreen
import com.motionsight.app.model.AppUiState
import com.motionsight.app.model.CameraStatus
import com.motionsight.app.model.FrameAcceptance
import com.motionsight.app.model.FrameRatePolicy
import com.motionsight.app.model.MotionMode
import com.motionsight.app.model.PresetResolver
import com.motionsight.app.model.Sheet
import com.motionsight.app.model.TargetPreset
import com.motionsight.app.motion.OpenCvMotionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Application.settingsDataStore by preferencesDataStore(name = "motion_settings")

class MotionViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var engine: OpenCvMotionEngine? = null
    private var initialized = false
    private var fallbackActive = false
    private var lastProcessedTimestampNanos = 0L

    init {
        viewModelScope.launch {
            val values = application.settingsDataStore.data.first()
            val mode = values[MODE_KEY]?.let { runCatching { MotionMode.valueOf(it) }.getOrNull() } ?: MotionMode.FIXED
            val preset = values[PRESET_KEY]?.let { runCatching { TargetPreset.valueOf(it) }.getOrNull() } ?: TargetPreset.CRAWLING
            _uiState.update { it.copy(mode = mode, preset = preset, settings = PresetResolver.resolve(mode, preset)) }
        }
    }

    fun onLaunch(permissionGranted: Boolean) {
        if (initialized) return
        initialized = true
        _uiState.update { it.copy(screen = if (permissionGranted) AppScreen.SETUP else AppScreen.WELCOME) }
    }

    fun showPermission() = _uiState.update { it.copy(screen = AppScreen.PERMISSION) }
    fun showWelcome() = _uiState.update { it.copy(screen = AppScreen.WELCOME) }
    fun showSetup() = _uiState.update { it.copy(screen = AppScreen.SETUP, sheet = Sheet.NONE) }
    fun showCamera() {
        fallbackActive = false
        ensureEngine()
        resetDetection()
        _uiState.update { it.copy(screen = AppScreen.CAMERA, cameraStatus = CameraStatus.BINDING, sheet = Sheet.NONE) }
    }

    fun showInfo(from: AppScreen) = _uiState.update { it.copy(screen = AppScreen.INFO, returnFromInfo = from, sheet = Sheet.NONE) }
    fun closeInfo() = _uiState.update { it.copy(screen = it.returnFromInfo) }
    fun showSheet(sheet: Sheet) = _uiState.update { it.copy(sheet = sheet) }
    fun closeSheet() = _uiState.update { it.copy(sheet = Sheet.NONE) }

    fun setMode(mode: MotionMode) {
        val current = _uiState.value
        val settings = PresetResolver.resolve(mode, current.preset)
        _uiState.update { it.copy(mode = mode, settings = settings, sheet = Sheet.NONE) }
        persistProfile(mode, current.preset)
        resetDetection()
    }

    fun setPreset(preset: TargetPreset) {
        val current = _uiState.value
        val settings = PresetResolver.resolve(current.mode, preset)
        _uiState.update { it.copy(preset = preset, settings = settings) }
        persistProfile(current.mode, preset)
        resetDetection()
    }

    fun setSensitivity(value: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(sensitivity = value)) }
        configureEngine()
    }

    fun setMinimumArea(value: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(minimumAreaPx = value)) }
        configureEngine()
    }

    fun setPersistence(value: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(persistenceFrames = value)) }
        resetDetection()
    }

    fun setLearningRate(value: Double) {
        _uiState.update { it.copy(settings = it.settings.copy(learningRate = value)) }
        configureEngine()
    }

    fun setInstabilityThreshold(value: Float) {
        _uiState.update { it.copy(settings = it.settings.copy(instabilityThreshold = value)) }
        configureEngine()
    }

    fun restoreDefaults() {
        val current = _uiState.value
        _uiState.update { it.copy(settings = PresetResolver.resolve(current.mode, current.preset)) }
        resetDetection()
    }

    fun onCameraBound(torchAvailable: Boolean, minZoom: Float, maxZoom: Float) {
        engine?.reset()
        configureEngine()
        _uiState.update {
            it.copy(
                cameraStatus = CameraStatus.WARMING_UP,
                torchAvailable = torchAvailable,
                minZoomRatio = minZoom,
                maxZoomRatio = maxZoom,
                zoomRatio = 1f.coerceIn(minZoom, maxZoom),
                errorMessage = null,
            )
        }
    }

    fun onCameraError(error: Throwable) {
        _uiState.update {
            it.copy(
                cameraStatus = CameraStatus.ERROR,
                errorMessage = error.message ?: "The camera could not be started.",
            )
        }
    }

    fun setTorch(enabled: Boolean) {
        _uiState.update { it.copy(torchEnabled = enabled) }
        resetDetection()
    }

    fun setZoom(value: Float) {
        _uiState.update { it.copy(zoomRatio = value) }
        resetDetection()
    }

    fun resetDetection() {
        lastProcessedTimestampNanos = 0L
        engine?.reset()
        configureEngine()
        _uiState.update {
            it.copy(
                cameraStatus = if (it.screen == AppScreen.CAMERA) CameraStatus.WARMING_UP else it.cameraStatus,
                statusMessage = "Preparing motion detection… Keep the camera still",
                frame = null,
            )
        }
    }

    fun analyze(image: ImageProxy) {
        try {
            val timestamp = image.imageInfo.timestamp
            val currentState = _uiState.value
            val minimumInterval = FrameRatePolicy.minimumIntervalNanos(currentState.mode, currentState.preset)
            if (lastProcessedTimestampNanos != 0L && timestamp - lastProcessedTimestampNanos < minimumInterval) {
                return
            }
            lastProcessedTimestampNanos = timestamp
            val activeEngine = ensureEngine()
            val bytes = LuminanceFrameReader.read(image)
            val result = activeEngine.process(
                luminance = bytes,
                width = image.width,
                height = image.height,
                rotationDegrees = image.imageInfo.rotationDegrees,
                timestampNanos = timestamp,
            )
            if (result.metrics.resolutionFallback) fallbackActive = true
            val visibleMetrics = result.metrics.copy(resolutionFallback = fallbackActive)
            val status = when (result.acceptance) {
                FrameAcceptance.ACCEPTED -> if (fallbackActive) CameraStatus.DEGRADED else CameraStatus.ACTIVE
                FrameAcceptance.WARMING_UP -> CameraStatus.WARMING_UP
                FrameAcceptance.REJECTED_UNSTABLE -> CameraStatus.UNSTABLE
                FrameAcceptance.REJECTED_SCENE_CHANGE,
                FrameAcceptance.REJECTED_EXPOSURE_FLASH -> CameraStatus.WARMING_UP
            }
            val message = when (result.acceptance) {
                FrameAcceptance.WARMING_UP -> "Preparing motion detection… Keep the camera still"
                FrameAcceptance.REJECTED_UNSTABLE -> "Camera unstable — move slowly and keep the scene steady"
                FrameAcceptance.REJECTED_SCENE_CHANGE -> "Scene changed — resetting"
                FrameAcceptance.REJECTED_EXPOSURE_FLASH -> "Light changed — resetting"
                FrameAcceptance.ACCEPTED -> if (fallbackActive) "Reduced analysis resolution" else null
            }
            _uiState.update {
                it.copy(
                    frame = result.frame,
                    metrics = visibleMetrics,
                    cameraStatus = status,
                    statusMessage = message,
                )
            }
        } catch (error: Throwable) {
            onCameraError(error)
        } finally {
            image.close()
        }
    }

    private fun ensureEngine(): OpenCvMotionEngine {
        val existing = engine
        if (existing != null) return existing
        return OpenCvMotionEngine().also {
            engine = it
            configureEngine()
        }
    }

    private fun configureEngine() {
        val current = _uiState.value
        engine?.configure(current.mode, current.settings)
    }

    private fun persistProfile(mode: MotionMode, preset: TargetPreset) {
        viewModelScope.launch {
            getApplication<Application>().settingsDataStore.edit {
                it[MODE_KEY] = mode.name
                it[PRESET_KEY] = preset.name
            }
        }
    }

    override fun onCleared() {
        engine?.release()
        engine = null
        super.onCleared()
    }

    companion object {
        private val MODE_KEY = stringPreferencesKey("mode")
        private val PRESET_KEY = stringPreferencesKey("preset")
    }
}
