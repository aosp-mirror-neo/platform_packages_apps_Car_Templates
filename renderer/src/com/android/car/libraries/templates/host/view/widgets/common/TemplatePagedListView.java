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
package com.android.car.libraries.templates.host.view.widgets.common;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static java.lang.Math.max;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.car.app.model.OnItemVisibilityChangedDelegate;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.view.widgets.ui.DayNightStyle;
import com.android.car.libraries.templates.host.view.widgets.ui.MaxWidthLayout;
import com.android.car.libraries.templates.host.view.widgets.ui.PagedListView;
import com.android.car.libraries.templates.host.view.widgets.ui.PagedScrollBarView;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * An abstract subclass of the {@link PagedListView} with some custom behaviors for template apps.
 *
 * <p>Borrows heavily from j/c/g/a/apps/gmm/car/views/RhsPagedListView.java for the scrollbar
 * manipulation.
 */
public abstract class TemplatePagedListView extends PagedListView {
    private static final int MSG_HANDLE_VISIBLE_ROWS_CHANGE = 1;
    private static final int HANDLE_ROW_CHANGE_DELAY_MILLIS = 150;
    private static final int INVALID_ROW_INDEX = Integer.MIN_VALUE;

    /** An observer that updates our cached places when the adapter data changes. */
    private final AdapterDataObserver mAdapterDataObserver =
            new AdapterDataObserver() {
                // call to handleVisibleRowsChange() not allowed on the given receiver.
                @SuppressWarnings("nullness:method.invocation.invalid")
                @Override
                public void onChanged() {
                    super.onChanged();
                    handleVisibleRowsChange();
                }
            };

    /** A reference to the scroll bar view of the underlying {@link PagedListView}. */
    private final PagedScrollBarView mScrollBar;

    /**
     * A reference to the "max width layout" child of the underlying {@link PagedListView}. This is
     * the layout that is responsible for using margins in order to shift the contents of the list
     * to make way for the scroll bar.
     */
    private final MaxWidthLayout mMaxWidthLayout;

    private final Handler mHandler1 = new Handler(new HandlerCallback());
    private final int mScrollBarWidth;
    private final int mNoScrollBarStartPadding;
    private final boolean mShowScrollBarDivider;
    private final float mListWidthFraction;
    private final int mListMaxWidth;
    @Nullable private OnItemVisibilityChangedDelegate mOnItemVisibilityChangedDelegate;
    @Nullable private TemplateContext mTemplateContext;

    private boolean mIsScrollBarVisible;
    private int mLastVisibleStartRowIndex;
    private int mLastVisibleEndRowIndex;

    protected TemplatePagedListView(Context context) {
        this(context, null);
    }

