package com.motionsight.app.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.TextureView
import com.motionsight.app.model.RenderFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Displays the analysis luminance and motion mask as two independent GPU textures.
 * The shader dims the camera texture and reserves pure white for accepted mask pixels.
 */
class MotionGlSurfaceView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private val motionRenderer = MotionGlRenderer()
    private val renderThread = HandlerThread("MotionSight-GL").apply { start() }
    private val renderHandler = Handler(renderThread.looper)
    private val renderScheduled = AtomicBoolean(false)
    private var latestFrame: RenderFrame? = null
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var surfaceReady = false

    init {
        surfaceTextureListener = this
        isOpaque = true
    }

    fun submitFrame(frame: RenderFrame) {
        latestFrame = frame
        scheduleRender()
    }

    fun onPause() = Unit

    fun onResume() {
        scheduleRender()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderHandler.post {
            initializeEgl(surface)
            motionRenderer.onSurfaceCreated()
            motionRenderer.onSurfaceChanged(width, height)
            surfaceReady = true
            scheduleRender()
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderHandler.post {
            if (surfaceReady) {
                motionRenderer.onSurfaceChanged(width, height)
                scheduleRender()
            }
        }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        val released = CountDownLatch(1)
        renderHandler.post {
            surfaceReady = false
            releaseEgl()
            released.countDown()
        }
        released.await(500, TimeUnit.MILLISECONDS)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderThread.quitSafely()
    }

    private fun scheduleRender() {
        if (!surfaceReady || !renderScheduled.compareAndSet(false, true)) return
        renderHandler.post {
            renderScheduled.set(false)
            if (!surfaceReady) return@post
            latestFrame?.let(motionRenderer::submit)
            motionRenderer.onDrawFrame()
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }
    }

    private fun initializeEgl(surfaceTexture: SurfaceTexture) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to obtain EGL display" }
        val versions = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1)) { "Unable to initialize EGL" }
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val configCount = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, attributes, 0, configs, 0, 1, configCount, 0)) {
            "Unable to choose EGL configuration"
        }
        val config = checkNotNull(configs[0])
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Unable to create EGL context" }
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            config,
            surfaceTexture,
            intArrayOf(EGL14.EGL_NONE),
            0,
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Unable to create EGL window surface" }
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "Unable to activate EGL context" }
    }

    private fun releaseEgl() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return
        EGL14.eglMakeCurrent(
            eglDisplay,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT,
        )
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}

private class MotionGlRenderer {
    private val pendingFrame = AtomicReference<RenderFrame?>()
    private var currentFrame: RenderFrame? = null
    private var program = 0
    private lateinit var grayTexture: TextureSlot
    private lateinit var maskTexture: TextureSlot
    private var viewWidth = 1
    private var viewHeight = 1

    private val positionBuffer: FloatBuffer = floatBufferOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f,
    )

    fun submit(frame: RenderFrame) {
        pendingFrame.set(frame)
    }

    fun onSurfaceCreated() {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        grayTexture = TextureSlot(createTexture())
        maskTexture = TextureSlot(createTexture())
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewWidth = width.coerceAtLeast(1)
        viewHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
    }

    fun onDrawFrame() {
        pendingFrame.getAndSet(null)?.let { currentFrame = it }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val frame = currentFrame ?: return
        if (frame.width <= 0 || frame.height <= 0) return

        GLES20.glUseProgram(program)
        uploadTexture(grayTexture, frame.width, frame.height, frame.grayscale, 0)
        uploadTexture(maskTexture, frame.width, frame.height, frame.mask, 1)

        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        val cameraHandle = GLES20.glGetUniformLocation(program, "uCamera")
        val maskHandle = GLES20.glGetUniformLocation(program, "uMask")

        val texCoordBuffer = cropTextureCoordinates(frame.width, frame.height)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, positionBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glUniform1i(cameraHandle, 0)
        GLES20.glUniform1i(maskHandle, 1)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun cropTextureCoordinates(imageWidth: Int, imageHeight: Int): FloatBuffer {
        val imageAspect = imageWidth.toFloat() / imageHeight
        val viewAspect = viewWidth.toFloat() / viewHeight
        var uMin = 0f
        var uMax = 1f
        var vMin = 0f
        var vMax = 1f
        if (imageAspect > viewAspect) {
            val visible = viewAspect / imageAspect
            uMin = (1f - visible) / 2f
            uMax = 1f - uMin
        } else {
            val visible = imageAspect / viewAspect
            vMin = (1f - visible) / 2f
            vMax = 1f - vMin
        }
        // The byte rows arrive top-first while OpenGL texture coordinates start at the bottom.
        return floatBufferOf(
            uMin, vMax,
            uMax, vMax,
            uMin, vMin,
            uMax, vMin,
        )
    }

    private fun uploadTexture(slot: TextureSlot, width: Int, height: Int, bytes: ByteArray, unit: Int) {
        if (slot.buffer.capacity() < bytes.size) {
            slot.buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        }
        slot.buffer.clear()
        slot.buffer.put(bytes).position(0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, slot.id)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        if (slot.width != width || slot.height != height) {
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_LUMINANCE,
                width,
                height,
                0,
                GLES20.GL_LUMINANCE,
                GLES20.GL_UNSIGNED_BYTE,
                slot.buffer,
            )
            slot.width = width
            slot.height = height
        } else {
            GLES20.glTexSubImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                0,
                0,
                width,
                height,
                GLES20.GL_LUMINANCE,
                GLES20.GL_UNSIGNED_BYTE,
                slot.buffer,
            )
        }
    }

    private data class TextureSlot(
        val id: Int,
        var width: Int = 0,
        var height: Int = 0,
        var buffer: ByteBuffer = ByteBuffer.allocateDirect(0),
    )

    private fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "OpenGL program link failed: ${GLES20.glGetProgramInfoLog(program)}" }
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
        }
    }

    private fun compileShader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { shader ->
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "OpenGL shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uCamera;
            uniform sampler2D uMask;
            varying vec2 vTexCoord;
            void main() {
                float luminance = texture2D(uCamera, vTexCoord).r * 0.45;
                float motion = texture2D(uMask, vTexCoord).r;
                float outputLevel = motion >= 0.5 ? 1.0 : luminance;
                gl_FragColor = vec4(outputLevel, outputLevel, outputLevel, 1.0);
            }
        """
    }
}

private fun floatBufferOf(vararg values: Float): FloatBuffer =
    ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(values)
            position(0)
        }
