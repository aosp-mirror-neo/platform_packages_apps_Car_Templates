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

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.view.widgets.ui.PagedListView;

import java.lang.annotation.Retention;
import java.util.List;

/** A list adapter for {@link GridRowWrapper}s. */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public class GridRowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
        implements PagedListView.ItemCap {
    @Retention(SOURCE)
    @IntDef({ViewType.GRID_ROW_CONTAINER})
    @interface ViewType {
        int GRID_ROW_CONTAINER = 1;
    }

    private final Context mContext;

    private List<GridRowWrapper> mGridRowWrappers;
    private TemplateContext mTemplateContext;

    static GridRowAdapter create(Context context, List<GridRowWrapper> gridRowWrappers) {
        return new GridRowAdapter(context, gridRowWrappers);
    }

    void setGridRows(TemplateContext templateContext, List<GridRowWrapper> gridRowWrappers) {
        mGridRowWrappers = gridRowWrappers;
        mTemplateContext = templateContext;

        notifyDataSetChanged();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        if (viewType != ViewType.GRID_ROW_CONTAINER) {
            L.e(LogTags.TEMPLATE, "Invalid view type creating view holder: %d", viewType);
        }

        return new RecyclerView.ViewHolder(
                LayoutInflater.from(mContext)
                        .inflate(R.layout.grid_row_container_view, viewGroup, false)) {};
    }

    @Override
    public int getItemViewType(int index) {
        return ViewType.GRID_ROW_CONTAINER;
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int index) {
        int viewType = viewHolder.getItemViewType();
        if (viewType == ViewType.GRID_ROW_CONTAINER) {
            ((GridRowContainerView) viewHolder.itemView)
                    .setGridRow(mTemplateContext, mGridRowWrappers, index);
        } else {
            L.e(LogTags.TEMPLATE, "Invalid view type binding view holder: %d", viewType);
        }
    }

    @Override
    public int getItemCount() {
        return mGridRowWrappers.size();
    }

    public List<GridRowWrapper> getGridRowWrappers() {
        return mGridRowWrappers;
    }

    @Override
    public void setMaxItems(int maxItems) {
        // Not needed, we cap the max items when we create the adapter.
    }

    private GridRowAdapter(Context context, List<GridRowWrapper> gridRowWrappers) {
        mContext = context;
        mGridRowWrappers = gridRowWrappers;
    }
}
