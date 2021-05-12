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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.StyleRes;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.libraries.templates.host.R;

/**
 * Custom {@link androidx.recyclerview.widget.RecyclerView} that displays a list of items that
 * resembles a {@link android.widget.ListView} but also has page up and page down arrows on the
 * right side.
 *
 * <p>Please do not use {@link android.view.View#setTag(Object)} when using {@link
 * #setForceFieldType(ForceFieldType)} or setting the {@code forceFieldType} attribute as this could
 * interfere with the implementation. You may use {@link android.view.View#setTag(int, Object)}
 * instead.
 */
public class PagedListView extends FrameLayout {
    /** Default maximum number of clicks allowed on a list */
    public static final int DEFAULT_MAX_CLICKS = 6;

    /**
     * The amount of time after settling to wait before autoscrolling to the next page when the user
     * holds down a pagination button.
     */
    protected static final int PAGINATION_HOLD_DELAY_MS = 400;

    /**
     * The minimum number of pixels to drag and hold on touchpad to trigger continuous focus
     * movement.
     */
    private static final int TOUCHPAD_SCROLL_THRESHOLD_PX = 50;

    /** The minimum number of pixels to trigger movement in touchpad. */
    private static final int TOUCHPAD_MOVE_THRESHOLD_PX = 15;

    private static final String TAG = "PagedListView";

    protected final CarRecyclerView mRecyclerView;
    protected final CarLayoutManager mLayoutManager;
    protected final Handler mHandler = new Handler();
    private final boolean mScrollBarEnabled;
    private final boolean mRightGutterEnabled;
    private boolean mRequestFocusUsesMemory;
    private final PagedScrollBarView mScrollBarView;

    private int mRowsPerPage = -1;
    protected RecyclerView.Adapter<? extends RecyclerView.ViewHolder> mAdapter;
    /** Maximum number of pages to show. Values < 0 show all pages. */
    private int mMaxPages = -1;

    protected OnScrollListener mOnScrollListener;
    private boolean mHideScrollBar;

    private Decoration mDecor = new Decoration(getContext());
    /** Number of visible rows per page */
    private int mDefaultMaxPages = DEFAULT_MAX_CLICKS;
    /** Used to check if there are more items added to the list. */
    private int mLastItemCount = 0;

    private boolean mNeedsFocus;
    private float mTouchpadDownY;
    private float mTouchpadLastMoveY;
    private boolean mTouchpadFirstMove;
    private boolean mTouchpadCanScroll;

    /**
     * Interface for a {@link androidx.recyclerview.widget.RecyclerView.Adapter} to cap the number
     * of items.
     *
     * <p>NOTE: it is still up to the adapter to use maxItems in {@link
     * androidx.recyclerview.widget.RecyclerView.Adapter#getItemCount()}.
     *
     * <p>the recommended way would be with:
     *
     * <pre>{@code
     * {@literal@}Override
     * public int getItemCount() {
     *   return Math.min(super.getItemCount(), mMaxItems);
     * }
     * }</pre>
     */
    public interface ItemCap {
        /**
         * Sets the maximum number of items available in the adapter. A value less than '0' means
         * the list should not be capped.
         */
        void setMaxItems(int maxItems);
    }

    /**
     * Interface for a {@link androidx.recyclerview.widget.RecyclerView.Adapter} to set the position
     * offset for the adapter to load the data.
     *
     * <p>For example in the adapter, if the positionOffset is 20, then for position 0 it will show
     * the item in position 20 instead, for position 1 it will show the item in position 21 instead
     * and so on.
     */
    // TODO(b/28003781): ItemPositionOffset and ItemCap interfaces should be merged once
    // we enable AlphaJump outside drawer.
    public interface ItemPositionOffset {
        /** Sets the position offset for the adapter. */
        void setPositionOffset(int positionOffset);
    }

