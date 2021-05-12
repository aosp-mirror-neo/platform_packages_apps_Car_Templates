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
package com.android.car.libraries.templates.host.view.widgets.ui;

import android.view.ViewGroup;

import androidx.annotation.NonNull;

/** Provides functions for setting and retrieving focus cluster behavior on a {@link ViewGroup}. */
public final class FocusClusterUtil {

    private FocusClusterUtil() {
        // hide constructor
    }

    /** Get the type of force field applied to focus-based navigation in the supplied layout. */
    @NonNull
    public static ForceFieldType getForceFieldType(@NonNull ViewGroup viewGroup) {
        return ForceFieldType.fromBehavior(FocusClusterBehavior.getForceFieldBehavior(viewGroup));
    }

    /** Set the type of force field applied to focus-based navigation in the supplied layout. */
    public static void setForceFieldType(
            @NonNull ViewGroup viewGroup, @NonNull ForceFieldType type) {
        FocusClusterBehavior behavior = FocusClusterBehavior.get(viewGroup);
        FocusClusterBehavior.set(
                viewGroup,
                new FocusClusterBehavior.Builder(behavior)
                        .forceFieldBehavior(ForceFieldType.toBehavior(type))
                        .build());
    }
}
