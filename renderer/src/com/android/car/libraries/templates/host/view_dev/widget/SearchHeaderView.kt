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

import android.view.View
import android.widget.EditText
import androidx.annotation.VisibleForTesting
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.SearchCallbackDelegate
import com.android.car.libraries.apphost.common.CarAppError
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.apphost.distraction.constraints.ActionsConstraints
import com.android.car.ui.core.CarUi
import com.android.car.ui.toolbar.Toolbar
import com.android.car.ui.toolbar.ToolbarController
import com.android.car.libraries.templates.host.R

/**
 * Implementation of a template search header. This uses Car UI Library [ToolbarController]
 * internally, which is initialized to show a search box and send updates through a
 * [SearchCallbackDelegate].
 *
 * <p>This view must be "installed" by using [SearchHeaderView.install] to insert a toolbar around a
 * provided content area.
 */
class SearchHeaderView private constructor(
    private val templateContext: TemplateContext,
    @VisibleForTesting
    val toolbarController: ToolbarController,
    rootView: View,
    initialSearchText: String?,
    private val searchCallbackDelegate: SearchCallbackDelegate?,
    keyboardOpened: Boolean
) {
    private val inputManager = templateContext.inputManager
    private var editableSearchBar: CarEditTextWrapper? = null
    private var searchBar: EditText? = null
    @Suppress("DEPRECATION")
    private val backListener = Toolbar.OnBackListener {
        templateContext.backPressedHandler.onBackPressed()
        true
    }

    init {
        @Suppress("DEPRECATION")
        toolbarController.state = Toolbar.State.SEARCH
        val searchBar: EditText = rootView.findViewById(R.id.car_ui_toolbar_search_bar)
        editableSearchBar = CarEditTextWrapper(searchBar, inputManager)
        this.searchBar = searchBar

        toolbarController.registerOnSearchListener { query ->
            templateContext.appDispatcher.dispatchSearchTextChanged(
                searchCallbackDelegate,
                query
            )
        }

        toolbarController.registerOnSearchCompletedListener {
            val query = searchBar.text.toString()
            templateContext.appDispatcher.dispatchSearchSubmitted(
                searchCallbackDelegate,
                query
            )
        }

        toolbarController.setSearchQuery(initialSearchText)

        if (keyboardOpened) {
            inputManager.startInput(editableSearchBar)
        }

        // TODO(b/179220417): Handle disabling search while driving
    }

    /** Updates the header action */
    fun setAction(action: Action?) {
        // TODO(b/179520668): Use icon from action and hide the back arrow if Action is not BACK
        if (action?.type == Action.TYPE_BACK) {
            toolbarController.registerOnBackListener(backListener)
        } else {
            toolbarController.unregisterOnBackListener(backListener)
        }
    }

    /** Returns a default focusable view from this controller */
    val defaultFocusableView: View?
        get() = searchBar

    /** Updates the search hint. */
    fun setHint(searchHint: String?) {
        toolbarController.searchHint = searchHint
    }

    /** Updates the [ActionStrip] associated with this toolbar */
    fun setActionStrip(actionStrip: ActionStrip?, constraints: ActionsConstraints) {
        validateActionStrip(actionStrip, constraints)
        toolbarController.showMenuItemsWhileSearching =
            actionStrip?.actions?.isNotEmpty() == true
        toolbarController.setMenuItems(actionStrip?.let {
            HeaderView.createMenuItems(it, templateContext)
        })
    }

    /** Ensure the model satisfies the input constraints. */
    private fun validateActionStrip(actionStrip: ActionStrip?, constraints: ActionsConstraints) {
        try {
            HeaderViewUtils.validateRequiredTypes(actionStrip, constraints)
        } catch (exception: HeaderViewUtils.ValidationException) {
            templateContext
                .errorHandler
                .showError(
                    CarAppError.builder(templateContext.carAppPackageInfo.componentName)
                        .setCause(exception)
                        .build()
                )
        }
    }

    companion object {
        /**
         * Installs a [SearchHeaderView] around the given container view. The resulting view is
         * initialized with the provided initial search text and [SearchCallbackDelegate].
         */
        fun install(
            templateContext: TemplateContext,
            container: View,
            rootView: View,
            initialSearchText: String?,
            searchCallbackDelegate: SearchCallbackDelegate?,
            keyboardOpened: Boolean
        ): SearchHeaderView {
            val toolbarController = CarUi.installBaseLayoutAround(container, null, true)!!
            return SearchHeaderView(
                templateContext,
                toolbarController,
                rootView,
                initialSearchText,
                searchCallbackDelegate,
                keyboardOpened)
        }
    }
}
