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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalFocusChangeListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.annotation.VisibleForTesting;
import androidx.car.app.model.GridItem;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.templates.host.R;

import java.util.List;

/**
 * Displays a container that can hold a row of {@link GridItem}s.
 *
 * <p>This view is a container for other views that render the actual {@link GridItem} models that
 * can go inside of a {@link GridRowWrapper}.
 */
public class GridRowContainerView extends FrameLayout {
    /** The height of a space between grid items. 1dp is fine, we only care about its width. */
    private static final int GRID_SPACE_HEIGHT = 1;

    /** The weight of each grid item in the row. */
    private static final float GRID_ITEM_WEIGHT = 1.0f;

    private static final int INVALID_INDEX = -1;

    // List of grid item views and spaces between those will be added dynamically to this container.
    // They will be laid out inside the container in this layout
    // |------------------------------------------------------------|
    // | ____________ _________ ____________ _________ ____________ |
    // | | GridItem | | Space | | GridItem | | Space | | GridItem | |
    // | |__________| |_______| |__________| |_______| |__________| |
    // |------------------------------------------------------------|
    private ViewGroup mContainer;

    /**
     * The last focused grid item index, used to restore focus when the focus moves out of the
     * {@link GridRowContainerView} and back in.
     */
    private int mLastFocusedItemIndex = INVALID_INDEX;

    private final int mGridItemHorizontalSpacing;

    @VisibleForTesting
    final OnGlobalFocusChangeListener mOnGlobalFocusChangeListener =
            new OnGlobalFocusChangeListener() {
                // call to getFocusedItemIndex() not allowed on the given receiver.
                @SuppressWarnings("nullness:method.invocation.invalid")
                @Override
                public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                    if (newFocus instanceof GridItemView) {
                        mLastFocusedItemIndex = getFocusedItemIndex();
                    }
                }
            };

    public GridRowContainerView(@NonNull Context context) {
        this(context, null);
    }

    public GridRowContainerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GridRowContainerView(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressWarnings({
            "ResourceType",
            "method.invocation.invalid",
            "assignment.type.incompatible",
            "argument.type.incompatible"
    })
    public GridRowContainerView(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr);
        @StyleableRes final int[] themeAttrs = {R.attr.templateGridItemHorizontalSpacing};

        TypedArray ta = context.obtainStyledAttributes(themeAttrs);
        mGridItemHorizontalSpacing = ta.getDimensionPixelOffset(0, -1);
        ta.recycle();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        getViewTreeObserver().addOnGlobalFocusChangeListener(mOnGlobalFocusChangeListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalFocusChangeListener(mOnGlobalFocusChangeListener);

        super.onDetachedFromWindow();
    }

    /** Returns whether the grid row container is visible in the screen, fully or partially. */
    public boolean isContainerVisible() {
        return mContainer.getGlobalVisibleRect(new Rect());
    }

    /** Updates the view with the given {@link GridRowWrapper}. */
    public void setGridRow(
            TemplateContext templateContext, List<GridRowWrapper> gridRowWrappers, int index) {
        GridRowWrapper gridRowWrapper = gridRowWrappers.get(index);

        L.v(LogTags.TEMPLATE, "Updating grid row at index %d: %s", index, gridRowWrapper);
        mContainer.removeAllViews();

        int itemsAdded = 0;
        for (GridItemWrapper gridItemWrapper : gridRowWrapper.getGridRowItems()) {
            GridItemView gridItemView = addGridItemView(mContainer);
            gridItemView.setGridItem(
                    templateContext,
                    gridItemWrapper,
                    gridRowWrapper.hasGridItemsWithTitle(),
                    gridRowWrapper.hasGridItemsWithText());
            addSpace(mContainer);
            itemsAdded++;
        }

        // The grid items have a fixed width. The spaces between grid items have a flexible width
        // take
        // up all the remaining width in the container. If required, adding empty grid items and
        // spaces
        // in the row to make sure items in all the rows look consistent.
        int maxColsPerGridRow = gridRowWrapper.getMaxColsPerGridRow();
        while (itemsAdded < maxColsPerGridRow) {
            addEmptyGridItemSpace(mContainer);
            addSpace(mContainer);
            itemsAdded++;
        }

        // Remove the unnecessary space after the last item.
        mContainer.removeViewAt(mContainer.getChildCount() - 1);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContainer = findViewById(R.id.container);
    }

    /**
     * Gets the item which is focused or an invalid index less than zero if no items are focused.
     */
    public int getFocusedItemIndex() {
        if (mContainer != null && mContainer.hasFocus()) {
            for (int index = 0; index < mContainer.getChildCount(); index++) {
                if (mContainer.getChildAt(index).isFocused()) {
                    return index;
                }
            }
        }

        return INVALID_INDEX;
    }

    /**
     * Gets the {@link View} at the given {@code index} or {@code null} if the index is outside the
     * range of the row.
     */
    @Nullable
    public View getItemByPosition(int index) {
        if (index >= 0 && index < mContainer.getChildCount()) {
            return mContainer.getChildAt(index);
        }

        return null;
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        View focusedItemView = getItemByPosition(mLastFocusedItemIndex);
        if (focusedItemView != null) {
            return focusedItemView.requestFocus();
        }

        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    /** Creates a new {@link GridItemView} and adds it to the provided parent view group. */
    private GridItemView addGridItemView(ViewGroup parentViewGroup) {
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        GridItemView gridItemView =
                (GridItemView) layoutInflater.inflate(R.layout.grid_item_view, this, false);

        // Equally distribute width among all grid items.
        ViewGroup.LayoutParams params = gridItemView.getLayoutParams();
        LinearLayout.LayoutParams newParams =
                new LinearLayout.LayoutParams(/* width= */ 0, params.height, GRID_ITEM_WEIGHT);
        gridItemView.setLayoutParams(newParams);

        parentViewGroup.addView(gridItemView);
        return gridItemView;
    }

    private void addEmptyGridItemSpace(ViewGroup parentViewGroup) {
        Space gridItemSpace = new Space(getContext());

        // Equally distribute width among all grid items.
        gridItemSpace.setLayoutParams(
                new LinearLayout.LayoutParams(/* width= */ 0, GRID_SPACE_HEIGHT, GRID_ITEM_WEIGHT));
        parentViewGroup.addView(gridItemSpace);
    }

    /** Creates a new {@link Space} and adds it to the provided parent view group. */
    private void addSpace(ViewGroup parentViewGroup) {
        Space space = new Space(getContext());
        space.setLayoutParams(
                new LinearLayout.LayoutParams(mGridItemHorizontalSpacing, GRID_SPACE_HEIGHT));
        parentViewGroup.addView(space);
    }
}
