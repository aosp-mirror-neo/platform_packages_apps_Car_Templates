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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorInflater;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.android.car.libraries.templates.host.R;

/** A custom view to provide list scroll behaviour -- up/down buttons and scroll indicator. */
public class PagedScrollBarView extends FrameLayout
        implements View.OnClickListener, View.OnLongClickListener {
    private static final int BUTTON_DISABLED_ALPHA = 50;
    private static final int BUTTON_ENABLED_ALPHA = 255;

    @VisibleForTesting static final int POST_SCROLL_FADE_DELAY_MS = 1000;

    @DayNightStyle private int mDayNightStyle;

    /** Listener for when the list should paginate. */
    public interface PaginationListener {
        int PAGE_UP = 0;
        int PAGE_DOWN = 1;

        /** Called when the linked view should be paged in the given direction */
        void onPaginate(int direction);
    }

    private final View mUpButtonContainer;
    private final View mDownButtonContainer;
    private final ImageView mUpButton;
    private final ImageView mDownButton;
    private final ImageView mScrollMovingThumb;
    private final ViewGroup mActionButtonParent;
    /** The "filler" view between the up and down buttons */
    private final View mFiller;

    private final Handler mScrollThumbFadeOutHandler = new Handler(Looper.getMainLooper());
    private final Runnable mScrollThumbFadeOutRunnable =
            () -> fadeThumbOutAndButtonIn(/* animate= */ true);
    private final Interpolator mPaginationInterpolator = new AccelerateDecelerateInterpolator();
    private final int mMinThumbLength;
    private final int mMaxThumbLength;
    private Float mOldThumbYPosition;
    private FadeDirection mFadeDirection = FadeDirection.STOPPED_AT_END;
    private PaginationListener mPaginationListener;
    private boolean mShowPaginationButtonsOnNonTouch;
    private boolean mIsActionButtonActive;
    /**
     * A {@link ValueAnimator} representing the current alpha of the scroll thumb and scroll bar
     * action button. This animator goes from 0 to 1 as the thumb fades in. Thus, the thumb's {@link
     * View#setAlpha(float) alpha} should be set to {@code animator value}, and the button's {@link
     * View#setAlpha(float) alpha} should be set to {@code 1 - animator value}.
     */
    private final ValueAnimator mScrollElementsFadeAnimator;

    public PagedScrollBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0 /*defStyleAttrs*/, 0 /*defStyleRes*/);
    }

    public PagedScrollBarView(Context context, AttributeSet attrs, int defStyleAttrs) {
        this(context, attrs, defStyleAttrs, 0 /*defStyleRes*/);
    }

    public PagedScrollBarView(
            Context context, AttributeSet attrs, int defStyleAttrs, int defStyleRes) {
        super(context, attrs, defStyleAttrs, defStyleRes);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(
                R.layout.system_sdk_paged_scrollbar_buttons, this /*root*/, true /*attachToRoot*/);

        mUpButton = findViewById(R.id.page_up);
        mUpButtonContainer = findViewById(R.id.page_up_container);
        mUpButtonContainer.setOnClickListener(this);
        mUpButtonContainer.setOnLongClickListener(this);
        mDownButton = findViewById(R.id.page_down);
        mDownButtonContainer = findViewById(R.id.page_down_container);
        mDownButtonContainer.setOnClickListener(this);
        mDownButtonContainer.setOnLongClickListener(this);

        mScrollMovingThumb = findViewById(R.id.scrollbar_moving_thumb);
        mFiller = findViewById(R.id.filler);
        mActionButtonParent = findViewById(R.id.action_button_parent);

        mMinThumbLength = getResources().getDimensionPixelSize(R.dimen.min_thumb_height);
        mMaxThumbLength = getResources().getDimensionPixelSize(R.dimen.max_thumb_height);

        if (!context.getResources().getBoolean(R.bool.system_sdk_true_for_touch)) {
            // Don't show the pagination buttons if there isn't touch.
            mUpButtonContainer.setVisibility(View.GONE);
            mDownButtonContainer.setVisibility(View.GONE);
        }
        mScrollElementsFadeAnimator =
                (ValueAnimator)
                        AnimatorInflater.loadAnimator(
                                context, R.animator.scroll_bar_thumb_elements_fade);
        mScrollElementsFadeAnimator.addUpdateListener(
                animation -> {
                    float currentValue = (float) animation.getAnimatedValue();
                    mScrollMovingThumb.setAlpha(currentValue);
                    mActionButtonParent.setAlpha(1 - currentValue);
                });
        mScrollElementsFadeAnimator.addListener(createScrollFadeAnimatorListener());
    }

    @Override
    public void onClick(View v) {
        dispatchPageClick(v);
    }

    @Override
    public boolean onLongClick(View v) {
        dispatchPageClick(v);
        return true;
    }

    public void setPaginationListener(PaginationListener listener) {
        mPaginationListener = listener;
    }

    /** Returns {@code true} if the "up" button is pressed */
    public boolean isUpPressed() {
        return mUpButtonContainer.isPressed();
    }

    /** Returns {@code true} if the "down" button is pressed */
    public boolean isDownPressed() {
        return mDownButtonContainer.isPressed();
    }

    /** Sets the range, offset and extent of the scroll bar. See {@link View}. */
    public void setParameters(int range, int offset, int extent, boolean animate) {
        /**
         * This method is where we take the computed parameters from the CarLayoutManager and render
         * it within the specified constraints ({@link #mMaxThumbLength} and {@link
         * #mMinThumbLength}).
         */
        final int size = mFiller.getHeight() - mFiller.getPaddingTop() - mFiller.getPaddingBottom();

        int thumbLength = extent * size / range;
        thumbLength = Math.max(Math.min(thumbLength, mMaxThumbLength), mMinThumbLength);

        int thumbOffset = size - thumbLength;
        if (isDownEnabled()) {
            // We need to adjust the offset so that it fits into the possible space inside the
            // filler with regarding to the constraints set by mMaxThumbLength and mMinThumbLength.
            thumbOffset = (size - thumbLength) * offset / range;
        }

        // Sets the size of the thumb and request a redraw if needed.
        final ViewGroup.LayoutParams lp = mScrollMovingThumb.getLayoutParams();
        if (lp.height != thumbLength) {
            lp.height = thumbLength;
            mScrollMovingThumb.requestLayout();
        }

        moveY(mScrollMovingThumb, thumbOffset, animate);
    }

    /** TODO(b/180361672): Add javadoc */
    public void setDayNightStyle(@DayNightStyle int dayNightStyle) {
        mDayNightStyle = dayNightStyle;
        reloadColors();
    }

    /**
     * Sets auto day/night mode. (this is the default)
     *
     * @deprecated please use setDayNightStyle(DayNightStyle.AUTO)
     */
    @Deprecated
    public void setAutoDayNightMode() {
        setDayNightStyle(DayNightStyle.AUTO);
    }

    /**
     * Sets auto light mode
     *
     * @deprecated please use setDayNightStyle(DayNightStyle.FORCE_NIGHT)
     */
    @Deprecated
    public void setLightMode() {
        setDayNightStyle(DayNightStyle.FORCE_NIGHT);
    }

    /**
     * Sets auto dark mode
     *
     * @deprecated please use setDayNightStyle(DayNightStyle.FORCE_DAY)
     */
    @Deprecated
    public void setDarkMode() {
        setDayNightStyle(DayNightStyle.FORCE_DAY);
    }

    /** TODO(b/180361672): Add javadoc */
    public void setUpEnabled(boolean enabled) {
        setPaginationButtonEnabled(mUpButtonContainer, enabled);
        setPaginationButtonAlpha(mUpButton, enabled);
    }

    /** TODO(b/180361672): Add javadoc */
    public void setDownEnabled(boolean enabled) {
        setPaginationButtonEnabled(mDownButtonContainer, enabled);
        setPaginationButtonAlpha(mDownButton, enabled);
    }

    public boolean isDownEnabled() {
        return mDownButtonContainer.isEnabled();
    }

    /**
     * Show the action button in the scroll bar, and hide the non-interactable thumb view.
     *
     * @param button the view to use as the button
     * @hide
     */
    public void showActionButton(View button) {
        mIsActionButtonActive = true;
        mActionButtonParent.removeAllViews();
        button.setDuplicateParentStateEnabled(true);
        mActionButtonParent.addView(button);
        mActionButtonParent.setOnClickListener(v -> button.callOnClick());
        fadeThumbOutAndButtonIn(/* animate= */ true);
    }

    /**
     * Show the non-interactable thumb view, and hide the scroll bar action button.
     *
     * @hide
     */
    public void showScrollMovingThumb() {
        mIsActionButtonActive = false;
        fadeThumbInAndButtonOut(/* animate= */ true);
    }

    /** Show the pagination buttons regardless of whether we are in touch mode, or not. */
    void showPaginationButtonsOnNonTouch() {
        mShowPaginationButtonsOnNonTouch = true;

        showPaginationButton(mUpButtonContainer);
        showPaginationButton(mDownButtonContainer);

        // Adjust the margins of the filler to make room for the buttons. We need to do this to
        // override
        // the margins used for the notouch-qualified resource dimensions which assumes the buttons
        // are
        // not shown.
        int marginPxResId =
                R.dimen.system_sdk_paged_list_view_scrollbar_thumb_visible_buttons_margin;
        int marginPx = getResources().getDimensionPixelSize(marginPxResId);
        ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) mFiller.getLayoutParams();
        layoutParams.setMargins(
                layoutParams.leftMargin, marginPx,
                layoutParams.rightMargin, marginPx);
        mFiller.setLayoutParams(layoutParams);
    }

    /**
     * Gets the parent view for the action button. Test-only
     *
     * @hide
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public ViewGroup getActionButtonParent() {
        return mActionButtonParent;
    }

    /**
     * Gets the moving scroll thumb view. Test-only
     *
     * @hide
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public ImageView getScrollMovingThumb() {
        return mScrollMovingThumb;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    Handler getScrollThumbFadeOutHandler() {
        return mScrollThumbFadeOutHandler;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    Runnable getScrollThumbFadeOutRunnable() {
        return mScrollThumbFadeOutRunnable;
    }

    /**
     * Checks whether the action button is active.
     *
     * <p>Note that there a couple of ways in which the return value from method may be misleading:
     *
     * <ul>
     *   <li>While the thumb is shown during a scroll, if the action button is set, method will
     *       return {@code true}. The button will technically be {@link View#VISIBLE} and able to
     *       receive focus, but it won't be clickable and it may be transparent.
     *   <li>When showing the thumb via {@link #showScrollMovingThumb()}, if the action button was
     *       just shown and is now being hidden, method will return {@code false}. However, the
     *       button will be {@link View#VISIBLE} and have a non-zero alpha until the fade animation
     *       to hide it has completed.
     * </ul>
     */
    boolean isActionButtonActive() {
        return mIsActionButtonActive;
    }

    /** Attempts to give focus to the action button. */
    boolean requestFocusInActionButton(int direction) {
        return mActionButtonParent.requestFocus(direction);
    }

    /** Checks whether the action button has focus. */
    boolean doesActionButtonHaveFocus() {
        return mActionButtonParent.hasFocus();
    }

    /**
     * Create the {@link AnimatorListener} to handle events for the {@link Animator} which faded the
     * scroll bar elements. This method is {@code protected} so it can be overridden in tests, for
     * spying.
     *
     * @hide
     */
    @VisibleForTesting
    protected AnimatorListener createScrollFadeAnimatorListener() {
        return new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mScrollMovingThumb.setVisibility(View.VISIBLE);
                mActionButtonParent.setVisibility(View.VISIBLE);
                mActionButtonParent.setClickable(false);
                mActionButtonParent.setFocusable(true);
            }

            @Override
            public void onAnimationStart(Animator animation, boolean isReverse) {
                onAnimationStart(animation);
                if (!isReverse) {
                    mActionButtonParent.setOnFocusChangeListener(
                            (v, hasFocus) -> {
                                if (hasFocus) {
                                    fadeThumbOutAndButtonIn(/* animate= */ false);
                                }
                            });
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mActionButtonParent.setClickable(true);
                if (!isActionButtonActive()) {
                    mActionButtonParent.setFocusable(false);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation, boolean isReverse) {
                if (!isReverse) {
                    if (!isActionButtonActive()) {
                        mActionButtonParent.setVisibility(View.GONE);
                    }
                    mFadeDirection = FadeDirection.STOPPED_AT_END;
                } else {
                    updateScrollElementsVisibility();
                    mFadeDirection = FadeDirection.STOPPED_AT_BEGINNING;
                    mActionButtonParent.setOnFocusChangeListener((v, hasFocus) -> {});
                }
                onAnimationEnd(animation);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // does nothing
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                // does nothing
            }
        };
    }

    private void showPaginationButton(View container) {
        container.setVisibility(View.VISIBLE);
        container.setFocusable(container.isEnabled());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            container.setDefaultFocusHighlightEnabled(false);
        }
    }

    private void setPaginationButtonAlpha(ImageView button, boolean enabled) {
        button.setImageAlpha(enabled ? BUTTON_ENABLED_ALPHA : BUTTON_DISABLED_ALPHA);
    }

    private void setPaginationButtonEnabled(View container, boolean enabled) {
        container.setEnabled(enabled);

        if (mShowPaginationButtonsOnNonTouch) {
            container.setFocusable(enabled);
        }
    }

    /** Reload the colors for the current {@link DayNightStyle}. */
    private void reloadColors() {
        int tint;
        int thumbBackground;
        int upDownBackgroundResId;

        switch (mDayNightStyle) {
            case DayNightStyle.AUTO:
                tint = ContextCompat.getColor(getContext(), R.color.system_sdk_tint);
                thumbBackground =
                        ContextCompat.getColor(getContext(), R.color.system_sdk_scrollbar_thumb);
                upDownBackgroundResId = R.drawable.system_sdk_pagination_background;
                break;
            case DayNightStyle.AUTO_INVERSE:
                tint = ContextCompat.getColor(getContext(), R.color.system_sdk_tint_inverse);
                thumbBackground =
                        ContextCompat.getColor(
                                getContext(), R.color.system_sdk_scrollbar_thumb_inverse);
                upDownBackgroundResId = R.drawable.system_sdk_pagination_background_inverse;
                break;
            case DayNightStyle.FORCE_NIGHT:
                tint = ContextCompat.getColor(getContext(), R.color.system_sdk_tint_night);
                thumbBackground =
                        ContextCompat.getColor(
                                getContext(), R.color.system_sdk_scrollbar_thumb_night);
                upDownBackgroundResId = R.drawable.system_sdk_pagination_background_night;
                break;
            case DayNightStyle.FORCE_DAY:
                tint = ContextCompat.getColor(getContext(), R.color.system_sdk_tint_day);
                thumbBackground =
                        ContextCompat.getColor(
                                getContext(), R.color.system_sdk_scrollbar_thumb_day);
                upDownBackgroundResId = R.drawable.system_sdk_pagination_background_day;
                break;
            default:
                throw new IllegalArgumentException("Unknown DayNightStyle: " + mDayNightStyle);
        }

        mScrollMovingThumb.setBackgroundColor(thumbBackground);

        mUpButton.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        mUpButton.setBackgroundResource(upDownBackgroundResId);

        mDownButton.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        mDownButton.setBackgroundResource(upDownBackgroundResId);
    }

    private void dispatchPageClick(View v) {
        final PaginationListener listener = mPaginationListener;
        if (listener == null) {
            return;
        }

        final int direction =
                (v.getId() == R.id.page_up_container)
                        ? PaginationListener.PAGE_UP
                        : PaginationListener.PAGE_DOWN;
        listener.onPaginate(direction);
    }

    /** Moves the given view to the specified 'y' position. */
    private void moveY(final View view, float newPosition, boolean animate) {
        final int duration = animate ? 200 : 0;
        view.animate()
                .y(newPosition)
                .setDuration(duration)
                .setInterpolator(mPaginationInterpolator)
                .start();

        if (mOldThumbYPosition == null) {
            fadeThumbInAndButtonOut(/* animate= */ false);
        } else if (mOldThumbYPosition != newPosition) {
            fadeThumbInAndButtonOut(/* animate= */ true);
        }
        mOldThumbYPosition = newPosition;
    }

    private void fadeThumbInAndButtonOut(boolean animate) {
        if (mFadeDirection == FadeDirection.STOPPED_AT_BEGINNING) {
            mScrollElementsFadeAnimator.start();
        } else if (mFadeDirection == FadeDirection.BACKWARD) {
            mScrollElementsFadeAnimator.reverse();
        }
        if (mFadeDirection != FadeDirection.STOPPED_AT_END) {
            mFadeDirection = FadeDirection.FORWARD;
        }
        scheduleThumbFadeOut();
        if (!animate && mScrollElementsFadeAnimator.isStarted()) {
            mScrollElementsFadeAnimator.end();
        }
    }

    private void fadeThumbOutAndButtonIn(boolean animate) {
        mScrollThumbFadeOutHandler.removeCallbacks(mScrollThumbFadeOutRunnable);
        if (!isActionButtonActive()) {
            return;
        }
        if (mFadeDirection != FadeDirection.BACKWARD) {
            mScrollElementsFadeAnimator.reverse();
        }
        mFadeDirection = FadeDirection.BACKWARD;
        if (!animate && mScrollElementsFadeAnimator.isStarted()) {
            mScrollElementsFadeAnimator.end();
        }
    }

    private void scheduleThumbFadeOut() {
        mScrollThumbFadeOutHandler.removeCallbacks(mScrollThumbFadeOutRunnable);
        mScrollThumbFadeOutHandler.postDelayed(
                mScrollThumbFadeOutRunnable, POST_SCROLL_FADE_DELAY_MS);
    }

    /**
     * Update the {@link View#setVisibility(int) visibility} of the mutable scroll elements to
     * respect {@link #isActionButtonActive()}.
     */
    private void updateScrollElementsVisibility() {
        mScrollMovingThumb.setVisibility(isActionButtonActive() ? View.GONE : View.VISIBLE);
        mActionButtonParent.setVisibility(isActionButtonActive() ? View.VISIBLE : View.GONE);
    }

    private enum FadeDirection {
        /** The button is showing */
        STOPPED_AT_BEGINNING,
        /** Fading button -> thumb */
        FORWARD,
        /** Thumb is showing */
        STOPPED_AT_END,
        /** Fading thumb -> button */
        BACKWARD
    }
}
