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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StyleableRes;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarText;
import androidx.car.app.model.TemplateWrapper;
import androidx.car.app.model.signin.InputSignInMethod;
import androidx.car.app.model.signin.ProviderSignInMethod;
import androidx.car.app.model.signin.SignInTemplate;
import androidx.car.app.model.signin.SignInTemplate.SignInMethod;

import com.android.car.libraries.apphost.common.EventManager;
import com.android.car.libraries.apphost.common.EventManager.EventType;
import com.android.car.libraries.apphost.common.StatusBarManager.StatusBarState;
import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.distraction.constraints.ActionsConstraints;
import com.android.car.libraries.apphost.input.InputManager;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.apphost.view.AbstractTemplatePresenter;
import com.android.car.libraries.apphost.view.TemplatePresenter;
import com.android.car.libraries.apphost.view.common.CarTextParams;
import com.android.car.libraries.apphost.view.common.CarTextUtils;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.view.widgets.common.ActionButtonListView;
import com.android.car.libraries.templates.host.view.widgets.common.ActionButtonView;
import com.android.car.libraries.templates.host.view.widgets.common.ActionStripView;
import com.android.car.libraries.templates.host.view.widgets.common.HeaderView;
import com.android.car.libraries.templates.host.view.widgets.common.InputSignInView;

import java.util.List;

/** A {@link TemplatePresenter} for {@link SignInTemplate} instances. */
public class SignInTemplatePresenter extends AbstractTemplatePresenter {
    // TODO(b/183643108): Use a common MAX_ALLOWED_ACTIONS between AAOS and AAP
    private static final int MAX_ALLOWED_ACTIONS = 2;
    private static final CarTextParams INSTRUCTION_TEXT_PARAMS =
            CarTextParams.builder().setAllowColorSpans(true).build();
    private static final CarTextParams ADDITIONAL_TEXT_PARAMS =
            CarTextParams.builder().setAllowClickableSpans(true).build();

    private final InputManager mInputManager;
    private final ViewGroup mRootView;
    private final ViewGroup mHeaderContainer;
    private final HeaderView mHeaderView;
    private final ActionStripView mActionStripView;
    private final TextView mInstructionTextView;
    private final ActionButtonView mProviderSignInButton;
    private final InputSignInView mInputSignInView;
    private final TextView mAdditionalTextView;
    private final ActionButtonListView mActionListView;
    private final float mAdaptiveWidthFraction;
    private final ViewGroup mContentContainer;
    private final String mDisabledInputHint;
    private boolean mInputWasActiveOnLastWindowFocus;

