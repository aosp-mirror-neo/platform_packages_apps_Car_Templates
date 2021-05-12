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

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * Utility functions for interacting with {@link View}s.
 *
 * @hide
 */
public final class ViewUtils {

    private ViewUtils() {
        // hide constructor
    }

    /** Obtain a reference to the root view (the view that has no parent. */
    @Nullable
    static ViewGroup getRootView(View view) {
        ViewGroup nextParent =
                view.getParent() instanceof ViewGroup ? (ViewGroup) view.getParent() : null;
        ViewGroup parent = nextParent;
        while (nextParent != null) {
            nextParent =
                    nextParent.getParent() instanceof ViewGroup
                            ? (ViewGroup) nextParent.getParent()
                            : null;
            if (nextParent != null) {
                parent = nextParent;
            }
        }
        return parent;
    }

    /** Check if the supplied view is a focusable view in the given parent */
    static boolean isFocusableChild(View view, ViewGroup parent) {
        ArrayList<View> focusables = new ArrayList<>();
        parent.addFocusables(focusables, View.FOCUSABLES_ALL);
        return focusables.contains(view);
    }

    @Nullable
    static View getFirstFocusable(View parent) {
        ArrayList<View> focusCandidates = new ArrayList<>();
        parent.addFocusables(focusCandidates, View.FOCUSABLES_ALL);
        if (focusCandidates.isEmpty()) {
            // The View is non-focusable and has no focusable children.  Return null.
            return null;
        }
        return focusCandidates.get(0);
    }
}
