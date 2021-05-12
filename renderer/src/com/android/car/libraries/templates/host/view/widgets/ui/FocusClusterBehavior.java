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

import android.os.Bundle;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Defines system behaviors for focus clusters.
 *
 * @hide
 */
// TODO(b/171476116): Resolve nullness suppression.
@SuppressWarnings("nullness")
public final class FocusClusterBehavior {

    private static final String KEY_IS_FOCUS_CLUSTER = "is_focus_cluster";
    private static final String KEY_FORCE_FIELD_BEHAVIOR = "force_field_behavior";

    /**
     * {@code IntDef} enumerating the force field behaviors which can be applied to a focus cluster.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        FORCE_FIELD_BEHAVIOR_NONE,
        FORCE_FIELD_BEHAVIOR_MODAL,
        FORCE_FIELD_BEHAVIOR_MULTIMOVE_MODAL
    })
    public @interface ForceFieldBehavior {}

    /** No force field, the default */
    public static final int FORCE_FIELD_BEHAVIOR_NONE = 0;
    /**
     * Force field intercepts all navigation events attempting to exit the layout and handles them.
     * Force field also allows circular focus navigation in linear focus movement.
     */
    public static final int FORCE_FIELD_BEHAVIOR_MODAL = 1;
    /**
     * Force field intercepts all navigation events attempting to exit the layout during a
     * multi-move and handles them.
     */
    public static final int FORCE_FIELD_BEHAVIOR_MULTIMOVE_MODAL = 2;

    private static final int MIN_FORCE_FIELD_BEHAVIOR = FORCE_FIELD_BEHAVIOR_NONE;
    private static final int MAX_FORCE_FIELD_BEHAVIOR = FORCE_FIELD_BEHAVIOR_MULTIMOVE_MODAL;

    @ForceFieldBehavior private final int mForceFieldBehavior;

    private FocusClusterBehavior(@ForceFieldBehavior int forceFieldBehavior) {
        mForceFieldBehavior = forceFieldBehavior;
    }

    public FocusClusterBehavior(Bundle bundle) {
        mForceFieldBehavior = bundle.getInt(KEY_FORCE_FIELD_BEHAVIOR);
    }

    /** TODO(b/180361672): Add javadoc */
    @Nullable
    public static FocusClusterBehavior get(View view) {
        Object tag = view.getTag();
        if (!(tag instanceof Bundle)) {
            return null;
        }
        Bundle bundle = (Bundle) tag;
        if (!bundle.getBoolean(KEY_IS_FOCUS_CLUSTER, false)) {
            return null;
        }
        return new FocusClusterBehavior(bundle);
    }

    /** TODO(b/180361672): Add javadoc */
    public static void set(View view, FocusClusterBehavior focusClusterBehavior) {
        view.setTag(focusClusterBehavior.toBundle());
    }

    /** TODO(b/180361672): Add javadoc */
    public static boolean isFocusCluster(View view) {
        Object tag = view.getTag();
        if (!(tag instanceof Bundle)) {
            return false;
        }
        Bundle bundle = (Bundle) tag;
        return bundle.getBoolean(KEY_IS_FOCUS_CLUSTER, false);
    }

    /** TODO(b/180361672): Add javadoc */
    @ForceFieldBehavior
    public static int getForceFieldBehavior(View view) {
        FocusClusterBehavior behavior = get(view);
        if (behavior == null) {
            return FORCE_FIELD_BEHAVIOR_NONE;
        }
        return behavior.getForceFieldBehavior();
    }

    /** TODO(b/180361672): Add javadoc */
    @ForceFieldBehavior
    public int getForceFieldBehavior() {
        return mForceFieldBehavior;
    }

    /** TODO(b/180361672): Add javadoc */
    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_IS_FOCUS_CLUSTER, true);
        bundle.putInt(KEY_FORCE_FIELD_BEHAVIOR, mForceFieldBehavior);
        return bundle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FocusClusterBehavior that = (FocusClusterBehavior) o;

        return mForceFieldBehavior == that.mForceFieldBehavior;
    }

    @Override
    public int hashCode() {
        return mForceFieldBehavior;
    }

    /** Builder for {@link FocusClusterBehavior} */
    public static class Builder {
        @ForceFieldBehavior private int mForceFieldBehavior;

        public Builder() {
            // public constructor
        }

        public Builder(@Nullable FocusClusterBehavior behavior) {
            this();
            if (behavior != null) {
                mForceFieldBehavior = behavior.mForceFieldBehavior;
            }
        }

        /** TODO(b/180361672): Add javadoc */
        public Builder forceFieldBehavior(@ForceFieldBehavior int forceFieldBehavior) {
            if (forceFieldBehavior < MIN_FORCE_FIELD_BEHAVIOR
                    || forceFieldBehavior > MAX_FORCE_FIELD_BEHAVIOR) {
                throw new IllegalStateException(
                        "Unsupported force field behavior tag: " + forceFieldBehavior);
            }
            mForceFieldBehavior = forceFieldBehavior;
            return this;
        }

        /** TODO(b/180361672): Add javadoc */
        public FocusClusterBehavior build() {
            return new FocusClusterBehavior(mForceFieldBehavior);
        }
    }
}