    /** Create a {@link SignInTemplatePresenter} instance. */
    static SignInTemplatePresenter create(
            TemplateContext templateContext, TemplateWrapper templateWrapper) {
        SignInTemplatePresenter presenter =
                new SignInTemplatePresenter(templateContext, templateWrapper);
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
    public void onStart() {
        super.onStart();
        EventManager eventManager = getTemplateContext().getEventManager();
        eventManager.subscribeEvent(
                this,
                EventType.WINDOW_FOCUS_CHANGED,
                () -> {
                    if (hasWindowFocus()) {
                        // If the input was active the last time the window was focused, it means
                        // that the user just dismissed the car screen keyboard. In this case, focus
                        // on the action button list.
                        if (mInputWasActiveOnLastWindowFocus) {
                            mActionListView.requestFocus();
                        }
                    }
                    mInputWasActiveOnLastWindowFocus = mInputManager.isInputActive();
                });
    }

    @Override
    public void onStop() {
        // TODO(b/182232738): Reenable keyboard listener
        // LocationManager locationManager = LocationManager.getInstance();
        // locationManager.removeKeyboardEnabledListener(driveStatusEventListener);
        getTemplateContext()
                .getEventManager()
                .unsubscribeEvent(this, EventType.WINDOW_FOCUS_CHANGED);
        super.onStop();
    }

    @Override
    public void onPause() {
        mInputManager.stopInput();
        super.onPause();
    }

    @Override
    public void onTemplateChanged() {
        update();
    }

    @Override
    public View getView() {
        return mRootView;
    }

    /** Updates the view with current values in the {@link SignInTemplate}. */
    private void update() {
        TemplateContext templateContext = getTemplateContext();
        SignInTemplate template = (SignInTemplate) getTemplate();

        setHeaderView(templateContext, template);
        setActionStrip(templateContext, template);
        setInstructionText(templateContext, template);
        setSignInView(templateContext, template);
        setAdditionalText(templateContext, template);
        setActionListButtons(templateContext, template);
    }

    private void setHeaderView(TemplateContext templateContext, SignInTemplate template) {
        // If we have a title or a header action, show the header; hide it otherwise.
        CarText title = template.getTitle();
        Action headerAction = template.getHeaderAction();
        if (!CarText.isNullOrEmpty(title) || headerAction != null) {
            mHeaderContainer.setVisibility(VISIBLE);
            mHeaderView.setContent(templateContext, title, headerAction);
        } else {
            mHeaderContainer.setVisibility(GONE);
        }
    }

    private void setActionStrip(TemplateContext templateContext, SignInTemplate template) {
        mActionStripView.setActionStrip(
                templateContext,
                template.getActionStrip(),
                ActionsConstraints.ACTIONS_CONSTRAINTS_SIMPLE);
    }

    private void setInstructionText(TemplateContext templateContext, SignInTemplate template) {
        CarText instructions = template.getInstructions();
        if (!CarText.isNullOrEmpty(instructions)) {
            mInstructionTextView.setText(
                    CarTextUtils.toCharSequenceOrEmpty(
                            templateContext, instructions, INSTRUCTION_TEXT_PARAMS));
            mInstructionTextView.setVisibility(VISIBLE);

            // Allow focus on the message view if there are no actions available.
            mInstructionTextView.setFocusable(template.getActions().isEmpty());
        } else {
            mInstructionTextView.setVisibility(GONE);
        }
    }

    private void setSignInView(TemplateContext templateContext, SignInTemplate template) {
        // Reset the sign-in view
        mProviderSignInButton.setVisibility(GONE);
        mInputSignInView.setVisibility(GONE);
        SignInMethod signInMethod = template.getSignInMethod();
        if (signInMethod instanceof ProviderSignInMethod) {
            ProviderSignInMethod providerSignInMethod = (ProviderSignInMethod) signInMethod;
            Action providerSignInAction = providerSignInMethod.getAction();
            mProviderSignInButton.setAction(templateContext, providerSignInAction);
            mProviderSignInButton.setVisibility(VISIBLE);
        } else if (signInMethod instanceof InputSignInMethod) {
            InputSignInMethod inputSignInMethod = (InputSignInMethod) signInMethod;
            mInputSignInView.setSignInMethod(
                    templateContext, inputSignInMethod, mInputManager, mDisabledInputHint);
            mInputSignInView.setVisibility(VISIBLE);
        } else {
            // TODO(b/182385496): Implement PIN sign in method
            L.w(LogTags.TEMPLATE, "Unknown sign in method: %s", signInMethod);
        }
    }

    private void setAdditionalText(TemplateContext templateContext, SignInTemplate template) {
        CarText additionalText = template.getAdditionalText();
        if (!CarText.isNullOrEmpty(additionalText)) {
            mAdditionalTextView.setText(
                    CarTextUtils.toCharSequenceOrEmpty(
                            templateContext, additionalText, ADDITIONAL_TEXT_PARAMS));
            mAdditionalTextView.setVisibility(VISIBLE);
        } else {
            mAdditionalTextView.setVisibility(GONE);
        }
    }

    private void setActionListButtons(TemplateContext templateContext, SignInTemplate template) {
        List<Action> actionList = template.getActions();
        if (!actionList.isEmpty()) {
            mActionListView.setActionList(templateContext, actionList, MAX_ALLOWED_ACTIONS);
            mActionListView.setVisibility(VISIBLE);
        } else {
            mActionListView.setVisibility(GONE);
        }
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

    @SuppressWarnings("method.invocation.invalid")
    private SignInTemplatePresenter(
            TemplateContext templateContext, TemplateWrapper templateWrapper) {
        super(templateContext, templateWrapper, StatusBarState.LIGHT);

        mInputManager = templateContext.getInputManager();
        mDisabledInputHint = templateContext.getString(R.string.search_hint_disabled);
        mRootView =
                (ViewGroup)
                        LayoutInflater.from(templateContext)
                                .inflate(R.layout.sign_in_template_layout, null);
        mInstructionTextView = mRootView.findViewById(R.id.instruction_text);
        mProviderSignInButton = mRootView.findViewById(R.id.provider_sign_in_button);
        mInputSignInView = mRootView.findViewById(R.id.input_sign_in_view);
        mAdditionalTextView = mRootView.findViewById(R.id.additional_text);
        mHeaderView = mRootView.findViewById(R.id.header_view);
        mActionStripView = mRootView.findViewById(R.id.action_strip);
        mHeaderContainer = mRootView.findViewById(R.id.header_container);
        mActionListView = mRootView.findViewById(R.id.action_button_list_view);
        mContentContainer = mRootView.findViewById(R.id.content_container);

        @StyleableRes
        final int[] themeAttrs = {
            R.attr.templateSignInAdaptiveWidthFraction,
        };

        TypedArray ta = templateContext.obtainStyledAttributes(themeAttrs);
        mAdaptiveWidthFraction = ta.getFloat(0, 0.f);
        ta.recycle();

        updateAdaptiveMargins();
    }
}
