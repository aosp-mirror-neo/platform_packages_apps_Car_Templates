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
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.CarText;
import androidx.car.app.model.GridItem;
import androidx.car.app.model.OnClickDelegate;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.apphost.template.view.model.SelectionGroup;
import com.android.car.libraries.apphost.view.common.CarTextParams;
import com.android.car.libraries.apphost.view.common.CarTextUtils;
import com.android.car.libraries.apphost.view.common.ImageUtils;
import com.android.car.libraries.apphost.view.common.ImageViewParams;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.internal.CommonUtils;

/** A view that can display a {@link GridItem} model. */
public class GridItemView extends LinearLayout {
    private static final int[] STATE_INACTIVE_FOCUS = {R.attr.templateFocusStateInactive};

    /** Text parameters for secondary text in a grid item. */
    private static final CarTextParams TEXT_PARAMS_SECONDARY_TEXT =
            CarTextParams.builder().setAllowColorSpans(true).setMaxImages(0).build();
    /**
     * Indicates whether or not this grid item has inactive focus.
     *
     * <p>The grid item has an inactive focus when it is not clickable.
     */
    private boolean mHasInactiveFocus;

    private final int mGridItemIconSize;
    private final int mGridItemImageSizeLarge;
    @ColorInt private final int mDefaultIconTint;
    private final int mHorizontalTextBottomPadding;
    private final Drawable mGridItemBackground;

    private LinearLayout mTextContainer;
    private TextView mTitleView;
    private TextView mTextview;
    private ImageView mImageView;
    private ProgressBar mProgressBar;

    public GridItemView(Context context) {
        this(context, null);
    }

    public GridItemView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressWarnings({
            "ResourceType",
            "method.invocation.invalid",
            "assignment.type.incompatible",
            "argument.type.incompatible"
    })
    public GridItemView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        @StyleableRes
        final int[] themeAttrs = {
            R.attr.templateGridItemIconSize,
            R.attr.templateGridItemImageSizeLarge,
            R.attr.templateGridItemDefaultIconTint,
            R.attr.templateGridItemTextBottomPadding,
            R.attr.templateGridItemBackground,
        };

        TypedArray ta = context.obtainStyledAttributes(themeAttrs);
        mGridItemIconSize = ta.getDimensionPixelSize(0, -1);
        mGridItemImageSizeLarge = ta.getDimensionPixelSize(1, -1);
        mDefaultIconTint = ta.getColor(2, 0);
        mHorizontalTextBottomPadding = ta.getDimensionPixelSize(3, 0);
        mGridItemBackground = ta.getDrawable(4);
        ta.recycle();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTextContainer = findViewById(R.id.grid_item_text_container);
        mTitleView = findViewById(R.id.grid_item_title);
        mTextview = findViewById(R.id.grid_item_text);
        mImageView = findViewById(R.id.grid_item_image);
        mProgressBar = findViewById(R.id.grid_item_progress_bar);
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        if (mHasInactiveFocus) {
            // We are going to add 1 extra state.
            final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);

