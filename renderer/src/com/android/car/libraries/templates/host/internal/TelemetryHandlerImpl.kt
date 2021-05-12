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

import android.util.Log
import com.android.car.libraries.apphost.logging.LogTags
import com.android.car.libraries.apphost.logging.TelemetryEvent
import com.android.car.libraries.apphost.logging.TelemetryHandler

/** The app specific implementation of [TelemetryHandler]. */
class TelemetryHandlerImpl : TelemetryHandler() {
    override fun logCarAppTelemetry(logEventBuilder: TelemetryEvent.Builder?) {
        // TODO(b/171308515): Add proper telemetry support.
        val event = logEventBuilder?.build()
        Log.d(LogTags.APP_HOST, event?.componentName.toString() + ": " + event?.action?.name)
    }
}
