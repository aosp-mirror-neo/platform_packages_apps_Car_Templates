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
package com.android.car.libraries.templates.host.view.presenters.navigation;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static com.android.car.libraries.templates.host.view.widgets.common.ActionStripView.ACTIONSTRIP_ACTIVE_STATE_DURATION_MILLIS;

import android.annotation.SuppressLint;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.transition.TransitionInflater;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalFocusChangeListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.CarText;
import androidx.car.app.model.TemplateWrapper;
import androidx.car.app.navigation.model.MessageInfo;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.navigation.model.NavigationTemplate.NavigationInfo;
import androidx.car.app.navigation.model.RoutingInfo;
import androidx.car.app.navigation.model.TravelEstimate;

import com.android.car.libraries.apphost.common.CarColorUtils;
import com.android.car.libraries.apphost.common.EventManager;
import com.android.car.libraries.apphost.common.EventManager.EventType;
import com.android.car.libraries.apphost.common.StatusBarManager.StatusBarState;
import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.distraction.constraints.ActionsConstraints;
import com.android.car.libraries.apphost.distraction.constraints.CarColorConstraints;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.apphost.view.AbstractSurfaceTemplatePresenter;
import com.android.car.libraries.apphost.view.TemplatePresenter;
import com.android.car.libraries.apphost.view.common.CarTextParams;
import com.android.car.libraries.apphost.view.common.ImageUtils;
import com.android.car.libraries.apphost.view.common.ImageViewParams;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.view.widgets.common.ActionStripView;
import com.android.car.libraries.templates.host.view.widgets.common.BleedingCardView;
import com.android.car.libraries.templates.host.view.widgets.navigation.CompactStepView;
import com.android.car.libraries.templates.host.view.widgets.navigation.DetailedStepView;
import com.android.car.libraries.templates.host.view.widgets.navigation.MessageView;
import com.android.car.libraries.templates.host.view.widgets.navigation.ProgressView;
import com.android.car.libraries.templates.host.view.widgets.navigation.TravelEstimateView;

/**
 * A {@link TemplatePresenter} that shows various navigation cards such as routing cards,
 * destination cards etc.
 */
public class NavigationTemplatePresenter extends AbstractSurfaceTemplatePresenter {
    private static final int MAX_IMAGES_PER_TEXT_LINE = 2;

    /** Percentage to lower the brightness of the card's background. */
    private static final float CARD_BACKGROUND_DARKEN_PERCENTAGE = 0.2f;

    /** Percentage to lower the brightness of the compact step section of the card's background. */
    private static final float COMPACT_STEP_CARD_BACKGROUND_DARKEN_PERCENTAGE = 0.4f;