    protected TemplatePagedListView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    protected TemplatePagedListView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressWarnings({"ResourceType", "method.invocation.invalid", "argument.type.incompatible"})
    protected TemplatePagedListView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttrs, int defStyleRes) {
        super(context, attrs, defStyleAttrs, defStyleRes);

        TypedArray ta =
                context.obtainStyledAttributes(
                        attrs, R.styleable.TemplatePagedListView, defStyleAttrs, 0);
        mListWidthFraction = ta.getFloat(R.styleable.TemplatePagedListView_listWidthFraction, 0.f);
        mListMaxWidth = ta.getDimensionPixelSize(R.styleable.TemplatePagedListView_listMaxWidth, 0);
        mScrollBarWidth =
                ta.getDimensionPixelSize(R.styleable.TemplatePagedListView_listScrollbarWidth, 0);
        mNoScrollBarStartPadding =
                ta.getDimensionPixelSize(
                        R.styleable.TemplatePagedListView_listNoScrollBarStartPadding, 0);
        mShowScrollBarDivider =
                ta.getBoolean(R.styleable.TemplatePagedListView_listShowScrollbarDivider, false);
        ta.recycle();

        mScrollBar = findViewById(R.id.paged_scroll_view);
        removeDefaultItemDecoration();

        mMaxWidthLayout = findViewById(R.id.max_width_layout);
        mMaxWidthLayout.setMaxChildrenWidth(0);

        initScrollbar();
        updateMaxWidthLayout();
        updateAdaptiveListWidth();

        // Listen to scroll events and publish the new places, based on what's visible in the list.
        getRecyclerView()
                .addOnScrollListener(
                        new RecyclerView.OnScrollListener() {
                            @Override
                            public void onScrollStateChanged(
                                    RecyclerView recyclerView, int newState) {
                                super.onScrollStateChanged(recyclerView, newState);
                                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                    handleVisibleRowsChange();
                                }
                            }
                        });

        // When the view requests focus, focus on the last focused row.
        setRequestFocusUsesMemory(true);

        // Do not count the top and bottom margins when determining the first and last fully visible
        // children. See b/160884435 for more context.
        getLayoutManager().setIncludeMarginInFullyVisibleChildCalculation(false);
    }

    @Override
    public void setAdapter(@NonNull Adapter<? extends ViewHolder> adapter) {
        // Register an observer to the adapter which we will use to update the published places
        // when the list data changes.
        Adapter<?> currentAdapter = getAdapter();
        if (currentAdapter != null) {
            currentAdapter.unregisterAdapterDataObserver(mAdapterDataObserver);
        }
        adapter.registerAdapterDataObserver(mAdapterDataObserver);

        super.setAdapter(adapter);
    }

    protected int getScrollbarWidth() {
        return mScrollBarWidth;
    }

    void setOnItemVisibilityChangedDelegate(
            TemplateContext templateContext,
            @Nullable OnItemVisibilityChangedDelegate onItemVisibilityChangedDelegate) {
        mTemplateContext = templateContext;
        mOnItemVisibilityChangedDelegate = onItemVisibilityChangedDelegate;

        // Reset the cached start/end indices, so that the newly-set listener will be invoked even
        // if
        // the visible rows might not have changed.
        mLastVisibleStartRowIndex = INVALID_ROW_INDEX;
        mLastVisibleEndRowIndex = INVALID_ROW_INDEX;
    }

    @Override
    protected void updatePaginationButtons(boolean animate) {
        super.updatePaginationButtons(animate);

        boolean isScrollBarVisible = mScrollBar.getVisibility() == View.VISIBLE;
        if (!isScrollBarVisible) {
            mScrollBar.setVisibility(View.GONE);
        }

        if (mIsScrollBarVisible == isScrollBarVisible) {
            return;
        }

        // The scroll bar visibility has changed, so the margins of the max width layout will need
        // to be
        // updated (either to add or remove the space for the scroll bar).
        mIsScrollBarVisible = isScrollBarVisible;

        updateMaxWidthLayout();
        updateAdaptiveListWidth();

        // If we're currently doing a layout pass, the updated margins might not get applied until
        // the
        // next layout pass, so in that case schedule another layout to happen after the current one
        // finishes.
        if (mMaxWidthLayout.isInLayout()) {
            mMaxWidthLayout.post(mMaxWidthLayout::requestLayout);
        }
    }

    /** Returns the set of rows that are currently visible in the list. */
    protected abstract ImmutableList<Object> getVisibleRows();

    /** Returns the index of the provided row object. */
    protected abstract int getRowIndex(Object row);

    /** Publish any associated metadata from the list of {@code rowWrappers}. */
    protected abstract void publishMetadata(
            @Nullable TemplateContext templateContext, List<Object> rowWrappers);

    private void updateMaxWidthLayout() {
        LayoutParams layoutParams = (LayoutParams) mMaxWidthLayout.getLayoutParams();
        layoutParams.setMarginStart(mIsScrollBarVisible ? mScrollBarWidth : 0);
        layoutParams.setMarginEnd(0);
        layoutParams.width = MATCH_PARENT;
        mMaxWidthLayout.setLayoutParams(layoutParams);
    }

    /**
     * Sets horizontal padding symmetrically around the list view so that the list occupies {@param
     * screenWidthFraction} times screen width, unless that value is larger than {@code maxWidth}
     * px, in which case the width will be capped at that value. Padding may also be set larger if
     * it would not be enough to cover the scroll bar and right gutter (see {@link
     * PagedListView#setListViewStartEndPadding(int, int)}.
     */
    private void updateAdaptiveListWidth() {
        if (mListWidthFraction < 0.f) {
            // A negative value means the list does not use an adaptive width.
            // If the scroll bar is not visible, add additional padding to the left.
            int startPadding = mIsScrollBarVisible ? 0 : mNoScrollBarStartPadding;
            mRecyclerView.setPaddingRelative(
                    startPadding,
                    mRecyclerView.getPaddingTop(),
                    mRecyclerView.getPaddingEnd(),
                    mRecyclerView.getPaddingBottom());
            return;
        }

        float paddingFraction = (1f - mListWidthFraction) / 2;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int padding = max((int) (screenWidth * paddingFraction), (screenWidth - mListMaxWidth) / 2);

        int startGutter = mIsScrollBarVisible ? mScrollBarWidth : 0;
        int startPadding = max(padding - startGutter, 0);

        mRecyclerView.setPaddingRelative(
                startPadding,
                mRecyclerView.getPaddingTop(),
                padding,
                mRecyclerView.getPaddingBottom());

        // Since we're setting padding we'll need to set the clip to padding to the same
        // value as clip children to ensure that the edge effects get resized to the padded section.
        mRecyclerView.setClipToPadding(mRecyclerView.getClipChildren());
    }

    @SuppressWarnings({"ResourceType"})
    private void initScrollbar() {
        Context context = getContext();

        // Add a divider to the right of the scrollbar, if needed.
        if (mShowScrollBarDivider) {
            // Read the divider attributes from the theme.
            @StyleableRes
            final int[] themeAttrs = {
                R.attr.templateDividerThickness, R.attr.templateDividerColor,
            };

            TypedArray ta = context.obtainStyledAttributes(themeAttrs);
            int dividerWidth = ta.getDimensionPixelSize(0, -1);
            @ColorInt int dividerColor = ta.getColor(1, -1);
            ta.recycle();

            View divider = new View(context);
            LayoutParams params = new LayoutParams(dividerWidth, MATCH_PARENT);
            params.gravity = Gravity.END;
            divider.setLayoutParams(params);
            divider.setBackgroundColor(dividerColor);
            mScrollBar.addView(divider);
        }

        // By default, the scroll bar has padding values which are also applied to the newly added
        // divider. We don't want this. As a workaround, we remove the padding from the scroll bar
        // view
        // and apply the padding to its child layout (at index 0).
        mScrollBar
                .getChildAt(0)
                .setPadding(0, mScrollBar.getPaddingTop(), 0, mScrollBar.getPaddingBottom());
        mScrollBar.setPadding(mScrollBar.getPaddingLeft(), 0, mScrollBar.getPaddingRight(), 0);

        // Change the icons and tint for the scrollbar up and down buttons.
        Resources resources = context.getResources();

        ImageView upButton = findViewById(R.id.page_up);
        upButton.setImageDrawable(
                resources.getDrawable(R.drawable.quantum_gm_ic_keyboard_arrow_up_white_36));

        ImageView downButton = findViewById(R.id.page_down);
        downButton.setImageDrawable(
                resources.getDrawable(R.drawable.quantum_gm_ic_keyboard_arrow_down_white_36));

        setDayNightStyle(DayNightStyle.FORCE_NIGHT);

        // Change the width of the scrollbar.
        LayoutParams scrollBarLayoutParams = (LayoutParams) mScrollBar.getLayoutParams();
        scrollBarLayoutParams.width = mScrollBarWidth;
        mScrollBar.setLayoutParams(scrollBarLayoutParams);
        mScrollBar.setVisibility(View.GONE);
    }

    /** Publishes the current set of visible rows to various listeners. */
    protected void handleVisibleRowsChange() {
        List<Object> rows = getVisibleRows();

        mHandler1.removeMessages(MSG_HANDLE_VISIBLE_ROWS_CHANGE);
        Message message = mHandler1.obtainMessage(MSG_HANDLE_VISIBLE_ROWS_CHANGE);
        message.obj = rows;
        if (rows.isEmpty()) {
            // When a full data refresh happens in the adapter that backs the paged list view, the
            // view
            // reports no visible rows first for a few milliseconds, and then reports the new
            // updated
            // rows.
            // This ephemeral state of emptiness can cause flickering for the views that listen to
            // the
            // published locations (e.g. the map view which clears and renders pins in the map).
            // This is hack to work around that behavior by adding a little bit of buffer before we
            // publish in this particular case.
            mHandler1.sendMessageDelayed(message, HANDLE_ROW_CHANGE_DELAY_MILLIS);
        } else {
            mHandler1.sendMessage(message);
        }
    }

    /** A {@link Handler.Callback} used to process the message queue for the view. */
    private class HandlerCallback implements Handler.Callback {
        @Override
        @SuppressWarnings("unchecked")
        public boolean handleMessage(Message msg) {
            if (msg.what == MSG_HANDLE_VISIBLE_ROWS_CHANGE) {
                List<Object> rowWrappers = (List<Object>) msg.obj;
                L.v(LogTags.TEMPLATE, "Handling %d visible rows", rowWrappers.size());

                publishMetadata(mTemplateContext, rowWrappers);
                dispatchRowVisibilityChangeEvent(rowWrappers);

                return true;
            } else {
                L.w(LogTags.TEMPLATE, "Unknown message: %s", msg);
            }
            return false;
        }

        /** Dispatch the index range of the currently visible rows to the client. */
        private void dispatchRowVisibilityChangeEvent(List<Object> visibleRows) {
            TemplateContext context = mTemplateContext;
            OnItemVisibilityChangedDelegate delegate = mOnItemVisibilityChangedDelegate;
            if (delegate == null || context == null) {
                return;
            }

            // We disallow hooking up the listener on sublists from the client, so here we assume
            // that all
            // the rows belong to the same ItemList with the same listener.
            // The indices default to -1 if the visible row list is empty, to inform the client that
            // no
            // rows are visible.
            int startIndex = visibleRows.isEmpty() ? -1 : getRowIndex(visibleRows.get(0));
            int endIndex = startIndex + visibleRows.size();

            if (startIndex != mLastVisibleStartRowIndex || endIndex != mLastVisibleEndRowIndex) {
                context.getAppDispatcher()
                        .dispatchItemVisibilityChanged(delegate, startIndex, endIndex);

                mLastVisibleStartRowIndex = startIndex;
                mLastVisibleEndRowIndex = endIndex;
            }
        }
    }
}
