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

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.SurfaceControlViewHost
import android.view.View
import androidx.annotation.RequiresApi
import androidx.car.app.activity.renderer.surface.SurfaceWrapper
import androidx.car.app.serialization.Bundleable

/** A simple wrapper around [SurfaceControlViewHost] that conforms to [SurfaceController]. */
@RequiresApi(Build.VERSION_CODES.R)
class SurfaceControlViewHostController(
    context: Context,
    surfaceWrapper: SurfaceWrapper
) : SurfaceController {

    private var surfaceControlViewHost: SurfaceControlViewHost
    private var surfacePackage: Bundleable?

    init {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val display = displayManager.getDisplay(surfaceWrapper.displayId)
        val hostToken = surfaceWrapper.hostToken
        surfaceControlViewHost = SurfaceControlViewHost(context, display, hostToken)
        surfacePackage = surfaceControlViewHost.surfacePackage?.let { Bundleable.create(it) }
    }

    override fun surfacePackage() = surfacePackage

    override fun relayout(width: Int, height: Int) =
        surfaceControlViewHost.relayout(width, height)

    override fun setView(view: View, width: Int, height: Int) =
        surfaceControlViewHost.setView(view, width, height)
}