    /**
     * {@link #showActionStripView()} is called in {@link #onStart()}, but a bug in GMS core causes
     * {@link View#isInTouchMode()} in {@link #onStart()} to return {@code true} even in rotary or
     * touchpad mode (b/128031459), which prevents the action strip from taking the input focus. We
     * use this listener to call {@link #showActionStripView()} after the touch mode changes to the
     * correct value.
     */
    private final OnGlobalFocusChangeListener mOnGlobalFocusChangeListener =
            new OnGlobalFocusChangeListener() {
                // call to showActionStripView() not allowed on the given receiver.
                @SuppressWarnings("nullness:method.invocation.invalid")
                @Override
                public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                    if (newFocus != null) {
                        showActionStripView();
                    }
                }
            };

    private final ViewGroup mRootView;
    private final BleedingCardView mStepsCardContainer;
    private final ViewGroup mStepsContainer;
    private final MessageView mMessageView;
    private final ProgressView mProgressView;
    private final ViewGroup mTravelEstimateContainer;
    private final ImageView mJunctionImageView;
    private final FrameLayout mJunctionImageContainer;
    private final DetailedStepView mDetailedStepView;
    private final CompactStepView mCompactStepView;
    private final TravelEstimateView mTravelEstimateView;
    private final ActionStripView mActionStripView;
    private final CarTextParams mCurrentStepParams;
    private final CarTextParams mNextStepParams;
    @ColorInt private final int mDefaultCardBackgroundColor;

    /** Creates a {@link NavigationTemplatePresenter}. */
    public static NavigationTemplatePresenter create(
            TemplateContext templateContext, TemplateWrapper templateWrapper) {
        NavigationTemplatePresenter presenter =
                new NavigationTemplatePresenter(templateContext, templateWrapper);
        presenter.update();
        return presenter;
    }

    @Override
    public void onStart() {
        super.onStart();

        showActionStripView();
        EventManager eventManager = getTemplateContext().getEventManager();
        eventManager.subscribeEvent(
                this, EventType.TEMPLATE_TOUCHED_OR_FOCUSED, this::showActionStripView);
        eventManager.subscribeEvent(
                this, EventType.WINDOW_FOCUS_CHANGED, this::showActionStripView);
        eventManager.subscribeEvent(
                this,
                EventType.CONFIGURATION_CHANGED,
                () -> updateActionStrip(((NavigationTemplate) getTemplate()).getActionStrip()));
        getView()
                .getViewTreeObserver()
                .addOnGlobalFocusChangeListener(mOnGlobalFocusChangeListener);
        getTemplateContext().getRoutingInfoState().setIsRoutingInfoVisible(true);
    }

    @Override
    public void onStop() {
        getTemplateContext().getRoutingInfoState().setIsRoutingInfoVisible(false);
        EventManager eventManager = getTemplateContext().getEventManager();
        eventManager.unsubscribeEvent(this, EventType.TEMPLATE_TOUCHED_OR_FOCUSED);
        eventManager.unsubscribeEvent(this, EventType.WINDOW_FOCUS_CHANGED);
        eventManager.unsubscribeEvent(this, EventType.CONFIGURATION_CHANGED);
        getView()
                .getViewTreeObserver()
                .removeOnGlobalFocusChangeListener(mOnGlobalFocusChangeListener);

        super.onStop();
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
        // Check the action strip visibility because the action buttons can take focus even when the
        // action strip is gone.
        if (mActionStripView.getVisibility() == VISIBLE) {
            return mActionStripView;
        }
        return super.getDefaultFocusedView();
    }

    @Override
    public void calculateAdditionalInset(Rect inset) {
        if (mStepsCardContainer.getVisibility() == View.VISIBLE) {
            inset.left = mStepsCardContainer.getRight();
        }
        if (mActionStripView.getVisibility() == VISIBLE) {
            inset.top = mActionStripView.getBottom();
        }
    }

    /**
     * Navigation template allows up to 4 buttons, which may overlap with the routing card container
     * in small screens. In this case, draw the buttons in 2 lines to avoid the overlap.
     */
    @Override
    public boolean onPreDraw() {
        View cardContainer = mStepsCardContainer;
        ActionStripView actionStrip = mActionStripView;

        // If the card container and the action strip view overlap, re-draw the action strip in 2
        // lines to avoid the overlap.
        if (cardContainer != null
                && cardContainer.getVisibility() == VISIBLE
                && actionStrip != null
                && actionStrip.getVisibility() == VISIBLE
                && !actionStrip.getAllowTwoLines()) {
            float cardContainerRight = cardContainer.getX() + cardContainer.getRight();
            float actionStripLeft = actionStrip.getX();
            if (cardContainerRight > actionStripLeft) {
                updateActionStrip(
                        ((NavigationTemplate) getTemplate()).getActionStrip(),
                        /* allowTwoLines= */ true);
                getView().requestLayout();
                return false;
            }
        }
        return true;
    }

    private void update() {
        NavigationTemplate template = (NavigationTemplate) getTemplate();
        updateActionStrip(template.getActionStrip());
        setStepsCardBackgroundColor();

        TransitionManager.beginDelayedTransition(
                mRootView,
                TransitionInflater.from(getTemplateContext())
                        .inflateTransition(R.transition.routing_card_transition));

        boolean shouldHideTravelEstimate = false;
        NavigationInfo navigationInfo = template.getNavigationInfo();
        if (navigationInfo == null) {
            mStepsCardContainer.setVisibility(GONE);
            mProgressView.setVisibility(GONE);
            mStepsContainer.setVisibility(GONE);
            mMessageView.setVisibility(GONE);
        } else if (navigationInfo instanceof RoutingInfo) {
            RoutingInfo routingInfo = (RoutingInfo) navigationInfo;

            if (routingInfo.isLoading()) {
                mStepsCardContainer.setVisibility(VISIBLE);
                mProgressView.setVisibility(VISIBLE);
                mStepsContainer.setVisibility(GONE);
                mMessageView.setVisibility(GONE);
            } else {

                boolean shouldShowJunctionImage =
                        ImageUtils.setImageSrc(
                                getTemplateContext(),
                                routingInfo.getJunctionImage(),
                                mJunctionImageView,
                                ImageViewParams.DEFAULT);

                boolean shouldShowNextStep = routingInfo.getNextStep() != null;

                mDetailedStepView.setStepAndDistance(
                        getTemplateContext(),
                        routingInfo.getCurrentStep(),
                        routingInfo.getCurrentDistance(),
                        mCurrentStepParams,
                        shouldShowJunctionImage);
                mCompactStepView.setStep(
                        getTemplateContext(), routingInfo.getNextStep(), mNextStepParams);

                if (shouldShowJunctionImage) {
                    mJunctionImageContainer.setVisibility(VISIBLE);
                    mCompactStepView.setVisibility(GONE);
                    shouldHideTravelEstimate = true;
                } else {
                    mJunctionImageContainer.setVisibility(GONE);
                    mCompactStepView.setVisibility(shouldShowNextStep ? VISIBLE : GONE);
                }

                boolean hasNextStepOrJunction = shouldShowJunctionImage || shouldShowNextStep;
                mStepsCardContainer
                        .findViewById(R.id.divider)
                        .setVisibility(hasNextStepOrJunction ? VISIBLE : GONE);

                mStepsCardContainer.setVisibility(VISIBLE);
                mProgressView.setVisibility(GONE);
                mStepsContainer.setVisibility(VISIBLE);
                mMessageView.setVisibility(GONE);
            }
        } else if (navigationInfo instanceof MessageInfo) {
            MessageInfo messageInfo = (MessageInfo) navigationInfo;
            CarText title = messageInfo.getTitle();
            if (title == null) {
                L.w(LogTags.TEMPLATE, "Title for the message is expected but not set");
                title = CarText.create("");
            }
            mMessageView.setMessage(
                    getTemplateContext(), messageInfo.getImage(), title, messageInfo.getText());
            mStepsCardContainer.setVisibility(VISIBLE);
            mProgressView.setVisibility(GONE);
            mStepsContainer.setVisibility(GONE);
            mMessageView.setVisibility(VISIBLE);
        } else {
            L.w(LogTags.TEMPLATE, "Unknown navigation info: %s", navigationInfo);
        }

        TravelEstimate travelEstimate = template.getDestinationTravelEstimate();
        if (travelEstimate == null || shouldHideTravelEstimate) {
            hideTravelEstimateContainer();
        } else {
            showTravelEstimate(getTemplateContext(), travelEstimate);
        }

        getTemplateContext().getSurfaceInfoProvider().invalidateStableArea();
    }

    private void updateActionStrip(@Nullable ActionStrip actionStrip) {
        updateActionStrip(actionStrip, /* allowTwoLines= */ false);
    }

    private void updateActionStrip(@Nullable ActionStrip actionStrip, boolean allowTwoLines) {
        mActionStripView.setActionStrip(
                getTemplateContext(),
                actionStrip,
                ActionsConstraints.ACTIONS_CONSTRAINTS_NAVIGATION,
                allowTwoLines);
    }

    private void showActionStripView() {
        mActionStripView.setActiveState(true);

        // If nothing was focused, set the default focus.
        if (!mRootView.hasFocus()) {
            setDefaultFocus();
        }

        // The action strip view should fade if the action strip or the window is not focused.
        if (!mActionStripView.hasFocus() || !hasWindowFocus()) {
            mActionStripView.setActiveStateWithDelay(
                    false, ACTIONSTRIP_ACTIVE_STATE_DURATION_MILLIS);
        }
    }

    @SuppressLint("InflateParams")
    @SuppressWarnings("method.invocation.invalid")
    private NavigationTemplatePresenter(
            TemplateContext templateContext, TemplateWrapper templateWrapper) {
        super(templateContext, templateWrapper, StatusBarState.OVER_SURFACE);

        mRootView =
                (ViewGroup)
                        LayoutInflater.from(templateContext)
                                .inflate(R.layout.navigation_template_layout, null);
        mStepsCardContainer = mRootView.findViewById(R.id.content_container);
        mMessageView = mRootView.findViewById(R.id.message_view);
        mProgressView = mRootView.findViewById(R.id.progress_view);
        mStepsContainer = mRootView.findViewById(R.id.steps_container);

        mJunctionImageContainer = mRootView.findViewById(R.id.junction_image_container);
        mJunctionImageView = mRootView.findViewById(R.id.junction_image);
        mDetailedStepView = mRootView.findViewById(R.id.detailed_step_view);
        mCompactStepView = mRootView.findViewById(R.id.compact_step_view);
        mTravelEstimateContainer = mRootView.findViewById(R.id.travel_estimate_card_container);
        mTravelEstimateView = mRootView.findViewById(R.id.travel_estimate_view);
        mActionStripView = mRootView.findViewById(R.id.action_strip);

        mCurrentStepParams = createStepTextParams(/* isNextStep= */ false);
        mNextStepParams = createStepTextParams(/* isNextStep= */ true);
        mDefaultCardBackgroundColor = mStepsCardContainer.getCardBackgroundColor();

        setStepsCardBackgroundColor();
    }

    /**
     * Returns a {@link CarTextParams} instance to use for the text of a step.
     *
     * <p>Unlike other text elsewhere, image spans are allowed in these strings.
     */
    @SuppressLint("ResourceType")
    private CarTextParams createStepTextParams(boolean isNextStep) {
        TemplateContext templateContext = getTemplateContext();

        // Read the default tint for the icons in the spans.
        @StyleableRes
        final int[] themeAttrs = {
            R.attr.templateRoutingDefaultIconTint,
            R.attr.templateRoutingImageSpanMaxWidth,
            R.attr.templateRoutingImageSpanBody2MaxHeight,
            R.attr.templateRoutingImageSpanBody3MaxHeight,
        };
        TypedArray ta = templateContext.obtainStyledAttributes(themeAttrs);
        @ColorInt int defaultIconTint = ta.getColor(0, 0);
        int maxWidth = ta.getDimensionPixelSize(1, 0);
        int body2MaxHeight = ta.getDimensionPixelSize(2, 0);
        int body3MaxHeight = ta.getDimensionPixelSize(3, 0);
        ta.recycle();

        return CarTextParams.builder()
                .setImageBoundingBox(
                        new Rect(0, 0, maxWidth, isNextStep ? body3MaxHeight : body2MaxHeight))
                .setMaxImages(MAX_IMAGES_PER_TEXT_LINE)
                .setDefaultIconTint(defaultIconTint)
                .setAllowColorSpans(false)
                .build();
    }

    private void showTravelEstimate(
            TemplateContext templateContext, @Nullable TravelEstimate travelEstimate) {
        mTravelEstimateView.setTravelEstimate(templateContext, travelEstimate);
        showTravelEstimateContainer();
    }

    private void showTravelEstimateContainer() {
        if (mTravelEstimateContainer.getVisibility() == VISIBLE) {
            return;
        }

        mTravelEstimateContainer.setVisibility(VISIBLE);
        Animation animation =
                AnimationUtils.loadAnimation(
                        getTemplateContext(), R.anim.travel_estimate_card_show_animation);
        mTravelEstimateContainer.setAnimation(animation);
    }

    private void hideTravelEstimateContainer() {
        if (mTravelEstimateContainer.getVisibility() == GONE) {
            return;
        }
        Animation animation =
                AnimationUtils.loadAnimation(
                        getTemplateContext(), R.anim.travel_estimate_card_hide_animation);
        // TODO(b/180455232): Create default AnimationListenerListener with empty methods.
        animation.setAnimationListener(
                new Animation.AnimationListener() {
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        mTravelEstimateContainer.setVisibility(GONE);
                    }

                    @Override
                    public void onAnimationStart(Animation animation) {}

                    @Override
                    public void onAnimationRepeat(Animation animation) {}
                });
        mTravelEstimateContainer.setAnimation(animation);
    }

    private void setStepsCardBackgroundColor() {
        // Set the card's background color to the one provided in the template, if any.
        CarColor backgroundColor = ((NavigationTemplate) getTemplate()).getBackgroundColor();
        @ColorInt int backgroundColorInt;
        if (backgroundColor != null) {
            backgroundColorInt =
                    CarColorUtils.resolveColor(
                            getTemplateContext(),
                            backgroundColor,
                            false,
                            Color.BLACK,
                            CarColorConstraints.UNCONSTRAINED);
        } else {
            backgroundColorInt = mDefaultCardBackgroundColor;
        }

        // Darken the background of the card.
        mStepsCardContainer.setCardBackgroundColor(
                CarColorUtils.darkenColor(backgroundColorInt, CARD_BACKGROUND_DARKEN_PERCENTAGE));

        // Darken the background of the compat step view.
        // We also create a drawable for it that has bottom rounded corners because otherwise the
        // background of the card won't clip within the parent's outline. It is probably possible to
        // do the clipping using a convex path (getting the card's background outline and using that
        // does not work as it returns a rect and not a path), and setting it through an outline
        // provider but this is cheaper regardless as clipping is an expensive operation.
        float bottomRadius = mStepsCardContainer.getCardRadius();
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadii(
                new float[] {0, 0, 0, 0, bottomRadius, bottomRadius, bottomRadius, bottomRadius});
        drawable.setColor(
                CarColorUtils.darkenColor(
                        backgroundColorInt, COMPACT_STEP_CARD_BACKGROUND_DARKEN_PERCENTAGE));
        mCompactStepView.setBackground(drawable);
    }
}
