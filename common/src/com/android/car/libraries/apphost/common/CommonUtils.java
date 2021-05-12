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

package com.android.car.libraries.apphost.common;

import android.content.Intent;

import androidx.car.app.model.OnClickDelegate;

import java.util.List;

/** Holds static util methods for common usage in the host. */
public final class CommonUtils {
    /**
     * Removes any extras that we pass around internally as metadata, preventing them from being
     * exposed to the client apps.
     */
    public static void removeInternalIntentExtras(Intent intent, List<String> extrasToRemove) {
        for (String extra : extrasToRemove) {
            intent.removeExtra(extra);
        }
    }

    /**
     * Checks if {@code onClickDelegate} is a parked only action and the car is driving, then shows
     * a toast and returns. Otherwise dispatches the {@code onClick} to the client.
     */
    public static void dispatchClick(
            TemplateContext templateContext, OnClickDelegate onClickDelegate) {
        // TODO(b/183654467): check parked state in the shared component
        templateContext.getAppDispatcher().dispatchClick(onClickDelegate);
    }

    private CommonUtils() {}
}
