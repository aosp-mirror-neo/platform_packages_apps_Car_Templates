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

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.annotation.VisibleForTesting;
import androidx.car.app.model.CarText;
import androidx.car.app.model.OnCheckedChangeDelegate;
import androidx.car.app.model.OnClickDelegate;
import androidx.car.app.model.OnItemVisibilityChangedDelegate;
import androidx.car.app.model.Place;
import androidx.car.app.model.Row;
import androidx.car.app.model.Toggle;
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver;
import androidx.recyclerview.widget.RecyclerView.LayoutManager;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.distraction.constraints.RowListConstraints;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.apphost.template.view.model.RowListWrapper;
import com.android.car.libraries.apphost.template.view.model.RowWrapper;
import com.android.car.libraries.apphost.template.view.model.SelectionGroup;
import com.android.car.libraries.apphost.view.common.CarTextUtils;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.view.widgets.common.MarkerFactory.MarkerAppearance;
import com.android.car.libraries.templates.host.view.widgets.common.RowHolder.RowListener;
import com.android.car.ui.recyclerview.CarUiRecyclerView;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

/**
 * A view that can render a list of {@link androidx.car.app.model.Row} (wrapped inside a {@link
 * RowListWrapper}.
 */
public class RowListView extends FrameLayout {
    private final AdapterDataObserver mAdapterDataObserver =
            new AdapterDataObserver() {
                // call to update() not allowed on the given receiver.
                @SuppressWarnings("nullness:method.invocation.invalid")
                @Override
                public void onChanged() {
                    super.onChanged();
                    update();
                }
            };

    private MarkerFactory mMarkerFactory;
    private RowAdapter mListAdapter;
    private RowVisibilityObserver mRowVisibilityObserver;
    private ViewGroup mProgressContainer;
    private TextView mEmptyListTextView;
    private CarUiRecyclerView mListView;
    private RowListWrapper mRowList;
    private boolean mIsLoading;
    private final int mHalfListBottomPadding;
    private final int mFullListBottomPadding;

    public RowListView(Context context) {
        this(context, null);
    }

