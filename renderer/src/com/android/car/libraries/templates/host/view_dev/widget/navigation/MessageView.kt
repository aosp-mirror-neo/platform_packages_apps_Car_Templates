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
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarText
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.apphost.logging.LogTags
import com.android.car.libraries.templates.host.R

/** A [View] that displays a message with optional image and subtext.  */
class MessageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private lateinit var imageView: ImageView
    private lateinit var titleView: TextView
    private lateinit var textView: TextView

    override fun onFinishInflate() {
        super.onFinishInflate()
        imageView = findViewById(R.id.message_image)
        titleView = findViewById(R.id.message_title)
        textView = findViewById(R.id.message_text)
    }

    fun setMessage(
        templateContext: TemplateContext?,
        image: CarIcon?,
        title: CarText?,
        text: CarText?
    ) {
        Log.v(
            LogTags.TEMPLATE,
            "Setting message view with message: ${CarText.toShortString(title)} " +
                "secondary: ${CarText.toShortString(text)}" +
                " image: $image"
        )
        // TODO(b/178735593): Use ImageUtils.setImageSrc when available.
        imageView.visibility = if (image?.icon == null) GONE else VISIBLE
        imageView.setImageIcon(image?.icon?.toIcon(templateContext))

        // TODO(b/178735593): Update to use CarTextUtils when available.
        titleView.text = title?.toString()
        textView.text = text?.toString()
        textView.visibility = if (TextUtils.isEmpty(text?.toString())) GONE else VISIBLE
    }
}