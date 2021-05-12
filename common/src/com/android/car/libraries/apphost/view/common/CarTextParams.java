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
package com.android.car.libraries.apphost.view.common;

import android.graphics.Color;
import android.graphics.Rect;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.car.app.model.CarColor;

/** Encapsulates parameters that configure the way car text instances are rendered. */
public class CarTextParams {
    /** Default params which should be used for most text in all templates. */
    public static final CarTextParams DEFAULT =
            new CarTextParams(
                    /* allowColorSpans= */ false,
                    /* allowClickableSpans= */ false,
                    /* imageBoundingBox= */ null,
                    /* maxImages= */ 0,
                    // No need to pass icon tint since no images are allowed.
                    /* defaultIconTint= */ Color.TRANSPARENT);

    @Nullable private final Rect mImageBoundingBox;
    private final int mMaxImages;
    @ColorInt private final int mDefaultIconTint;
    private final boolean mAllowColorSpans;
    private final boolean mAllowClickableSpans;

    /** Returns a builder of {@link CarTextParams}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the bounding box for a span image.
     *
     * <p>Images are scaled to fit within this bounding box.
     */
    @Nullable
    Rect getImageBoundingBox() {
        return mImageBoundingBox;
    }

    /** Returns the maximum number of image spans to allow in the text. */
    int getMaxImages() {
        return mMaxImages;
    }

    /** Returns whether color spans are allowed in the text. */
    boolean getAllowColorSpans() {
        return mAllowColorSpans;
    }

    /** Returns whether clickable spans are allowed in the text. */
    boolean getAllowClickableSpans() {
        return mAllowClickableSpans;
    }

    @ColorInt
    int getDefaultIconTint() {
        return mDefaultIconTint;
    }

    private CarTextParams(
            boolean allowColorSpans,
            boolean allowClickableSpans,
            @Nullable Rect imageBoundingBox,
            int maxImages,
            @ColorInt int defaultIconTint) {
        mAllowColorSpans = allowColorSpans;
        mAllowClickableSpans = allowClickableSpans;
        mImageBoundingBox = imageBoundingBox;
        mMaxImages = maxImages;
        mDefaultIconTint = defaultIconTint;
    }

    /** A builder of {@link CarTextParams} instances. */
    public static class Builder {
        private boolean mAllowColorSpans;
        private boolean mAllowClickableSpans;
        @Nullable private Rect mImageBoundingBox;
        private int mMaxImages;
        @ColorInt private int mDefaultIconTint = Color.TRANSPARENT;

        /**
         * Sets whether color spans are allowed in the text.
         *
         * <p>By default, no color spans are allowed in the text.
         *
         * @see #getAllowColorSpans()
         */
        public Builder setAllowColorSpans(boolean allowColorSpans) {
            mAllowColorSpans = allowColorSpans;
            return this;
        }

        /**
         * Sets whether clickable spans are allowed in the text.
         *
         * <p>By default, no clickable spans are allowed in the text.
         *
         * @see #getAllowClickableSpans()
         */
        public Builder setAllowClickableSpans(boolean allowClickableSpans) {
            mAllowClickableSpans = allowClickableSpans;
            return this;
        }

        /**
         * Sets the bounding box for the image spans.
         *
         * <p>By default, no bounding box is specified.
         *
         * @see #getImageBoundingBox()
         */
        public Builder setImageBoundingBox(@Nullable Rect imageBoundingBox) {
            mImageBoundingBox = imageBoundingBox;
            return this;
        }

        /**
         * Sets the maximum number of image spans to allow for the text.
         *
         * <p>By default, no images are allowed in the text.
         *
         * @see #getMaxImages()
         */
        public Builder setMaxImages(int maxImages) {
            mMaxImages = maxImages;
            return this;
        }

        /**
         * Sets the default tint to use for the images in the span that set their tint to {@link
         * CarColor#DEFAULT}.
         *
         * <p>This tint may vary depending on where the spans are rendered, and can be specified
         * here.
         *
         * <p>By default, this tint is transparent.
         */
        public Builder setDefaultIconTint(@ColorInt int defaultIconTint) {
            mDefaultIconTint = defaultIconTint;
            return this;
        }

        /** Constructs a {@link CarTextParams} instance defined by this builder. */
        public CarTextParams build() {
            if (mImageBoundingBox == null && mMaxImages > 0) {
                throw new IllegalStateException(
                        "A bounding box needs to be provided if images are allowed in the text");
            }

            return new CarTextParams(
                    mAllowColorSpans,
                    mAllowClickableSpans,
                    mImageBoundingBox,
                    mMaxImages,
                    mDefaultIconTint);
        }
    }
}
