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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.res.TypedArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.StyleableRes;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.CarText;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.TemplateWrapper;

import com.android.car.libraries.apphost.common.StatusBarManager.StatusBarState;
import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.view.AbstractTemplatePresenter;
import com.android.car.libraries.apphost.view.common.CarTextUtils;
import com.android.car.libraries.apphost.view.common.ImageUtils;
import com.android.car.libraries.apphost.view.common.ImageViewParams;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.internal.CommonUtils;
import com.android.car.libraries.templates.host.view.widgets.common.ActionButtonListView;
import com.android.car.libraries.templates.host.view.widgets.common.HeaderView;

import com.google.common.collect.ImmutableList;

import java.util.List;

/** An {@link AbstractTemplatePresenter} that shows an alert message, some actions, and an icon. */
public class MessageTemplatePresenter extends AbstractTemplatePresenter {
    // TODO(b/183643108): Use a common MAX_ALLOWED_ACTIONS between AAOS and AAP
    private static final int MAX_ALLOWED_ACTIONS = 2;

    private final ViewGroup mRootView;
    private final ViewGroup mHeaderContainer;
    private final HeaderView mHeaderView;
    private final TextView mMessageTextView;
    private final ViewGroup mStackTraceContainer;
    private final TextView mStackTraceView;
    private final ImageView mIconView;
    private final ActionButtonListView mActionListView;
    private final ImageViewParams mImageViewParams;
    private final float mAdaptiveWidthFraction;
    private final boolean mIsDebugEnabled;
    private final Switch mDebugToggle;
    private final ViewGroup mDebugToggleContainer;
    private final ViewGroup mContentContainer;

    /** Create a MessageTemplatePresenter */
    static MessageTemplatePresenter create(
            TemplateContext templateContext, TemplateWrapper templateWrapper) {
        MessageTemplatePresenter presenter =
                new MessageTemplatePresenter(templateContext, templateWrapper);
        presenter.update();
        return presenter;
    }

    @Override
    protected View getDefaultFocusedView() {
        if (mActionListView.getVisibility() == VISIBLE) {
            return mActionListView;
        }
        if (mHeaderView.getVisibility() == VISIBLE) {
            return mHeaderView;
        }
        return super.getDefaultFocusedView();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
        // Move focus on up or down on a rotary nudge.
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (moveFocusIfPresent(
                    /* fromViews= */ ImmutableList.of(mActionListView, mStackTraceContainer),
                    /* toViews= */ ImmutableList.of(mHeaderView))) {
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (moveFocusIfPresent(
                    /* fromViews= */ ImmutableList.of(mHeaderView),
                    /* toViews= */ ImmutableList.of(mActionListView))) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, keyEvent);
    }

    @Override
    public void onTemplateChanged() {
        update();
    }

    @Override
    public View getView() {
        return mRootView;
    }

