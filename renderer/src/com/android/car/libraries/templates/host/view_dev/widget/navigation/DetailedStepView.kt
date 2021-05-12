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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Step
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.apphost.logging.LogTags
import com.android.car.libraries.templates.host.R

/**
 * A [View] that displays a detailed navigation step.
 *
 * This view tries to display all the elements of a [Step] and [Distance]. For
 * example if available, it would show a turn icon, description and lanes image. It could be used
 * with another view to show the next turn.
 */
class DetailedStepView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private lateinit var turnSymbolView: ImageView
    private lateinit var distanceText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var lanesImageView: ImageView
    private lateinit var turnContainerView: LinearLayout
    private lateinit var lanesImageContainerView: FrameLayout

    @get:VisibleForTesting
    var step: Step? = null
        private set

    @get:VisibleForTesting
    var distance: Distance? = null
        private set

    override fun onFinishInflate() {
        super.onFinishInflate()
        turnSymbolView = findViewById(R.id.turn_symbol)
        distanceText = findViewById(R.id.distance_text)
        descriptionText = findViewById(R.id.description_text)
        lanesImageView = findViewById(R.id.lanes_image)
        turnContainerView = findViewById(R.id.turn_container)
        lanesImageContainerView = findViewById(R.id.lanes_image_container)
    }

    // TODO(b/178735593): Update to include CarTextParams
    fun setStepAndDistance(
        templateContext: TemplateContext?,
        step: Step?,
        distance: Distance?,
        hideLaneImages: Boolean
    ) {
        Log.v(
            LogTags.TEMPLATE,
            "Setting detailed step view with step: $step, and distance: $distance"
        )
        this.step = step
        if (step == null) {
            visibility = GONE
            return
        }
        this.distance = distance
        val maneuver = step.maneuver
        val turnIcon = maneuver?.icon
        // TODO(b/178735593): Use ImageUtils.setImageSrc when available.
        turnSymbolView.setImageIcon(turnIcon?.icon?.toIcon(templateContext))
        turnSymbolView.visibility = if (turnIcon?.icon == null) GONE else VISIBLE

        // TODO(b/178735593): Use DistanceUtils when available
        distanceText.text = distance?.toString()
        distanceText.visibility = if (distance != null) VISIBLE else GONE

        // TODO(b/178735593): Use CarTextUtils when available.
        descriptionText.text = step.cue.toString()
        descriptionText.visibility = VISIBLE

        val lanesImage = step.lanesImage
        val turnContainerBottomMargin: Int
        // TODO(b/178735593): Use ImageUtils.setImageSrc when available.
        if (hideLaneImages || lanesImage?.icon == null) {
            lanesImageView.visibility = GONE
            lanesImageContainerView.visibility = GONE
            turnContainerBottomMargin = resources.getDimensionPixelSize(R.dimen.template_padding_2)
        } else {
            lanesImageView.visibility = VISIBLE
            lanesImageContainerView.visibility = VISIBLE
            turnContainerBottomMargin = resources.getDimensionPixelSize(R.dimen.template_padding_1)
        }
        val layoutParams = turnContainerView.layoutParams as LayoutParams
        layoutParams.bottomMargin = turnContainerBottomMargin
        turnContainerView.layoutParams = layoutParams
        visibility = VISIBLE
    }
}