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
import android.content.res.Configuration;
import android.graphics.PointF;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Recycler;
import androidx.recyclerview.widget.RecyclerView.SmoothScroller.Action;

import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.view.widgets.ui.PagedListView.PagingUserAction;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * Custom {@link RecyclerView.LayoutManager} that behaves similar to LinearLayoutManager except that
 * it has a few tricks up its sleeve.
 *
 * <ol>
 *   <li>In a normal ListView, when views reach the top of the list, they are clipped. In
 *       CarLayoutManager, views have the option of flying off of the top of the screen as the next
 *       row settles in to place. This functionality can be enabled or disabled with {@link
 *       #setOffsetRows(boolean)}.
 *   <li>Standard list physics is disabled. Instead, when the user scrolls, it will settle on the
 *       next page.
 *   <li>Items can scroll past the bottom edge of the screen. This helps with pagination so that the
 *       last page can be properly aligned.
 * </ol>
 *
 * This LayoutManger should be used with {@link CarRecyclerView}.
 */
public class CarLayoutManager extends RecyclerView.LayoutManager {
    private static final String TAG = "CarLayoutManager";

    /**
     * Any fling below the threshold will just scroll to the top fully visible row. The units is
     * whatever {@link android.widget.Scroller} would return.
     *
     * <p>A reasonable value is ~200
     *
     * <p>This can be disabled by setting the threshold to -1.
     */
    private static final int FLING_THRESHOLD_TO_PAGINATE = -1;

    /**
     * Any fling shorter than this threshold (in px) will just scroll to the top fully visible row.
     *
     * <p>A reasonable value is 15.
     *
     * <p>This can be disabled by setting the distance to -1.
     */
    private static final int DRAG_DISTANCE_TO_PAGINATE = -1;

    /**
     * If you scroll really quickly, you can hit the end of the laid out rows before Android has a
     * chance to layout more. To help counter this, we can layout a number of extra rows past
     * wherever the focus is if necessary.
     */
    private static final int NUM_EXTRA_ROWS_TO_LAYOUT_PAST_FOCUS = 2;

    /**
     * In order to guarantee scrolling is possible in situations where children are taller than the
     * layout, specify a minimum number of children which must be laid out around the anchor (first
     * visible child) position
     */
    private static final int NUM_MINIMUM_ROWS_TO_LAYOUT_AROUND_ANCHOR = 1;

    /**
     * Scroll bar calculation is a bit complicated. This basically defines the granularity we want
     * our scroll bar to move. Set this to 1 means our scrollbar will have really jerky movement.
     * Setting it too big will risk an overflow (although there is no performance impact). Ideally
     * we want to set this higher than the height of our list view. We can't use our list view
     * height directly though because we might run into situations where getHeight() returns 0, for
     * example, when the view is not yet measured.
     */
    private static final int SCROLL_RANGE = 1000;

    private boolean mShouldRequestFocusOnLayout;
    private boolean mShouldRequestFocusOnScroll;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MARIO, SUPER_MARIO})
    private @interface ScrollStyle {}

    private static final int MARIO = 0;
    private static final int SUPER_MARIO = 1;

    @ScrollStyle private static final int SCROLL_TYPE = MARIO;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({BEFORE, AFTER})
    private @interface LayoutDirection {}

    private static final int BEFORE = 0;
    private static final int AFTER = 1;

    // Multiplier used for additional pages to layout/persist past the bottom fold
    private static final int BOTTOM_OVERLAP_MULTIPLIER = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ROW_OFFSET_MODE_INDIVIDUAL, ROW_OFFSET_MODE_PAGE})
    public @interface RowOffsetMode {}

    public static final int ROW_OFFSET_MODE_INDIVIDUAL = 0;
    public static final int ROW_OFFSET_MODE_PAGE = 1;

    private final AccelerateInterpolator mDanglingRowInterpolator = new AccelerateInterpolator(2);
    private final Context mContext;

    /** Determines whether or not rows will be offset as they slide off screen */
    private boolean mOffsetRows = false;
    /** Determines whether rows will be offset individually or a page at a time */
    @RowOffsetMode private int mRowOffsetMode = ROW_OFFSET_MODE_PAGE;

    /**
     * The LayoutManager only gets {@link #onScrollStateChanged(int)} updates. This enables the
     * scroll state to be used anywhere.
     */
    private int mScrollState = RecyclerView.SCROLL_STATE_IDLE;
    /** Used to inspect the current scroll state to help with the various calculations. */
    private CarSmoothScroller mSmoothScroller;

    private PagedListView.OnScrollListener mOnScrollListener;

    /** The distance that the list has actually scrolled in the most recent drag gesture */
    private int mLastDragDistance = 0;
    /** True if the current drag was limited/capped because it was at some boundary */
    private boolean mReachedLimitOfDrag;
    /** The index of the first item on the current page */
    private int mAnchorPageBreakPosition = 0;
    /** The index of the first item on the previous page */
    private int mUpperPageBreakPosition = -1;
    /** The index of the first item on the next page */
    private int mLowerPageBreakPosition = -1;
    /**
     * Index of the item at the top of the currently focused page. Focus page positions are similar
     * to page break positions, but only used for bookkeeping of scrolling due to focus movement.
     * These values are decoupled from paging logic in touch mode.
     */
    private int mCurrentFocusPagePosition = -1;
    /** Index of the item at the top of the page above the currently focused page. */
    private int mPreviousFocusPagePosition = -1;
    /** Index of the item at the top of the page below the currently focused page. */
    private int mNextFocusPagePosition = -1;
    /** Used in the bookkeeping of mario style scrolling to prevent extra calculations. */
    private int mLastChildPositionToRequestFocus = -1;

    private int mSampleViewHeight = -1;

    /** True if we have had a onLayoutChildren() call after this view attaches to the window. */
    private boolean mLayedOutAfterAttach = false;
    /**
     * The values are continuously updated to keep track of where the current page boundaries are on
     * screen. The anchor page break is the page break that is currently within or at the top of the
     * viewport. The Upper page break is the page break before it and the lower page break is the
     * page break after it.
     *
     * <p>A page break will be set to -1 if it is unknown or n/a.
     *
     * @see #updatePageBreakPositions()
     */
    private int mItemCountDuringLastPageBreakUpdate;

    private static final int MAX_ANIMATIONS_IN_CACHE = 30;
    /**
     * Cache of TranslateAnimation per child view. These are needed since using a single animation
     * for all children doesn't apply the animation effect multiple times. Key = the view the
     * animation will transform.
     */
    private LruCache<View, TranslateAnimation> mFlyOffscreenAnimations;

    /** Set the anchor to the following position on the next layout pass. */
    private int mPendingScrollPosition = -1;

    /** Flag controlling whether BOTTOM_OVERLAP_MULTIPLIER is applied in layout calculations */
    private boolean mLayoutExtraBottomPage = false;

    /**
     * Set to true to request focus on the child when {@link #scrollToPosition(int)} or {@link
     * #smoothScrollToPositionWithQuickAnimation(int)} is called.
     */
    private boolean mScrollToPositionFocusRequestEnabled = true;

    /**
     * Set to true to verify that the child is focusable before requesting focus when {@link
     * #scrollToPosition(int)} is called. Verification is never performed for {@link
     * #smoothScrollToPositionWithQuickAnimation(int)}, since focus is expected to be requested on
     * an offscreen view while the animation is ongoing.
     */
    private boolean mScrollToPositionFocusVerificationEnabled;

    private boolean mRetainFocusOnListUpdate;

    /**
     * Set to true to include top and bottom margins when calculating the first and last fully
     * visible child index respectively.
     *
     * @see #getFirstFullyVisibleChildIndex()
     * @see #getLastFullyVisibleChildIndex()
     */
    private boolean mIncludeMarginInFullyVisibleChildCalculation = true;

    public CarLayoutManager(Context context) {
        mContext = context;
    }
    // TODO(b/62544568): add tests

    /**
     * @hide Sets the associated PagedListView to layout an additional page below the fold. Default
     *     behavior is "false."
     */
    public void setLayoutExtraBottomPage(boolean shouldLayoutExtraPage) {
        mLayoutExtraBottomPage = shouldLayoutExtraPage;
    }

    /** @hide */
    @VisibleForTesting
    public boolean getLayoutExtraBottomPage() {
        return mLayoutExtraBottomPage;
    }

    /**
     * Whether this {@link CarLayoutManager} will automatically request focus when {@link
     * #scrollToPosition(int)} is invoked. Default is {@code true}.
     */
    public boolean isScrollToPositionFocusRequestEnabled() {
        return mScrollToPositionFocusRequestEnabled;
    }

    /**
     * Sets whether this {@link CarLayoutManager} will automatically request focus when {@link
     * #scrollToPosition(int)} is invoked. Default is {@code true}.
     */
    public void setScrollToPositionFocusRequestEnabled(
            boolean scrollToPositionFocusRequestEnabled) {
        mScrollToPositionFocusRequestEnabled = scrollToPositionFocusRequestEnabled;
    }

    /**
     * Whether this {@link CarLayoutManager} will verify that a view is focusable before
     * automatically requesting focus when {@link #scrollToPosition(int)} is invoked. Default is
     * {@code false}.
     */
    public boolean isScrollToPositionFocusVerificationEnabled() {
        return mScrollToPositionFocusVerificationEnabled;
    }

    /**
     * Sets whether this {@link CarLayoutManager} will verify that a view is focusable before
     * automatically requesting focus when {@link #scrollToPosition(int)} is invoked. Default is
     * {@code false}.
     */
    public void setScrollToPositionFocusVerificationEnabled(
            boolean scrollToPositionFocusVerificationEnabled) {
        mScrollToPositionFocusVerificationEnabled = scrollToPositionFocusVerificationEnabled;
    }

    /**
     * Retains focus when the focused list item moves to a different position.
     *
     * <p>Prerequisites:
     *
     * <ul>
     *   <li>{@link #setScrollToPositionFocusRequestEnabled(boolean)} to be set to {@code true}.
     *   <li>List diffs must be provided (see {@link android.support.v7.util.DiffUtil}, which is
     *       used with {@link RecyclerView.Adapter}), so item movement can be detected
     * </ul>
     *
     * @hide
     */
    public void setRetainFocusOnListUpdate(boolean value) {
        mRetainFocusOnListUpdate = value;
    }

    /**
     * Sets whether this {@link CarLayoutManager} will include top and bottom margins when
     * calculating the first and last fully visible child index respectively.
     *
     * @see #getFirstFullyVisibleChildIndex()
     * @see #getLastFullyVisibleChildIndex()
     * @hide
     */
    public void setIncludeMarginInFullyVisibleChildCalculation(boolean value) {
        mIncludeMarginInFullyVisibleChildCalculation = value;
    }

    /** @hide */
    @VisibleForTesting
    public boolean getRetainFocusOnListUpdate() {
        return mRetainFocusOnListUpdate;
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }

    @Override
    public boolean isAutoMeasureEnabled() {
        return true;
    }

    /**
     * onLayoutChildren is sort of like a "reset" for the layout state. At a high level, it should:
     *
     * <ol>
     *   <li>Check the current views to get the current state of affairs
     *   <li>Detach all views from the window (a lightweight operation) so that rows not re-added
     *       will be removed after onLayoutChildren.
     *   <li>Re-add rows as necessary.
     * </ol>
     *
     * @see RecyclerView.LayoutManager#onLayoutChildren(RecyclerView.Recycler, RecyclerView.State)
     */
    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        /**
         * The anchor view is the first fully visible view on screen at the beginning of
         * onLayoutChildren (or 0 if there is none). This row will be laid out first. After that,
         * layoutNextRow will layout rows above and below it until the boundaries of what should be
         * laid out have been reached. See {@link #shouldLayoutNextRow(View, int)} for more
         * information.
         */
        int anchorPosition = 0;
        int anchorTop = -1;
        if (mPendingScrollPosition == -1) {
            View anchor = getFirstFullyVisibleChild();
            if (anchor != null) {
                anchorPosition = getPosition(anchor);
                if (anchorPosition < 0) {
                    // If we cannot find the position from the view which happens when removing row
                    // 0,
                    // get the index for the first visible child to start laying out.
                    anchorPosition = getFirstFullyVisibleChildIndex();
                }
                anchorTop = getDecoratedTop(anchor);
            }
        } else {
            anchorPosition = mPendingScrollPosition;
            mPendingScrollPosition = -1;
            mAnchorPageBreakPosition = anchorPosition;
            mUpperPageBreakPosition = -1;
            mLowerPageBreakPosition = -1;
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                    TAG,
                    String.format(
                            ":: onLayoutChildren anchorPosition:%s, anchorTop:%s,"
                                    + " mPendingScrollPosition: %s, mAnchorPageBreakPosition:%s,"
                                    + " mUpperPageBreakPosition:%s, mLowerPageBreakPosition:%s",
                            anchorPosition,
                            anchorTop,
                            mPendingScrollPosition,
                            mAnchorPageBreakPosition,
                            mUpperPageBreakPosition,
                            mLowerPageBreakPosition));
        }

        /**
         * Detach all attached view for 2 reasons:
         *
         * <ol>
         *   <li>So that views are put in the scrap heap. This enables us to call {@link
         *       RecyclerView.Recycler#getViewForPosition(int)} which will either return one of
         *       these detached views if it is in the scrap heap, one from the recycled pool (will
         *       only call onBind in the adapter), or create an entirely new row if needed (will
         *       call onCreate and onBind in the adapter).
         *   <li>So that views are automatically removed if they are not manually re-added.
         * </ol>
         */
        detachAndScrapAttachedViews(recycler);

        /**
         * Layout the views recursively.
         *
         * <p>It's possible that this re-layout is triggered because an item gets removed. If the
         * anchor view is at the end of the list, the anchor view position will be bigger than the
         * number of available items. Correct that, and only start the layout if the anchor position
         * is valid.
         */
        anchorPosition = Math.min(anchorPosition, getItemCount() - 1);
        if (anchorPosition >= 0) {
            View anchor = layoutAnchor(recycler, anchorPosition, anchorTop);
            View adjacentRow = anchor;
            while (shouldLayoutNextRow(state, anchorPosition, adjacentRow, BEFORE)) {
                adjacentRow = layoutNextRow(recycler, adjacentRow, BEFORE);
            }
            adjacentRow = anchor;
            while (shouldLayoutNextRow(state, anchorPosition, adjacentRow, AFTER)) {
                adjacentRow = layoutNextRow(recycler, adjacentRow, AFTER);
            }
        }

        // Always force update the page break positions in our first onLayoutChildren() after
        // attaching to a window since they could be out of date if the items adapter has changed.
        if (!mLayedOutAfterAttach) {
            updatePageBreakPositions(/* forceUpdateBreakPosition= */ true);
            mLayedOutAfterAttach = true;
        } else {
            updatePageBreakPositions();
        }
        offsetRows();

        if (Log.isLoggable(TAG, Log.VERBOSE) && getChildCount() > 1) {
            Log.v(
                    TAG,
                    "Currently showing "
                            + getChildCount()
                            + " views "
                            + getPosition(getChildAt(0))
                            + " to "
                            + getPosition(getChildAt(getChildCount() - 1))
                            + " anchor "
                            + anchorPosition);
        }

        // Once focus lands in the list, onRequestChildFocus() will initialize the focus page
        // positions.
        clearFocusPagePositions();

        // If we're using focus-based navigation and there was a pending focus request, honor it.
        if (mShouldRequestFocusOnLayout && hasFocusBasedNavigation()) {
            mShouldRequestFocusOnLayout = false;
            View firstHasFocusableChild = getFirstFullyVisibleChildWithFocusable();
            // Only focus if the list is focused and we're using rotary or a hybrid car that is
            // currently
            // in rotary mode.
            if (firstHasFocusableChild != null
                    && !((ViewGroup) firstHasFocusableChild.getParent()).isInTouchMode()) {

                View firstFocusableView = ViewUtils.getFirstFocusable(firstHasFocusableChild);
                if (firstFocusableView == null) {
                    // Safety First! This should never happen, though.
                    Log.w(TAG, "firstHasFocusableChild does not have a focusable.");
                    return;
                }

                boolean requestFocusVerified;
                if (mScrollToPositionFocusVerificationEnabled) {
                    // Verify a child of the view we are about to request focus on can be found with
                    // a focus
                    // search from the decor view. This prevents issues where we request focus on a
                    // View that
                    // is a child of an invisible parent.
                    ViewGroup dv = ViewUtils.getRootView(firstFocusableView);
                    requestFocusVerified =
                            dv != null && ViewUtils.isFocusableChild(firstFocusableView, dv);
                } else {
                    requestFocusVerified = true;
                }

                if (requestFocusVerified) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(
                                TAG,
                                String.format(
                                        "Explicit Focus: onLayoutChildren focus-based navigation"
                                                + " enabled: requestFocus(%s)",
                                        firstHasFocusableChild));
                    }
                    firstHasFocusableChild.requestFocus();
                } else {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(
                                TAG,
                                String.format(
                                        "onLayoutChildren focus-based navigation enabled: %s not"
                                                + " focusable",
                                        firstHasFocusableChild));
                    }
                }
            }
        }
    }

    /**
     * scrollVerticallyBy does the work of what should happen when the list scrolls in addition to
     * handling cases where the list hits the end. It should be lighter weight than
     * onLayoutChildren. It doesn't have to detach all views. It only looks at the end of the list
     * and removes views that have gone out of bounds and lays out new ones that scroll in.
     *
     * @param dy The amount that the list is supposed to scroll. > 0 means the list is scrolling
     *     down. < 0 means the list is scrolling up.
     * @param recycler The recycler that enables views to be reused or created as they scroll in.
     * @param state Various information about the current state of affairs.
     * @return The amount the list actually scrolled.
     * @see RecyclerView.LayoutManager#scrollVerticallyBy(int, RecyclerView.Recycler,
     *     RecyclerView.State)
     */
    @Override
    public int scrollVerticallyBy(
            int dy, @NonNull RecyclerView.Recycler recycler, @NonNull RecyclerView.State state) {
        // If the list is empty, we can prevent the overscroll glow from showing by just
        // telling RecycerView that we scrolled.
        if (getItemCount() == 0) {
            return dy;
        }

        // Prevent redundant computations if there is definitely nowhere to scroll to.
        if (getChildCount() <= 1 || dy == 0) {
            mReachedLimitOfDrag = true;
            return 0;
        }

        View firstChild = getChildAt(0);
        if (firstChild == null) {
            mReachedLimitOfDrag = true;
            return 0;
        }
        int firstChildPosition = getPosition(firstChild);
        RecyclerView.LayoutParams firstChildParams = getParams(firstChild);
        int firstChildTopWithMargin = getDecoratedTop(firstChild) - firstChildParams.topMargin;

        View firstFullyVisibleChild = getFirstFullyVisibleChild();
        if (firstFullyVisibleChild == null) {
            mReachedLimitOfDrag = true;
            return 0;
        }
        int firstFullyVisiblePosition = getPosition(firstFullyVisibleChild);
        RecyclerView.LayoutParams firstFullyVisibleChildParams = getParams(firstFullyVisibleChild);
        int topRemainingSpace =
                getDecoratedTop(firstFullyVisibleChild)
                        - firstFullyVisibleChildParams.topMargin
                        - getPaddingTop();

        if (isAtBottom()
                && firstFullyVisiblePosition == mAnchorPageBreakPosition
                && dy > topRemainingSpace
                && dy > 0) {
            // Prevent dragging down more than 1 page. As a side effect, this also prevents you
            // from dragging past the bottom because if you are on the second to last page, it
            // prevents you from dragging past the last page.
            dy = topRemainingSpace;
            mReachedLimitOfDrag = true;
        } else if (dy < 0
                && firstChildPosition == 0
                && firstChildTopWithMargin + Math.abs(dy) > getPaddingTop()) {
            // Prevent scrolling past the beginning
            dy = firstChildTopWithMargin - getPaddingTop();
            mReachedLimitOfDrag = true;
        } else {
            mReachedLimitOfDrag = false;
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                    TAG,
                    String.format(
                            "scrollVerticallyBy dy=%d mReachedLimitOfDrag=%b",
                            dy, mReachedLimitOfDrag));
        }

        boolean isDragging = mScrollState == RecyclerView.SCROLL_STATE_DRAGGING;
        if (isDragging) {
            mLastDragDistance += dy;
        }
        // We offset by -dy because the views translate in the opposite direction that the
        // list scrolls (think about it.)
        offsetChildrenVertical(-dy);

        // The last item in the layout should never scroll above the viewport
        View view = getChildAt(getChildCount() - 1);
        if (view.getTop() < 0) {
            view.setTop(0);
        }

        // This is the meat of this function. We remove views on the trailing edge of the scroll
        // and add views at the leading edge as necessary.
        View adjacentRow;
        int firstVisibleChildPositionAfterOffset = getFirstFullyVisibleChildPosition();
        if (dy > 0) {
            recycleChildrenFromStart(recycler, firstVisibleChildPositionAfterOffset);
            adjacentRow = getChildAt(getChildCount() - 1);
            while (adjacentRow != null
                    && shouldLayoutNextRow(
                            state, firstVisibleChildPositionAfterOffset, adjacentRow, AFTER)) {
                adjacentRow = layoutNextRow(recycler, adjacentRow, AFTER);
            }
        } else {
            recycleChildrenFromEnd(recycler, firstVisibleChildPositionAfterOffset);
            adjacentRow = getChildAt(0);
            while (adjacentRow != null
                    && shouldLayoutNextRow(
                            state, firstVisibleChildPositionAfterOffset, adjacentRow, BEFORE)) {
                adjacentRow = layoutNextRow(recycler, adjacentRow, BEFORE);
            }
        }

        // If the list is operating in focus mode, snapping to page breakpoints is disabled in favor
        // of
        // focus scrolling (see ScrollStyle). Force updating orients the page breaks around the
        // current
        // viewport, so a transition back to touch mode (e.g. with hybrid cars) is seamless.
        updatePageBreakPositions(/* forceUpdateBreakPosition= */ hasFocus());

        // Now that the correct views are laid out, offset rows as necessary so we can do whatever
        // fancy animation we want such as having the top view fly off the screen as the next one
        // settles in to place.
        offsetRows();

        if (getChildCount() > 1) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(
                        TAG,
                        String.format(
                                "Currently showing  %d views (%d to %d)",
                                getChildCount(),
                                getPosition(getChildAt(0)),
                                getPosition(getChildAt(getChildCount() - 1))));
            }
        }
        updateFocusPageState(firstVisibleChildPositionAfterOffset);
        return dy;
    }

    @Override
    public void scrollToPosition(int position) {
        if (mScrollToPositionFocusRequestEnabled) {
            mShouldRequestFocusOnLayout = true;
            clearFocusPagePositions();
        }
        mPendingScrollPosition = position;
        requestLayout();
    }

    /**
     * Clients should use method to initiate a quick animated scroll rather than {@link
     * #smoothScrollToPosition(RecyclerView, RecyclerView.State, int)}, the latter of which should
     * only be accessed internally by RecyclerView during normal scroll logic.
     *
     * @hide
     */
    public void smoothScrollToPositionWithQuickAnimation(int position) {
        if (mScrollToPositionFocusRequestEnabled) {
            mShouldRequestFocusOnScroll = true;
            clearFocusPagePositions();
        }
        initiateSmoothScroll(position, ScrollAnimationSpeed.QUICK);
    }

    @Override
    public void smoothScrollToPosition(
            RecyclerView recyclerView, RecyclerView.State state, int position) {
        initiateSmoothScroll(position, ScrollAnimationSpeed.STANDARD);
    }

    private void initiateSmoothScroll(int targetPosition, ScrollAnimationSpeed scrollSpeed) {
        /**
         * startSmoothScroll will handle stopping the old one if there is one. We only keep a copy
         * of it to handle the translation of rows as they slide off the screen in {@link
         * #offsetRowsWithPageBreak()}
         */
        mSmoothScroller = new CarSmoothScroller(mContext, targetPosition, scrollSpeed);
        mSmoothScroller.setTargetPosition(targetPosition);
        startSmoothScroll(mSmoothScroller);
    }

    /** Miscellaneous bookkeeping. */
    @Override
    public void onScrollStateChanged(int state) {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, ":: onScrollStateChanged " + state);
        }
        if (state == RecyclerView.SCROLL_STATE_IDLE) {
            // If the focused view is off screen, give focus to one that is.
            // If the first fully visible view is first in the list, focus the first item.
            // Otherwise, focus the second so that you have the first item as scrolling context.
            View focusedChild = getFocusedChild();
            if (focusedChild != null
                    && (getDecoratedTop(focusedChild) >= getHeight() - getPaddingBottom()
                            || getDecoratedBottom(focusedChild) <= getPaddingTop())) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(
                            TAG,
                            String.format(
                                    "Explicit Focus: onScrollStateChanged clearFocus(%s)",
                                    focusedChild));
                }
                clearFocus(focusedChild, hasTouchpadForNavigation());
                requestLayout();
            }

        } else if (state == RecyclerView.SCROLL_STATE_DRAGGING) {
            mLastDragDistance = 0;
        }

        if (state != RecyclerView.SCROLL_STATE_SETTLING) {
            mSmoothScroller = null;
        }

        mScrollState = state;
        updatePageBreakPositions();
    }

    private static void clearFocus(View view, boolean hasTouchpadForUiNavigation) {
        // {@link View#clearFocus()} does not always dispatch global focus change events
        // (b/118897705)
        // which can result a View's hovered state not being cleaned up. This is a hack to work
        // around.
        if (hasTouchpadForUiNavigation) {
            view.setHovered(false);
        }
        view.clearFocus();
    }

    @Override
    public void onItemsChanged(RecyclerView recyclerView) {
        super.onItemsChanged(recyclerView);
        // When item changed, our sample view height is no longer accurate, and need to be
        // recomputed.
        mSampleViewHeight = -1;
    }

    /**
     * Gives us the opportunity to override the order of the focused views. By default, it will just
     * go from top to bottom. However, if there is no focused views, we take over the logic and
     * start the focused views from the middle of what is visible and move from there until the end
     * of the laid out views in the specified direction.
     */
    @Override
    public boolean onAddFocusables(
            RecyclerView recyclerView, ArrayList<View> views, int direction, int focusableMode) {

        View focusedChild = getFocusedChild();
        if (focusedChild != null) {
            // If there is a view that already has focus, we can just return false and the normal
            // Android ViewGroup.addFocusables will work fine.
            return false;
        }

        // Now we know that there isn't a focused view. We need to set up focusables such that
        // instead of just focusing the first item that has been laid out, it focuses starting
        // from a visible item.

        int firstFullyVisibleChildIndex = getFirstFullyVisibleChildIndex();
        if (firstFullyVisibleChildIndex == -1) {
            // Somehow there isn't a focused view and there is no fully visible view. There
            // shouldn't
            // be a way for this to happen but we'd better stop here and return instead of
            // continuing on with -1.
            Log.w(TAG, "There is no focused child and no first fully visible child.");
            return false;
        }

        if (hasTouchpadForNavigation()) {
            // Iterate from the first focusable child View to the end.
            // Note that View.addFocusables does nothing if there aren't focusables in the view
            // hierarchy. This effectively skips non-focusable views.
            for (int i = firstFullyVisibleChildIndex; i < getChildCount(); i++) {
                // In touchpad, FocusFinder needs access to all children of each eligible view to
                // choose the most appropriate view (rather than just the top-level view)
                // See b/110698294 and b/110413374
                View child = getChildAt(i);
                if (child != null) {
                    child.addFocusables(views, direction);
                }
            }
            return true;
        } else {
            // Framework calls onAddFocusables with FOCUS_DOWN direction when the focus is first
            // gained. Thereafter, it calls onAddFocusables with FOCUS_FORWARD or FOCUS_BACKWARD.
            if (direction == View.FOCUS_FORWARD || direction == View.FOCUS_BACKWARD) {
                // Identify the range of visible children. The focus search strategy is expected to
                // select
                // an item at the start or end of this range.
                int firstFocusableChildIndex = firstFullyVisibleChildIndex;
                int lastFocusableChildIndex = getLastFullyVisibleChildIndex();

                // If only one child is visible, and its top is shown and bottom is clipped, ensure
                // the
                // range includes just that child
                if (lastFocusableChildIndex < firstFullyVisibleChildIndex) {
                    Log.w(
                            TAG,
                            String.format(
                                    "No child view's bottom is visible. Selecting partially"
                                            + " visible child (index %d)",
                                    firstFocusableChildIndex));
                    lastFocusableChildIndex = firstFocusableChildIndex;
                }

                // If top of list isn't visible, instead of starting focusables on the first visible
                // item,
                // start on second visible item (but only if it's focusable) to give some context
                // that we
                // aren't at the beginning.
                if (!isAtTop()
                        && firstFocusableChildIndex + 1 < getItemCount()
                        && firstFocusableChildIndex < lastFocusableChildIndex
                        && getChildAt(firstFocusableChildIndex + 1).hasFocusable()) {
                    firstFocusableChildIndex++;
                }

                // If end of list isn't visible, instead of ending focusables on the last visible
                // item, end
                // on second to last visible item (but only if it's focusable) to give some context
                // that we
                // aren't at the end.
                if (!isAtBottom()
                        && lastFocusableChildIndex > 0
                        && lastFocusableChildIndex > firstFocusableChildIndex
                        && getChildAt(lastFocusableChildIndex - 1).hasFocusable()) {
                    lastFocusableChildIndex--;
                }

                // Iterate through the clamped range.
                // Note that View.addFocusables does nothing if there aren't focusables in the view
                // hierarchy. This effectively skips non-focusable views.
                for (int i = firstFocusableChildIndex; i <= lastFocusableChildIndex; i++) {
                    View child = getChildAt(i);
                    if (child != null) {
                        child.addFocusables(views, direction);
                    }
                }
                return true;
            } else if (direction == View.FOCUS_UP
                    || direction == View.FOCUS_DOWN
                    || direction == View.FOCUS_RIGHT
                    || direction == View.FOCUS_LEFT) {
                // Try to put the focus back on the last focused child view if it is visible.
                View lastFocusedVisibleChild = getLastFocusedChildIfVisible();
                if (lastFocusedVisibleChild != null) {
                    lastFocusedVisibleChild.addFocusables(views, direction);
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public View onFocusSearchFailed(
            View focused, int direction, RecyclerView.Recycler recycler, RecyclerView.State state) {
        // This doesn't seem to get called the way focus is handled on the phone...
        return null;
    }

    /**
     * This is the function that decides where to scroll to when a new view is focused. You can get
     * the position of the currently focused child through the child parameter. Once you have that,
     * determine where to smooth scroll to and scroll there.
     *
     * @param parent The RecyclerView hosting this LayoutManager
     * @param state Current state of RecyclerView
     * @param child Direct child of the RecyclerView containing the newly focused view
     * @param focused The newly focused view. This may be the same view as child or it may be null
     * @return true if the default scroll behavior should be suppressed
     */
    @Override
    public boolean onRequestChildFocus(
            RecyclerView parent, RecyclerView.State state, View child, View focused) {
        if (child == null) {
            Log.w(TAG, "onRequestChildFocus with a null child!");
            return true;
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                    TAG,
                    String.format(":: onRequestChildFocus child: %s, focused: %s", child, focused));
        }

        if (mCurrentFocusPagePosition == -1) {
            // Set the page positions as a baseline for future comparison.
            setFocusPagePositions(getFirstFullyVisibleChildPosition());
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(
                        TAG,
                        String.format(
                                ":: onRequestChildFocus mCurrentFocusPagePosition=%d,"
                                        + " mPreviousFocusedPagePosition=%d,"
                                        + " mNextFocusedPagePosition=%d",
                                mCurrentFocusPagePosition,
                                mPreviousFocusPagePosition,
                                mNextFocusPagePosition));
            }
        }

        // We have several distinct scrolling methods. Each implementation has been delegated
        // to its own method.
        if (SCROLL_TYPE == MARIO) {
            return onRequestChildFocusMarioStyle(parent, child);
        } else if (SCROLL_TYPE == SUPER_MARIO) {
            return onRequestChildFocusSuperMarioStyle(parent, state, child);
        } else {
            throw new IllegalStateException("Unknown scroll type (" + SCROLL_TYPE + ")");
        }
    }

    /**
     * Goal: the scrollbar maintains the same size throughout scrolling and that the scrollbar
     * reaches the bottom of the screen when the last item is fully visible. This is because there
     * are multiple points that could be considered the bottom since the last item can scroll past
     * the bottom edge of the screen.
     *
     * <p>To find the extent, we divide the number of items that can fit on screen by the number of
     * items in total.
     */
    @Override
    public int computeVerticalScrollExtent(RecyclerView.State state) {
        if (getChildCount() <= 1) {
            return 0;
        }

        int sampleViewHeight = getSampleViewHeight();
        int availableHeight = getAvailableHeight();
        int sampleViewsThatCanFitOnScreen = Math.max(availableHeight / sampleViewHeight, 1);

        if (state.getItemCount() <= sampleViewsThatCanFitOnScreen) {
            return SCROLL_RANGE;
        } else {
            return SCROLL_RANGE * sampleViewsThatCanFitOnScreen / state.getItemCount();
        }
    }

    /**
     * The scrolling offset is calculated by determining what position is at the top of the list.
     * However, instead of using fixed integer positions for each row, the scroll position is
     * factored in and the position is recalculated as a float that takes in to account the current
     * scroll state. This results in a smooth animation for the scrollbar when the user scrolls the
     * list.
     */
    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        View firstChild = getFirstFullyVisibleChild();
        if (firstChild == null) {
            return 0;
        }

        RecyclerView.LayoutParams params = getParams(firstChild);
        int firstChildPosition = getPosition(firstChild);

        // Assume the previous view is the same height as the current one.
        float percentOfPreviousViewShowing =
                (getDecoratedTop(firstChild) - params.topMargin)
                        / (float)
                                (getDecoratedMeasuredHeight(firstChild)
                                        + params.topMargin
                                        + params.bottomMargin);
        // If the previous view is actually larger than the current one then this the percent
        // can be greater than 1.
        percentOfPreviousViewShowing = Math.min(percentOfPreviousViewShowing, 1);

        float currentPosition = (float) firstChildPosition - percentOfPreviousViewShowing;

        int sampleViewHeight = getSampleViewHeight();
        int availableHeight = getAvailableHeight();
        int numberOfSampleViewsThatCanFitOnScreen = Math.max(availableHeight / sampleViewHeight, 1);
        int positionWhenLastItemIsVisible =
                state.getItemCount() - numberOfSampleViewsThatCanFitOnScreen;

        if (positionWhenLastItemIsVisible <= 0) {
            return 0;
        }

        if (currentPosition >= positionWhenLastItemIsVisible) {
            return SCROLL_RANGE;
        }

        return (int) (SCROLL_RANGE * currentPosition / positionWhenLastItemIsVisible);
    }

    /**
     * The range of the scrollbar can be understood as the granularity of how we want the scrollbar
     * to scroll.
     */
    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        return SCROLL_RANGE;
    }

    @Override
    public void onAttachedToWindow(RecyclerView view) {
        super.onAttachedToWindow(view);
        // The purpose of calling this is so that any animation offsets are re-applied. These are
        // cleared in View.onDetachedFromWindow().
        // This fixes b/27672379
        updatePageBreakPositions();
        offsetRows();
    }

    @Override
    public void onDetachedFromWindow(RecyclerView recyclerView, Recycler recycler) {
        super.onDetachedFromWindow(recyclerView, recycler);
        mLayedOutAfterAttach = false;
    }

    /**
     * Returns the index of the first child view that starts below the top of the layout. If the
     * first visible child is taller than the layout (i.e. its bottom is clipped), it is still
     * considered "fully visible" and its index will be returned.
     *
     * @return The index of the first visible child view or {@code -1} if no child's top is visible.
     */
    public int getFirstFullyVisibleChildIndex() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            RecyclerView.LayoutParams params = getParams(child);
            int top = getDecoratedTop(child);
            if (mIncludeMarginInFullyVisibleChildCalculation) {
                top -= params.topMargin;
            }
            if (top >= getPaddingTop()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the position of first visible child in the list. If the first visible child is taller
     * than the layout (i.e. its bottom is clipped), it is still considered "fully visible" and its
     * position will be returned.
     *
     * @return The position of the first visible child view or {@code -1} if no child's top is
     *     visible.
     */
    public int getFirstFullyVisibleChildPosition() {
        View child = getFirstFullyVisibleChild();
        if (child == null) {
            return -1;
        }
        return getPosition(child);
    }

    /** Returns the position of the focused item in the list or {@code -1} if there is no focus. */
    public int getFocusedItemPosition() {
        View focused = getFocusedChild();
        if (focused == null) {
            return -1;
        }
        return getPosition(focused);
    }

    /**
     * Returns the position of last visible child in the list. If the first visible child is taller
     * than the layout (i.e. no child fits completely on screen), then the position of the child
     * directly above the first visible child will be returned.
     *
     * @return The position of the last visible child view or {@code -1} if no child's bottom is
     *     above the bottom of the layout.
     */
    public int getLastFullyVisibleChildPosition() {
        View child = getLastFullyVisibleChild();
        if (child == null) {
            return -1;
        }
        return getPosition(child);
    }

    /**
     * Returns the first child view that starts below the top of the layout. If the first visible
     * child is taller than the layout (i.e. its bottom is clipped), it is still considered "fully
     * visible" and will be returned.
     *
     * @return The first visible child or {@code null} if no child's top is visible.
     */
    public View getFirstFullyVisibleChild() {
        int firstFullyVisibleChildIndex = getFirstFullyVisibleChildIndex();
        View firstChild = null;
        if (firstFullyVisibleChildIndex != -1) {
            firstChild = getChildAt(firstFullyVisibleChildIndex);
        }
        return firstChild;
    }

    /**
     * Returns first visible child that can take focus, or {@code null} if no focusable children
     * exist. May return a child past the bottom of the layout if no focusable children are on
     * screen.
     *
     * @hide
     */
    @Nullable
    public View getFirstFullyVisibleChildWithFocusable() {
        return getFirstFocusableChildStartingAtIndex(getFirstFullyVisibleChildIndex());
    }

    /**
     * Returns first child that can take focus whose index is equal to or greater than {@code
     * startIndex}. Returns {@code null} if no focusable children exist with that constraint.
     */
    @Nullable
    private View getFirstFocusableChildStartingAtIndex(int startIndex) {
        if (startIndex < 0 || startIndex >= getChildCount()) {
            return null;
        }
        // Move down the list until we reach a child that can take focus.
        for (int index = startIndex; index < getChildCount(); index++) {
            View child = getChildAt(index);
            if (child.hasFocusable()) {
                return child;
            }
        }
        return null;
    }

    /**
     * Returns the last child view that ends above the bottom of the layout. If the first visible
     * child is taller than the layout (i.e. no child fits completely on screen), then the child
     * directly above the first visible child will be returned.
     *
     * @return The last visible child or {@code null} if no child's bottom is above the bottom of
     *     the layout.
     */
    public View getLastFullyVisibleChild() {
        int lastFullyVisibleChildIndex = getLastFullyVisibleChildIndex();
        View lastChild = null;
        if (lastFullyVisibleChildIndex != -1) {
            lastChild = getChildAt(lastFullyVisibleChildIndex);
        }
        return lastChild;
    }

    /**
     * Returns the index of the last child view that ends above the bottom of the layout.
     *
     * <p>Since the LayoutManager snaps to view starts, if only one view is visible and it is taller
     * than the screen, and there are offscreen children above the visible child, then the index of
     * the offscreen child directly above the visible child will be returned. In this scenario,
     * {@link #getLastFullyVisibleChildIndex()} will be less than {@link
     * #getFirstFullyVisibleChildIndex()}.
     *
     * @return The index of the child view or {@code -1} if no child's bottom is above the bottom of
     *     the layout.
     */
    public int getLastFullyVisibleChildIndex() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            RecyclerView.LayoutParams params = getParams(child);
            int childBottom = getDecoratedBottom(child);
            if (mIncludeMarginInFullyVisibleChildCalculation) {
                childBottom += params.bottomMargin;
            }
            int listBottom = getHeight() - getPaddingBottom();
            if (childBottom <= listBottom) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns child view which last had focus, or {@code null} if no memory exists or last focused
     * view is not fully visible.
     *
     * @hide
     */
    public View getLastFocusedChildIfVisible() {
        int lastFocusedVisibleChildIndex = getLastFocusedChildIndexIfVisible();
        View lastFocusedChild = null;
        if (lastFocusedVisibleChildIndex != -1) {
            lastFocusedChild = getChildAt(lastFocusedVisibleChildIndex);
        }
        return lastFocusedChild;
    }

    /**
     * Returns the index of the child view that was last focused and is currently visible to the
     * user.
     *
     * @return The index of the child view or {@code -1} if no visible child is found.
     */
    public int getLastFocusedChildIndexIfVisible() {
        int lastFocusedChildIndex = getLastFocusedChildIndex();
        return isFullyVisibleChild(lastFocusedChildIndex) ? lastFocusedChildIndex : -1;
    }

    /**
     * Returns the index of the child view that was last focused.
     *
     * @return The index of the child view or {@code -1} if no memory exists, or last focused view
     *     is no longer in list of children
     */
    private int getLastFocusedChildIndex() {
        if (mLastChildPositionToRequestFocus != -1) {
            for (int i = 0; i < getChildCount(); i++) {
                if (getPosition(getChildAt(i)) == mLastChildPositionToRequestFocus) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Returns whether the child at a given index is fully visible, or {@code false} if the given
     * index is invalid. A child is fully visible if
     *
     * <ul>
     *   <li>Its top and bottom are inside the layout's vertical bounds, or
     *   <li>It is the only child on screen and is taller than the layout
     * </ul>
     */
    private boolean isFullyVisibleChild(int childIndex) {
        if (childIndex < 0 || childIndex >= getChildCount()) {
            return false;
        }
        int firstVisibleChildIndex = getFirstFullyVisibleChildIndex();
        int lastVisibleChildIndex =
                Math.max(getLastFullyVisibleChildIndex(), firstVisibleChildIndex);
        return firstVisibleChildIndex != -1
                && childIndex >= firstVisibleChildIndex
                && childIndex <= lastVisibleChildIndex;
    }

    /** Returns whether or not the first view is fully visible. */
    public boolean isAtTop() {
        // getFirstFullyVisibleChildIndex() can return -1 which indicates that there are no views
        // and also means that the list is at the top.
        return getFirstFullyVisibleChildIndex() <= 0;
    }

    /**
     * Returns whether or not the paged list has been scrolled as far down as possible. This is true
     * when:
     *
     * <ul>
     *   <li>The last view is fully visible (top and bottom are on screen), or
     *   <li>The last view is the first visible child, but is partially visible since it's taller
     *       than the layout
     * </ul>
     */
    public boolean isAtBottom() {
        int firstVisibleChildPosition = getFirstFullyVisibleChildPosition();
        int lastVisibleChildPosition = getLastFullyVisibleChildPosition();
        return firstVisibleChildPosition == -1
                || firstVisibleChildPosition == getItemCount() - 1
                || lastVisibleChildPosition == getItemCount() - 1;
    }

    /** TODO(b/180361672): Add javadoc */
    public void setOffsetRows(boolean offsetRows) {
        mOffsetRows = offsetRows;
        if (offsetRows) {
            // Card animation offsets are only needed when we use the flying off the screen effect
            if (mFlyOffscreenAnimations == null) {
                mFlyOffscreenAnimations = new LruCache<>(MAX_ANIMATIONS_IN_CACHE);
            }
            offsetRows();
        } else {
            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                setCardFlyingEffectOffset(getChildAt(i), 0);
            }
            mFlyOffscreenAnimations = null;
        }
    }

    /** TODO(b/180361672): Add javadoc */
    public void setRowOffsetMode(@RowOffsetMode int mode) {
        if (mode == mRowOffsetMode) {
            return;
        }

        mRowOffsetMode = mode;
        offsetRows();
    }

    public void setOnScrollListener(PagedListView.OnScrollListener listener) {
        mOnScrollListener = listener;
    }

    /**
     * Finish the pagination taking into account where the gesture started (not where we are now).
     *
     * @return Whether the list was scrolled as a result of the fling.
     */
    public boolean settleScrollForFling(RecyclerView parent, int flingVelocity) {
        if (getChildCount() == 0) {
            return false;
        }

        if (mReachedLimitOfDrag) {
            return false;
        }

        // If the fling was too slow or too short, settle on the first fully visible row instead.
        if (Math.abs(flingVelocity) <= FLING_THRESHOLD_TO_PAGINATE
                || Math.abs(mLastDragDistance) <= DRAG_DISTANCE_TO_PAGINATE) {
            int firstFullyVisibleChildIndex = getFirstFullyVisibleChildIndex();
            if (firstFullyVisibleChildIndex != -1) {
                int scrollPosition = getPosition(getChildAt(firstFullyVisibleChildIndex));
                parent.smoothScrollToPosition(scrollPosition);
                return true;
            }
            return false;
        }

        // Finish the pagination taking into account where the gesture
        // started (not where we are now).
        boolean isDownGesture = flingVelocity > 0 || (flingVelocity == 0 && mLastDragDistance >= 0);
        boolean isUpGesture = flingVelocity < 0 || (flingVelocity == 0 && mLastDragDistance < 0);
        if (isDownGesture && mLowerPageBreakPosition != -1) {
            // If the last view is fully visible then only settle on the first fully visible view
            // instead of the original page down position. However, don't page down if the last
            // item has come fully into view.
            parent.smoothScrollToPosition(mAnchorPageBreakPosition);
            if (mOnScrollListener != null) {
                mOnScrollListener.onUserPagedDown(PagingUserAction.GESTURE);
            }
            return true;
        } else if (isUpGesture && mUpperPageBreakPosition != -1) {
            parent.smoothScrollToPosition(mUpperPageBreakPosition);
            if (mOnScrollListener != null) {
                mOnScrollListener.onUserPagedUp(PagingUserAction.GESTURE);
            }
            return true;
        } else {
            Log.e(
                    TAG,
                    "Error setting scroll for fling! flingVelocity: \t"
                            + flingVelocity
                            + "\tlastDragDistance: "
                            + mLastDragDistance
                            + "\tpageUpAtStartOfDrag: "
                            + mUpperPageBreakPosition
                            + "\tpageDownAtStartOfDrag: "
                            + mLowerPageBreakPosition);
            // As a last resort, at the last smooth scroller target position if there is one.
            if (mSmoothScroller != null) {
                parent.smoothScrollToPosition(mSmoothScroller.getTargetPosition());
                return true;
            }
        }
        return false;
    }

    /** @return The position that paging up from the current position would settle at. */
    public int getPageUpPosition() {
        return mUpperPageBreakPosition;
    }

    /** @return The position that paging down from the current position would settle at. */
    public int getPageDownPosition() {
        return mLowerPageBreakPosition;
    }

    /**
     * Layout the anchor row. The anchor row is the first fully visible row.
     *
     * @param anchorTop The decorated top of the anchor. If it is not known or should be reset to
     *     the top, pass -1.
     */
    private View layoutAnchor(RecyclerView.Recycler recycler, int anchorPosition, int anchorTop) {
        View anchor = recycler.getViewForPosition(anchorPosition);
        RecyclerView.LayoutParams params = getParams(anchor);
        measureChildWithMargins(anchor, 0, 0);
        int left = getPaddingLeft() + params.leftMargin;
        int top = (anchorTop == -1) ? params.topMargin : anchorTop;
        int right = left + getDecoratedMeasuredWidth(anchor);
        int bottom = top + getDecoratedMeasuredHeight(anchor);
        layoutDecorated(anchor, left, top, right, bottom);
        addView(anchor);
        return anchor;
    }

    /**
     * Lays out the next row in the specified direction next to the specified adjacent row.
     *
     * @param recycler The recycler from which a new view can be created.
     * @param adjacentRow The View of the adjacent row which will be used to position the new one.
     * @param layoutDirection The side of the adjacent row that the new row will be laid out on.
     * @return The new row that was laid out.
     */
    private View layoutNextRow(
            RecyclerView.Recycler recycler,
            View adjacentRow,
            @LayoutDirection int layoutDirection) {
        int adjacentRowPosition = getPosition(adjacentRow);
        int newRowPosition = adjacentRowPosition;
        if (layoutDirection == BEFORE) {
            newRowPosition = adjacentRowPosition - 1;
        } else if (layoutDirection == AFTER) {
            newRowPosition = adjacentRowPosition + 1;
        }

        // Because we detach all rows in onLayoutChildren, this will often just return a view from
        // the scrap heap.
        View newRow = recycler.getViewForPosition(newRowPosition);

        measureChildWithMargins(newRow, 0, 0);
        RecyclerView.LayoutParams newRowParams =
                (RecyclerView.LayoutParams) newRow.getLayoutParams();
        RecyclerView.LayoutParams adjacentRowParams =
                (RecyclerView.LayoutParams) adjacentRow.getLayoutParams();
        int left = getPaddingLeft() + newRowParams.leftMargin;
        int right = left + getDecoratedMeasuredWidth(newRow);
        int top;
        int bottom;
        if (layoutDirection == BEFORE) {
            bottom = adjacentRow.getTop() - adjacentRowParams.topMargin - newRowParams.bottomMargin;
            top = bottom - getDecoratedMeasuredHeight(newRow);
        } else {
            top =
                    getDecoratedBottom(adjacentRow)
                            + adjacentRowParams.bottomMargin
                            + newRowParams.topMargin;
            bottom = top + getDecoratedMeasuredHeight(newRow);
        }
        layoutDecorated(newRow, left, top, right, bottom);

        if (layoutDirection == BEFORE) {
            addView(newRow, 0);
        } else {
            addView(newRow);
        }

        return newRow;
    }

    /** @return Whether another row should be laid out in the specified direction. */
    private boolean shouldLayoutNextRow(
            RecyclerView.State state,
            int anchorPosition,
            View adjacentRow,
            @LayoutDirection int layoutDirection) {
        int adjacentRowPosition = getPosition(adjacentRow);

        if (layoutDirection == BEFORE) {
            if (adjacentRowPosition == 0) {
                // We already laid out the first row.
                return false;
            }
        } else if (layoutDirection == AFTER) {
            if (adjacentRowPosition >= state.getItemCount() - 1) {
                // We already laid out the last row.
                return false;
            }
        }

        // In focus mode, ensure a minimum number of rows around the focused child are laid out
        View focusedRow = getFocusedChild();
        if (focusedRow != null) {
            int focusedRowPosition = getPosition(focusedRow);
            if (layoutDirection == BEFORE
                    && adjacentRowPosition
                            >= focusedRowPosition - NUM_EXTRA_ROWS_TO_LAYOUT_PAST_FOCUS) {
                return true;
            } else if (layoutDirection == AFTER
                    && adjacentRowPosition
                            <= focusedRowPosition + NUM_EXTRA_ROWS_TO_LAYOUT_PAST_FOCUS) {
                return true;
            }
        }

        // Ensure a minimum number of rows around the anchor row are laid out, so it's possible to
        // set
        // page breaks in both directions, regardless of the height of the rows
        if (anchorPosition != -1) {
            if (layoutDirection == BEFORE
                    && adjacentRowPosition
                            >= anchorPosition - NUM_MINIMUM_ROWS_TO_LAYOUT_AROUND_ANCHOR) {
                return true;
            }
            if (layoutDirection == AFTER
                    && adjacentRowPosition
                            <= anchorPosition + NUM_MINIMUM_ROWS_TO_LAYOUT_AROUND_ANCHOR) {
                return true;
            }
        }

        RecyclerView.LayoutParams params = getParams(adjacentRow);
        int adjacentRowTop = getDecoratedTop(adjacentRow) - params.topMargin;
        int adjacentRowBottom = getDecoratedBottom(adjacentRow) - params.bottomMargin;

        // Default bottom bound is anything beyond the first page.
        int bottomBound = getHeight() - getPaddingBottom();
        if (mLayoutExtraBottomPage) {
            // In mode, we layout an extra page below the fold.
            bottomBound = getHeight() * BOTTOM_OVERLAP_MULTIPLIER;
        }

        if (layoutDirection == BEFORE && adjacentRowTop < getPaddingTop() - getHeight()) {
            // View is more than 1 page past the top of the screen and also past where the user has
            // scrolled to. We want to keep one page past the top to make the scroll up calculation
            // easier and scrolling smoother.
            return false;
        } else if (layoutDirection == AFTER && adjacentRowBottom > bottomBound) {
            // View is beyond the bottom bound.
            return false;
        }

        return true;
    }

    /** Remove and recycle views that are no longer needed. */
    private void recycleChildrenFromStart(RecyclerView.Recycler recycler, int anchorPosition) {
        // Start laying out children one page before the top of the viewport.
        int childrenStart = getPaddingTop() - getHeight();

        int focusedChildPosition = Integer.MAX_VALUE;
        View focusedChild = getFocusedChild();
        if (focusedChild != null) {
            focusedChildPosition = getPosition(focusedChild);
        }

        // Count the number of views that should be removed.
        int detachedCount = 0;
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            int childEnd = getDecoratedBottom(child);
            int childPosition = getPosition(child);

            // Do not remove views which lie within the previous page or are positioned within the
            // minimum
            // distance behind the focused child (if in focus mode) or anchor child
            if (childEnd >= childrenStart
                    || childPosition >= focusedChildPosition - NUM_EXTRA_ROWS_TO_LAYOUT_PAST_FOCUS
                    || childPosition >= anchorPosition - NUM_MINIMUM_ROWS_TO_LAYOUT_AROUND_ANCHOR) {
                break;
            }

            detachedCount++;
        }

        // Remove the number of views counted above. Done by removing the first child n times.
        while (--detachedCount >= 0) {
            final View child = getChildAt(0);
            removeAndRecycleView(child, recycler);
        }
    }

    /** Remove and recycle views that are no longer needed. */
    private void recycleChildrenFromEnd(RecyclerView.Recycler recycler, int anchorPosition) {
        // Layout views until the end of the viewport.
        int childrenEnd = getHeight();
        if (mLayoutExtraBottomPage) {
            // If true, maintain an extra page below the fold.
            childrenEnd *= BOTTOM_OVERLAP_MULTIPLIER;
        }

        int focusedChildPosition = Integer.MIN_VALUE;
        View focusedChild = getFocusedChild();
        if (focusedChild != null) {
            focusedChildPosition = getPosition(focusedChild);
        }

        // Count the number of views that should be removed.
        int firstDetachedPos = 0;
        int detachedCount = 0;
        int childCount = getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            int childStart = getDecoratedTop(child);
            int childPosition = getPosition(child);

            // Do not remove views which lie within the current page or are positioned within the
            // minimum
            // distance after the focused child (if in focus mode) or anchor child
            if (childStart <= childrenEnd
                    || childPosition <= focusedChildPosition + NUM_EXTRA_ROWS_TO_LAYOUT_PAST_FOCUS
                    || childPosition <= anchorPosition + NUM_MINIMUM_ROWS_TO_LAYOUT_AROUND_ANCHOR) {
                break;
            }

            firstDetachedPos = i;
            detachedCount++;
        }

        while (--detachedCount >= 0) {
            final View child = getChildAt(firstDetachedPos);
            removeAndRecycleView(child, recycler);
        }
    }

    /**
     * Offset rows to do fancy animations. If offset rows was not enabled with {@link
     * #setOffsetRows}, this will do nothing.
     *
     * @see #setOffsetRows
     */
    public void offsetRows() {
        if (!mOffsetRows) {
            return;
        }

        if (mRowOffsetMode == ROW_OFFSET_MODE_PAGE) {
            offsetRowsByPage();
        } else if (mRowOffsetMode == ROW_OFFSET_MODE_INDIVIDUAL) {
            offsetRowsIndividually();
        }
    }

    /**
     * Offset the single row that is scrolling off the screen such that by the time the next row
     * reaches the top, it will have accelerated completely off of the screen.
     */
    private void offsetRowsIndividually() {
        if (getChildCount() == 0) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, ":: offsetRowsIndividually getChildCount=0");
            }
            return;
        }

        // Identify the dangling row. It will be the first row that is at the top of the
        // list or above.
        int danglingChildIndex = -1;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (getDecoratedTop(child) - getParams(child).topMargin <= getPaddingTop()) {
                danglingChildIndex = i;
                break;
            }
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, ":: offsetRowsIndividually danglingChildIndex: " + danglingChildIndex);
        }

        // Calculate the total amount that the view will need to scroll in order to go completely
        // off screen.
        RecyclerView rv = (RecyclerView) getChildAt(0).getParent();
        int[] locs = new int[2];
        rv.getLocationInWindow(locs);
        int listTopInWindow = locs[1] + rv.getPaddingTop();
        int maxDanglingViewTranslation;

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            RecyclerView.LayoutParams params = getParams(child);

            maxDanglingViewTranslation = listTopInWindow;
            // If the child has a negative margin, we'll actually need to translate the view a
            // little but further to get it completely off screen.
            if (params.topMargin < 0) {
                maxDanglingViewTranslation -= params.topMargin;
            }
            if (params.bottomMargin < 0) {
                maxDanglingViewTranslation -= params.bottomMargin;
            }

            if (i < danglingChildIndex) {
                child.setAlpha(0f);
            } else if (i > danglingChildIndex) {
                child.setAlpha(1f);
                setCardFlyingEffectOffset(child, 0);
            } else {
                int totalScrollDistance =
                        getDecoratedMeasuredHeight(child) + params.topMargin + params.bottomMargin;

                int distanceLeftInScroll =
                        getDecoratedBottom(child) + params.bottomMargin - getPaddingTop();
                float percentageIntoScroll = 1 - distanceLeftInScroll / (float) totalScrollDistance;
                float interpolatedPercentage =
                        mDanglingRowInterpolator.getInterpolation(percentageIntoScroll);

                child.setAlpha(1f);
                setCardFlyingEffectOffset(
                        child, -(maxDanglingViewTranslation * interpolatedPercentage));
            }
        }
    }

    /**
     * When the list scrolls, the entire page of rows will offset in one contiguous block. This
     * significantly reduces the amount of extra motion at the top of the screen.
     */
    private void offsetRowsByPage() {
        View anchorView = findViewByPosition(mAnchorPageBreakPosition);
        if (anchorView == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, ":: offsetRowsByPage anchorView null");
            }
            return;
        }
        int anchorViewTop = getDecoratedTop(anchorView) - getParams(anchorView).topMargin;

        View upperPageBreakView = findViewByPosition(mUpperPageBreakPosition);
        if (upperPageBreakView == null) {
            Log.w(TAG, ":: offsetRowsByPage upperPageBreakView null");
            return;
        }
        int upperViewTop =
                getDecoratedTop(upperPageBreakView) - getParams(upperPageBreakView).topMargin;

        int scrollDistance = upperViewTop - anchorViewTop;

        int distanceLeft = anchorViewTop - getPaddingTop();
        float scrollPercentage =
                (Math.abs(scrollDistance) - distanceLeft) / (float) Math.abs(scrollDistance);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                    TAG,
                    String.format(
                            ":: offsetRowsByPage scrollDistance:%s, distanceLeft:%s,"
                                    + " scrollPercentage:%s",
                            scrollDistance, distanceLeft, scrollPercentage));
        }

        // Calculate the total amount that the view will need to scroll in order to go completely
        // off screen.
        RecyclerView rv = (RecyclerView) getChildAt(0).getParent();
        int[] locs = new int[2];
        rv.getLocationInWindow(locs);
        int listTopInWindow = locs[1] + rv.getPaddingTop();

        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            int position = getPosition(child);
            if (position < mUpperPageBreakPosition) {
                child.setAlpha(0f);
                setCardFlyingEffectOffset(child, -listTopInWindow);
            } else if (position < mAnchorPageBreakPosition) {
                // If the child has a negative margin, we need to offset the row by a little bit
                // extra so that it moves completely off screen.
                RecyclerView.LayoutParams params = getParams(child);
                int extraTranslation = 0;
                if (params.topMargin < 0) {
                    extraTranslation -= params.topMargin;
                }
                if (params.bottomMargin < 0) {
                    extraTranslation -= params.bottomMargin;
                }
                int translation =
                        (int)
                                ((listTopInWindow + extraTranslation)
                                        * mDanglingRowInterpolator.getInterpolation(
                                                scrollPercentage));
                child.setAlpha(1f);
                setCardFlyingEffectOffset(child, -translation);
            } else {
                child.setAlpha(1f);
                setCardFlyingEffectOffset(child, 0);
            }
        }
    }

    /**
     * Apply an offset to this view. This offset is applied post-layout so it doesn't affect when
     * views are recycled
     *
     * @param child The view to apply this to
     * @param verticalOffset The offset for this child.
     */
    private void setCardFlyingEffectOffset(View child, float verticalOffset) {
        // Ideally instead of doing all this, we could use View.setTranslationY(). However, the
        // default RecyclerView.ItemAnimator also uses method which causes layout issues.
        // See: http://b/25977087
        if (mFlyOffscreenAnimations.get(child) == null) {
            TranslateAnimation a = new TranslateAnimation();
            a.setFillEnabled(true);
            a.setFillAfter(true);
            a.setDuration(0);
            mFlyOffscreenAnimations.put(child, a);
        }
        final TranslateAnimation anim = mFlyOffscreenAnimations.get(child);

        anim.reset();
        anim.verticalOffset = verticalOffset;
        anim.setStartTime(Animation.START_ON_FIRST_FRAME);
        child.setAnimation(anim);
        anim.startNow();
    }

    /**
     * Update the page break positions based on the position of the views on screen. This should be
     * called whenever view move or change such as during a scroll or layout.
     */
    private void updatePageBreakPositions() {
        updatePageBreakPositions(/* forceUpdateBreakPosition= */ false);
    }

    private void updatePageBreakPositions(boolean forceUpdateBreakPosition) {
        if (getChildCount() == 0) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, ":: updatePageBreakPosition getChildCount: 0");
            }
            return;
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                    TAG,
                    String.format(
                            ":: #BEFORE updatePageBreakPositions "
                                    + "mAnchorPageBreakPosition:%s, mUpperPageBreakPosition:%s, "
                                    + "mLowerPageBreakPosition:%s",
                            mAnchorPageBreakPosition,
                            mUpperPageBreakPosition,
                            mLowerPageBreakPosition));
        }

        // If the item count has changed, our page boundaries may no longer be accurate. This will
        // force the page boundaries to reset around the current view that is closest to the top.
        if (getItemCount() != mItemCountDuringLastPageBreakUpdate || forceUpdateBreakPosition) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Item count changed. Resetting page break positions.");
            }
            mAnchorPageBreakPosition = getPosition(getFirstFullyVisibleChild());
            mUpperPageBreakPosition = -1;
        }
        mItemCountDuringLastPageBreakUpdate = getItemCount();

        if (mAnchorPageBreakPosition == -1) {
            Log.w(TAG, "Unable to update anchor positions. There is no initial anchor position.");
            return;
        }

        // Find the anchor view's y-coordinate, to know where it is located relative to the
        // viewport.
        Integer anchorTop = findViewTopByPosition(mAnchorPageBreakPosition);
        if (anchorTop == null) {
            Log.w(TAG, "Unable to update anchor positions. Anchor top coordinate not found.");
            return;
        }

        // Find the upper page break view's y-coordinate, to know where it is located relative to
        // the
        // viewport.
        int upperPageBreakTop;
        if (mUpperPageBreakPosition == -1) {
            // The upper page break is uninitialized; set the top to uppermost value to avoid
            // triggering
            // any page shifting logic below.
            upperPageBreakTop = Integer.MIN_VALUE;
        } else {
            Integer upperPageBreakTopBoxed = findViewTopByPosition(mUpperPageBreakPosition);
            if (upperPageBreakTopBoxed == null) {
                Log.w(
                        TAG,
                        "Unable to update anchor positions. Upper page top coordinate not found.");
                return;
            }
            upperPageBreakTop = upperPageBreakTopBoxed;
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                    TAG,
                    String.format(
                            ":: #MID updatePageBreakPositions anchorTop:%s upperPageBreakTop:%s"
                                    + " mAnchorPageBreakPosition:%s, mUpperPageBreakPosition:%s,"
                                    + " mLowerPageBreakPosition:%s",
                            anchorTop,
                            upperPageBreakTop,
                            mAnchorPageBreakPosition,
                            mUpperPageBreakPosition,
                            mLowerPageBreakPosition));
        }

        if (anchorTop < getPaddingTop()) {
            // The anchor has moved above the viewport. We are now on the next page. Shift the page
            // break positions and calculate a new lower one.
            mUpperPageBreakPosition = mAnchorPageBreakPosition;
            mAnchorPageBreakPosition = mLowerPageBreakPosition;
            mLowerPageBreakPosition = calculateNextPageBreakPosition(mAnchorPageBreakPosition);
        } else if (mAnchorPageBreakPosition > 0 && upperPageBreakTop >= getPaddingTop()) {
            // The upper page break view has moved into the viewport. We are now on the previous
            // page.
            // Shift the page break positions and calculate a new upper one.
            mLowerPageBreakPosition = mAnchorPageBreakPosition;
            mAnchorPageBreakPosition = mUpperPageBreakPosition;
            mUpperPageBreakPosition = calculatePreviousPageBreakPosition(mAnchorPageBreakPosition);
        } else {
            mUpperPageBreakPosition = calculatePreviousPageBreakPosition(mAnchorPageBreakPosition);
            mLowerPageBreakPosition = calculateNextPageBreakPosition(mAnchorPageBreakPosition);
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                    TAG,
                    String.format(
                            ":: #AFTER updatePageBreakPositions"
                                    + " mAnchorPageBreakPosition:%s, mUpperPageBreakPosition:%s,"
                                    + " mLowerPageBreakPosition:%s",
                            mAnchorPageBreakPosition,
                            mUpperPageBreakPosition,
                            mLowerPageBreakPosition));
        }
    }

    /**
     * Attempts to find the y-coordinate of the top of the view with the given {@code position}. If
     * the view is not attached, returns {@link Integer#MAX_VALUE} or {@link Integer#MIN_VALUE} to
     * indicate where the view is, relative to the attached children. If the view is not attached
     * and its relative position cannot be determined, returns {@code null}.
     */
    @Nullable
    private Integer findViewTopByPosition(int position) {
        if (position < 0) {
            return null;
        }
        View view = findViewByPosition(position);
        if (view != null) {
            return getDecoratedTop(view) - getParams(view).topMargin;
        }
        if (getChildCount() == 0) {
            Log.w(TAG, "View not attached, and no children exist.");
            return null;
        }
        int firstAttachedChildPosition = getPosition(getChildAt(0));
        int lastAttachedChildPosition = getPosition(getChildAt(getChildCount() - 1));
        if (position < firstAttachedChildPosition) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "View not attached, but is positioned above the first child.");
            }
            return Integer.MIN_VALUE;
        } else if (position > lastAttachedChildPosition) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "View not attached, but is positioned below the last child.");
            }
            return Integer.MAX_VALUE;
        }
        Log.e(TAG, "View not attached, but still positioned in range of children.");
        return null;
    }

    /**
     * Returns the position value among attached children which is closest to the given {@code
     * position} (possibly {@code position} itself, if the corresponding view is attached). If no
     * children are attached, returns {@code null}.
     */
    @Nullable
    private Integer findNearestAttachedPosition(int position) {
        if (getChildCount() == 0) {
            Log.w(TAG, "No children are attached, so no baseline position can be used.");
            return null;
        }
        int firstAttachedChildPosition = getPosition(getChildAt(0));
        int lastAttachedChildPosition = getPosition(getChildAt(getChildCount() - 1));
        if (position < firstAttachedChildPosition) {
            return firstAttachedChildPosition;
        } else if (position > lastAttachedChildPosition) {
            return lastAttachedChildPosition;
        }
        return position;
    }

    /**
     * @return The page break position of the page before the anchor page break position. However,
     *     if it reaches the end of the attached children or position 0, it will just return that.
     */
    @VisibleForTesting
    int calculatePreviousPageBreakPosition(int position) {
        if (position == -1) {
            return -1;
        }
        View referenceView = findViewByPosition(position);
        if (referenceView == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, ":: calculatePreviousPageBreakPosition referenceView is null");
            }
            Integer nearestPosition = findNearestAttachedPosition(position);
            if (nearestPosition == null) {
                Log.e(TAG, ":: calculatePreviousPageBreakPosition nearest position not found");
                return position;
            }
            return nearestPosition;
        }
        int referenceViewTop = getDecoratedTop(referenceView) - getParams(referenceView).topMargin;

        int previousPagePosition = position;
        while (previousPagePosition > 0) {
            previousPagePosition--;
            View child = findViewByPosition(previousPagePosition);
            if (child == null) {
                // View has not been laid out yet.
                return previousPagePosition + 1;
            }

            int childTop = getDecoratedTop(child) - getParams(child).topMargin;
            if (childTop < referenceViewTop - getHeight()) {
                // If choosing the next child causes the view to snap back to the referenceView
                // position,
                // use the previous child anyway. This avoids the case where tall children in the
                // layout
                // prevent scrolling.
                return previousPagePosition + 1 == position
                        ? previousPagePosition
                        : previousPagePosition + 1;
            }
        }
        // Beginning of the list.
        return 0;
    }

    /**
     * @return The page break position of the next page after the anchor page break position.
     *     However, if it reaches the end of the attached children or end of the list, it will just
     *     return that.
     */
    @VisibleForTesting
    int calculateNextPageBreakPosition(int position) {
        if (position == -1) {
            return -1;
        }

        View referenceView = findViewByPosition(position);
        if (referenceView == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, ":: calculatePreviousPageBreakPosition referenceView is null");
            }
            Integer nearestPosition = findNearestAttachedPosition(position);
            if (nearestPosition == null) {
                Log.e(TAG, ":: calculatePreviousPageBreakPosition nearest position not found");
                return position;
            }
            return nearestPosition;
        }
        int referenceViewTop = getDecoratedTop(referenceView) - getParams(referenceView).topMargin;

        int nextPagePosition = position;

        // Search for the first child item after the referenceView that didn't fully fit on to the
        // screen. The next page should start from the item before this child, so that users have
        // a visual anchoring point of the page change.
        while (nextPagePosition < getItemCount() - 1) {
            nextPagePosition++;
            View child = findViewByPosition(nextPagePosition);
            if (child == null) {
                // The next view has not been laid out yet.
                return nextPagePosition - 1;
            }

            int childTop = getDecoratedTop(child) - getParams(child).topMargin;
            if (childTop > referenceViewTop + getHeight()) {
                // If choosing the previous child causes the view to snap back to the referenceView
                // position, then skip that and go directly to the child. This avoids the case
                // where a tall card in the layout causes the view to constantly snap back to
                // the top when scrolled.
                return nextPagePosition - 1 == position ? nextPagePosition : nextPagePosition - 1;
            }
        }
        // End of the list.
        return nextPagePosition;
    }

    /**
     * Compares {@code firstVisibleChildPosition} against the current focus page positions to detect
     * whether a focus page change has occurred. If the page has changed, the page positions are
     * updated and the listener is informed.
     */
    private void updateFocusPageState(int firstVisibleChildPosition) {
        // If the recycler view doesn't have focus, reset focus page positions. This prevents
        // spurious
        // callbacks in hybrid cars, which can scroll in either touch or focus modes.
        if (!hasFocus()) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "updateFocusPageState clearing focus page positions");
            }
            clearFocusPagePositions();
            return;
        }

        if (mCurrentFocusPagePosition == -1 || firstVisibleChildPosition == -1) {
            // No baseline focused page, or no child to compare to.
            return;
        }

        // Calculate whether the currently focused page has changed. The implementation assumes that
        // focus changes occurring outside of scrollToPosition and
        // scrollToPositionWithQuickAnimation
        // are initiated by the user, but if the client code is redirecting focus within the list
        // outside those APIs, this assumption is invalid.
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                    TAG,
                    String.format(
                            ":: #BEFORE updateFocusPageState mCurrentFocusPagePosition=%d,"
                                    + " mPreviousFocusPagePosition=%d, mNextFocusPagePosition=%d",
                            mCurrentFocusPagePosition,
                            mPreviousFocusPagePosition,
                            mNextFocusPagePosition));
        }
        if (firstVisibleChildPosition <= mPreviousFocusPagePosition
                && mCurrentFocusPagePosition > mPreviousFocusPagePosition) {
            if (mOnScrollListener != null) {
                mOnScrollListener.onUserPagedUp(PagingUserAction.FOCUS_SCROLL);
            }
            setFocusPagePositions(firstVisibleChildPosition);
        } else if (firstVisibleChildPosition >= mNextFocusPagePosition
                && mCurrentFocusPagePosition < mNextFocusPagePosition) {
            if (mOnScrollListener != null) {
                mOnScrollListener.onUserPagedDown(PagingUserAction.FOCUS_SCROLL);
            }
            setFocusPagePositions(firstVisibleChildPosition);
        }
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                    TAG,
                    String.format(
                            ":: #AFTER updateFocusPageState mCurrentFocusPagePosition=%d,"
                                    + " mPreviousFocusPagePosition=%d, mNextFocusPagePosition=%d",
                            mCurrentFocusPagePosition,
                            mPreviousFocusPagePosition,
                            mNextFocusPagePosition));
        }
    }

    /** Orients the focus page positions around the given {@code anchorViewPosition}. */
    private void setFocusPagePositions(int anchorViewPosition) {
        mCurrentFocusPagePosition = anchorViewPosition;
        mPreviousFocusPagePosition = calculatePreviousPageBreakPosition(anchorViewPosition);
        // Note that if the anchor view is in the middle of the viewport and layoutExtraBottomPage
        // is
        // not set, calculateNextPageBreakPosition may return a value corresponding to a view less
        // than
        // a page away (since views on the next page may not be attached).
        mNextFocusPagePosition = calculateNextPageBreakPosition(anchorViewPosition);
    }

    private void clearFocusPagePositions() {
        mCurrentFocusPagePosition = -1;
        mPreviousFocusPagePosition = -1;
        mNextFocusPagePosition = -1;
    }

    /**
     * In this style, the focus will scroll down to the middle of the screen and lock there so that
     * moving in either direction will move the entire list by 1.
     */
    private boolean onRequestChildFocusMarioStyle(RecyclerView parent, View child) {
        int focusedPosition = getPosition(child);
        if (focusedPosition == mLastChildPositionToRequestFocus) {
            return true;
        }
        mLastChildPositionToRequestFocus = focusedPosition;

        int availableHeight = getAvailableHeight();
        int focusedChildTop = getDecoratedTop(child);
        int focusedChildBottom = getDecoratedBottom(child);

        int childIndex = parent.indexOfChild(child);
        // Iterate through children starting at the focused child to find the child above it to
        // smooth scroll to such that the focused child will be as close to the middle of the screen
        // as possible.
        for (int i = childIndex; i >= 0; i--) {
            View childAtI = getChildAt(i);
            if (childAtI == null) {
                Log.e(TAG, "Child is null at index " + i);
                continue;
            }
            // We haven't found a view that is more than half of the recycler view height above it
            // but we've reached the top so we can't go any further.
            if (i == 0) {
                parent.smoothScrollToPosition(getPosition(childAtI));
                break;
            }

            // Because we want to scroll to the first view that is less than half of the screen
            // away from the focused view, we "look ahead" one view. When the look ahead view
            // is more than availableHeight / 2 away, the current child at i is the one we want to
            // scroll to. However, sometimes, that view can be null (ie, if the view is in
            // transition). In that case, just skip that view.

            View childBefore = getChildAt(i - 1);
            if (childBefore == null) {
                continue;
            }
            int distanceToChildBeforeFromTop = focusedChildTop - getDecoratedTop(childBefore);
            int distanceToChildBeforeFromBottom = focusedChildBottom - getDecoratedTop(childBefore);

            if (distanceToChildBeforeFromTop > availableHeight / 2
                    || distanceToChildBeforeFromBottom > availableHeight) {
                parent.smoothScrollToPosition(getPosition(childAtI));
                break;
            }
        }
        return true;
    }

    /**
     * In this style, you can free scroll in the middle of the list but if you get to the edge, the
     * list will advance to ensure that there is context ahead of the focused item.
     */
    private boolean onRequestChildFocusSuperMarioStyle(
            RecyclerView parent, RecyclerView.State state, View child) {
        int focusedPosition = getPosition(child);
        if (focusedPosition == mLastChildPositionToRequestFocus) {
            return true;
        }
        mLastChildPositionToRequestFocus = focusedPosition;

        int bottomEdgeThatMustBeOnScreen;
        int focusedIndex = parent.indexOfChild(child);
        // The amount of the last card at the end that must be showing to count as visible.
        int peekAmount =
                mContext.getResources()
                        .getDimensionPixelSize(R.dimen.system_sdk_last_card_peek_amount);
        if (focusedPosition == state.getItemCount() - 1) {
            // The last item is focused.
            bottomEdgeThatMustBeOnScreen = getDecoratedBottom(child);
        } else if (focusedIndex == getChildCount() - 1) {
            // The last laid out item is focused. Scroll enough so that the next card has at least
            // the peek size visible
            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            // We add params.topMargin as an estimate because we don't actually know the top margin
            // of the next row.
            bottomEdgeThatMustBeOnScreen =
                    getDecoratedBottom(child) + params.bottomMargin + params.topMargin + peekAmount;
        } else {
            View nextChild = getChildAt(focusedIndex + 1);
            bottomEdgeThatMustBeOnScreen = getDecoratedTop(nextChild) + peekAmount;
        }

        if (bottomEdgeThatMustBeOnScreen > getHeight()) {
            // We're going to have to scroll because the bottom edge that must be on screen is past
            // the bottom.
            int topEdgeToFindViewUnder =
                    getPaddingTop() + bottomEdgeThatMustBeOnScreen - getHeight();

            View nextChild = null;
            for (int i = 0; i < getChildCount(); i++) {
                View potentialNextChild = getChildAt(i);
                RecyclerView.LayoutParams params = getParams(potentialNextChild);
                float top = getDecoratedTop(potentialNextChild) - params.topMargin;
                if (top >= topEdgeToFindViewUnder) {
                    nextChild = potentialNextChild;
                    break;
                }
            }

            if (nextChild == null) {
                Log.e(TAG, "There is no view under " + topEdgeToFindViewUnder);
                return true;
            }
            int nextChildPosition = getPosition(nextChild);
            parent.smoothScrollToPosition(nextChildPosition);
        } else {
            int firstFullyVisibleIndex = getFirstFullyVisibleChildIndex();
            if (focusedIndex <= firstFullyVisibleIndex) {
                parent.smoothScrollToPosition(Math.max(focusedPosition - 1, 0));
            }
        }
        return true;
    }

    /**
     * We don't actually know the size of every single view, only what is currently laid out. This
     * makes it difficult to do accurate scrollbar calculations. However, lists in the car often
     * consist of views with identical heights. Because of that, we can use a single sample view to
     * do our calculations for. The main exceptions are in the first items of a list (hero card,
     * last call card, etc) so if the first view is at position 0, we pick the next one.
     *
     * @return The decorated measured height of the sample view plus its margins.
     */
    private int getSampleViewHeight() {
        if (mSampleViewHeight != -1) {
            return mSampleViewHeight;
        }
        int sampleViewIndex = getFirstFullyVisibleChildIndex();
        View sampleView = getChildAt(sampleViewIndex);
        if (getPosition(sampleView) == 0 && sampleViewIndex < getChildCount() - 1) {
            sampleView = getChildAt(++sampleViewIndex);
        }
        RecyclerView.LayoutParams params = getParams(sampleView);
        int height =
                getDecoratedMeasuredHeight(sampleView) + params.topMargin + params.bottomMargin;
        if (height == 0) {
            // This can happen if the view isn't measured yet.
            Log.w(
                    TAG,
                    "The sample view has a height of 0. Returning a placeholder value for now "
                            + "that won't be cached.");
            height =
                    mContext.getResources()
                            .getDimensionPixelSize(R.dimen.system_sdk_sample_row_height);
        } else {
            mSampleViewHeight = height;
        }
        return height;
    }

    /** @return The height of the RecyclerView excluding padding. */
    private int getAvailableHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    /**
     * @return {@link RecyclerView.LayoutParams} for the given view or null if it isn't a child of
     *     {@link RecyclerView}.
     */
    private static RecyclerView.LayoutParams getParams(View view) {
        return (RecyclerView.LayoutParams) view.getLayoutParams();
    }

    // TODO(b/118895668): Replace with {@link View#isInTouchMode()} once it is reliable.
    private boolean hasFocusBasedNavigation() {
        return hasRotaryController() || hasTouchpadForNavigation();
    }

    private boolean hasRotaryController() {
        // R.bool.has_wheel implies the car has controller support (either hybrid or rotary)
        return mContext.getResources().getBoolean(R.bool.has_wheel);
    }

    private boolean hasTouchpadForNavigation() {
        return mContext.getResources().getConfiguration().navigation
                == Configuration.NAVIGATION_DPAD;
    }

    @Override
    public void onItemsMoved(RecyclerView recyclerView, int from, int to, int itemCount) {
        super.onItemsMoved(recyclerView, from, to, itemCount);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                    TAG,
                    String.format("onItemsMoved: from=%d to=%d itemCount=%d", from, to, itemCount));
        }

        retainFocusOnItemMoved(from, to, itemCount);
    }

    private void retainFocusOnItemMoved(int from, int to, int itemCount) {
        if (!mRetainFocusOnListUpdate) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "retainFocusOnItemMoved - not retaining focus because flag disabled");
            }
            return;
        }

        if (!hasFocus()) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(
                        TAG,
                        "retainFocusOnItemMoved - not retaining focus because list does not have"
                                + " focus");
            }
            return;
        }

        int focusedPosition = getFocusedItemPosition();
        int relativeFocusedPosition = focusedPosition - from;
        if (relativeFocusedPosition < 0 || relativeFocusedPosition >= itemCount) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(
                        TAG,
                        String.format(
                                "retainFocusOnItemMoved - not retaining focus because focused item"
                                        + " was not moved (position=%d)",
                                focusedPosition));
            }
            return;
        }

        int newFocusPosition = to + relativeFocusedPosition;
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(
                    TAG,
                    String.format(
                            "retainFocusOnItemMoved - retaining focus at new position %d",
                            newFocusPosition));
        }
        scrollToPosition(newFocusPosition);
    }

    /**
     * Custom {@link LinearSmoothScroller} that has: a) Custom control over the speed of scrolls. b)
     * Scrolling snaps to start. All of our scrolling logic depends on that. c) Keeps track of some
     * state of the current scroll so that can aid in things like the scrollbar calculations.
     */
    private final class CarSmoothScroller extends LinearSmoothScroller {
        private final int mTargetPosition;
        private final ScrollAnimationSpeed mScrollSpeed;
        private final boolean mHasTouch;
        private final Interpolator mInterpolator;

        CarSmoothScroller(Context context, int targetPosition, ScrollAnimationSpeed scrollSpeed) {
            super(context);
            mTargetPosition = targetPosition;
            mScrollSpeed = scrollSpeed;
            mHasTouch = mContext.getResources().getBoolean(R.bool.system_sdk_true_for_touch);
            mInterpolator =
                    new DecelerateInterpolator(
                            mScrollSpeed.getDecelerateInterpolatorEasingFactor());
        }

        @Override
        public PointF computeScrollVectorForPosition(int i) {
            if (getChildCount() == 0) {
                return null;
            }
            final int firstChildPos = getPosition(getChildAt(getFirstFullyVisibleChildIndex()));
            final int direction = (mTargetPosition < firstChildPos) ? -1 : 1;
            return new PointF(0, direction);
        }

        @Override
        protected int getVerticalSnapPreference() {
            // This is key for most of the scrolling logic that guarantees that scrolling
            // will settle with a view aligned to the top.
            return LinearSmoothScroller.SNAP_TO_START;
        }

        @Override
        protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
            if (mShouldRequestFocusOnScroll && hasFocusBasedNavigation()) {
                requestFocusOnFirstFocusableChildStartingAtTarget(targetView);
                mShouldRequestFocusOnScroll = false;
            }

            int dy = calculateDyToMakeVisible(targetView, SNAP_TO_START);
            if (dy == 0) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Scroll distance is 0");
                }
                return;
            }

            final int time = calculateTimeForDeceleration(dy);
            if (time > 0) {
                action.update(0, -dy, time, mInterpolator);
            }
        }

        @Override
        protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
            return mScrollSpeed.getLinearSpeedInMsPerInch() / displayMetrics.densityDpi;
        }

        @Override
        protected int calculateTimeForDeceleration(int dx) {
            int time =
                    (int)
                            Math.ceil(
                                    calculateTimeForScrolling(dx)
                                            / mScrollSpeed.getTimeDecelerationDivisor());
            return mHasTouch ? time : Math.min(time, mScrollSpeed.getNonTouchMaxDecelerationMs());
        }

        @Override
        public int getTargetPosition() {
            return mTargetPosition;
        }

        private void requestFocusOnFirstFocusableChildStartingAtTarget(View targetView) {
            if (getChildCount() == 0) {
                Log.w(TAG, "Smooth scroller used when no children are laid out");
                return;
            }
            RecyclerView parent = (RecyclerView) getChildAt(0).getParent();
            int targetIndex = parent.indexOfChild(targetView);
            View firstFocusableChild = getFirstFocusableChildStartingAtIndex(targetIndex);

            if (firstFocusableChild == null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(
                            TAG,
                            String.format(
                                    "No focusable children exist near %s, not requesting focus",
                                    targetView));
                }
                return;
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(
                        TAG,
                        String.format(
                                "Explicitly requesting focus on %s, targetView = %s",
                                firstFocusableChild, targetView));
            }
            firstFocusableChild.requestFocus();
        }
    }

    /**
     * Levels of speed at which a smooth scroll animation may occur. The values offered through its
     * methods were hand-tuned until the UX felt right.
     */
    private enum ScrollAnimationSpeed {
        STANDARD(150f, 0.45f, 1.8f),
        QUICK(20f, 0.0625f, 1.2f);

        private final float mLinearSpeedMsPerInch;
        private final float mTimeDecelerationDivisor;
        private final float mDecelerateInterpolatorEasingFactor;

        ScrollAnimationSpeed(
                float linearSpeedMsPerInch,
                float timeDecelerationDivisor,
                float decelerateInterpolatorEasingFactor) {
            mLinearSpeedMsPerInch = linearSpeedMsPerInch;
            mTimeDecelerationDivisor = timeDecelerationDivisor;
            mDecelerateInterpolatorEasingFactor = decelerateInterpolatorEasingFactor;
        }

        public float getLinearSpeedInMsPerInch() {
            return mLinearSpeedMsPerInch;
        }

        public float getTimeDecelerationDivisor() {
            return mTimeDecelerationDivisor;
        }

        public int getNonTouchMaxDecelerationMs() {
            return 1000;
        }

        public float getDecelerateInterpolatorEasingFactor() {
            return mDecelerateInterpolatorEasingFactor;
        }
    }

    /**
     * Animation that translates a view by the specified amount. Used for card flying off the screen
     * effect.
     */
    private static class TranslateAnimation extends Animation {
        public float verticalOffset;

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            super.applyTransformation(interpolatedTime, t);
            t.getMatrix().setTranslate(0, verticalOffset);
        }
    }
}
