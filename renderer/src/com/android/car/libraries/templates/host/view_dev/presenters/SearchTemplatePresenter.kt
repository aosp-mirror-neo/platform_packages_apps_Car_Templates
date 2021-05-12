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
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.TemplateWrapper
import com.android.car.libraries.templates.host.R
import com.android.car.libraries.apphost.common.StatusBarManager
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.apphost.distraction.constraints.ActionsConstraints
import com.android.car.libraries.apphost.template.view.model.RowListWrapper
import com.android.car.libraries.apphost.template.view.model.RowWrapper
import com.android.car.libraries.apphost.view.AbstractTemplatePresenter
import com.android.car.libraries.templates.host.view.widgets.common.ContentView
import com.android.car.libraries.templates.host.view_dev.widget.SearchHeaderView

/**
 * A presenter that presents a search template by showing a search bar in the toolbar along with
 * a list of search results.
 */
class SearchTemplatePresenter internal constructor(
    templateContext: TemplateContext,
    templateWrapper: TemplateWrapper
) : AbstractTemplatePresenter(
    templateContext, templateWrapper, StatusBarManager.StatusBarState.LIGHT
) {
    private var rootView = View.inflate(templateContext, R.layout.search_template_layout_dev, null)
    private val contentView: ContentView = rootView.findViewById(R.id.content_view)
    private val headerView = SearchHeaderView.install(
        templateContext,
        contentView,
        rootView,
        template.initialSearchText,
        template.searchCallbackDelegate,
        template.isShowKeyboardByDefault)

    init {
        update()
    }

    override fun getTemplate(): SearchTemplate = super.getTemplate() as SearchTemplate

    override fun getView(): View = rootView

    override fun onTemplateChanged() {
        update()
    }

    override fun getDefaultFocusedView(): View? =
        if (template.isShowKeyboardByDefault) headerView.defaultFocusableView else null

    private fun update() {
        headerView.setAction(template.headerAction)
        headerView.setActionStrip(
            template.actionStrip,
            ActionsConstraints.ACTIONS_CONSTRAINTS_SIMPLE
        )
        headerView.setHint(template.searchHint)

        val rowListWrapper = RowListWrapper.wrap(templateContext, template.itemList)
            .setIsLoading(template.isLoading)
            .setRowFlags(RowWrapper.DEFAULT_UNIFORM_LIST_ROW_FLAGS)
            .setIsRefresh(templateWrapper.isRefresh)
            .build()

        contentView.setRowListContent(templateContext, rowListWrapper)
    }
}
