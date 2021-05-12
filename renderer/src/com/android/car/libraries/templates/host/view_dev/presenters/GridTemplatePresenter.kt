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
package com.android.car.libraries.templates.host.view_dev.presenters

import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.ProgressBar
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.TemplateWrapper
import com.android.car.libraries.templates.host.R
import com.android.car.libraries.apphost.common.StatusBarManager
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.apphost.distraction.constraints.ActionsConstraints
import com.android.car.libraries.apphost.view.AbstractTemplatePresenter
import com.android.car.libraries.templates.host.view_dev.widget.GridItemAdapter
import com.android.car.libraries.templates.host.view_dev.widget.HeaderView
import com.android.car.ui.recyclerview.CarUiRecyclerView

/**
 * A [TemplatePresenter] for [GridTemplate] instances
 *
 * @param templateContext the context in which this template will be rendered
 * @param templateWrapper the contents of the [GridTemplate] being rendered
 */
class GridTemplatePresenter internal constructor(
    templateContext: TemplateContext,
    templateWrapper: TemplateWrapper
) : AbstractTemplatePresenter(
    templateContext, templateWrapper, StatusBarManager.StatusBarState.LIGHT
) {

    private val rootView: View = View.inflate(
        templateContext,
        R.layout.grid_template_layout_dev,
        null
    )
    private val contentView: View = rootView.findViewById(R.id.grid_container)
    private val progressBar: ProgressBar = rootView.findViewById(R.id.grid_progress_bar)
    private val gridView: CarUiRecyclerView = rootView.findViewById(R.id.grid_view)
    private val headerView = HeaderView.install(templateContext, contentView)
    private val gridItemAdapter = GridItemAdapter(templateContext)

    init {
        gridView.adapter = gridItemAdapter
        update()
    }

    override fun getView(): View = rootView

    override fun getTemplate(): GridTemplate =
        super.getTemplate() as GridTemplate

    override fun onTemplateChanged() {
        update()
    }

    private fun update() {
        headerView.setContent(template.title, template.headerAction)
        headerView.setActionStrip(
            template.actionStrip,
            ActionsConstraints.ACTIONS_CONSTRAINTS_SIMPLE
        )

        progressBar.visibility = if (template.isLoading) VISIBLE else GONE
        gridView.visibility = if (template.isLoading) GONE else VISIBLE
        // TODO(b/178399426): Add action strip support

        if (!template.isLoading) {
            val items: List<GridItem> = template.singleList?.items as List<GridItem>
            gridItemAdapter.setItems(items)
        }
    }
}
