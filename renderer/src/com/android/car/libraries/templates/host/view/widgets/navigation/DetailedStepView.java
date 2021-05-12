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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.annotation.VisibleForTesting;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Distance;
import androidx.car.app.navigation.model.Maneuver;
import androidx.car.app.navigation.model.Step;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.apphost.view.common.CarTextParams;
import com.android.car.libraries.apphost.view.common.CarTextUtils;
import com.android.car.libraries.apphost.view.common.DistanceUtils;
import com.android.car.libraries.apphost.view.common.ImageUtils;
import com.android.car.libraries.apphost.view.common.ImageViewParams;
import com.android.car.libraries.templates.host.R;

/**
 * A view that displays a detailed navigation step.
 *
 * <p>This view tries to display all the elements of a {@link Step} and {@link Distance}. For
 * example if available, it would show a turn icon, description and lanes image. It could be used
 * with another view to show the next turn.
 */
public class DetailedStepView extends LinearLayout {
    private ImageView mTurnSymbolView;
    private TextView mDistanceText;
    private TextView mDescriptionText;
    private ImageView mLanesImageView;
    private LinearLayout mTurnContainerView;
    private FrameLayout mLanesImageContainerView;
    private final ImageViewParams mImageViewParams;

    @Nullable private Step mStep;
    @Nullable private Distance mDistance;

    public DetailedStepView(Context context) {
        this(context, null);
    }

    public DetailedStepView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DetailedStepView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
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
        mTurnSymbolView = findViewById(R.id.turn_symbol);
        mDistanceText = findViewById(R.id.distance_text);
        mDescriptionText = findViewById(R.id.description_text);
        mLanesImageView = findViewById(R.id.lanes_image);
        mTurnContainerView = findViewById(R.id.turn_container);
        mLanesImageContainerView = findViewById(R.id.lanes_image_container);
    }

    /**
     * Sets the {@link Step} and {@link Distance} to be shown.
     *
     * <p>If the {@link Step} is {@code null} then the entire view is hidden. If the {@link
     * Distance} is null then the just the distance text is hidden and the step is still shown.
     */
    public void setStepAndDistance(
            TemplateContext templateContext,
            @Nullable Step step,
            @Nullable Distance distance,
            CarTextParams cueTextParams,
            boolean hideLaneImages) {
        L.v(
                LogTags.TEMPLATE,
                "Setting detailed step view with step: %s, and distance: %s",
                step,
                distance);

        mStep = step;
        if (step == null) {
            setVisibility(GONE);
            return;
        }
        mDistance = distance;
        Maneuver maneuver = step.getManeuver();
        CarIcon turnIcon = maneuver == null ? null : maneuver.getIcon();
        boolean shouldShowTurnIcon =
                ImageUtils.setImageSrc(
                        templateContext, turnIcon, mTurnSymbolView, mImageViewParams);
        mTurnSymbolView.setVisibility(shouldShowTurnIcon ? VISIBLE : GONE);

        if (distance != null) {
            mDistanceText.setText(
                    DistanceUtils.convertDistanceToDisplayString(templateContext, distance));
            mDistanceText.setVisibility(VISIBLE);
        } else {
            mDistanceText.setVisibility(GONE);
        }

        mDescriptionText.setText(
                CarTextUtils.toCharSequenceOrEmpty(templateContext, step.getCue(), cueTextParams));
        mDescriptionText.setVisibility(VISIBLE);

        CarIcon laneImage = step.getLanesImage();
        boolean shouldShowLanesImage =
                !hideLaneImages
                        && ImageUtils.setImageSrc(
                                templateContext, laneImage, mLanesImageView, mImageViewParams);

        int turnContainerBottomMargin;
        if (shouldShowLanesImage) {
            mLanesImageContainerView.setVisibility(VISIBLE);
            turnContainerBottomMargin =
                    getResources().getDimensionPixelSize(R.dimen.template_padding_1);
        } else {
            mLanesImageContainerView.setVisibility(GONE);
            turnContainerBottomMargin =
                    getResources().getDimensionPixelSize(R.dimen.template_padding_2);
        }
        LinearLayout.LayoutParams layoutParams =
                (LinearLayout.LayoutParams) mTurnContainerView.getLayoutParams();
        layoutParams.bottomMargin = turnContainerBottomMargin;
        mTurnContainerView.setLayoutParams(layoutParams);

        setVisibility(VISIBLE);
    }

    @VisibleForTesting
    @Nullable
    public Step getStep() {
        return mStep;
    }

    @VisibleForTesting
    @Nullable
    public Distance getDistance() {
        return mDistance;
    }
}
