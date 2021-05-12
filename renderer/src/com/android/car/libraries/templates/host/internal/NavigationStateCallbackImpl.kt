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

import androidx.car.app.navigation.model.Trip
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.apphost.logging.L
import com.android.car.libraries.apphost.logging.LogTags
import com.android.car.libraries.apphost.nav.NavigationStateCallback

/** Handles navigation state change events from [NavigationHost].  */
class NavigationStateCallbackImpl(private val templateContext: TemplateContext) :
    NavigationStateCallback {
    private var onNavigationStopRunnable: Runnable? = null

    override fun onUpdateTrip(trip: Trip): Boolean {
        val packageInfo = templateContext.carAppPackageInfo
        L.v(LogTags.NAVIGATION, "onUpdateTrip %s.", packageInfo)
        return publish(trip)
    }

    override fun onNavigationStarted(onNavigationStopRunnable: Runnable) {
        val packageInfo = templateContext.carAppPackageInfo
        L.v(LogTags.NAVIGATION, "onNavigationStarted %s.", packageInfo)
        this.onNavigationStopRunnable = onNavigationStopRunnable
    }

    override fun onNavigationEnded() {
        val packageInfo = templateContext.carAppPackageInfo
        L.v(LogTags.NAVIGATION, "onNavigationEnded %s.", packageInfo)
        onNavigationStopRunnable = null
    }

    private fun publish(trip: Trip): Boolean {
        val packageInfo = templateContext.carAppPackageInfo
        L.v(LogTags.NAVIGATION, "Updating trip for app %s: %s", packageInfo, trip)
        return true
    }
}
