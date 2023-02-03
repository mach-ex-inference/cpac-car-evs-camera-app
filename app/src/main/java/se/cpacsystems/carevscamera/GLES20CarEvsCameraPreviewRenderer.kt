/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.cpacsystems.carevscamera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.HardwareBuffer
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLU
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLES20 SurfaceView Renderer.
 */
class GLES20CarEvsCameraPreviewRenderer(private val mFrameProvider: FrameProvider) :
    GLSurfaceView.Renderer {

    companion object {
        private val TAG = GLES20CarEvsCameraPreviewRenderer::class.java.simpleName
        private const val FLOAT_SIZE_BYTES = 4
        private val sVertCarPosData = floatArrayOf(
            -1.0f, 1.0f, 0.0f,
            1.0f, 1.0f, 0.0f,
            -1.0f, -1.0f, 0.0f,
            1.0f, -1.0f, 0.0f
        )
        private val sVertCarTexData = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
        private val sIdentityMatrix = floatArrayOf(
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        )
        private const val sVertexShader = "#version 300 es                    \n" +
                "layout(location = 0) in vec4 pos;  \n" +
                "layout(location = 1) in vec2 tex;  \n" +
                "uniform mat4 cameraMat;            \n" +
                "out vec2 uv;                       \n" +
                "void main()                        \n" +
                "{                                  \n" +
                "   gl_Position = cameraMat * pos;  \n" +
                "   uv = tex;                       \n" +
                "}                                  \n"
        private const val sFragmentShader = "#version 300 es                    \n" +
                "precision mediump float;           \n" +
                "uniform sampler2D tex;             \n" +
                "in vec2 uv;                        \n" +
                "out vec4 color;                    \n" +
                "void main()                        \n" +
                "{                                  \n" +
                "    vec4 texel = texture(tex, uv); \n" +
                "    color = texel;                 \n" +
                "}                                  \n"

        private fun checkGlError(op: String) {
            var error: Int
            while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "$op: glError $error")
                throw RuntimeException(op + ": glError " + GLU.gluErrorString(error))
            }
        }
    }

    private var program = 0
    private var textureId = 0
    private val vertCarPos = ByteBuffer.allocateDirect(sVertCarPosData.size * FLOAT_SIZE_BYTES)
                                .order(ByteOrder.nativeOrder())
                                .asFloatBuffer()
                                .apply { put(sVertCarPosData).position(0) }
    private val vertCarTex = ByteBuffer.allocateDirect(sVertCarTexData.size * FLOAT_SIZE_BYTES)
                                .order(ByteOrder.nativeOrder())
                                .asFloatBuffer()
                                .apply { put(sVertCarTexData).position(0) }
    private var width = 0
    private var height = 0
    init {
        System.loadLibrary("carevsglrenderer_jni")
    }

    interface FrameProvider {
        /**
         * Request a new frame from the frame buffer queue.
         */
        fun getNewFrame(): HardwareBuffer?

        /**
         * Request to return a frame that we're done with.
         */
        fun returnFrame(frame: HardwareBuffer?)
    }

    override fun onDrawFrame(glUnused: GL10) {
        // Specify a shader program to use
        GLES20.glUseProgram(program)

        // Set a cameraMat as 4x4 identity matrix
        val matrix = GLES20.glGetUniformLocation(program, "cameraMat")
        if (matrix < 0) {
            throw RuntimeException("Could not get a attribute location for cameraMat")
        }
        GLES20.glUniformMatrix4fv(matrix, 1, false, sIdentityMatrix, 0)
        val buffer = mFrameProvider.getNewFrame()
        if (buffer != null) {
            // Retrieve a hardware buffer from a descriptor and update the texture
            // Update the texture with a given hardware buffer
            if (!nUpdateTexture(buffer, textureId)) {
                throw RuntimeException(
                    "Failed to update the texture with the preview frame"
                )
            }
        }
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        // Select active texture unit
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        // Bind a named texture to the target
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Use a texture slot 0 as the source
        val sampler = GLES20.glGetUniformLocation(program, "tex")
        if (sampler < 0) {
            throw RuntimeException("Could not get a attribute location for tex")
        }
        GLES20.glUniform1i(sampler, 0)

        // We'll ignore the alpha value
        GLES20.glDisable(GLES20.GL_BLEND)

        // Define an array of generic vertex attribute data
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, vertCarPos)
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 0, vertCarTex)

        // Enable a generic vertex attribute array
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glEnableVertexAttribArray(1)

        // Render primitives from array data
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(0)
        GLES20.glDisableVertexAttribArray(1)

        // Wait until all GL execution is complete
        GLES20.glFinish()
        buffer?.close()
        mFrameProvider.returnFrame(buffer)
    }

    override fun onSurfaceChanged(glUnused: GL10, width: Int, height: Int) {
        // Use the GLES20 class's static methods instead of a passed GL10 interface.
        GLES20.glViewport(0, 0, width, height)
        this.width = width
        this.height = height
    }

    override fun onSurfaceCreated(glUnused: GL10, config: EGLConfig) {
        // Use the GLES20 class's static methods instead of a passed GL10 interface.
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, sVertexShader)
        if (vertexShader == 0) {
            Log.e(TAG, "Failed to load a vertex shader")
            return
        }
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, sFragmentShader)
        if (fragmentShader == 0) {
            Log.e(TAG, "Failed to load a fragment shader")
            return
        }
        program = buildShaderProgram(vertexShader, fragmentShader)
        if (program == 0) {
            Log.e(TAG, "Failed to build shader programs")
            return
        }
        // Generate texture name
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        if (textureId <= 0) {
            Log.e(TAG, "Did not get a texture handle")
            return
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        // Use a linear interpolation to upscale the texture
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        // Use a nearest-neighbor to downscale the texture
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST
        )
        // Clamp s, t coordinates at the edges
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
    }

    fun renderDrawable(drawable: Drawable) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,  /* level = */0, bitmap,  /* border = */0)
        bitmap.recycle()
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        if (shader == 0) {
            Log.e(TAG, "Failed to create a shader for $source")
            return 0
        }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader $shaderType: ")
            Log.e(TAG, GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun buildShaderProgram(vertexShader: Int, fragmentShader: Int): Int {
        if (vertexShader == 0 || fragmentShader == 0) {
            Log.e(TAG, "Needs vertexShader and fragmentShader to continue")
            return 0
        }
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Failed to create a program")
            return 0
        }
        GLES20.glAttachShader(program, vertexShader)
        checkGlError("glAttachShader")
        GLES20.glAttachShader(program, fragmentShader)
        checkGlError("glAttachShader")
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link a program: ")
            Log.e(TAG, GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    /**
     * Native method to update the texture with a received frame buffer.
     */
    private external fun nUpdateTexture(buffer: HardwareBuffer, textureId: Int): Boolean
}