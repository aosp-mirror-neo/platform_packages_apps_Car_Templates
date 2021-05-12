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

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.templates.host.view.widgets.common.GridRowAdapter.ViewType;
import com.android.car.libraries.templates.host.view.widgets.ui.CarLayoutManager;

import com.google.common.collect.ImmutableList;

import java.util.List;

/** A subclass of the {@link TemplatePagedListView} with some custom behaviors for grid template. */
public class GridPagedListView extends TemplatePagedListView {
    public GridPagedListView(Context context) {
        this(context, null);
    }

    public GridPagedListView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GridPagedListView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public GridPagedListView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttrs, int defStyleRes) {
        super(context, attrs, defStyleAttrs, defStyleRes);
    }

    @Override
    protected ImmutableList<Object> getVisibleRows() {
        RecyclerView recyclerView = getRecyclerView();
        GridRowAdapter adapter = (GridRowAdapter) recyclerView.getAdapter();
        if (adapter == null) {
            return ImmutableList.of();
        }

        CarLayoutManager layoutManager = getLayoutManager();
        int first = recyclerView.getChildAdapterPosition(layoutManager.getFirstFullyVisibleChild());
        int last = recyclerView.getChildAdapterPosition(layoutManager.getLastFullyVisibleChild());

        // If there's one more row below the last fully visible one, check to see if its content is
        // visible. If it is, then extend the range to include it. If we don't do this, we have the
        // risk that the content may be displayed in a partially visible row.
        if (last < adapter.getItemCount() - 1) {
            int nextLast = last + 1;
            ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(nextLast);

            if (viewHolder != null && viewHolder.getItemViewType() == ViewType.GRID_ROW_CONTAINER) {
                GridRowContainerView view = (GridRowContainerView) viewHolder.itemView;
                if (view.isContainerVisible()) {
                    L.v(
                            LogTags.TEMPLATE,
                            "Last row is partially visible, will consider fully visible");
                    last++;
                }
            }
        }
        L.v(LogTags.TEMPLATE, "Visible rows: [%d, %d]", first, last);

        // Loop through the list of rows in the adapter and find the visible ones.
        ImmutableList.Builder<Object> listBuilder = ImmutableList.builder();
        if (first != RecyclerView.NO_POSITION
                && last != RecyclerView.NO_POSITION
                && first <= last) {
            List<GridRowWrapper> gridRowWrappers = requireNonNull(adapter).getGridRowWrappers();
            for (int i = first; i <= last; ++i) {
                listBuilder.add(gridRowWrappers.get(i));
            }
        }
        return listBuilder.build();
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // PagedListView allows its CarLayoutManager manipulate the input focus in onLayout(), but
        // we
        // want to control the focus in template presenters. Revert any focus changes made there.
        int focusedGridRowIndex = getLayoutManager().getFocusedItemPosition();
        int focusedItemInRowIndex = -1;

        if (focusedGridRowIndex >= 0) {
            View view = getLayoutManager().findViewByPosition(focusedGridRowIndex);
            if (view instanceof GridRowContainerView) {
                // If one of the grid rows have focus, find out which item inside it has the focus.
                focusedItemInRowIndex = ((GridRowContainerView) view).getFocusedItemIndex();
            }
        }

        super.onLayout(changed, left, top, right, bottom);
        if (focusedItemInRowIndex >= 0
                && focusedGridRowIndex < getLayoutManager().getChildCount()) {
            GridRowContainerView focusedGridRowContainerView =
                    (GridRowContainerView)
                            getLayoutManager().findViewByPosition(focusedGridRowIndex);
            if (focusedGridRowContainerView != null) {
                View focusedView =
                        focusedGridRowContainerView.getItemByPosition(focusedItemInRowIndex);
                if (focusedView != null) {
                    focusedView.requestFocus();
                }
            }
        }

        // Republish any visible rows on layout as those may change, and more importantly, makes
        // sure
        // that the scrollbar visibility checks happen after the rows have been laid out initially,
        // and
        // not before.
        handleVisibleRowsChange();
    }

    @Override
    protected int getRowIndex(Object row) {
        return ((GridRowWrapper) row).getGridRowIndex();
    }

    @Override
    protected void publishMetadata(
            @Nullable TemplateContext templateContext, List<Object> rowWrappers) {
        // No need to publish metadata for grid template.
    }
}
