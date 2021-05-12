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
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Checkable;
import android.widget.ImageButton;

import com.android.car.libraries.templates.host.R;

/**
 * The default floating action button for Android Auto.
 *
 * <p>This supports multiple types (which can be set in the layout using {@code app:buttonType})
 *
 * <ul>
 *   <li>{@code custom}: the default type with no background
 *   <li>{@code rectangular}: a floating button with a rectangular background
 *   <li>{@code oval}: a floating button with a round background
 *   <li>{@code toggle}: a floating button with a round background that is toggleable.
 * </ul>
 */
public class FloatingActionButton extends ImageButton implements Checkable {
    private static final String TAG = "FloatingActionButton";

    private Animation mFocusGainedAnimation;
    private Animation mFocusLostAnimation;
    private Drawable mBackground;

    private Type mType;

    // For ToggleButton only
    private boolean mIsChecked;
    private boolean mIsBroadcasting;
    private OnCheckedChangeListener mOnCheckedChangeListener;

    // For ToggleButton only
    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};

    /** Different types of the Button. */
    private enum Type {
        /**
         * Custom is the default type. It defaults to the default carButtonStyle specified in the
         * SystemSdkAppTheme.
         */
        Custom(0, 0),
        /**
         * An round button with a ripple. Since it's actually Oval (not Round), if you make a non
         * square button it will appear to be oval.
         */
        Oval(1, R.drawable.system_sdk_oval_button_ripple),
        /** A rectangular button with ripple. */
        Rectangular(2, R.drawable.system_sdk_rectangular_button_ripple),
        /**
         * A toggle button. The default style from Android Auto is to have a circle on the button to
         * indicate the checked state.
         */
        Toggle(3, R.drawable.system_sdk_toggle_button_ripple);

        final int mId;
        final int mBackgroundResId;

        /**
         * Convert a resource enum to this Type.
         *
         * @param id the resource enum specified by app:buttonType attribute on the (@link
         *     FloatingActionButton}
         * @return the corresponding {@link Type}
         */
        static Type fromId(int id) {
            return Type.values()[id];
        }

        Type(int id, int backgroundResId) {
            mId = id;
            mBackgroundResId = backgroundResId;
        }

        /**
         * Gets the background drawable corresponding to this Type. This should have a ripple by
         * default unless this Type is Custom, and the carButtonStyle points to a no ripple button
         * style.
         *
         * @param context context from where the drawable should be loaded
         * @return the background drawable
         */
        Drawable getBackground(Context context) {
            if (mBackgroundResId == 0) {
                return null;
            }
            return context.getDrawable(mBackgroundResId);
        }
    }

    public FloatingActionButton(Context context) {
        this(context, null, R.attr.carButtonStyle, 0);
    }

    public FloatingActionButton(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.carButtonStyle, 0);
    }

    public FloatingActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public FloatingActionButton(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray array =
                getContext()
                        .obtainStyledAttributes(
                                attrs, R.styleable.FloatingActionButton, defStyleAttr, defStyleRes);
        try {
            readAttributes(array);
        } finally {
            array.recycle();
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) {
            if (mFocusLostAnimation != null) {
                startAnimation(mFocusLostAnimation);
            }
            if (mBackground != null) {
                setBackground(mBackground);
            }
            debugDump();
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);

        if (focused) {
            if (mFocusGainedAnimation != null) {
                startAnimation(mFocusGainedAnimation);
            }
        } else {
            if (mFocusLostAnimation != null) {
                startAnimation(mFocusLostAnimation);
            }
        }
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }

    @Override
    public void toggle() {
        if (mType == Type.Toggle) {
            setChecked(!mIsChecked);
        }
    }

    @Override
    public boolean isChecked() {
        return mIsChecked;
    }

    @Override
    public void setChecked(boolean isChecked) {
        if (mIsChecked == isChecked) {
            return;
        }
        mIsChecked = isChecked;

        refreshDrawableState();
        // Avoid infinite recursions if setChecked() is called from a listener
        if (mIsBroadcasting) {
            return;
        }

        mIsBroadcasting = true;
        if (mOnCheckedChangeListener != null) {
            mOnCheckedChangeListener.onCheckedChanged(this, mIsChecked);
        }
        mIsBroadcasting = false;
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState =
                super.onCreateDrawableState(extraSpace + CHECKED_STATE_SET.length);
        if (isChecked()) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.mChecked = isChecked();
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setChecked(ss.mChecked);
        requestLayout();
    }

    private void readAttributes(TypedArray attributes) {
        int resId;
        resId = attributes.getResourceId(R.styleable.FloatingActionButton_focusGainedAnimation, 0);
        if (resId != 0) {
            mFocusGainedAnimation = AnimationUtils.loadAnimation(getContext(), resId);
        }
        resId = attributes.getResourceId(R.styleable.FloatingActionButton_focusLostAnimation, 0);
        if (resId != 0) {
            mFocusLostAnimation = AnimationUtils.loadAnimation(getContext(), resId);
        }
        resId = attributes.getInt(R.styleable.FloatingActionButton_buttonType, 0);
        mType = Type.fromId(resId);
        mBackground = mType.getBackground(getContext());
    }

    private void debugDump() {
        Log.i(TAG, "id: " + getResources().getResourceEntryName(getId()));
        Log.i(TAG, "mType: " + mType);
        Log.i(TAG, "mFocusGainedAnimation: " + mFocusGainedAnimation);
        Log.i(TAG, "mFocusLostAnimation: " + mFocusLostAnimation);
        Log.i(TAG, "mBackground: " + mBackground);
        Log.i(TAG, "mIsChecked: " + mIsChecked);
        Log.i(TAG, "mIsBroadcasting: " + mIsBroadcasting);
        Log.i(TAG, "mOnCheckedChangeListener: " + mOnCheckedChangeListener);
    }

    /**
     * Register a callback to be invoked when the checked state of this button changes.
     *
     * @param listener the callback to call on checked state change
     */
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        mOnCheckedChangeListener = listener;
    }

    /**
     * Interface definition for a callback to be invoked when the checked state of a compound button
     * changed.
     */
    public interface OnCheckedChangeListener {
        /**
         * Called when the checked state of a compound button has changed.
         *
         * @param buttonView The button view whose state has changed.
         * @param isChecked The new checked state of buttonView.
         */
        void onCheckedChanged(FloatingActionButton buttonView, boolean isChecked);
    }

    private static class SavedState extends BaseSavedState {
        boolean mChecked;

        /** Constructor called from {@link FloatingActionButton#onSaveInstanceState()} */
        SavedState(Parcelable superState) {
            super(superState);
        }

        /** Constructor called from {@link #CREATOR} */
        private SavedState(Parcel in) {
            super(in);
            mChecked = (Boolean) in.readValue(null);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeValue(mChecked);
        }

        @Override
        public String toString() {
            return "FloatingActionButton.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " checked="
                    + mChecked
                    + "}";
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }
}
