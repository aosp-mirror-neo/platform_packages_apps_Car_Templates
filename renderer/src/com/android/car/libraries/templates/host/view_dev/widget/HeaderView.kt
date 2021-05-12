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

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import com.android.car.libraries.apphost.common.CarAppError
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.apphost.distraction.constraints.ActionsConstraints
import com.android.car.libraries.apphost.logging.LogTags
import com.android.car.libraries.apphost.view.common.CarTextUtils
import com.android.car.libraries.apphost.view.common.ImageUtils
import com.android.car.libraries.apphost.view.common.ImageViewParams
import com.android.car.libraries.templates.host.internal.CommonUtils
import com.android.car.ui.core.CarUi
import com.android.car.ui.toolbar.MenuItem
import com.android.car.ui.toolbar.Toolbar
import com.android.car.ui.toolbar.ToolbarController

/**
 * Implementation of a template header. This uses Car UI Library [ToolbarController] internally.
 * This view must be "installed" by using [HeaderView.install] to insert a toolbar around a provided
 * content area.
 */
class HeaderView private constructor(
    private val templateContext: TemplateContext,
    @VisibleForTesting
    val toolbarController: ToolbarController
) {
    // TODO(b/183853224): Replace with equivalent ToolbarController, once is available
    @Suppress("deprecation")
    private val backListener = Toolbar.OnBackListener {
        templateContext.backPressedHandler.onBackPressed()
        true
    }

    /** Updates the header title and action */
    // TODO(b/183853224): Replace with equivalent ToolbarController, once is available
    @Suppress("deprecation")
    fun setContent(text: CarText?, action: Action?) {
        toolbarController.title = CarTextUtils.toCharSequenceOrEmpty(templateContext, text)

        toolbarController.state = if (action?.type == Action.TYPE_BACK) {
            toolbarController.registerOnBackListener(backListener)
            Toolbar.State.SUBPAGE
        } else {
            toolbarController.unregisterOnBackListener(backListener)
            Toolbar.State.HOME
        }

        if (action?.type == Action.TYPE_APP_ICON) {
            toolbarController.setLogo(templateContext.carAppPackageInfo.roundAppIcon)
        } else {
            toolbarController.setLogo(0)
        }
    }

    /** Updates the [ActionStrip] associated with this toolbar */
    fun setActionStrip(actionStrip: ActionStrip?, constraints: ActionsConstraints) {
        validateActionStrip(actionStrip, constraints)
        toolbarController.setMenuItems(actionStrip?.let {
            createMenuItems(it, templateContext)
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
        /** Installs a [HeaderView] around the given container view] */
        fun install(templateContext: TemplateContext, container: View): HeaderView {
            val toolbarController = CarUi.installBaseLayoutAround(container, null, true)!!
            return HeaderView(templateContext, toolbarController)
        }

        /** Converts an [ActionStrip] to a list of [MenuItem]s. */
        fun createMenuItems(
            actionStrip: ActionStrip,
            templateContext: TemplateContext
        ): List<MenuItem> {
            val menuItems = mutableListOf<MenuItem>()
            for (action in actionStrip.actions) {
                if (action is Action) {
                    val menuItem = createMenuItem(action, templateContext)
                    menuItems.add(menuItem)
                } else {
                    Log.e(LogTags.TEMPLATE, "Action is not supported: $action")
                }
            }
            return menuItems
        }

        /** Converts an [Action] to a [MenuItem]. */
        private fun createMenuItem(action: Action, templateContext: TemplateContext): MenuItem {
            Log.i(LogTags.TEMPLATE, "Tinted: ${action.type}")
            val menuItemBuilder = MenuItem.Builder(templateContext)
                    .setPrimary(true)
                    .setEnabled(true)
                    .setTinted(action.icon?.type != CarIcon.TYPE_APP_ICON)
                    .setShowIconAndTitle(true)
                    .setTitle(CarTextUtils.toCharSequenceOrEmpty(templateContext, action.title))
            action.onClickDelegate?.let { onClickDelegate ->
                menuItemBuilder.setOnClickListener {
                    CommonUtils.dispatchClick(templateContext, onClickDelegate)
                }
            }
            val menuItem = menuItemBuilder.build()

            val menuItemIconSize = templateContext.resources
                    .getDimension(com.android.car.ui.R.dimen.car_ui_toolbar_menu_item_icon_size)
                    .toInt()
            ImageUtils.getIconFromAction(action)?.let {
                ImageUtils.setImageTargetSrc(
                        templateContext,
                        it,
                        { drawable: Drawable -> menuItem.icon = drawable },
                        ImageViewParams.DEFAULT, // tint is applied by CarUiToolbar
                        menuItemIconSize,
                        menuItemIconSize
                )
            }

            return menuItem
        }
    }
}
