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
package com.android.car.libraries.templates.host.view.widgets.navigation;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.CarText;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.apphost.view.common.CarTextParams;
import com.android.car.libraries.apphost.view.common.CarTextUtils;
import com.android.car.libraries.apphost.view.common.ImageUtils;
import com.android.car.libraries.apphost.view.common.ImageViewParams;
import com.android.car.libraries.templates.host.R;

/** A view that displays a message with optional image and subtext. */
public class MessageView extends LinearLayout {
    private ImageView mImageView;
    private TextView mTitleView;
    private TextView mTextView;
    private final ImageViewParams mImageViewParams;

    public MessageView(@NonNull Context context) {
        this(context, null);
    }

    public MessageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressWarnings({"argument.type.incompatible"})
    public MessageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        @StyleableRes
        final int[] themeAttrs = {
            R.attr.templateRoutingDefaultIconTint,
        };
        TypedArray ta = context.obtainStyledAttributes(themeAttrs);
        @ColorInt int defaultIconTint = ta.getColor(0, 0);
        ta.recycle();
        mImageViewParams = ImageViewParams.builder().setDefaultTint(defaultIconTint).build();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mImageView = findViewById(R.id.message_image);
        mTitleView = findViewById(R.id.message_title);
        mTextView = findViewById(R.id.message_text);
    }

    /** Sets the title, image and text content the view. */
    public void setMessage(
            TemplateContext templateContext,
            @Nullable CarIcon image,
            CarText title,
            @Nullable CarText text) {
        L.v(
                LogTags.TEMPLATE,
                "Setting message view with message: %s secondary: %s image: %s",
                title,
                text,
                image);

        boolean shouldShowImage =
                ImageUtils.setImageSrc(templateContext, image, mImageView, mImageViewParams);
        mImageView.setVisibility(shouldShowImage ? VISIBLE : GONE);

        CharSequence messageTitle =
                CarTextUtils.toCharSequenceOrEmpty(templateContext, title, CarTextParams.DEFAULT);
        mTitleView.setText(messageTitle);

        CharSequence messageText =
                CarTextUtils.toCharSequenceOrEmpty(templateContext, text, CarTextParams.DEFAULT);
        mTextView.setText(messageText);
        boolean shouldShowSecondaryMessage = !TextUtils.isEmpty(messageText);
        mTextView.setVisibility(shouldShowSecondaryMessage ? VISIBLE : GONE);
    }
}
