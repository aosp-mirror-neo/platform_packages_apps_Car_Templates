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
package com.android.car.libraries.templates.host.internal

import androidx.lifecycle.Lifecycle
import android.view.inputmethod.EditorInfo
import androidx.car.app.CarContext
import androidx.car.app.activity.renderer.IProxyInputConnection
import androidx.car.app.activity.renderer.IRendererCallback
import androidx.car.app.utils.ThreadUtils
import com.android.car.libraries.apphost.CarHost
import com.android.car.libraries.apphost.template.AppHost

/** Calls associated with rotary. */
// TODO(b/175328654): Remove once IRendererCallback removed rotary support.
interface RotaryCallback {
    /** Handles the rotary rotate gesture */
    fun onRotate(steps: Int, isClockwise: Boolean)
    /** Handles the rotary nudge key events */
    fun onNudge(keyCode: Int): Boolean
    /** Handles the rotary center button click */
    fun onSelect()
}

/** Handles events from the car app. */
class RendererCallback(
    private val carHost: CarHost,
    private val inputManager: InputManagerImpl,
    private val rotaryHandler: RotaryHandler
) : IRendererCallback.Stub(), RotaryCallback {

    override fun onBackPressed() {
        val appHost = carHost.getHostOrThrow(CarContext.APP_SERVICE) as? AppHost
        appHost?.onBackPressed()
    }

    override fun onCreate() {
        ThreadUtils.runOnMain {
            carHost.dispatchAppLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
    }

    override fun onStart() {
        ThreadUtils.runOnMain {
            carHost.dispatchAppLifecycleEvent(Lifecycle.Event.ON_START)
        }
    }

    override fun onResume() {
        ThreadUtils.runOnMain {
            carHost.dispatchAppLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    override fun onPause() {
        ThreadUtils.runOnMain {
            carHost.dispatchAppLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
    }

    override fun onStop() {
        ThreadUtils.runOnMain {
            carHost.dispatchAppLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    override fun onDestroyed() {
        ThreadUtils.runOnMain {
            carHost.dispatchAppLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    override fun onCreateInputConnection(editorInfo: EditorInfo): IProxyInputConnection? {
        return inputManager.onCreateInputConnection(editorInfo)
    }

    override fun onRotate(steps: Int, isClockwise: Boolean) {
        rotaryHandler.onRotate(steps, isClockwise)
    }

    override fun onNudge(keyCode: Int): Boolean {
        return rotaryHandler.onNudgeKey(keyCode)
    }

    override fun onSelect() {
        rotaryHandler.onSelect()
    }
}