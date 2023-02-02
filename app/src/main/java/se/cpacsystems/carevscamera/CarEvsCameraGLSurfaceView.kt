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

import android.content.Context
import android.graphics.drawable.Drawable
import android.opengl.GLSurfaceView
import se.cpacsystems.carevscamera.GLES20CarEvsCameraPreviewRenderer.FrameProvider

/**
 * https://developer.android.com/reference/android/opengl/GLSurfaceView.html
 */
class CarEvsCameraGLSurfaceView(context: Context?, frameProvider: FrameProvider) :
    GLSurfaceView(context) {
    private val renderer: GLES20CarEvsCameraPreviewRenderer

    init {
        debugFlags = DEBUG_LOG_GL_CALLS
        setEGLContextClientVersion(2)
        renderer = GLES20CarEvsCameraPreviewRenderer(frameProvider)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun renderDrawable(drawable: Drawable) {
        renderer.renderDrawable(drawable)
        requestRender()
    }
}