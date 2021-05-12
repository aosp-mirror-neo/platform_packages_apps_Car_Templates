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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.annotation.VisibleForTesting;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.OnClickDelegate;
import androidx.car.app.model.Toggle;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.apphost.view.common.CarTextUtils;
import com.android.car.libraries.apphost.view.common.ImageUtils;
import com.android.car.libraries.apphost.view.common.ImageViewParams;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.internal.CommonUtils;

/** Displays an {@link Action} or {@link Toggle} as a FAB. */
// TODO(b/158142806): Merge with ActionButtonView
public class FabView extends LinearLayout {
    private Object mAction;
    private final ImageViewParams mImageViewParams;
    private final int mMinWidthWithText;
    private final int mMinWidthWithoutText;
    @ColorInt private final int mContentColor;

    public FabView(Context context) {
        this(context, null);
    }

    public FabView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FabView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressWarnings({
            "ResourceType",
            "method.invocation.invalid",
            "assignment.type.incompatible",
            "argument.type.incompatible"
    })
    public FabView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        @StyleableRes
        final int[] themeAttrs = {
            R.attr.templateActionWithTextMinWidth, R.attr.templateActionWithoutTextMinWidth,
        };

        TypedArray ta = context.obtainStyledAttributes(themeAttrs);
        mMinWidthWithText = ta.getDimensionPixelSize(0, 0);
        mMinWidthWithoutText = ta.getDimensionPixelSize(1, 0);
        ta.recycle();

        ta = context.obtainStyledAttributes(defStyleRes, new int[] {R.attr.fabDefaultContentColor});
        mContentColor = ta.getColor(0, -1);
        ta.recycle();

        mImageViewParams =
                ImageViewParams.builder()
                        .setDefaultTint(mContentColor)
                        .setForceTinting(true)
                        .build();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public Object getAction() {
        return mAction;
    }

    /**
     * Updates the view from based on the input {@code action}.
     *
     * <p>Note that {@code action} is expected to be of type {@link Action} or {@link Toggle}.
     */
    public void setAction(TemplateContext templateContext, Object action) {
        removeAllViews();
        mAction = action;

        if (action instanceof Action) {
            populateAction(templateContext, (Action) action);
        } else {
            L.w(LogTags.TEMPLATE, "Unsupported action object type: %s", action.getClass());
        }
    }

    private void populateAction(TemplateContext templateContext, Action action) {
        CharSequence title = CarTextUtils.toCharSequenceOrEmpty(templateContext, action.getTitle());
        CarIcon icon = ImageUtils.getIconFromAction(action);

        LayoutInflater inflater = LayoutInflater.from(getContext());

        boolean hasTitle = title.length() > 0;
        setMinimumWidth(hasTitle ? mMinWidthWithText : mMinWidthWithoutText);

        // Add the icon.
        if (icon != null) {
            inflater.inflate(R.layout.action_button_view_icon, this);
            ImageView iconView = findViewById(R.id.action_icon);

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
                    icon != null ? R.layout.fab_view_text : R.layout.fab_view_text_no_icon, this);
            TextView textView = findViewById(R.id.action_text);
            textView.setText(title);
            textView.setTextColor(mContentColor);
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
                            //     UiAction.ACTION_STRIP_FAB_CLICKED,
                            //     templateContext.getCarAppPackageInfo().getComponentName());
                            CommonUtils.INSTANCE.dispatchClick(templateContext, onClickDelegate);
                        });
            } else {
                setOnClickListener(null);
            }
        }
    }
}
