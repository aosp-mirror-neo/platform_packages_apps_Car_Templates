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
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.car.app.model.GridItem
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.templates.host.internal.CommonUtils
import com.android.car.libraries.templates.host.R

class GridViewHolder(
    itemView: View
) : RecyclerView.ViewHolder(itemView) {

    private val container: View = itemView.findViewById(R.id.grid_item_container)
    private val title: TextView = itemView.findViewById(R.id.grid_item_title)
    private val text: TextView = itemView.findViewById(R.id.grid_item_text)
    private val image: ImageView = itemView.findViewById(R.id.grid_item_image)
    private val progressBar: ProgressBar = itemView.findViewById(R.id.grid_item_progress_bar)

    private val gridItemIconSize: Int =
        itemView.resources.getDimensionPixelSize(R.dimen.grid_item_icon_size)
    private val gridItemImageSizeLarge: Int =
        itemView.resources.getDimensionPixelSize(R.dimen.grid_item_image_size_large)

    fun bind(context: TemplateContext, gridItem: GridItem) {
        title.text = gridItem.title.toString()
        text.text = gridItem.text?.toString()

        image.visibility = if (gridItem.isLoading) GONE else VISIBLE
        progressBar.visibility = if (gridItem.isLoading) VISIBLE else GONE

        if (!gridItem.isLoading) {
            image.setImageIcon(gridItem.image?.icon?.toIcon(context))
            if (gridItem.onClickDelegate != null) {
                container.setOnClickListener {
                    CommonUtils.dispatchClick(context, gridItem.onClickDelegate!!)
                }
            }
        }

        val imageSize = getImageSize(gridItem.imageType)
        image.layoutParams.height = imageSize
        image.layoutParams.width = imageSize
    }

    fun getImageSize(imageType: Int): Int {
        return if (imageType == GridItem.IMAGE_TYPE_ICON) gridItemIconSize
        else gridItemImageSizeLarge
    }
}