    /** Updates the view with current values in the {@link MessageTemplate}. */
    private void update() {
        TemplateContext templateContext = getTemplateContext();
        MessageTemplate template = (MessageTemplate) getTemplate();

        // If we have a title or a header action, show the header; hide it otherwise.
        CarText title = template.getTitle();
        Action headerAction = template.getHeaderAction();
        if (!CarText.isNullOrEmpty(title) || headerAction != null) {
            mHeaderContainer.setVisibility(VISIBLE);
            mHeaderView.setContent(getTemplateContext(), title, headerAction);
        } else {
            mHeaderContainer.setVisibility(GONE);
        }

        // Show a message if we have it, hide it otherwise.
        CarText message = template.getMessage();
        if (!CarText.isNullOrEmpty(message)) {
            mMessageTextView.setText(
                    CarTextUtils.toCharSequenceOrEmpty(templateContext, template.getMessage()));
            mMessageTextView.setVisibility(VISIBLE);

            // Allow focus on the message view if there are no actions available.
            mMessageTextView.setFocusable(template.getActions().isEmpty());
        } else {
            mMessageTextView.setVisibility(GONE);
        }

        // Show the icon if we have it, hide it otherwise.
        CarIcon icon = template.getIcon();
        boolean showIcon = icon != null;
        if (icon != null) {
            showIcon = ImageUtils.setImageSrc(templateContext, icon, mIconView, mImageViewParams);
        }
        mIconView.setVisibility(showIcon ? VISIBLE : GONE);

        // Show the action list if we have it, hide it otherwise.
        List<Action> actionList = template.getActions();
        if (!actionList.isEmpty()) {
            mActionListView.setActionList(getTemplateContext(), actionList, MAX_ALLOWED_ACTIONS);
            mActionListView.setVisibility(VISIBLE);
        } else {
            mActionListView.setVisibility(GONE);
        }

        // If we can show the debug information, add a button to the action strip that toggles it
        // on and off when tapped.
        CarText debugMessage = template.getDebugMessage();
        if (mIsDebugEnabled && !CarText.isNullOrEmpty(debugMessage)) {
            mDebugToggleContainer.setVisibility(VISIBLE);
            mDebugToggle.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> showTraceView(isChecked));

            mStackTraceView.setText(
                    CarTextUtils.toCharSequenceOrEmpty(templateContext, debugMessage));
            mStackTraceContainer.setVisibility(VISIBLE);
        } else {
            mDebugToggleContainer.setVisibility(GONE);
            mStackTraceContainer.setVisibility(GONE);
        }
    }

    private void showTraceView(boolean show) {
        mStackTraceContainer.setVisibility(show ? VISIBLE : GONE);
        mContentContainer.setVisibility(show ? GONE : VISIBLE);
    }

    /**
     * Sets the margins of the contents of the template to a fraction of the total width of the
     * screen.
     */
    private void updateAdaptiveMargins() {
        int margin = PresenterUtils.getAdaptiveMargin(getTemplateContext(), mAdaptiveWidthFraction);
        LinearLayout.LayoutParams layoutParams =
                (LinearLayout.LayoutParams) mContentContainer.getLayoutParams();
        layoutParams.setMarginStart(margin);
        layoutParams.setMarginEnd(margin);
        mContentContainer.setLayoutParams(layoutParams);
    }

    @SuppressWarnings({
            "ResourceType",
            "InflateParams",
            "method.invocation.invalid",
            "argument.type.incompatible"
    })
    private MessageTemplatePresenter(
            TemplateContext templateContext, TemplateWrapper templateWrapper) {
        super(templateContext, templateWrapper, StatusBarState.LIGHT);

        mRootView =
                (ViewGroup)
                        LayoutInflater.from(templateContext)
                                .inflate(R.layout.message_template_layout, null);
        mMessageTextView = mRootView.findViewById(R.id.message_text);
        mStackTraceContainer = mRootView.findViewById(R.id.stack_trace_container);
        mStackTraceView = mRootView.findViewById(R.id.stack_trace);
        mIconView = mRootView.findViewById(R.id.message_icon);
        mHeaderView = mRootView.findViewById(R.id.header_view);
        mHeaderContainer = mRootView.findViewById(R.id.header_container);
        mActionListView = mRootView.findViewById(R.id.action_button_list_view);
        mDebugToggle = mRootView.findViewById(R.id.debug_toggle);
        mDebugToggleContainer = mRootView.findViewById(R.id.debug_toggle_container);
        mContentContainer = mRootView.findViewById(R.id.content_container);

        @StyleableRes
        final int[] themeAttrs = {
            R.attr.templateMessageDefaultIconTint, R.attr.templateAdaptiveWidthFraction,
        };

        TypedArray ta = templateContext.obtainStyledAttributes(themeAttrs);
        @ColorInt int defaultIconTint = ta.getColor(0, 0);
        mAdaptiveWidthFraction = ta.getFloat(1, 0.f);
        ta.recycle();

        mImageViewParams = ImageViewParams.builder().setDefaultTint(defaultIconTint).build();
        // TODO(b/180084995): Re-enable debug stack trace in error template from flags
        mIsDebugEnabled = CommonUtils.INSTANCE.isConnectedToEmulator();

        updateAdaptiveMargins();
    }
}
