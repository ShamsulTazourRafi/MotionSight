package com.motionsight.app.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraController(private val context: Context) {
    data class Capabilities(
        val torchAvailable: Boolean,
        val minZoomRatio: Float,
        val maxZoomRatio: Float,
    )

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    fun bind(
        lifecycleOwner: LifecycleOwner,
        useFallbackResolution: Boolean,
        analyzer: (ImageProxy) -> Unit,
        onBound: (Capabilities) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                cameraProvider = provider
                provider.unbindAll()
                val requestedSize = if (useFallbackResolution) Size(960, 540) else Size(1280, 720)
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            requestedSize,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                analysis.setAnalyzer(analysisExecutor, analyzer)
                val boundCamera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    analysis,
                )
                camera = boundCamera
                val zoom = boundCamera.cameraInfo.zoomState.value
                onBound(
                    Capabilities(
                        torchAvailable = boundCamera.cameraInfo.hasFlashUnit(),
                        minZoomRatio = zoom?.minZoomRatio ?: 1f,
                        maxZoomRatio = zoom?.maxZoomRatio ?: 1f,
                    ),
                )
            }.onFailure(onError)
        }, ContextCompat.getMainExecutor(context))
    }

    fun setTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    fun unbind() {
        cameraProvider?.unbindAll()
        camera = null
    }

    fun release() {
        unbind()
        analysisExecutor.shutdown()
    }
}
