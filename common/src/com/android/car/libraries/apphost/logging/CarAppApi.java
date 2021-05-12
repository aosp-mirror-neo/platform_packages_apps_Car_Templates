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
package com.android.car.libraries.apphost.logging;

/** Each enum represents one of the Car App Library's possible host to client APIs. */
// TODO(b/171817245): Remove LINT.IFTT in  copybara
// Please update the related file
public enum CarAppApi {
    UNKNOWN_API,
    GET_APP_VERSION,
    ON_HANDSHAKE_COMPLETED,
    GET_MANAGER,
    GET_TEMPLATE,
    ON_APP_CREATE,
    DISPATCH_LIFECYCLE,
    ON_NEW_INTENT,
    ON_CONFIGURATION_CHANGED,
    ON_SURFACE_AVAILABLE,
    ON_SURFACE_DESTROYED,
    ON_VISIBLE_AREA_CHANGED,
    ON_STABLE_AREA_CHANGED,
    ON_CLICK,
    ON_SELECTED,
    ON_SEARCH_TEXT_CHANGED,
    ON_SEARCH_SUBMITTED,
    ON_NAVIGATE,
    STOP_NAVIGATION,
    ON_RECORDING_STARTED,
    ON_RECORDING_STOPPED,
    ON_ITEM_VISIBILITY_CHANGED,
    ON_CHECKED_CHANGED,
    ON_BACK_PRESSED,
    BIND,
    ON_INPUT_SUBMITTED,
}
// LINT.ThenChange(//depot/google3/java/com/google/android/apps/auto/components/apphost/internal/\
//      TelemetryHandlerImpl.java)
