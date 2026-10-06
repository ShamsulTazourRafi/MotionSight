package com.motionsight.app.motion

import com.motionsight.app.model.FrameAcceptance
import com.motionsight.app.model.FrameMetrics
import com.motionsight.app.model.MotionFrameResult
import com.motionsight.app.model.MotionMode
import com.motionsight.app.model.MotionSettings
import com.motionsight.app.model.RenderFrame
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.video.BackgroundSubtractorMOG2
import org.opencv.video.Video
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

class OpenCvMotionEngine {
    private var mode = MotionMode.FIXED
    private var settings = MotionSettings(0.56f, 80, 5, 0.002, 0.45f)
    private var subtractor: BackgroundSubtractorMOG2? = null
    private var previous: Mat? = null
    private var previousTwo: Mat? = null
    private var warmupFrames = 0
    private var lastFrameTimeNanos = 0L
    private var rejectedFrames = 0L
    private val performanceGovernor = PerformanceGovernor()
    private val generation = AtomicLong(0)

    init {
        check(OpenCVLoader.initLocal()) { "OpenCV could not be initialized" }
        rebuildSubtractor()
    }

    @Synchronized
    fun configure(newMode: MotionMode, newSettings: MotionSettings) {
        val modeChanged = mode != newMode
        mode = newMode
        settings = newSettings
        if (modeChanged) reset()
    }

    @Synchronized
    fun reset() {
        previous?.release()
        previousTwo?.release()
        previous = null
        previousTwo = null
        warmupFrames = 0
        rejectedFrames = 0
        lastFrameTimeNanos = 0L
        performanceGovernor.clear()
        generation.incrementAndGet()
        rebuildSubtractor()
    }

    @Synchronized
    fun process(
        luminance: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        timestampNanos: Long,
    ): MotionFrameResult {
        val started = System.nanoTime()
        val source = Mat(height, width, CvType.CV_8UC1)
        source.put(0, 0, luminance)
        val current = if (width > MAX_PROCESSING_WIDTH) {
            val scale = MAX_PROCESSING_WIDTH.toDouble() / width
            Mat().also {
                Imgproc.resize(
                    source,
                    it,
                    Size(MAX_PROCESSING_WIDTH.toDouble(), height * scale),
                    0.0,
                    0.0,
                    Imgproc.INTER_AREA,
                )
            }
        } else {
            source
        }

        val pipeline = when (mode) {
            MotionMode.FIXED -> processFixed(current)
            MotionMode.HANDHELD -> processHandheld(current)
        }

        val output = createRenderFrame(current, pipeline.mask, rotationDegrees)
        val durationMs = (System.nanoTime() - started) / 1_000_000f
        val perf = performanceGovernor.add(durationMs)
        val fps = if (lastFrameTimeNanos == 0L) 0f else {
            1_000_000_000f / max(1L, timestampNanos - lastFrameTimeNanos)
        }
        lastFrameTimeNanos = timestampNanos

        val resolutionFallback = perf.shouldFallback
        val acceptance = if (resolutionFallback && pipeline.acceptance == FrameAcceptance.ACCEPTED) {
            FrameAcceptance.ACCEPTED
        } else {
            pipeline.acceptance
        }

        val metrics = FrameMetrics(
            fps = fps.coerceIn(0f, 60f),
            processingMs = durationMs,
            medianMs = perf.medianMs,
            p95Ms = perf.p95Ms,
            motionPercentage = pipeline.motionPercentage,
            stabilizationConfidence = pipeline.confidence,
            width = width,
            height = height,
            rejectedFrames = rejectedFrames,
            resolutionFallback = resolutionFallback,
        )

        pipeline.mask.release()
        current.release()
        if (current !== source) source.release()
        return MotionFrameResult(output, acceptance, metrics)
    }

    @Synchronized
    fun release() {
        reset()
        subtractor?.clear()
        subtractor = null
    }

    private data class PipelineOutput(
        val mask: Mat,
        val acceptance: FrameAcceptance,
        val motionPercentage: Float,
        val confidence: Float?,
    )

