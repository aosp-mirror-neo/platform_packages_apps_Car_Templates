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
package com.android.car.libraries.templates.host.renderer

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.car.app.activity.renderer.surface.LegacySurfacePackage
import androidx.car.app.activity.renderer.surface.SurfaceControlCallback
import androidx.car.app.activity.renderer.surface.SurfaceWrapper
import androidx.car.app.serialization.Bundleable
import androidx.car.app.utils.ThreadUtils

/**
 * A presenter similar to [SurfaceControlViewHost] that conforms to [SurfaceController].
 *
 * <p>This presenter should only be used if API version is lower than [Build.VERSION_CODES.R].
 * Otherwise, [SurfaceControlViewHostController] should be used.
 */
class LegacySurfaceController(private val context: Context) : SurfaceController {

    private var presentation: Presentation? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var densityDpi: Int = 0
    private var contentView: View? = null
    private val surfaceControl = object : SurfaceControlCallback {
        override fun setSurfaceWrapper(surfaceWrapper: SurfaceWrapper) {
            ThreadUtils.runOnMain {
                updatePresentation(surfaceWrapper)
            }
        }

        override fun onWindowFocusChanged(hasFocus: Boolean, isInTouchMode: Boolean) {
            ThreadUtils.runOnMain {
                if (contentView != null) {
                    presentation?.window?.setLocalFocus(hasFocus, isInTouchMode)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            ThreadUtils.runOnMain {
                presentation?.window?.injectInputEvent(event)
            }
        }
    }

    private val surfacePackage = Bundleable.create(LegacySurfacePackage(surfaceControl))

    override fun surfacePackage(): Bundleable? = surfacePackage

    override fun relayout(width: Int, height: Int) {
        contentView?.layoutParams = FrameLayout.LayoutParams(width, height)
    }

    override fun setView(view: View, width: Int, height: Int) {
        contentView = view
        presentation?.setContentView(view)
    }

    private fun updatePresentation(surfaceWrapper: SurfaceWrapper) {
        // Release the old virtual display if exists.
        virtualDisplay?.release()

        val virtualDisplay = createVirtualDisplay(surfaceWrapper)
        this.virtualDisplay = virtualDisplay
        val presentation = Presentation(PresentationContext(context), virtualDisplay.display)
        this.presentation = presentation
        contentView?.let { contentView ->
            (contentView.parent as? ViewGroup)?.removeView(contentView)
            presentation.setContentView(contentView)
        }
        densityDpi = surfaceWrapper.densityDpi
        presentation.show()
    }

    private fun createVirtualDisplay(surfaceWrapper: SurfaceWrapper): VirtualDisplay {
        val displayManager =
            context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displayManager.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            surfaceWrapper.width,
            surfaceWrapper.height,
            surfaceWrapper.densityDpi,
            surfaceWrapper.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        )
    }

    protected fun finalize() {
        virtualDisplay?.release()
    }

    companion object {
        const val VIRTUAL_DISPLAY_NAME = "ScreenRendererVirtualDisplay"
    }
}
