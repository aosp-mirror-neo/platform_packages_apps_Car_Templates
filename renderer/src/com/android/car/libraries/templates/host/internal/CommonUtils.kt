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

import android.content.res.Configuration
import android.widget.Toast
import androidx.car.app.model.OnClickDelegate
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.templates.host.R

/** Holds static util methods for common usage in the host.  */
object CommonUtils {
    /**
     * Key for the extra that we insert into an Intent to mark it as coming from a notification
     * action.
     */
    const val EXTRA_NOTIFICATION_INTENT = "CAR_APP_NOTIFICATION_INTENT"

    // TODO(b/179827749): Renable emulator check
    /** Checks whether Android Auto is currently running on an emulator (DHU).  */
    val isConnectedToEmulator = true

    /**
     * Checks if `onClickDelegate` is a parked only action and the car is driving, then shows a
     * toast and returns. Otherwise dispatches the `onClick` to the client.
     */
    fun dispatchClick(
        templateContext: TemplateContext,
        onClickDelegate: OnClickDelegate
    ) {
        if (onClickDelegate.isParkedOnly &&
                CarDrivingStateMonitor.getInstance(templateContext).isSetupRestricted) {
            templateContext
                .toastController
                .showToast(
                    templateContext.resources.getString(R.string.parked_only_action),
                    Toast.LENGTH_SHORT
                )
            return
        }
        // TODO: Investigate whether this should be able to be called directly, bypassing this
        // parked only mode check
        templateContext.appDispatcher.dispatchClick(onClickDelegate)
    }

    /** Returns `true` if the host is in dark mode, `false` otherwise.  */
    fun isDarkMode(templateContext: TemplateContext): Boolean {
        val configuration = templateContext.resources.configuration
        return (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            == Configuration.UI_MODE_NIGHT_YES)
    }
}