    private fun processFixed(current: Mat): PipelineOutput {
        val mask = Mat.zeros(current.size(), CvType.CV_8UC1)
        val mogMask = Mat()
        subtractor?.apply(current, mogMask, settings.learningRate)

        val prior = previous
        val priorTwo = previousTwo
        val isWarm = prior == null || priorTwo == null || warmupFrames < 12
        if (!isWarm) {
            val diff1 = Mat()
            val diff2 = Mat()
            val temporal = Mat()
            Core.absdiff(current, prior, diff1)
            Core.absdiff(prior, priorTwo, diff2)
            val threshold = sensitivityThreshold()
            Imgproc.threshold(diff1, diff1, threshold, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.threshold(diff2, diff2, threshold, 255.0, Imgproc.THRESH_BINARY)
            Core.bitwise_and(diff1, diff2, temporal)
            Imgproc.threshold(mogMask, mogMask, 200.0, 255.0, Imgproc.THRESH_BINARY)
            Core.bitwise_or(mogMask, temporal, mask)
            cleanup(mask)
            filterSmallComponents(mask)
            diff1.release()
            diff2.release()
            temporal.release()
        }

        val meanShift = prior?.let { kotlin.math.abs(Core.mean(current).`val`[0] - Core.mean(it).`val`[0]) } ?: 0.0
        val percentage = motionPercentage(mask)
        val flash = meanShift > 24.0 || percentage > 45f
        val acceptance = when {
            isWarm -> FrameAcceptance.WARMING_UP
            flash -> FrameAcceptance.REJECTED_EXPOSURE_FLASH
            else -> FrameAcceptance.ACCEPTED
        }
        if (flash) mask.setTo(Scalar(0.0))

        advanceHistory(current)
        warmupFrames++
        mogMask.release()
        return PipelineOutput(mask, acceptance, if (flash) 0f else percentage, null)
    }

    private fun processHandheld(current: Mat): PipelineOutput {
        val mask = Mat.zeros(current.size(), CvType.CV_8UC1)
        val prior = previous
        if (prior == null || warmupFrames < 4) {
            advanceHistory(current)
            warmupFrames++
            return PipelineOutput(mask, FrameAcceptance.WARMING_UP, 0f, null)
        }

        val cornerPoints = MatOfPoint()
        Imgproc.goodFeaturesToTrack(prior, cornerPoints, 96, 0.012, 10.0)
        val corners = MatOfPoint2f(*cornerPoints.toArray())
        cornerPoints.release()
        if (corners.rows() < 12) {
            rejectedFrames++
            corners.release()
            advanceHistory(current)
            return PipelineOutput(mask, FrameAcceptance.REJECTED_UNSTABLE, 0f, 0f)
        }

        val nextPoints = MatOfPoint2f()
        val status = MatOfByte()
        val errors = MatOfFloat()
        Video.calcOpticalFlowPyrLK(prior, current, corners, nextPoints, status, errors)

        val from = mutableListOf<Point>()
        val to = mutableListOf<Point>()
        val sourceArray = corners.toArray()
        val targetArray = nextPoints.toArray()
        val statusArray = status.toArray()
        for (index in statusArray.indices) {
            if (statusArray[index].toInt() != 0 && index < sourceArray.size && index < targetArray.size) {
                from += sourceArray[index]
                to += targetArray[index]
            }
        }

        var confidence = 0f
        var acceptance = FrameAcceptance.REJECTED_UNSTABLE
        if (from.size >= 10) {
            val fromMat = MatOfPoint2f(*from.toTypedArray())
            val toMat = MatOfPoint2f(*to.toTypedArray())
            val inliers = Mat()
            val transform = Calib3d.estimateAffinePartial2D(
                fromMat,
                toMat,
                inliers,
                Calib3d.RANSAC,
                3.0,
            )
            if (!transform.empty()) {
                confidence = if (inliers.empty()) 0f else Core.countNonZero(inliers).toFloat() / from.size
                if (confidence >= settings.instabilityThreshold) {
                    val aligned = Mat()
                    val validSource = Mat.ones(prior.size(), CvType.CV_8UC1)
                    val valid = Mat()
                    Imgproc.warpAffine(prior, aligned, transform, current.size(), Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0))
                    Imgproc.warpAffine(validSource, valid, transform, current.size(), Imgproc.INTER_NEAREST, Core.BORDER_CONSTANT, Scalar(0.0))
                    Core.absdiff(current, aligned, mask)
                    Imgproc.threshold(mask, mask, sensitivityThreshold() + 4.0, 255.0, Imgproc.THRESH_BINARY)
                    Core.bitwise_and(mask, valid, mask)
                    cleanup(mask)
                    filterSmallComponents(mask)
                    acceptance = if (motionPercentage(mask) > 28f) {
                        mask.setTo(Scalar(0.0))
                        FrameAcceptance.REJECTED_UNSTABLE
                    } else {
                        FrameAcceptance.ACCEPTED
                    }
                    aligned.release()
                    validSource.release()
                    valid.release()
                }
            }
            transform.release()
            inliers.release()
            fromMat.release()
            toMat.release()
        }

        if (acceptance != FrameAcceptance.ACCEPTED) rejectedFrames++
        val percentage = if (acceptance == FrameAcceptance.ACCEPTED) motionPercentage(mask) else 0f
        if (acceptance != FrameAcceptance.ACCEPTED) mask.setTo(Scalar(0.0))
        advanceHistory(current)
        warmupFrames++
        corners.release()
        nextPoints.release()
        status.release()
        errors.release()
        return PipelineOutput(mask, acceptance, percentage, confidence)
    }

