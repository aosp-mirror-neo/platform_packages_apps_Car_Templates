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
package com.android.car.libraries.templates.host.view.widgets.common.testing;

import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.view.widgets.common.ContentView;
import com.android.car.libraries.templates.host.view.widgets.common.GridItemView;
import com.android.car.libraries.templates.host.view.widgets.common.GridPagedListView;
import com.android.car.libraries.templates.host.view.widgets.common.GridRowAdapter;
import com.android.car.libraries.templates.host.view.widgets.common.GridRowContainerView;
import com.android.car.libraries.templates.host.view.widgets.common.GridRowWrapper;
import com.android.car.libraries.templates.host.view.widgets.common.GridView;
import com.android.car.libraries.templates.host.view.widgets.common.GridWrapper;

import java.util.List;

/** Test helper for {@link ContentView} that has the {@link GridWrapper} content set. */
public class GridContentViewHelper {
    private static final int LAYOUT_WIDTH = 400;
    private static final int LAYOUT_HEIGHT = 600;

    private final ContentView mContentView;

    public GridContentViewHelper(ContentView contentView) {
        mContentView = contentView;
    }

    /** Force a measure and layout for the content view. */
    public void measureAndLayout() {
        GridPagedListView pagedListView = getPagedListView();
        if (pagedListView != null) {
            pagedListView.measure(
                    MeasureSpec.makeMeasureSpec(LAYOUT_WIDTH, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(LAYOUT_HEIGHT, MeasureSpec.EXACTLY));
            pagedListView.layout(0, 0, LAYOUT_WIDTH, LAYOUT_HEIGHT);
        }
    }

    /** Returns the {@link GridPagedListView} from the content view. */
    @Nullable
    public GridPagedListView getPagedListView() {
        GridView gridView = getGridView();
        if (gridView == null) {
            return null;
        }
        return (GridPagedListView) gridView.findViewById(R.id.grid_paged_list_view);
    }

    /** Returns a {@link List} of {@link GridRowWrapper}s from the content view. */
    @Nullable
    public List<GridRowWrapper> getGridRowWrappers() {
        GridPagedListView listView = getPagedListView();
        if (listView != null) {
            GridRowAdapter adapter = (GridRowAdapter) listView.getAdapter();
            if (adapter != null) {
                return adapter.getGridRowWrappers();
            }
        }
        return null;
    }

    /** Returns a specified {@link GridItemView} from the content view. */
    @Nullable
    public GridItemView getGridItemView(int index) {
        List<GridRowWrapper> gridRowWrappers = getGridRowWrappers();
        if (gridRowWrappers == null) {
            return null;
        }

        int rowWrapperIndexForGridItem = -1;
        int gridItemIndexInGridRow = index;

        for (GridRowWrapper gridRowWrapper : gridRowWrappers) {
            if (gridItemIndexInGridRow >= gridRowWrapper.getGridRowItems().size()) {
                gridItemIndexInGridRow -= gridRowWrapper.getGridRowItems().size();
            } else {
                rowWrapperIndexForGridItem = gridRowWrapper.getGridRowIndex();
                break;
            }
        }

        if (rowWrapperIndexForGridItem >= 0) {
            View view = getGridRowContainerView(rowWrapperIndexForGridItem);
            if (view != null) {
                // Grid row has spaces in between, so map the grid item index to the actual view
                // index
                // by multiplying it by 2.
                return (GridItemView)
                        ((ViewGroup) view.findViewById(R.id.container))
                                .getChildAt(2 * gridItemIndexInGridRow);
            }
        }

        return null;
    }

    /** Returns a specified {@link GridRowContainerView} from the content view. */
    @Nullable
    public GridRowContainerView getGridRowContainerView(int index) {
        return getPagedListItemView(index, GridRowContainerView.class);
    }

    /** Returns a specified {@link GridItemViewHelper} from the content view. */
    @Nullable
    public GridItemViewHelper getGridItemViewHelper(int index) {
        GridItemView gridItemView = getGridItemView(index);
        if (gridItemView == null) {
            return null;
        }
        return new GridItemViewHelper(gridItemView);
    }

    @Nullable
    private <T> T getPagedListItemView(int index, Class<T> clazz) {
        GridPagedListView listView = getPagedListView();
        if (listView != null) {
            return clazz.cast(listView.getLayoutManager().getChildAt(index));
        }
        return null;
    }

    @Nullable
    private GridView getGridView() {
        ViewGroup container = mContentView.findViewById(R.id.container);
        if (container == null) {
            return null;
        }
        return (GridView) container.getChildAt(0);
    }
}
