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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarText;
import androidx.car.app.model.OnClickDelegate;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.view.common.CarTextUtils;
import com.android.car.libraries.apphost.view.common.ImageUtils;
import com.android.car.libraries.apphost.view.common.ImageViewParams;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.internal.CommonUtils;

/** A view that displays the header for the templates. */
public class HeaderView extends LinearLayout {
    private TextView mHeaderTitle;
    private ImageView mHeaderButtonIcon;
    private FrameLayout mButtonContainer;
    @ColorInt private final int mHeadericontint;
    private final boolean mIsCard;

    public HeaderView(Context context) {
        this(context, null);
    }

    public HeaderView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HeaderView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressWarnings("argument.type.incompatible") // Fix UnderInitialization warnings
    public HeaderView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        LayoutInflater.from(context).inflate(R.layout.header_view, this);

        @StyleableRes final int[] themeAttrs = {R.attr.templateHeaderButtonIconTint};
        TypedArray themeAttrsArray = context.obtainStyledAttributes(themeAttrs);
        mHeadericontint = themeAttrsArray.getColor(0, 0);
        themeAttrsArray.recycle();

        TypedArray headerAttrsArray =
                context.obtainStyledAttributes(
                        attrs, R.styleable.HeaderView, defStyleAttr, defStyleRes);
        mIsCard = headerAttrsArray.getBoolean(R.styleable.HeaderView_isCard, false);
        headerAttrsArray.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHeaderTitle = findViewById(R.id.header_title);
        mButtonContainer = findViewById(R.id.header_button_container);
        mHeaderButtonIcon = findViewById(R.id.header_icon);
        setMinTapTarget(
                mButtonContainer,
                getResources().getDimensionPixelSize(R.dimen.template_min_tap_target_size));
    }

    /**
     * Set or clear the content of the view.
     *
     * <p>If the {@code title} is {@code null} then the view is hidden.
     */
    public void setContent(
            TemplateContext templateContext, @Nullable CarText title, @Nullable Action action) {
        boolean isVisible = title != null;
        if (isVisible) {
            mHeaderTitle.setText(CarTextUtils.toCharSequenceOrEmpty(templateContext, title));
            mHeaderTitle.setVisibility(VISIBLE);
        } else {
            mHeaderTitle.setVisibility(GONE);
        }

        isVisible |= updateButton(templateContext, action);
        setVisibility(isVisible ? VISIBLE : GONE);
    }

    /**
     * Updates the optional button in the header.
     *
     * @return true if the button ended up visible, false otherwise.
     */
    private boolean updateButton(TemplateContext templateContext, @Nullable Action action) {
        if (action == null) {
            mButtonContainer.setVisibility(GONE);
            updateButtonReplacementPadding(/* buttonIsVisible= */ false);
            return false;
        }

        mButtonContainer.setVisibility(VISIBLE);
        updateButtonReplacementPadding(/* buttonIsVisible= */ true);

        ImageUtils.setImageSrc(
                templateContext,
                ImageUtils.getIconFromAction(action),
                mHeaderButtonIcon,
                ImageViewParams.builder()
                        .setDefaultTint(mHeadericontint)
                        .setForceTinting(true)
                        .build());

        if (action.getType() == Action.TYPE_APP_ICON) {
            // Special treatment for app icon as it is un-clickable and un-focusable.
            mButtonContainer.setFocusable(false);
            mButtonContainer.setClickable(false);
        } else if (action.getType() == Action.TYPE_BACK) {
            // Special treatment for back as it doesn't have a custom click listener
            mButtonContainer.setOnClickListener(
                    view -> templateContext.getBackPressedHandler().onBackPressed());
            mButtonContainer.setFocusable(true);
            mButtonContainer.setClickable(true);
        } else {
            OnClickDelegate onClickDelegate = action.getOnClickDelegate();
            if (onClickDelegate != null) {
                mButtonContainer.setOnClickListener(
                        view ->
                                CommonUtils.INSTANCE.dispatchClick(
                                        templateContext, onClickDelegate));
                mButtonContainer.setFocusable(true);
                mButtonContainer.setClickable(true);
            } else {
                mButtonContainer.setFocusable(false);
                mButtonContainer.setClickable(false);
            }
        }

        return true;
    }

    /** Updates padding to keep elements aligned when the button is gone */
    private void updateButtonReplacementPadding(boolean buttonIsVisible) {
        Context context = getContext();

        if (mIsCard) {
            int rightPadding =
                    context.getResources().getDimensionPixelSize(R.dimen.template_width_keyline_0);
            int leftPadding =
                    buttonIsVisible
                            ? rightPadding
                            : context.getResources()
                                    .getDimensionPixelSize(R.dimen.template_padding_5);

            mHeaderTitle.setPadding(leftPadding, /* top= */ 0, rightPadding, /* bottom= */ 0);
        }
    }

    /**
     * Sets the tap target for the given view to encompass at least the area of a square of the
     * given dimensions.
     *
     * <p>If the current tap area is already larger in either dimension, method will not shrink it
     * (hence "min" tap target).
     *
     * <p>If the current tap area is smaller, method will expand it equally on either side to meet
     * the minimum size.
     *
     * @param view the view to potentially expand the tap target for.
     * @param tapTargetSize the dimensions of a square that will become the new minimum tap target
     *     for the given view.
     */
    // TODO(b/180084331): Copied from ViewUtils. Put in clean location.
    private static void setMinTapTarget(View view, int tapTargetSize) {
        final View parent = (View) view.getParent();
        parent.post(
                () -> {
                    Rect rect = new Rect();
                    view.getHitRect(rect);

                    int rectHeight = rect.height();
                    if (rectHeight < tapTargetSize) {
                        int delta = (tapTargetSize - rectHeight) / 2;
                        rect.top -= delta;
                        rect.bottom += delta;
                    }

                    int rectWidth = rect.width();
                    if (rectWidth < tapTargetSize) {
                        int delta = (tapTargetSize - rectWidth) / 2;
                        rect.left -= delta;
                        rect.right += delta;
                    }
                    parent.setTouchDelegate(new TouchDelegate(rect, view));
                });
    }
}
