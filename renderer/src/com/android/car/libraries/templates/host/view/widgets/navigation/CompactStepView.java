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
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.annotation.VisibleForTesting;
import androidx.car.app.model.CarIcon;
import androidx.car.app.navigation.model.Maneuver;
import androidx.car.app.navigation.model.Step;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.apphost.view.common.CarTextParams;
import com.android.car.libraries.apphost.view.common.CarTextUtils;
import com.android.car.libraries.apphost.view.common.ImageUtils;
import com.android.car.libraries.apphost.view.common.ImageViewParams;
import com.android.car.libraries.templates.host.R;

/**
 * A view that displays a compact view of a navigation maneuver.
 *
 * <p>For example it could show just the maneuver description or a description and an icon.
 */
public class CompactStepView extends LinearLayout {
    private ImageView mTurnSymbolView;
    private TextView mDescriptionText;
    @Nullable private Step mStep;
    private final ImageViewParams mImageViewParams;

    public CompactStepView(Context context) {
        this(context, null);
    }

    public CompactStepView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CompactStepView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CompactStepView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
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
        mTurnSymbolView = findViewById(R.id.compact_turn_symbol);
        mDescriptionText = findViewById(R.id.compact_description_text);
    }

    /**
     * Sets the {@link Step} to be shown.
     *
     * <p>Setting a {@code null} steo will cause the view to be hidden.
     */
    public void setStep(
            TemplateContext templateContext, @Nullable Step step, CarTextParams carTextParams) {
        L.v(LogTags.TEMPLATE, "Setting compact step view with step: %s", step);

        mStep = step;
        if (step == null) {
            setVisibility(GONE);
            return;
        }
        Maneuver maneuver = step.getManeuver();
        CarIcon turnIcon = maneuver == null ? null : maneuver.getIcon();
        boolean shouldShowTurnIcon =
                ImageUtils.setImageSrc(
                        templateContext, turnIcon, mTurnSymbolView, mImageViewParams);
        mTurnSymbolView.setVisibility(shouldShowTurnIcon ? VISIBLE : GONE);

        mDescriptionText.setText(
                CarTextUtils.toCharSequenceOrEmpty(templateContext, step.getCue(), carTextParams));
        setVisibility(VISIBLE);
    }

    @VisibleForTesting
    @Nullable
    public Step getStep() {
        return mStep;
    }
}
