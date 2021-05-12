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
import androidx.car.app.model.TemplateWrapper
import com.android.car.libraries.templates.host.R
import com.android.car.libraries.apphost.common.StatusBarManager
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.apphost.template.view.model.RowListWrapperTemplate
import com.android.car.libraries.apphost.view.AbstractTemplatePresenter
import com.android.car.libraries.templates.host.view.widgets.common.ContentView
import com.android.car.libraries.templates.host.view_dev.widget.HeaderView

class RowListWrapperTemplatePresenter internal constructor(
    templateContext: TemplateContext,
    templateWrapper: TemplateWrapper
) : AbstractTemplatePresenter(
    templateContext, templateWrapper, StatusBarManager.StatusBarState.LIGHT
) {
    private val rootView: View = View.inflate(templateContext, R.layout.row_list_layout_dev, null)
    private val contentView: ContentView = rootView.findViewById(R.id.content_view)
    private val headerView = HeaderView.install(templateContext, contentView)

    init {
        update()
    }

    override fun getTemplate(): RowListWrapperTemplate =
        super.getTemplate() as RowListWrapperTemplate

    override fun getView(): View = rootView

    override fun onTemplateChanged() {
        update()
    }

    private fun update() {
        headerView.setContent(template.title, template.headerAction)
        headerView.setActionStrip(template.actionStrip, template.actionsConstraints)
        contentView.setRowListContent(templateContext, template.list)
    }
}
