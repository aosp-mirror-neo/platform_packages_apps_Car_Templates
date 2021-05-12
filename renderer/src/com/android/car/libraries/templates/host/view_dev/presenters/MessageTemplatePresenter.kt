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

import android.content.pm.ApplicationInfo
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.StyleableRes
import androidx.car.app.model.CarText
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.TemplateWrapper
import com.android.car.libraries.templates.host.R
import com.android.car.libraries.apphost.common.StatusBarManager
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.apphost.view.AbstractTemplatePresenter
import com.android.car.libraries.apphost.view.common.CarTextUtils
import com.android.car.libraries.apphost.view.common.ImageUtils
import com.android.car.libraries.apphost.view.common.ImageViewParams
import com.android.car.libraries.templates.host.view.widgets.common.ActionButtonListView
import com.android.car.libraries.templates.host.view_dev.widget.HeaderView

/**
 * A simple presenter that shows a single message, title, and icon from a message template
 */
class MessageTemplatePresenter internal constructor(
    templateContext: TemplateContext,
    templateWrapper: TemplateWrapper
) : AbstractTemplatePresenter(
    templateContext, templateWrapper, StatusBarManager.StatusBarState.LIGHT
) {

    private val rootView =
        View.inflate(templateContext, R.layout.message_template_layout_dev, null)
    private val messageTemplateContainer: View
    private val messageContainer: View
    private val messageText: TextView
    private val messageIcon: ImageView
    private val actionButtonListView: ActionButtonListView
    private val headerView: HeaderView
    private val imageViewParams: ImageViewParams
    private val adaptiveWidthFraction: Float

    private val debugToggle: Switch
    private val debugToggleContainer: ViewGroup
    private val isDebugEnabled: Boolean
    private val stackTraceContainer: ViewGroup
    private val stackTraceView: TextView

    init {
        messageTemplateContainer = rootView.findViewById(R.id.message_template_container)
        messageContainer = rootView.findViewById(R.id.message_container)
        messageText = rootView.findViewById(R.id.message_text)
        messageIcon = rootView.findViewById(R.id.message_icon)
        headerView = HeaderView.install(templateContext, messageTemplateContainer)
        actionButtonListView = rootView.findViewById(R.id.action_button_list_view)

        debugToggle = rootView.findViewById(R.id.debug_toggle)
        debugToggleContainer = rootView.findViewById(R.id.debug_toggle_container)
        stackTraceContainer = rootView.findViewById(R.id.stack_trace_container)
        stackTraceView = rootView.findViewById(R.id.stack_trace)
        isDebugEnabled =
            (0 != (templateContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE))

        @StyleableRes val themeAttrs = intArrayOf(
            R.attr.templateMessageDefaultIconTint,
            R.attr.templateAdaptiveWidthFraction
        )

        val ta = templateContext.obtainStyledAttributes(themeAttrs)
        @ColorInt val defaultIconTint = ta.getColor(0, 0)
        adaptiveWidthFraction = ta.getFloat(themeAttrs.lastIndex, 0f)
        ta.recycle()

        imageViewParams = ImageViewParams.builder().setDefaultTint(defaultIconTint).build()
        update()
    }

    override fun getTemplate(): MessageTemplate = super.getTemplate() as MessageTemplate

    override fun getView() = rootView

    override fun onTemplateChanged() {
        update()
    }

    // TODO(b/179666082): Implement getting focused view and rotary controlled focus change

    private fun update() {
        headerView.setContent(template.title, template.headerAction)

        messageText.text = CarTextUtils.toCharSequenceOrEmpty(templateContext, template.message)
        messageText.visibility = if (CarText.isNullOrEmpty(template.message)) GONE else VISIBLE
        // Allow focus on the message view if there are no actions available.
        messageText.isFocusable = template.actions.isEmpty()

        // Show the icon if we have it, hide it otherwise.
        val iconShown = template.icon?.let {
            ImageUtils.setImageSrc(templateContext, it, messageIcon, imageViewParams)
        } ?: false
        messageIcon.visibility = if (iconShown) VISIBLE else GONE

        actionButtonListView.setActionList(templateContext, template.actions, MAX_ALLOWED_ACTIONS)

        updateAdaptiveMargins()

        if (isDebugEnabled && !CarText.isNullOrEmpty(template.debugMessage)) {
            debugToggleContainer.visibility = VISIBLE
            stackTraceContainer.visibility = if (debugToggle.isChecked) VISIBLE else GONE
            debugToggle.setOnCheckedChangeListener { _, isChecked -> showStackTrace(isChecked) }
            stackTraceView.text =
                CarTextUtils.toCharSequenceOrEmpty(templateContext, template.debugMessage)
        } else {
            debugToggleContainer.visibility = GONE
            stackTraceContainer.visibility = GONE
        }
    }

    /**
     * Sets the margins of the contents of the template to a fraction of the total width of the
     * screen.
     */
    private fun updateAdaptiveMargins() {
        val screenWidth = templateContext.resources.displayMetrics.widthPixels
        val marginFraction: Float = (1f - adaptiveWidthFraction) / 2
        val margin = (screenWidth * marginFraction).toInt()
        val layoutParams = messageContainer.layoutParams as LinearLayout.LayoutParams
        layoutParams.marginStart = margin
        layoutParams.marginEnd = margin
        messageContainer.layoutParams = layoutParams
    }

    /**
     * Toggles visibility of the stack trace view
     */
    private fun showStackTrace(show: Boolean) {
        stackTraceContainer.visibility = if (show) VISIBLE else GONE
        messageContainer.visibility = if (show) GONE else VISIBLE
    }

    companion object {
        // TODO (b/182491749): Move to apphost
        private const val MAX_ALLOWED_ACTIONS = 2
    }
}