            mergeDrawableStates(drawableState, STATE_INACTIVE_FOCUS);
            return drawableState;
        } else {
            return super.onCreateDrawableState(extraSpace);
        }
    }

    /** Updates the view with the given {@link GridItemWrapper}. */
    public void setGridItem(
            TemplateContext templateContext,
            GridItemWrapper gridItemWrapper,
            boolean shouldShowTitle,
            boolean shouldShowText) {
        GridItem gridItem = gridItemWrapper.getGridItem();

        L.v(LogTags.TEMPLATE, "Setting grid item view with grid item: %s", gridItem);

        // Unset any click/focus listeners tied to the previous content. New ones will be added
        // below.
        setOnClickListener(null);
        setOnFocusChangeListener(null);

        updateTextView(
                templateContext,
                mTitleView,
                gridItem.getTitle(),
                CarTextParams.DEFAULT,
                shouldShowTitle);
        updateTextView(
                templateContext,
                mTextview,
                gridItem.getText(),
                TEXT_PARAMS_SECONDARY_TEXT,
                shouldShowText);

        mTextContainer.setPadding(
                0,
                mTextContainer.getPaddingTop(),
                0,

                // If there is not secondary text to be shown, add an extra padding at the bottom of
                // the
                // container. This makes it so that there's more separation between rows when
                // there's only
                // a title (for example, in the system's wallpaper picker), while we use up some
                // more
                // of the vertical space for text when there is a secondary line.
                mTextContainer.getPaddingBottom()
                        + (shouldShowText ? 0 : mHorizontalTextBottomPadding));

        SelectionGroup selectionGroup = gridItemWrapper.getSelectionGroup();
        OnClickDelegate onClickDelegate = gridItem.getOnClickDelegate();

        boolean isLoading = gridItem.isLoading();

        // The grid item is clickable iff...
        boolean isClickable =
                // ...it is not in the loading state, and
                !isLoading
                        && (
                        // ...it has a click listener coming from the client
                        onClickDelegate != null
                                // ...is selectable
                                || selectionGroup != null);

        // Show either the image or the loading spinner.
        mProgressBar.setVisibility(isLoading ? VISIBLE : GONE);
        mImageView.setVisibility(isLoading ? GONE : VISIBLE);
        if (!isLoading) {
            int imageType = gridItem.getImageType();

            // Show the grid item image.
            CarIcon image = gridItem.getImage();
            ImageUtils.setImageSrc(
                    templateContext,
                    image,
                    mImageView,
                    ImageViewParams.builder()
                            .setDefaultTint(mDefaultIconTint)
                            .setForceTinting(imageType == GridItem.IMAGE_TYPE_ICON)
                            .build());

            // Set size of the image based on image type.
            int imageSize = getImageSize(imageType);
            LayoutParams layoutParams = (LayoutParams) mImageView.getLayoutParams();
            layoutParams.width = imageSize;
            layoutParams.height = imageSize;
            mImageView.setLayoutParams(layoutParams);

            // Set the onClickListener on the grid item iff...
            if (onClickDelegate != null) {
                // ...it has a click listener from the client. Dispatch click event to the
                // onClickListener.
                setOnClickListener(
                        v -> {
                            CommonUtils.INSTANCE.dispatchClick(templateContext, onClickDelegate);
                        });
            } else if (selectionGroup != null) {
                // ...it is part of a selection group. Dispatch a selection change event to the
                // selection
                // group's onSelectedListener.
                setOnClickListener(
                        v -> {
                            int currentSelectionIndex = selectionGroup.getSelectedIndex();
                            int newIndex = gridItemWrapper.getGridItemIndex();

                            if (currentSelectionIndex != newIndex) {
                                selectionGroup.setSelectedIndex(newIndex);
                            }

                            // Dispatch the selection callbacks.
                            // Note the selection event is dispatched regardless of selection index
                            // actually
                            // changing.
                            templateContext
                                    .getAppDispatcher()
                                    .dispatchSelected(
                                            selectionGroup.getOnSelectedDelegate(),
                                            selectionGroup.getRelativeIndex(newIndex));
                        });
            }
        }

        setClickable(isClickable);
        setBackground(mGridItemBackground);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        setInactiveFocus(!isClickable);
    }

    private static void updateTextView(
            TemplateContext templateContext,
            TextView textView,
            @Nullable CarText text,
            CarTextParams textParams,
            boolean shouldShowTextView) {
        // The visibility of the text view inside a grid view depends on all the grid items in the
        // row.
        // It's possible that this particular grid item doesn't have a valid title or text, but
        // another
        // grid item in the row may have a title. We need to have consistent height and focus states
        // for
        // all the grid items in a gird row. Using information provided by the grid row container to
        // decide the visibility of text view's inside a grid item.
        textView.setVisibility(shouldShowTextView ? VISIBLE : GONE);

        if (text != null && !text.isEmpty()) {
            // With the "normal" buffer type, the text view sets a spanned text with immutable
            // spans.
            // BufferType.SPANNABLE allows mutable spans, but causes issues with ellipsized texts
            // (See b/157754626).
            textView.setText(
                    CarTextUtils.toCharSequenceOrEmpty(templateContext, text, textParams),
                    BufferType.NORMAL);
        }
    }

    private int getImageSize(int imageType) {
        switch (imageType) {
            case GridItem.IMAGE_TYPE_ICON:
                return mGridItemIconSize;
            case GridItem.IMAGE_TYPE_LARGE:
                return mGridItemImageSizeLarge;
            default:
                throw new IllegalArgumentException("Unrecognized image type: " + imageType);
        }
    }

    /** @see #mHasInactiveFocus */
    private void setInactiveFocus(boolean hasInactiveFocus) {
        if (mHasInactiveFocus != hasInactiveFocus) {
            mHasInactiveFocus = hasInactiveFocus;

            // Refresh the drawable state so that it includes the inactive focus state.
            refreshDrawableState();
        }
    }
}
