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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.libraries.templates.host.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * A custom {@link FrameLayout} which applies "force field" behavior to its view hierarchy. A force
 * field affects focus-based navigation by modifying the default focus navigation behavior of the
 * system. The <code>type</code> defines the behavior of the force field.
 *
 * <p>Please do not use {@link android.view.View#setTag(Object)} on instances of this view as this
 * could interfere with its implementation. You may use {@link android.view.View#setTag(int,
 * Object)} instead.
 */
public class FocusClusterLayout extends FrameLayout {

    // NOTE: Change resolveRequestFocusDescendantFocusability and requestFocus methods
    // when this IntDef changes
    /** {@link IntDef} for possible {@code requestFocusDescendantFocusability} values. */
    @IntDef({REQUEST_FOCUS_NONE, REQUEST_FOCUS_AFTER_DESCENDANTS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RequestFocusDescendantFocusability {}

    /**
     * This view will request focus based on the behavior defined by the {@link
     * ViewGroup#getDescendantFocusability()}}.
     */
    public static final int REQUEST_FOCUS_NONE = 0;

    /**
     * This view will request focus based on the behavior defined by {@link
     * ViewGroup#FOCUS_AFTER_DESCENDANTS}}.
     */
    public static final int REQUEST_FOCUS_AFTER_DESCENDANTS = 1;

    @RequestFocusDescendantFocusability
    private int mRequestFocusDescendantFocusability = REQUEST_FOCUS_NONE;

    public FocusClusterLayout(@NonNull Context context) {
        this(context, null, 0, 0);
    }

    public FocusClusterLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public FocusClusterLayout(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public FocusClusterLayout(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        ForceFieldType type;
        TypedArray a =
                context.getTheme()
                        .obtainStyledAttributes(
                                attrs, R.styleable.FocusClusterLayout, defStyleAttr, defStyleRes);
        try {
            type = resolveForceFieldType(a);
            mRequestFocusDescendantFocusability = resolveRequestFocusDescendantFocusability(a);
        } finally {
            a.recycle();
        }

        FocusClusterUtil.setForceFieldType(this, type);
    }

    private ForceFieldType resolveForceFieldType(TypedArray a) {
        int typeInt = a.getInteger(R.styleable.FocusClusterLayout_forceFieldType, 0);
        return ForceFieldType.fromInt(typeInt, ForceFieldType.NONE);
    }

    @RequestFocusDescendantFocusability
    private int resolveRequestFocusDescendantFocusability(TypedArray a) {
        int typeInt =
                a.getInteger(
                        R.styleable.FocusClusterLayout_requestFocusDescendantFocusability,
                        REQUEST_FOCUS_NONE);
        if (typeInt != REQUEST_FOCUS_NONE && typeInt != REQUEST_FOCUS_AFTER_DESCENDANTS) {
            throw new IllegalArgumentException(
                    "Could not resolve requestFocusDescendantFocusability");
        }
        return typeInt;
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        /*
        The linear rotary focus search is too simplistic to handle the
        REQUEST_FOCUS_AFTER_DESCENDANTS feature. If the FocusClusterLayout is added as a focus
        candidate, focus movement will eventually be blocked in one direction because the linear
        model will find the FocusClusterLayout in that direction, then redirect focus to its child.
        This will repeat indefinitely when attempting to move in that same direction. Therefore, we
        do not make the FocusClusterLayout focusable. Instead we conditionally add the
        FocusClusterLayout as a focus candidate. We only add it when a) the
        requestFocusDescendantFocusability attribute is set and b) the direction for the search is
        a dpad direction.
        */
        if (mRequestFocusDescendantFocusability == REQUEST_FOCUS_AFTER_DESCENDANTS
                && isDpadDirection(direction)) {
            views.add(this);
        }

        super.addFocusables(views, direction, focusableMode);
    }

    /** Get the type of force field applied to focus-based navigation in this layout. */
    @NonNull
    public ForceFieldType getForceFieldType() {
        return FocusClusterUtil.getForceFieldType(this);
    }

    /** Set the type of force field applied to focus-based navigation in this layout. */
    public void setForceFieldType(@NonNull ForceFieldType type) {
        FocusClusterUtil.setForceFieldType(this, type);
    }

    /**
     * Gets the descendant focusability of this view group. The descendant focusability defines the
     * relationship between this view group and its descendants when looking for a view to take
     * focus in {@link #requestFocus(int, Rect)}.
     */
    @RequestFocusDescendantFocusability
    public int getRequestFocusDescendantFocusability() {
        return mRequestFocusDescendantFocusability;
    }

    /**
     * Set the descendant focusability of this view group. This defines the relationship between
     * this view group and its descendants when looking for a view to take focus in {@link
     * #requestFocus(int, Rect)} .
     */
    public void setRequestFocusDescendantFocusability(
            @RequestFocusDescendantFocusability int requestFocusDescendantFocusability) {
        mRequestFocusDescendantFocusability = requestFocusDescendantFocusability;
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        if (mRequestFocusDescendantFocusability == REQUEST_FOCUS_AFTER_DESCENDANTS) {
            return requestFocusAfterDescendants(direction, previouslyFocusedRect);
        }
        return super.requestFocus(direction, previouslyFocusedRect);
    }

    private boolean requestFocusAfterDescendants(int direction, Rect previouslyFocusedRect) {
        // save real descendantFocusability
        int realDescendantFocusability = getDescendantFocusability();

        // override descendant focusability
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        boolean result = super.requestFocus(direction, previouslyFocusedRect);

        // restore real descendantFocusability
        setDescendantFocusability(realDescendantFocusability);
        return result;
    }

    private static boolean isDpadDirection(int direction) {
        return direction == View.FOCUS_UP
                || direction == View.FOCUS_DOWN
                || direction == View.FOCUS_LEFT
                || direction == View.FOCUS_RIGHT;
    }
}
