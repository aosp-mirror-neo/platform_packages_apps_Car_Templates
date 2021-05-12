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
package com.android.car.libraries.templates.host.view_dev.widget

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.car.app.model.GridItem
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.templates.host.R

/**
 * A [RecyclerView.Adapter] for grid items to be rendered from a [GridTemplate]
 *
 * @param context to use in inflating this grid item
 */
class GridItemAdapter(
    private val context: TemplateContext
) : RecyclerView.Adapter<GridViewHolder>() {

    private var gridItems: List<GridItem> = emptyList()

    fun setItems(items: List<GridItem>) {
        gridItems = items

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): GridViewHolder {
        val view =
            LayoutInflater.from(context).inflate(R.layout.grid_item_view_dev, viewGroup, false)
        return GridViewHolder(view)
    }

    override fun getItemCount(): Int = gridItems.size

    override fun onBindViewHolder(viewHolder: GridViewHolder, index: Int) {
        viewHolder.bind(context, gridItems[index])
    }
}