    public PagedListView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0 /*defStyleAttrs*/, 0 /*defStyleRes*/);
    }

    public PagedListView(
            Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttrs) {
        this(context, attrs, defStyleAttrs, 0 /*defStyleRes*/);
    }

    public PagedListView(
            Context context,
            @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttrs,
            @StyleRes int defStyleRes) {
        this(context, attrs, defStyleAttrs, defStyleRes, 0);
    }

    public PagedListView(
            Context context,
            @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttrs,
            @StyleRes int defStyleRes,
            int layoutId) {
        super(context, attrs, defStyleAttrs, defStyleRes);
        if (layoutId == 0) {
            layoutId = R.layout.system_sdk_paged_recycler_view;
        }
        LayoutInflater.from(context).inflate(layoutId, this /*root*/, true /*attachToRoot*/);

        FrameLayout maxWidthLayout = findViewById(R.id.max_width_layout);

        boolean fadeLastItem;
        boolean offsetRows;
        int forceFieldTypeInt;
        boolean scrollToPositionFocusRequestEnabled;
        boolean scrollToPositionFocusVerificationEnabled;
        boolean paginationButtonsVisibleOnNonTouch;
        int scrollBarWidthOverride;
        TypedArray a =
                context.obtainStyledAttributes(
                        attrs, R.styleable.PagedListView, defStyleAttrs, defStyleRes);
        try {
            fadeLastItem = a.getBoolean(R.styleable.PagedListView_fadeLastItem, false);
            offsetRows = a.getBoolean(R.styleable.PagedListView_offsetRows, false);
            mScrollBarEnabled = a.getBoolean(R.styleable.PagedListView_scrollBarEnabled, true);
            scrollBarWidthOverride =
                    a.getDimensionPixelSize(R.styleable.PagedListView_scrollBarWidthOverride, -1);
            mRightGutterEnabled = a.getBoolean(R.styleable.PagedListView_rightGutterEnabled, false);
            paginationButtonsVisibleOnNonTouch =
                    a.getBoolean(
                            R.styleable.PagedListView_paginationButtonsVisibleOnNonTouch, false);
            forceFieldTypeInt =
                    a.getInteger(
                            R.styleable.PagedListView_forceFieldType,
                            ForceFieldType.MULTIMOVE_MODAL.ordinal());
            scrollToPositionFocusRequestEnabled =
                    a.getBoolean(
                            R.styleable.PagedListView_scrollToPositionFocusRequestEnabled, true);
            scrollToPositionFocusVerificationEnabled =
                    a.getBoolean(
                            R.styleable.PagedListView_scrollToPositionFocusVerificationEnabled,
                            false);
        } finally {
            a.recycle();
        }

        ForceFieldType forceFieldType =
                ForceFieldType.fromInt(forceFieldTypeInt, ForceFieldType.NONE);
        if (forceFieldType != ForceFieldType.NONE) {
            FocusClusterBehavior.set(
                    this,
                    new FocusClusterBehavior.Builder()
                            .forceFieldBehavior(ForceFieldType.toBehavior(forceFieldType))
                            .build());
        }

        mRecyclerView = (CarRecyclerView) findViewById(R.id.recycler_view);
        mRecyclerView.setFadeLastItem(fadeLastItem);

        mMaxPages = getDefaultMaxPages();

        mLayoutManager = new CarLayoutManager(context);
        mLayoutManager.setOffsetRows(offsetRows);
        mLayoutManager.setScrollToPositionFocusRequestEnabled(scrollToPositionFocusRequestEnabled);
        mLayoutManager.setScrollToPositionFocusVerificationEnabled(
                scrollToPositionFocusVerificationEnabled);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(mDecor);
        mRecyclerView.setOnScrollListener(mRecyclerViewOnScrollListener);
        mRecyclerView.getRecycledViewPool().setMaxRecycledViews(0, 12);
        mRecyclerView.setItemAnimator(new CarItemAnimator(mLayoutManager));

        // Set this to true so that this view consumes clicks events and views underneath
        // don't receive this click event. Without this it's possible to click places in the
        // view that don't capture the event, and as a result, elements visually hidden consume
        // the event.
        setClickable(true);

        // Set focusable false explicitly to handle the behavior change in Android O where
        // clickable view becomes focusable by default.
        setFocusable(false);

        mScrollBarView = (PagedScrollBarView) findViewById(R.id.paged_scroll_view);
        mScrollBarView.setPaginationListener(
                new PagedScrollBarView.PaginationListener() {
                    @Override
                    public void onPaginate(int direction) {
                        if (direction == PagedScrollBarView.PaginationListener.PAGE_UP) {
                            mRecyclerView.pageUp();
                            if (mOnScrollListener != null) {
                                mOnScrollListener.onUserPagedUp(
                                        PagingUserAction.SCROLL_BUTTON_CLICK);
                            }
                        } else if (direction == PagedScrollBarView.PaginationListener.PAGE_DOWN) {
                            mRecyclerView.pageDown();
                            if (mOnScrollListener != null) {
                                mOnScrollListener.onUserPagedDown(
                                        PagingUserAction.SCROLL_BUTTON_CLICK);
                            }
                        } else {
                            Log.e(TAG, "Unknown pagination direction (" + direction + ")");
                        }
                    }
                });
        if (scrollBarWidthOverride >= 0) {
            mScrollBarView.getLayoutParams().width = scrollBarWidthOverride;
        }

        if (paginationButtonsVisibleOnNonTouch) {
            mScrollBarView.showPaginationButtonsOnNonTouch();
        }
        mScrollBarView.setVisibility(mScrollBarEnabled ? VISIBLE : GONE);

        // Modify the layout if the Gutter or the Scroll Bar are not visible.
        if (mRightGutterEnabled || !mScrollBarEnabled) {
            FrameLayout.LayoutParams maxWidthLayoutLayoutParams =
                    (FrameLayout.LayoutParams) maxWidthLayout.getLayoutParams();
            if (mRightGutterEnabled) {
                maxWidthLayoutLayoutParams.rightMargin =
                        getResources().getDimensionPixelSize(R.dimen.system_sdk_card_margin);
            }
            if (!mScrollBarEnabled) {
                maxWidthLayoutLayoutParams.setMarginStart(0);
            }
            maxWidthLayout.setLayoutParams(maxWidthLayoutLayoutParams);
        }

        setDayNightStyle(DayNightStyle.AUTO);
    }

    /** TODO(b/180361672): Add javadoc */
    public void setListViewStartEndPadding(@Px int start, @Px int end) {
        int carCardMargin = getResources().getDimensionPixelSize(R.dimen.system_sdk_card_margin);
        int startGutter = mScrollBarEnabled ? carCardMargin : 0;
        int startPadding = Math.max(start - startGutter, 0);
        int endGutter = mRightGutterEnabled ? carCardMargin : 0;
        int endPadding = Math.max(end - endGutter, 0);
        mRecyclerView.setPaddingRelative(
                startPadding,
                mRecyclerView.getPaddingTop(),
                endPadding,
                mRecyclerView.getPaddingBottom());
        // Since we're setting padding we'll need to set the clip to padding to the same
        // value as clip children to ensure that the cards fly off the screen.
        mRecyclerView.setClipToPadding(mRecyclerView.getClipChildren());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(mUpdatePaginationRunnable);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            // The user has interacted with the list using touch. All movements will now paginate
            // the list.
            mLayoutManager.setRowOffsetMode(CarLayoutManager.ROW_OFFSET_MODE_PAGE);
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        // The user has interacted with the list using the controller. Movements through the list
        // will now be one row at a time.
        mLayoutManager.setRowOffsetMode(CarLayoutManager.ROW_OFFSET_MODE_INDIVIDUAL);
    }

    /** TODO(b/180361672): Add javadoc */
    public int positionOf(@Nullable View v) {
        if (v == null || v.getParent() != mRecyclerView) {
            return -1;
        }
        return mLayoutManager.getPosition(v);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        Log.v(TAG, "onGenericMotionEvent");

        // Disable list scrolling for car which has touchscreen or rotary.
        // Currently, we also disable list scrolling for touchpad with ui navigation, however, there
        // is
        // a to-do below to provide a better list scrolling experience for touchpad in the future.
        if (getResources().getBoolean(R.bool.system_sdk_true_for_touch)
                || hasFocusBasedNavigation()) {
            return false;
        }

        // TODO(b/21812604): Update TouchpadListScroller to this behaviour,
        // then use it for  scrolling.
        int action = event.getAction();
        // Most events need to goto ProjectedPresentation.
        boolean handled = false;
        boolean changedDirection = false;
        if (action == MotionEvent.ACTION_MOVE) {
            // Distance since last down or change of direction.
            float dy = event.getY() - mTouchpadDownY;
            // Distance since last ACTION_MOVE.
            float dyCurrent = event.getY() - mTouchpadLastMoveY;
            // First event, figure out if we can scroll in the current direction.
            // If we can, all following events in this direction should be handled, so we can't
            // jump from bottom of the list to the rail in 1 swipe.
            if (mTouchpadFirstMove && dy != 0.0) {
                if (canScroll((int) Math.signum(dy))) {
                    mTouchpadCanScroll = true;
                }
                mTouchpadFirstMove = false;
            }
            // If the movement in Y axis is less than the threshold, then let ProjectedPresentation
            // handle it, or else let PagedListView handle it and consume the events. We don't
            // want Y axis focus movement to be triggered at both ProjectedPresentation and
            // PagedListView, because it will make extra movement.
            handled = (mTouchpadCanScroll && (Math.abs(dy) >= TOUCHPAD_MOVE_THRESHOLD_PX));
            // We want to scroll up/down every TOUCHPAD_SCROLL_THRESHOLD_PX
            // Scrolling in the same direction.
            if ((dy >= 0 && dyCurrent >= 0) || (dy <= 0 && dyCurrent <= 0)) {
                int stepsBefore =
                        (int)
                                ((mTouchpadLastMoveY - mTouchpadDownY)
                                        / TOUCHPAD_SCROLL_THRESHOLD_PX);
                int stepsNow = (int) (dy / TOUCHPAD_SCROLL_THRESHOLD_PX);
                // We've moved enough for a scroll.
                if (stepsNow != stepsBefore) {
                    scroll((int) Math.signum(dy));
                }
            } else {
                // User has changed scrolling directions
                changedDirection = true;
            }
            mTouchpadLastMoveY = event.getY();
        }

        // ACTION_DOWN and changing direction are very similar.
        if (changedDirection || action == MotionEvent.ACTION_DOWN) {
            mTouchpadDownY = event.getY();
            mTouchpadLastMoveY = event.getY();
            mTouchpadFirstMove = true;
            mTouchpadCanScroll = false;
            if (action == MotionEvent.ACTION_DOWN) {
                // Except we want ACTION_DOWN to goto ProjectedPresentation.
                handled = false;
            }
        }
        return handled;
    }

    private void scroll(int direction) {
        View focusedView = mRecyclerView.getFocusedChild();
        if (focusedView != null) {
            int position = mLayoutManager.getPosition(focusedView);
            int newPosition =
                    Math.max(Math.min(position + direction, mLayoutManager.getItemCount() - 1), 0);
            if (newPosition != position) {
                // newPosition/position are adapter positions.
                // Convert to layout position by subtracting adapter position of view at layout
                // position 0.
                View childAt =
                        mRecyclerView.getChildAt(
                                newPosition
                                        - mLayoutManager.getPosition(mLayoutManager.getChildAt(0)));
                if (childAt != null) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(
                                TAG,
                                String.format("Explicit Focus: scroll: requestFocus(%s)", childAt));
                    }
                    childAt.requestFocus();
                }
            }
        }
    }

    private boolean canScroll(int direction) {
        View focusedView = mRecyclerView.getFocusedChild();
        if (focusedView != null) {
            int position = mLayoutManager.getPosition(focusedView);
            int newPosition =
                    Math.max(Math.min(position + direction, mLayoutManager.getItemCount() - 1), 0);
            if (newPosition != position) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public CarRecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    /** Scrolls instantly to a given {@code position}. */
    public void scrollToPosition(int position) {
        mLayoutManager.scrollToPosition(position);

        // Sometimes #scrollToPosition doesn't change the scroll state so we need to make sure
        // the pagination arrows actually get updated. See b/15801119
        mHandler.post(mUpdatePaginationRunnable);
    }

    /**
     * Starts an animation to scroll smoothly to a given {@code position}.
     *
     * @hide
     */
    public void smoothScrollToPosition(int position) {
        mLayoutManager.smoothScrollToPositionWithQuickAnimation(position);
    }

    /**
     * Sets the adapter for the list.
     *
     * <p>It <em>must</em> implement {@link ItemCap}, otherwise, will throw an {@link
     * IllegalArgumentException}.
     */
    public void setAdapter(
            @NonNull RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter) {
        if (!(adapter instanceof ItemCap)) {
            throw new IllegalArgumentException(
                    "ERROR: adapter "
                            + "["
                            + adapter.getClass().getCanonicalName()
                            + "] MUST implement ItemCap");
        }

        mAdapter = adapter;
        mRecyclerView.setAdapter(adapter);
        updateMaxItems();
    }

    /** @hide */
    @NonNull
    public CarLayoutManager getLayoutManager() {
        return mLayoutManager;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public RecyclerView.Adapter<? extends RecyclerView.ViewHolder> getAdapter() {
        return mRecyclerView.getAdapter();
    }

    /** TODO(b/180361672): Add javadoc */
    public void setMaxPages(int maxPages) {
        mMaxPages = maxPages;
        updateMaxItems();
    }

    /** TODO(b/180361672): Add javadoc */
    public int getMaxPages() {
        return mMaxPages;
    }

    /**
     * Gets the number of rows per page. Default value of mRowsPerPage is -1. If the first child of
     * CarLayoutManager is null or the height of the first child is 0, it will return 1.
     */
    public int getRowsPerPage() {
        return mRowsPerPage;
    }

    /** TODO(b/180361672): Add javadoc */
    public void resetMaxPages() {
        mMaxPages = getDefaultMaxPages();
        updateMaxItems();
    }

    /**
     * @return The position of first visible child in the list. -1 will be returned if there is no
     *     child.
     */
    public int getFirstFullyVisibleChildPosition() {
        return mLayoutManager.getFirstFullyVisibleChildPosition();
    }

    /**
     * @return The position of last visible child in the list. -1 will be returned if there is no
     *     child.
     */
    public int getLastFullyVisibleChildPosition() {
        return mLayoutManager.getLastFullyVisibleChildPosition();
    }

    /** TODO(b/180361672): Add javadoc */
    public void setDefaultItemDecoration(Decoration decor) {
        removeDefaultItemDecoration();
        mDecor = decor;
        addItemDecoration(mDecor);
    }

    /** TODO(b/180361672): Add javadoc */
    public void removeDefaultItemDecoration() {
        mRecyclerView.removeItemDecoration(mDecor);
    }

    /** TODO(b/180361672): Add javadoc */
    public void addItemDecoration(@NonNull RecyclerView.ItemDecoration decor) {
        mRecyclerView.addItemDecoration(decor);
    }

    /** TODO(b/180361672): Add javadoc */
    public void removeItemDecoration(@NonNull RecyclerView.ItemDecoration decor) {
        mRecyclerView.removeItemDecoration(decor);
    }

    /** TODO(b/180361672): Add javadoc */
    public void addOnItemTouchListener(@NonNull RecyclerView.OnItemTouchListener touchListener) {
        mRecyclerView.addOnItemTouchListener(touchListener);
    }

    /** TODO(b/180361672): Add javadoc */
    public void removeOnItemTouchListener(@NonNull RecyclerView.OnItemTouchListener touchListener) {
        mRecyclerView.removeOnItemTouchListener(touchListener);
    }

    /** TODO(b/180361672): Add javadoc */
    public void setDayNightStyle(int dayNightStyle) {
        // Update the scrollbar
        mScrollBarView.setDayNightStyle(dayNightStyle);
        mDecor.updateDividerColor();
    }

    /** TODO(b/180361672): Add javadoc */
    @Nullable
    public RecyclerView.ViewHolder getLastViewHolder() {
        View lastFullyVisible = mLayoutManager.getLastFullyVisibleChild();
        if (lastFullyVisible == null) {
            return null;
        }
        int lastFullyVisibleAdapterPosition = mLayoutManager.getPosition(lastFullyVisible);
        RecyclerView.ViewHolder lastViewHolder =
                getRecyclerView()
                        .findViewHolderForAdapterPosition(lastFullyVisibleAdapterPosition + 1);
        // We want to get the very last ViewHolder in the list, even if it's only fully visible
        // If it doesn't exist, return the last fully visible ViewHolder.
        if (lastViewHolder == null) {
            lastViewHolder =
                    getRecyclerView()
                            .findViewHolderForAdapterPosition(lastFullyVisibleAdapterPosition);
        }
        return lastViewHolder;
    }

    /**
     * Sets auto day/night mode
     *
     * @deprecated use setDayNightStyle(DayNightStyle.AUTO)
     */
    @Deprecated
    public void setAutoDayNightMode() {
        setDayNightStyle(DayNightStyle.AUTO);
    }

    /**
     * Sets auto light mode
     *
     * @deprecated use setDayNightStyle(DayNightStyle.FORCE_NIGHT)
     */
    @Deprecated
    public void setLightMode() {
        setDayNightStyle(DayNightStyle.FORCE_NIGHT);
    }

    /**
     * Sets auto dark mode
     *
     * @deprecated use setDayNightStyle(DayNightStyle.FORCE_DAY)
     */
    @Deprecated
    public void setDarkMode() {
        setDayNightStyle(DayNightStyle.FORCE_DAY);
    }

    /** Get the type of force field applied to focus-based navigation in this layout. */
    public ForceFieldType getForceFieldType() {
        return ForceFieldType.fromBehavior(FocusClusterBehavior.getForceFieldBehavior(this));
    }

    /** Set the type of force field applied to focus-based navigation in this layout. */
    public void setForceFieldType(@NonNull ForceFieldType forceFieldType) {
        FocusClusterBehavior behavior = FocusClusterBehavior.get(this);
        FocusClusterBehavior.set(
                this,
                new FocusClusterBehavior.Builder(behavior)
                        .forceFieldBehavior(ForceFieldType.toBehavior(forceFieldType))
                        .build());
    }

    /** TODO(b/180361672): Add javadoc */
    public void setOnScrollListener(
            PagedListView this,
            @Nullable OnScrollListener listener) {
        mOnScrollListener = listener;
        mLayoutManager.setOnScrollListener(mOnScrollListener);
    }

    /** Returns the page the given position is on, starting with page 0. */
    public int getPage(int position) {
        if (mRowsPerPage == -1) {
            return -1;
        }
        if (mRowsPerPage == 0) {
            return 0;
        }
        return position / mRowsPerPage;
    }

    public void setDefaultMaxPages(int newDefault) {
        mDefaultMaxPages = newDefault;
    }

    /** Returns the default number of pages the list should have */
    protected int getDefaultMaxPages() {
        // assume list shown in response to a click, so, reduce number of clicks by one
        return mDefaultMaxPages - 1;
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // if a late item is added to the top of the layout after the layout is stabilized, causing
        // the former top item to be pushed to the 2nd page, the focus will still be on the former
        // top item. Since our car layout manager tries to scroll the viewport so that the focused
        // item is visible, the view port will be on the 2nd page. That means the newly added item
        // will not be visible, on the first page.

        // what we want to do is: if the formerly focused item is the first one in the list, any
        // item added above it will make the focus to move to the new first item.
        // if the focus is not on the formerly first item, then we don't need to do anything. Let
        // the layout manager do the job and scroll the viewport so the currently focused item
        // is visible.

        // we need to calculate whether we want to request focus here, before the super call,
        // because after the super call, the first born might be changed.
        // focusedChild = the child view which has focus
        View focusedChild = mLayoutManager.getFocusedChild();
        // firstBorn = the first child view
        View firstBorn = mLayoutManager.getChildAt(0);

        super.onLayout(changed, left, top, right, bottom);

        if (mAdapter != null) {
            int itemCount = mAdapter.getItemCount();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(
                        TAG,
                        String.format(
                                "onLayout hasFocus: %s, mLastItemCount: %s, itemCount: %s,"
                                        + " focusedChild: %s, firstBorn: %s, isInTouchMode: %s,"
                                        + " mNeedsFocus: %s",
                                hasFocus(),
                                mLastItemCount,
                                itemCount,
                                focusedChild,
                                firstBorn,
                                isInTouchMode(),
                                mNeedsFocus));
            }
            updateMaxItems();

            // This is a workaround for missing focus because isInTouchMode() is not always
            // returning the right value.
            // This is okay for the Engine release since focus is always showing.
            // However, in Tala and Fender, we want to show focus only when the user uses
            // hardware controllers, so we need to revisit this logic. b/22990605.
            if (mNeedsFocus && itemCount > 0) {
                if (focusedChild == null) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Explicit Focus: onLayout not empty: requestFocus()");
                    }
                    requestFocus();
                }
                mNeedsFocus = false;
            }

            // If firstBorn and focusedChild are both null, the list is empty and should not request
            // focus
            if (itemCount > mLastItemCount // number of children has increased since last layout
                    && focusedChild
                            == firstBorn // the child view with focus is not the first child view
                    && focusedChild != null // a child view has focus
                    && hasFocusBasedNavigation()) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(
                            TAG,
                            "Explicit Focus: onLayout + rotary + new items + firstBorn =="
                                    + " focusedChild: requestFocus()");
                }
                requestFocus();
            }

            mLastItemCount = itemCount;
        }
        // We need to update the scroll buttons after layout has happened.
        // Determining if a scrollbar is necessary requires looking at the layout of the child
        // views. Therefore, this determination can only be done after layout has happened.
        // Note: don't animate here to prevent b/26849677
        updatePaginationButtons(false /*animate*/);
    }

    /**
     * Sets whether {@link #requestFocus(int, Rect)} will land focus on the last focused child,
     * using memory stored by {@link CarLayoutManager}. If no memory exists or the last focused
     * child is offscreen, focus will land on the first fully visible child.
     *
     * @hide
     */
    public void setRequestFocusUsesMemory(boolean requestFocusUsesMemory) {
        mRequestFocusUsesMemory = requestFocusUsesMemory;
    }

    @Override
    public boolean requestFocus(int direction, Rect rect) {
        if (hasFocusBasedNavigation()) {
            mNeedsFocus = true;
        }
        return super.requestFocus(direction, rect);
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (mRequestFocusUsesMemory) {
            // TODO(b/140870704): ensure stale memory does not get used here
            View lastFocusedVisibleChild = mLayoutManager.getLastFocusedChildIfVisible();
            if (lastFocusedVisibleChild != null) {
                return lastFocusedVisibleChild.requestFocus(direction, previouslyFocusedRect);
            }
        }
        View firstVisibleChild = mLayoutManager.getFirstFullyVisibleChildWithFocusable();
        if (firstVisibleChild != null) {
            return firstVisibleChild.requestFocus(direction, previouslyFocusedRect);
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!hasFocus()) {
            return false;
        }
        if (mScrollBarView.doesActionButtonHaveFocus()) {
            if (scrollBarActionButtonOnKeyUp(keyCode)) {
                return true;
            }
        } else {
            return keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    && mScrollBarView.isActionButtonActive()
                    && mScrollBarView.requestFocusInActionButton(View.FOCUS_LEFT);
        }
        return false;
    }

    /** TODO(b/180361672): Add javadoc */
    public View findViewByPosition(int position) {
        return mLayoutManager.findViewByPosition(position);
    }

    /**
     * See {@link PagedScrollBarView#showActionButton}
     *
     * @hide
     */
    public void showScrollBarActionButton(View view) {
        mScrollBarView.showActionButton(view);
    }

    /**
     * See {@link PagedScrollBarView#showScrollMovingThumb}
     *
     * @hide
     */
    public void showScrollBarMovingThumb() {
        mScrollBarView.showScrollMovingThumb();
    }

    /**
     * Determines if scrollbar should be visible or not and shows/hides it accordingly. If this is
     * being called as a result of adapter changes, it should be called after the new layout has
     * been calculated because the method of determining scrollbar visibility uses the current
     * layout. If this is called after an adapter change but before the new layout, the visibility
     * determination may not be correct.
     *
     * @param animate True if the scrollbar should animate to its new position. False if no
     *     animation is used
     */
    protected void updatePaginationButtons(boolean animate) {
        if (!mScrollBarEnabled) {
            // Don't change the visibility of the ScrollBar unless it's enabled.
            return;
        }

        if ((mLayoutManager.isAtTop() && mLayoutManager.isAtBottom())
                || mLayoutManager.getItemCount() == 0
                || mHideScrollBar) {
            mScrollBarView.setVisibility(View.INVISIBLE);
        } else {
            mScrollBarView.setVisibility(View.VISIBLE);
        }
        mScrollBarView.setUpEnabled(shouldEnablePageUpButton());
        mScrollBarView.setDownEnabled(shouldEnablePageDownButton());

        mScrollBarView.setParameters(
                mRecyclerView.computeVerticalScrollRange(),
                mRecyclerView.computeVerticalScrollOffset(),
                mRecyclerView.computeVerticalScrollExtent(),
                animate);
        invalidate();
    }

    protected boolean shouldEnablePageUpButton() {
        return !mLayoutManager.isAtTop();
    }

    protected boolean shouldEnablePageDownButton() {
        return !mLayoutManager.isAtBottom();
    }

    @UiThread
    protected void updateMaxItems() {
        if (mAdapter == null) {
            return;
        }

        final int originalCount = mAdapter.getItemCount();
        updateRowsPerPage();
        ((ItemCap) mAdapter).setMaxItems(calculateMaxItemCount());
        final int newCount = mAdapter.getItemCount();
        if (newCount == originalCount) {
            return;
        }

        if (newCount < originalCount) {
            mAdapter.notifyItemRangeRemoved(newCount, originalCount - newCount);
        } else {
            mAdapter.notifyItemRangeInserted(originalCount, newCount - originalCount);
        }
    }

    protected int calculateMaxItemCount() {
        final View firstChild = mLayoutManager.getChildAt(0);
        if (firstChild == null || firstChild.getHeight() == 0) {
            return -1;
        } else {
            return (mMaxPages < 0) ? -1 : mRowsPerPage * mMaxPages;
        }
    }

    /**
     * Updates the rows number per current page, which is used for calculating how many items we
     * want to show.
     */
    protected void updateRowsPerPage() {
        final View firstChild = mLayoutManager.getChildAt(0);
        if (firstChild == null || firstChild.getHeight() == 0) {
            mRowsPerPage = 1;
        } else {
            mRowsPerPage = Math.max(1, (getHeight() - getPaddingTop()) / firstChild.getHeight());
        }
    }

    /**
     * Gets the scroll bar view. Test-only
     *
     * @hide
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public PagedScrollBarView getScrollBarView() {
        return mScrollBarView;
    }

    // TODO(b/118895668): Replace with {@link View#isInTouchMode()} once it is reliable.
    private boolean hasFocusBasedNavigation() {
        return hasRotaryController() || hasTouchpadForNavigation();
    }

    private boolean hasRotaryController() {
        return getResources().getBoolean(R.bool.has_wheel);
    }

    private boolean hasTouchpadForNavigation() {
        return getResources().getConfiguration().navigation == Configuration.NAVIGATION_DPAD;
    }

    private boolean scrollBarActionButtonOnKeyUp(int keyCode) {
        if (!mScrollBarView.isActionButtonActive() || !mScrollBarView.doesActionButtonHaveFocus()) {
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return requestFocus(View.FOCUS_RIGHT);
        }
        return false;
    }

    private final RecyclerView.OnScrollListener mRecyclerViewOnScrollListener =
            new RecyclerView.OnScrollListener() {

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (mOnScrollListener != null) {
                        mOnScrollListener.onScrolled(recyclerView, dx, dy);
                        if (!mLayoutManager.isAtTop() && mLayoutManager.isAtBottom()) {
                            mOnScrollListener.onReachBottom();
                        } else if (mLayoutManager.isAtTop() || !mLayoutManager.isAtBottom()) {
                            mOnScrollListener.onLeaveBottom();
                        }
                    }
                    updatePaginationButtons(false);
                }

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (mOnScrollListener != null) {
                        mOnScrollListener.onScrollStateChanged(recyclerView, newState);
                    }
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        mHandler.postDelayed(mPaginationRunnable, PAGINATION_HOLD_DELAY_MS);
                    }
                }
            };

    protected final Runnable mPaginationRunnable =
            new Runnable() {
                @Override
                public void run() {
                    boolean upPressed = mScrollBarView.isUpPressed();
                    boolean downPressed = mScrollBarView.isDownPressed();
                    if (upPressed && downPressed) {
                        // noop
                    } else if (upPressed) {
                        mRecyclerView.pageUp();
                    } else if (downPressed) {
                        mRecyclerView.pageDown();
                    }
                }
            };

    private final Runnable mUpdatePaginationRunnable =
            new Runnable() {
                @Override
                public void run() {
                    updatePaginationButtons(true /*animate*/);
                }
            };

    /** Used to listen for {@code PagedListView} scroll events. */
    public abstract static class OnScrollListener {
        /** Called when menu reaches the bottom */
        public void onReachBottom() {}

        /** Called when menu leaves the bottom */
        public void onLeaveBottom() {}

        /**
         * Called when RecyclerView.OnScrollListener#onScrolled is called. See
         * RecyclerView.OnScrollListener
         */
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {}

        /** See RecyclerView.OnScrollListener */
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {}

        /**
         * Called when a user action, indicated by {@code source}, has caused the previous page to
         * become the currently visible page.
         */
        public void onUserPagedUp(PagingUserAction source) {}

        /**
         * Called when a user action, indicated by {@code source}, has caused the next page to
         * become the currently visible page.
         */
        public void onUserPagedDown(PagingUserAction source) {}
    }

    /** User-initiated events which trigger page changes within the list view. */
    public enum PagingUserAction {
        GESTURE,
        SCROLL_BUTTON_CLICK,
        FOCUS_SCROLL
    }

    /** TODO(b/180361672): Add javadoc */
    public static class Decoration extends RecyclerView.ItemDecoration {
        protected final Paint mPaint;
        protected final int mDividerHeight;
        protected final Context mContext;
        private final boolean mShouldDrawFirstSeparator;

        public Decoration(Context context) {
            this(context, true);
        }

        public Decoration(Context context, boolean shouldDrawFirstSeparator) {
            mContext = context;
            mShouldDrawFirstSeparator = shouldDrawFirstSeparator;
            mPaint = new Paint();
            updateDividerColor();
            mDividerHeight =
                    mContext.getResources()
                            .getDimensionPixelSize(R.dimen.system_sdk_divider_height);
        }

        @Override
        public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
            final int left = getLeft(parent.getChildAt(0));
            final int right = parent.getWidth() - parent.getPaddingRight();
            int top;
            int bottom;

            if (mShouldDrawFirstSeparator) {
                c.drawRect(left, 0, right, mDividerHeight, mPaint);
            }

            final int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = parent.getChildAt(i);
                final RecyclerView.LayoutParams params =
                        (RecyclerView.LayoutParams) child.getLayoutParams();
                bottom = child.getBottom() - params.bottomMargin;
                top = bottom - mDividerHeight;
                if (top > 0) {
                    c.drawRect(left, top, right, bottom, mPaint);
                }
            }
        }

        /** Updates the list divider color which may have changed due to a day night transition. */
        public void updateDividerColor() {
            mPaint.setColor(mContext.getResources().getColor(R.color.system_sdk_list_divider));
        }

        /**
         * Find the left edge of the decoration line. It should be left aligned with the left edge
         * of the first {@link TextView}.
         */
        protected int getLeft(View root) {
            if (root == null) {
                return 0;
            }
            View view = findTextView(root);
            if (view == null) {
                view = root;
            }
            int left = 0;
            while (view != null && view != root) {
                left += view.getLeft();
                view = (View) view.getParent();
            }
            return left;
        }

        private TextView findTextView(View root) {
            if (root == null) {
                return null;
            }
            if (root instanceof TextView) {
                return (TextView) root;
            }
            if (root instanceof ViewGroup) {
                ViewGroup parent = (ViewGroup) root;
                final int childCount = parent.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    TextView tv = findTextView(parent.getChildAt(i));
                    if (tv != null) {
                        return tv;
                    }
                }
            }
            return null;
        }
    }
}
