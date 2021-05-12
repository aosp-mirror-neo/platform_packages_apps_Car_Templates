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
package com.android.car.libraries.templates.host.view.presenters.common;

import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.GridTemplate;
import androidx.car.app.model.TemplateWrapper;

import com.android.car.libraries.apphost.common.StatusBarManager.StatusBarState;
import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.distraction.constraints.ActionsConstraints;
import com.android.car.libraries.apphost.view.AbstractTemplatePresenter;
import com.android.car.libraries.apphost.view.TemplatePresenter;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.view.widgets.common.ActionStripView;
import com.android.car.libraries.templates.host.view.widgets.common.ContentView;
import com.android.car.libraries.templates.host.view.widgets.common.GridWrapper;
import com.android.car.libraries.templates.host.view.widgets.common.HeaderView;

import com.google.common.collect.ImmutableList;

import java.util.List;

/** A {@link TemplatePresenter} for {@link GridTemplate} instances. */
public class GridTemplatePresenter extends AbstractTemplatePresenter {
    private final ViewGroup mRootView;
    private final HeaderView mHeaderView;
    private final ContentView mContentView;
    private final ActionStripView mActionStripView;
    private final List<View> mRotaryNudgeTopViews;
    private final List<View> mRotaryNudgeBottomViews;

    /** Create a GridTemplatePresenter */
    public static GridTemplatePresenter create(
            TemplateContext templateContext, TemplateWrapper templateWrapper) {
        GridTemplatePresenter presenter =
                new GridTemplatePresenter(templateContext, templateWrapper);
        presenter.update();
        return presenter;
    }

    @Override
    public View getView() {
        return mRootView;
    }

    @Override
    public void onTemplateChanged() {
        update();
    }

    @Override
    protected View getDefaultFocusedView() {
        if (mContentView.getVisibility() == VISIBLE) {
            return mContentView;
        }
        return super.getDefaultFocusedView();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
        // Move focus on up or down rotary nudge.
        // Up from a grid item: move to the back button or action strip button
        // Down from the back or action strip button: move to the grid item
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (moveFocusIfPresent(mRotaryNudgeBottomViews, mRotaryNudgeTopViews)) {
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (moveFocusIfPresent(mRotaryNudgeTopViews, mRotaryNudgeBottomViews)) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, keyEvent);
    }

    private void update() {
        GridTemplate template = (GridTemplate) getTemplate();
        ActionStrip actionStrip = template.getActionStrip();
        GridWrapper gridWrapper;
        if (template.isLoading()) {
            gridWrapper =
                    GridWrapper.wrap(null)
                            .setIsLoading(true)
                            .setIsRefresh(getTemplateWrapper().isRefresh())
                            .build();
        } else {
            gridWrapper =
                    GridWrapper.wrap(template.getSingleList())
                            .setIsRefresh(getTemplateWrapper().isRefresh())
                            .build();
        }

        mActionStripView.setActionStrip(
                getTemplateContext(), actionStrip, ActionsConstraints.ACTIONS_CONSTRAINTS_SIMPLE);
        mHeaderView.setContent(
                getTemplateContext(), template.getTitle(), template.getHeaderAction());
        mContentView.setGridContent(getTemplateContext(), gridWrapper);
    }

    @SuppressLint("InflateParams")
    @SuppressWarnings({"methodref.receiver.bound.invalid"})
    private GridTemplatePresenter(
            TemplateContext templateContext, TemplateWrapper templateWrapper) {
        super(templateContext, templateWrapper, StatusBarState.LIGHT);

        mRootView =
                (ViewGroup)
                        LayoutInflater.from(templateContext)
                                .inflate(R.layout.grid_wrapper_template_layout, null);
        mHeaderView = mRootView.findViewById(R.id.header_view);
        mContentView = mRootView.findViewById(R.id.grid_content_view);
        mActionStripView = mRootView.findViewById(R.id.action_strip);
        mRotaryNudgeTopViews = ImmutableList.of(mHeaderView, mActionStripView);
        mRotaryNudgeBottomViews = ImmutableList.of(mContentView);
    }
}