    private fun cleanup(mask: Mat) {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        kernel.release()
    }

    private fun filterSmallComponents(mask: Mat) {
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(mask, labels, stats, centroids)
        val filtered = Mat.zeros(mask.size(), CvType.CV_8UC1)
        val componentMask = Mat()
        for (label in 1 until count) {
            val area = stats.get(label, Imgproc.CC_STAT_AREA)?.firstOrNull()?.toInt() ?: 0
            if (area >= settings.minimumAreaPx) {
                Core.compare(labels, Scalar(label.toDouble()), componentMask, Core.CMP_EQ)
                filtered.setTo(Scalar(255.0), componentMask)
            }
        }
        filtered.copyTo(mask)
        labels.release()
        stats.release()
        centroids.release()
        componentMask.release()
        filtered.release()
    }

    private fun createRenderFrame(current: Mat, mask: Mat, rotationDegrees: Int): RenderFrame {
        val maxOutputWidth = 480.0
        val scale = if (current.cols() > maxOutputWidth) maxOutputWidth / current.cols() else 1.0
        val scaledGray = Mat()
        val scaledMask = Mat()
        val targetSize = Size(current.cols() * scale, current.rows() * scale)
        Imgproc.resize(current, scaledGray, targetSize, 0.0, 0.0, Imgproc.INTER_AREA)
        Imgproc.resize(mask, scaledMask, targetSize, 0.0, 0.0, Imgproc.INTER_NEAREST)
        val orientedGray = Mat()
        val orientedMask = Mat()
        when (rotationDegrees) {
            90 -> {
                Core.rotate(scaledGray, orientedGray, Core.ROTATE_90_CLOCKWISE)
                Core.rotate(scaledMask, orientedMask, Core.ROTATE_90_CLOCKWISE)
            }
            180 -> {
                Core.rotate(scaledGray, orientedGray, Core.ROTATE_180)
                Core.rotate(scaledMask, orientedMask, Core.ROTATE_180)
            }
            270 -> {
                Core.rotate(scaledGray, orientedGray, Core.ROTATE_90_COUNTERCLOCKWISE)
                Core.rotate(scaledMask, orientedMask, Core.ROTATE_90_COUNTERCLOCKWISE)
            }
            else -> {
                scaledGray.copyTo(orientedGray)
                scaledMask.copyTo(orientedMask)
            }
        }
        val grayBytes = ByteArray((orientedGray.total() * orientedGray.channels()).toInt())
        val maskBytes = ByteArray((orientedMask.total() * orientedMask.channels()).toInt())
        orientedGray.get(0, 0, grayBytes)
        orientedMask.get(0, 0, maskBytes)
        val result = RenderFrame(grayBytes, maskBytes, orientedGray.cols(), orientedGray.rows())
        scaledGray.release()
        scaledMask.release()
        orientedGray.release()
        orientedMask.release()
        return result
    }

    private fun advanceHistory(current: Mat) {
        previousTwo?.release()
        previousTwo = previous
        previous = current.clone()
    }

    private fun motionPercentage(mask: Mat): Float =
        (Core.countNonZero(mask).toFloat() * 100f / (mask.rows() * mask.cols()).coerceAtLeast(1))

    private fun sensitivityThreshold(): Double = 34.0 - (settings.sensitivity * 24.0)

    private fun rebuildSubtractor() {
        subtractor?.clear()
        subtractor = Video.createBackgroundSubtractorMOG2(
            if (settings.persistenceFrames >= 4) 360 else 180,
            16.0,
            false,
        )
    }

    companion object {
        private const val MAX_PROCESSING_WIDTH = 960
    }
}
