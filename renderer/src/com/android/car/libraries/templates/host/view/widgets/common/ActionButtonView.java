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
package com.android.car.libraries.templates.host.view.widgets.common;

import static androidx.car.app.model.Action.TYPE_BACK;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.annotation.VisibleForTesting;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.OnClickDelegate;

import com.android.car.libraries.apphost.common.CarColorUtils;
import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.distraction.constraints.CarColorConstraints;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.apphost.view.common.CarTextUtils;
import com.android.car.libraries.apphost.view.common.ImageUtils;
import com.android.car.libraries.apphost.view.common.ImageViewParams;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.internal.CommonUtils;

/** Displays an {@link Action} as a button. */
public class ActionButtonView extends FrameLayout {
    private LinearLayout mContainer;
    private final ImageViewParams mImageViewParams;
    @ColorInt private final int mDefaultBackgroundColor;
    private final int mMinWidthWithText;
    private final int mMinWidthWithoutText;

    public ActionButtonView(Context context) {
        this(context, null);
    }

    public ActionButtonView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionButtonView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressWarnings({
            "ResourceType",
            "method.invocation.invalid",
            "assignment.type.incompatible",
            "argument.type.incompatible"
    })
    public ActionButtonView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        @StyleableRes
        final int[] themeAttrs = {
            R.attr.templateActionButtonDefaultBackgroundColor,
            R.attr.templateActionDefaultIconTint,
            R.attr.templateActionWithTextMinWidth,
            R.attr.templateActionWithoutTextMinWidth,
        };

        TypedArray ta = context.obtainStyledAttributes(themeAttrs);
        mDefaultBackgroundColor = ta.getColor(0, 0);
        @ColorInt int defaultIconTint = ta.getColor(1, 0);
        mMinWidthWithText = ta.getDimensionPixelSize(2, 0);
        mMinWidthWithoutText = ta.getDimensionPixelSize(3, 0);
        ta.recycle();
        mImageViewParams =
                ImageViewParams.builder()
                        .setDefaultTint(defaultIconTint)
                        .setForceTinting(true)
                        .build();
    }

    /** Returns the {@link android.view.View} title for testing. */
    @Nullable
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public String getTitle() {
        TextView textView = mContainer.findViewById(R.id.action_text);
        if (textView == null) {
            return null;
        }
        return textView.getText().toString();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContainer = findViewById(R.id.action_container);
    }

    /** Updates the view from the {@link Action} model. */
    public ActionButtonView setAction(TemplateContext templateContext, Action action) {
        L.v(LogTags.TEMPLATE, "Setting action view with action: %s", action);

        LayoutInflater inflater = LayoutInflater.from(getContext());

        CarColor color = action.getBackgroundColor();
        if (color != null) {
            applyBackgroundColor(templateContext, color);
        } else {
            L.w(LogTags.TEMPLATE, "Background color on the Action is expected but not set");
        }

        mContainer.removeAllViews();

        CharSequence title = CarTextUtils.toCharSequenceOrEmpty(templateContext, action.getTitle());
        CarIcon icon = ImageUtils.getIconFromAction(action);

        boolean hasTitle = title.length() > 0;

        setMinimumWidth(hasTitle ? mMinWidthWithText : mMinWidthWithoutText);

        // Add the icon.
        if (icon != null) {
            inflater.inflate(R.layout.action_button_view_icon, mContainer);
            ImageView iconView = mContainer.findViewById(R.id.action_icon);

            if (hasTitle) {
                // If the button has a title, the icon needs to have a bit extra padding at the
                // start.
                LinearLayout.LayoutParams layoutParams =
                        (LinearLayout.LayoutParams) iconView.getLayoutParams();
                layoutParams.setMarginStart(
                        getResources().getDimensionPixelSize(R.dimen.template_padding_3));
                iconView.setLayoutParams(layoutParams);
            }

            ImageUtils.setImageSrc(templateContext, icon, iconView, mImageViewParams);
        }

        // Add the text view.
        if (hasTitle) {
            inflater.inflate(
                    icon != null
                            ? R.layout.action_button_view_text
                            : R.layout.action_button_view_text_no_icon,
                    mContainer);
            TextView textView = mContainer.findViewById(R.id.action_text);
            textView.setText(title);
        }

        // Update the click listener, if one is set.
        if (action.getType() == TYPE_BACK) {
            setOnClickListener(v -> templateContext.getBackPressedHandler().onBackPressed());
        } else {
            OnClickDelegate onClickDelegate = action.getOnClickDelegate();
            if (onClickDelegate != null) {
                setOnClickListener(
                        v -> {
                            // TODO(b/179828022): Fix Telemetry
                            // logCarAppTelemetry(
                            //     UiAction.ACTION_BUTTON_CLICKED,
                            //     templateContext.getCarAppPackageInfo().getComponentName());
                            CommonUtils.INSTANCE.dispatchClick(templateContext, onClickDelegate);
                        });
            } else {
                setOnClickListener(null);
            }
        }

        return this;
    }

    private void applyBackgroundColor(TemplateContext templateContext, CarColor backgroundColor) {
        @ColorInt
        int color =
                CarColorUtils.resolveColor(
                        templateContext,
                        backgroundColor,
                        true,
                        mDefaultBackgroundColor,
                        CarColorConstraints.STANDARD_ONLY);

        // Set the background as tint to not override the round-corner drawable with ripple effects.
        setBackgroundTintList(ColorStateList.valueOf(color));
    }
}
