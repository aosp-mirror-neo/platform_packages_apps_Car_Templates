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

import androidx.annotation.NonNull;

import com.android.car.libraries.templates.host.view.widgets.ui.FocusClusterBehavior.ForceFieldBehavior;

/** Enum for the available types of force fields */
public enum ForceFieldType {
    /** No force field, the default */
    NONE(0),
    /**
     * Force field intercepts all navigation events attempting to exit the layout and handles them.
     */
    MODAL(1),
    /**
     * Force field intercepts all navigation events attempting to exit the layout during a
     * multi-move and handles them.
     */
    MULTIMOVE_MODAL(2);

    private final int mType;

    ForceFieldType(int type) {
        mType = type;
    }

    @NonNull
    static ForceFieldType fromInt(int type, ForceFieldType fallback) {
        for (ForceFieldType val : values()) {
            if (val.mType == type) {
                return val;
            }
        }
        return fallback;
    }

    @ForceFieldBehavior
    static int toBehavior(ForceFieldType type) {
        int behavior;
        switch (type) {
            case NONE:
                behavior = FocusClusterBehavior.FORCE_FIELD_BEHAVIOR_NONE;
                break;
            case MODAL:
                behavior = FocusClusterBehavior.FORCE_FIELD_BEHAVIOR_MODAL;
                break;
            case MULTIMOVE_MODAL:
                behavior = FocusClusterBehavior.FORCE_FIELD_BEHAVIOR_MULTIMOVE_MODAL;
                break;
            default:
                throw new IllegalArgumentException("Unsupported type: " + type);
        }
        return behavior;
    }

    static ForceFieldType fromBehavior(@ForceFieldBehavior int behavior) {
        ForceFieldType type;
        if (behavior == FocusClusterBehavior.FORCE_FIELD_BEHAVIOR_NONE) {
            type = ForceFieldType.NONE;
        } else if (behavior == FocusClusterBehavior.FORCE_FIELD_BEHAVIOR_MODAL) {
            type = ForceFieldType.MODAL;
        } else if (behavior == FocusClusterBehavior.FORCE_FIELD_BEHAVIOR_MULTIMOVE_MODAL) {
            type = ForceFieldType.MULTIMOVE_MODAL;
        } else {
            throw new IllegalArgumentException("Unsupported behavior: " + behavior);
        }
        return type;
    }
}
