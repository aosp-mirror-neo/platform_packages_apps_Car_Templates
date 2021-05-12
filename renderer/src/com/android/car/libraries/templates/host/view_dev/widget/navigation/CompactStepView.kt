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
package com.android.car.libraries.templates.host.view_dev.widget.navigation

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.car.app.navigation.model.Step
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.apphost.logging.LogTags
import com.android.car.libraries.templates.host.R

/**
 * A [View] that displays a compact view of a navigation maneuver.
 *
 * For example it could show just the maneuver description or a description and an icon.
 */
class CompactStepView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private lateinit var turnSymbolView: ImageView
    private lateinit var descriptionText: TextView

    @get:VisibleForTesting
    var step: Step? = null
        private set

    override fun onFinishInflate() {
        super.onFinishInflate()
        turnSymbolView = findViewById(R.id.compact_turn_symbol)
        descriptionText = findViewById(R.id.compact_description_text)
    }

    // TODO(b/178735593): Update to include CarTextParams
    fun setStep(
        templateContext: TemplateContext?,
        step: Step?
    ) {
        Log.v(LogTags.TEMPLATE, "Setting compact step view with step: $step")
        this.step = step
        if (step == null) {
            visibility = GONE
            return
        }
        val maneuver = step.maneuver
        val turnIcon = maneuver?.icon
        // TODO(b/178735593): Use ImageUtils.setImageSrc when available.
        turnSymbolView.setImageIcon(turnIcon?.icon?.toIcon(templateContext))
        turnSymbolView.visibility = if (turnIcon?.icon == null) GONE else VISIBLE
        // TODO(b/178735593): Use CarTextUtils when available.
        descriptionText.text = step.cue.toString()
        visibility = VISIBLE
    }
}