    public RowListView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RowListView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);

        mMarkerFactory =
                MarkerFactory.create(
                        context, new MarkerAppearance(context, attrs, defStyleAttr, defStyleAttr));
    }

    @SuppressWarnings({"ResourceType", "method.invocation.invalid", "argument.type.incompatible"})
    public RowListView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        @StyleableRes
        final int[] themeAttrs = {
            R.attr.templateHalfListBottomPadding, R.attr.templateFullListBottomPadding,
        };
        TypedArray ta = context.obtainStyledAttributes(themeAttrs);
        mHalfListBottomPadding = ta.getDimensionPixelSize(0, 0);
        mFullListBottomPadding = ta.getDimensionPixelSize(1, 0);
        ta.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mProgressContainer = findViewById(R.id.progress_container);
        mEmptyListTextView = findViewById(R.id.list_no_items_text);
        mListView = findViewById(R.id.list_view);
        mRowVisibilityObserver = RowVisibilityObserver.create(mListView.getRecyclerView());
        mListAdapter = RowAdapter.create(getContext(), new ArrayList<>(), mMarkerFactory);
        mListView.setAdapter(mListAdapter);
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        update();
    }

    @Nullable
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public LayoutManager getLayoutManager() {
        return mListView.getLayoutManager();
    }

    @Nullable
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public RowAdapter getAdapter() {
        return mListAdapter;
    }

    void setRowList(TemplateContext templateContext, RowListWrapper rowList) {
        mRowList = rowList;

        boolean isLoading = rowList.isLoading();
        if (mIsLoading != isLoading) {
            // Trigger a visibility update if the loading state has changed.
            mIsLoading = isLoading;
            update();

            if (mIsLoading) {
                // Scroll to the top so we will show the first row when the loading finishes.
                mListView.scrollToPosition(0);

                // Do not update the listView/listAdapater, as we want to maintain the
                // list size during the loading phase until the new content is populated.
                return;
            }
        }

        CarText emptyListCarText = rowList.getEmptyListText();
        CharSequence emptyText;
        if (emptyListCarText != null && !emptyListCarText.isEmpty()) {
            emptyText =
                    CarTextUtils.toCharSequenceOrEmpty(templateContext, rowList.getEmptyListText());
        } else {
            emptyText = getContext().getString(R.string.template_list_no_items);
        }
        mEmptyListTextView.setText(emptyText);

        RowListConstraints constraints = rowList.getRowListConstraints();
        List<RowHolder> rowHolders =
                RowHolder.createHolders(templateContext, rowList.getRowWrappers(), constraints);

        updateBottomPadding();

        mRowVisibilityObserver.setOnItemVisibilityChangedListener(
                (startIndexInclusive, endIndexExclusive) -> {
                    OnItemVisibilityChangedDelegate onItemVisibilityChangedDelegate =
                            rowList.getOnItemVisibilityChangedDelegate();
                    if (onItemVisibilityChangedDelegate != null) {
                        templateContext
                                .getAppDispatcher()
                                .dispatchItemVisibilityChanged(
                                        onItemVisibilityChangedDelegate,
                                        startIndexInclusive,
                                        endIndexExclusive);
                    }
                    publishMetadata(
                            templateContext,
                            rowList.getRowWrappers(),
                            startIndexInclusive,
                            endIndexExclusive);
                });

        mListAdapter.setRows(
                templateContext,
                rowHolders,
                new RowListener() {
                    @Override
                    public void onRowClicked(int index) {
                        // TODO(b/179828022): Fix Telemetry
                        // logCarAppTelemetry(
                        //     createCarAppTelemetryEvent(
                        //             UiAction.ROW_CLICKED,
                        //             templateContext.getCarAppPackageInfo().getComponentName())
                        //         .setPosition(index));

                        onRowSelected(templateContext, index, /* clicked= */ true);
                    }

                    @Override
                    public void onCheckedChange(int index) {
                        // TODO(b/179828022): Fix Telemetry
                        // logCarAppTelemetry(
                        //     createCarAppTelemetryEvent(
                        //             UiAction.ROW_CLICKED,
                        //             templateContext.getCarAppPackageInfo().getComponentName())
                        //         .setPosition(index));

                        maybeSwitchToggleState(templateContext, index);
                    }

                    @Override
                    public void onRowFocusChanged(int index, boolean hasFocus) {
                        RowListView.this.onRowFocused(templateContext, index, hasFocus);
                    }
                });

        if (!rowList.isRefresh()) {
            mListView.scrollToPosition(0);
        }

        // TODO(b/179828022): Fix Telemetry
        // logCarAppTelemetry(
        //     createCarAppTelemetryEvent(
        //             UiAction.LIST_SIZE,
        // templateContext.getCarAppPackageInfo().getComponentName())
        //         .setItemsLoadedCount(rowHolders.size()));
    }

    private void onRowSelected(TemplateContext templateContext, int newIndex, boolean clicked) {
        RowWrapper rowWrapper = mRowList.getRowWrappers().get(newIndex);

        if (rowWrapper.getRow() instanceof Row) {
            Row row = (Row) rowWrapper.getRow();
            final OnClickDelegate onClickDelegate = row.getOnClickDelegate();
            if (onClickDelegate != null) {
                templateContext.getAppDispatcher().dispatchClick(onClickDelegate);
            }
        }

        SelectionGroup selectionGroup = rowWrapper.getSelectionGroup();

        // If the row belongs to a selection group, change the selection in the group if necessary.
        // This is done here in the host without having to do a round-trip to the client to change
        // the
        // model and re-fresh the entire list, which is much faster and more convenient for apps.
        if (selectionGroup != null) {
            int currentSelectionIndex = selectionGroup.getSelectedIndex();

            // If the selected index changed, deselect the previously selected row, and select the
            // new
            // one.
            boolean isRowPreviouslySelected = currentSelectionIndex == newIndex;
            if (!isRowPreviouslySelected) {
                // Store the new selection. This is important also in case the rows get re-created
                // during recycling so that they maintain the proper state.
                selectionGroup.setSelectedIndex(newIndex);

                mListAdapter.updateRow(currentSelectionIndex);
                mListAdapter.updateRow(newIndex);

                boolean shouldScrollToSelectedRow =
                        (mRowList.getListFlags()
                                        & RowListWrapper.LIST_FLAGS_SELECTABLE_SCROLL_TO_ROW)
                                != 0;
                if (shouldScrollToSelectedRow) {
                    // Post to the main thread so that the scroll happens after the UI changes for
                    // the selected state is completed.
                    post(() -> mListView.smoothScrollToPosition(newIndex));
                }
            }

            // Dispatch the selection callbacks.
            // Note the selection event is dispatched regardless of selection index actually
            // changing.
            templateContext
                    .getAppDispatcher()
                    .dispatchSelected(
                            selectionGroup.getOnSelectedDelegate(),
                            selectionGroup.getRelativeIndex(newIndex));
            if (isRowPreviouslySelected && clicked) {
                Runnable runnable = mRowList.getRepeatedSelectionCallback();
                if (runnable != null) {
                    runnable.run();
                }
            }
        }
    }

    private void onRowFocused(TemplateContext templateContext, int index, boolean hasFocus) {
        // Select the row if moving the focus should change the selection, and we have a new focus.
        boolean focusChangeSelection =
                (mRowList.getListFlags() & RowListWrapper.LIST_FLAGS_SELECTABLE_FOCUS_SELECT_ROW)
                        != 0;
        if (focusChangeSelection && hasFocus) {
            onRowSelected(templateContext, index, /* clicked= */ false);
        }
    }

    /** Switches the toggle state of a row if it does contain a toggle. */
    private void maybeSwitchToggleState(TemplateContext templateContext, int index) {
        RowWrapper rowWrapper = mRowList.getRowWrappers().get(index);
        Object rowObj = rowWrapper.getRow();

        // Only rows can contain toggles.
        if (rowObj instanceof Row) {
            Row row = (Row) rowObj;

            // Does the row contain a toggle ? if so, switch its state.
            Toggle toggle = row.getToggle();
            if (toggle != null) {
                rowWrapper.switchToggleState();
                // Dispatch the checked change callback to the app.
                OnCheckedChangeDelegate delegate = toggle.getOnCheckedChangeDelegate();
                templateContext
                        .getAppDispatcher()
                        .dispatchCheckedChanged(delegate, rowWrapper.isToggleChecked());
            }
        }
    }

    private void updateBottomPadding() {
        int bottomPadding =
                mRowList.isHalfList() && !mRowList.isEmpty()
                        ? mHalfListBottomPadding
                        : mFullListBottomPadding;

        // We add the bottom padding to the recyler view so that the vertical divider between the
        // scrollbar and the list will extend to the end of the list.
        View recyclerView = mListView.getView();
        recyclerView.setPadding(
                recyclerView.getPaddingLeft(),
                recyclerView.getPaddingTop(),
                recyclerView.getPaddingRight(),
                bottomPadding);
    }

    private void update() {
        boolean isLoading = mIsLoading;
        if (isLoading) {
            mProgressContainer.setVisibility(VISIBLE);

            // Mark the content views as invisible so that the size of the container remains the
            // same while the progress bar is showing.
            mEmptyListTextView.setVisibility(INVISIBLE);
            mListView.setVisibility(INVISIBLE);
            return;
        }

        mProgressContainer.setVisibility(GONE);

        // If the list is empty, hide it and display a message instead.
        boolean isEmpty = mListAdapter.getItemCount() == 0;
        if (isEmpty) {
            mEmptyListTextView.setVisibility(VISIBLE);
            mListView.setVisibility(GONE);

            // When the empty list text view is displayed, show the focus ring by not clipping
            // children.
            setClipChildren(false);
            mEmptyListTextView.setFocusable(true);
        } else {
            mEmptyListTextView.setVisibility(GONE);
            mListView.setVisibility(VISIBLE);

            // When the list view is displayed, clip its rows that get out of the view boundary.
            setClipChildren(true);
        }
    }

    /** Publish any non-null {@link Place}s from the list of {@link RowWrapper}. */
    private void publishMetadata(
            TemplateContext templateContext,
            List<RowWrapper> rowWrappers,
            int startIndexInclusive,
            int endIndexExclusive) {
        if (templateContext == null) {
            L.e(LogTags.TEMPLATE, "TemplateContext is null");
            return;
        }

        // Return if the range is empty.
        if (startIndexInclusive < 0) {
            return;
        }

        if (endIndexExclusive > rowWrappers.size()) {
            L.e(
                    LogTags.TEMPLATE,
                    "Index out of bound: (%d > %d)",
                    endIndexExclusive,
                    rowWrappers.size());
            return;
        }

        ImmutableList.Builder<Place> builder = new ImmutableList.Builder<>();
        for (int index = startIndexInclusive; index < endIndexExclusive; index++) {
            RowWrapper rowWrapper = rowWrappers.get(index);
            Place place = rowWrapper.getMetadata().getPlace();
            if (place != null) {
                builder.add(place);
            }
        }

        ImmutableList<Place> places = builder.build();
        L.v(LogTags.TEMPLATE, "Publishing %d visible places", places.size());
        templateContext.getPlaceMediator().publish(places);
    }
}
