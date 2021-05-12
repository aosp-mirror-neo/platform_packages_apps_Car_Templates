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
package com.android.car.libraries.apphost.view;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

import androidx.annotation.VisibleForTesting;
import androidx.car.app.model.Template;
import androidx.car.app.model.TemplateWrapper;

import com.android.car.libraries.apphost.common.StatusBarManager.StatusBarState;
import com.android.car.libraries.apphost.common.TemplateContext;

/**
 * Abstract base class for {@link TemplatePresenter}s which have a {@link
 * androidx.car.app.SurfaceContainer}.
 */
public abstract class AbstractSurfaceTemplatePresenter extends AbstractTemplatePresenter {
    private final OnGlobalLayoutListener mGlobalLayoutListener =
            new OnGlobalLayoutListener() {
                @SuppressWarnings("nullness") // suppress under initialization warning for this
                @Override
                public void onGlobalLayout() {
                    View rootView = getView();
                    Rect safeAreaInset = new Rect();
                    safeAreaInset.left = rootView.getLeft() + rootView.getPaddingLeft();
                    safeAreaInset.top = rootView.getTop() + rootView.getPaddingTop();
                    safeAreaInset.bottom = rootView.getBottom() - rootView.getPaddingBottom();
                    safeAreaInset.right = rootView.getRight() - rootView.getPaddingRight();
                    calculateAdditionalInset(safeAreaInset);
                    getTemplateContext().getSurfaceInfoProvider().setVisibleArea(safeAreaInset);
                }
            };

    /**
     * Constructs a new instance of a {@link AbstractTemplatePresenter} with the given {@link
     * Template}.
     */
    public AbstractSurfaceTemplatePresenter(
            TemplateContext templateContext,
            TemplateWrapper templateWrapper,
            StatusBarState statusBarState) {
        super(templateContext, templateWrapper, statusBarState);
    }

    @Override
    public void onPause() {
        getView().getViewTreeObserver().removeOnGlobalLayoutListener(mGlobalLayoutListener);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        getView().getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);
    }

    @Override
    public boolean usesSurface() {
        return true;
    }

    @Override
    public boolean isFullScreen() {
        return false;
    }

    /** Adjusts the {@code inset} according to the views visible on screen. */
    public abstract void calculateAdditionalInset(Rect inset);

    /** Returns the {@link OnGlobalLayoutListener} instance attached to the view tree. */
    @VisibleForTesting
    public OnGlobalLayoutListener getGlobalLayoutListener() {
        return mGlobalLayoutListener;
    }
}
