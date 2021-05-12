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
package com.android.car.libraries.templates.host.view.presenters.maps;

import static android.view.View.VISIBLE;

import static java.lang.Math.max;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.transition.TransitionInflater;
import android.transition.TransitionManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowInsets;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.PlaceListMapTemplate;
import androidx.car.app.model.TemplateWrapper;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Lifecycle.State;

import com.android.car.libraries.apphost.common.EventManager.EventType;
import com.android.car.libraries.apphost.common.PlaceMediator.PlaceMediatorListener;
import com.android.car.libraries.apphost.common.StatusBarManager.StatusBarState;
import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.distraction.constraints.ActionsConstraints;
import com.android.car.libraries.apphost.distraction.constraints.RowListConstraints;
import com.android.car.libraries.apphost.template.view.model.RowListWrapper;
import com.android.car.libraries.apphost.template.view.model.RowWrapper;
import com.android.car.libraries.apphost.view.AbstractTemplatePresenter;
import com.android.car.libraries.apphost.view.TemplatePresenter;
import com.android.car.libraries.apphost.view.widget.map.AbstractMapViewContainer;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.di.MapViewContainerFactory;
import com.android.car.libraries.templates.host.view.presenters.common.PresenterUtils;
import com.android.car.libraries.templates.host.view.widgets.common.ActionStripView;
import com.android.car.libraries.templates.host.view.widgets.common.ContentView;
import com.android.car.libraries.templates.host.view.widgets.common.HeaderView;

import com.google.common.collect.ImmutableList;

/** A {@link TemplatePresenter} that shows a map view with pins for locations. */
public class PlaceListMapTemplatePresenter extends AbstractTemplatePresenter {
    private final PlaceMediatorListener mPlaceMediatorListener;
    private final ViewGroup mRootView;
    private final ViewGroup mContentContainer;
    private final ActionStripView mActionStripView;
    private final HeaderView mHeaderView;
    private final ContentView mContentView;
    @Nullable private AbstractMapViewContainer mMapContainer;
    private final OnGlobalLayoutListener mGlobalLayoutListener;
    private final MapViewContainerFactory mMapViewContainerFactory;

    /** Creates a {@link PlaceListMapTemplatePresenter}. */
    public static PlaceListMapTemplatePresenter create(
            TemplateContext templateContext,
            TemplateWrapper templateWrapper,
            MapViewContainerFactory mapViewContainerFactory) {
        PlaceListMapTemplatePresenter presenter =
                new PlaceListMapTemplatePresenter(
                        templateContext, templateWrapper, mapViewContainerFactory);
        presenter.update();
        return presenter;
    }

    @VisibleForTesting
    @Nullable
    public AbstractMapViewContainer getMapContainer() {
        return mMapContainer;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // TODO(b/180162594): Use ifNotNull when available here and below.
        if (mMapContainer != null) {
            mMapContainer.getLifecycleRegistry().setCurrentState(State.CREATED);
        }
    }

    @Override
    public void onDestroy() {
        if (mMapContainer != null) {
            mMapContainer.getLifecycleRegistry().setCurrentState(State.DESTROYED);
        }
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mMapContainer != null) {
            mMapContainer.getLifecycleRegistry().setCurrentState(State.STARTED);
        }

        TemplateContext templateContext = getTemplateContext();
        templateContext.getPlaceMediator().addListener(mPlaceMediatorListener);
        templateContext
                .getEventManager()
                .subscribeEvent(this, EventType.CONFIGURATION_CHANGED, this::refreshViews);

        // For some reason the map view is not laid out when the user exits the app and comes back
        // to the template, preventing the map from updating from place changes. Refresh the map
        // view in order to work around this issue. See b/178606261 for more details.
        refreshViews();
    }

    @Override
    public void onStop() {
        if (mMapContainer != null) {
            mMapContainer.getLifecycleRegistry().setCurrentState(State.CREATED);
        }
        TemplateContext templateContext = getTemplateContext();
        templateContext.getPlaceMediator().removeListener(mPlaceMediatorListener);
        templateContext.getEventManager().unsubscribeEvent(this, EventType.CONFIGURATION_CHANGED);

        super.onStop();
    }

    @Override
    public void onPause() {
        getView().getViewTreeObserver().removeOnGlobalLayoutListener(mGlobalLayoutListener);
        if (mMapContainer != null) {
            mMapContainer.getLifecycleRegistry().setCurrentState(State.STARTED);
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        getView().getViewTreeObserver().addOnGlobalLayoutListener(mGlobalLayoutListener);
        if (mMapContainer != null) {
            mMapContainer.getLifecycleRegistry().setCurrentState(State.RESUMED);
        }
    }

    @Override
    public View getView() {
        return mRootView;
    }

    @Override
    public void applyWindowInsets(WindowInsets windowInsets, int minimumTopPadding) {
        int topInset = max(minimumTopPadding, windowInsets.getSystemWindowInsetTop());

        PresenterUtils.applyTopWindowInsetsToContainer(topInset, mContentContainer);
        PresenterUtils.applyTopWindowInsetsToContainer(topInset, mActionStripView);
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
        // Move between the card view and the action button view on left or right rotary nudge.
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // If the focus is in the card view (back button or row list), request focus in the
            // action strip.
            if (moveFocusIfPresent(
                    ImmutableList.of(mContentContainer), ImmutableList.of(mActionStripView))) {
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            // Request focus on the content view so that the first row in the list will take focus.
            if (moveFocusIfPresent(
                    ImmutableList.of(mActionStripView), ImmutableList.of(mContentView))) {
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
    public boolean handlesTemplateChangeAnimation() {
        // PlaceListMapTemplate has special behavior since we don't want to destroy the MapView for
        // a refresh, and it handles the update motion correctly internally.
        return true;
    }

    @Override
    public boolean isFullScreen() {
        return false;
    }

    /** Updates the locations in the map */
    private void update() {
        PlaceListMapTemplate mapTemplate = (PlaceListMapTemplate) getTemplate();

        TransitionManager.beginDelayedTransition(
                mRootView,
                TransitionInflater.from(getTemplateContext())
                        .inflateTransition(R.transition.map_template_transition));

        mHeaderView.setContent(
                getTemplateContext(), mapTemplate.getTitle(), mapTemplate.getHeaderAction());

        ItemList itemList = mapTemplate.getItemList();
        RowListWrapper rowListWrapper =
                RowListWrapper.wrap(getTemplateContext(), itemList)
                        .setIsLoading(mapTemplate.isLoading())
                        .setRowFlags(RowWrapper.DEFAULT_UNIFORM_LIST_ROW_FLAGS)
                        .setRowListConstraints(RowListConstraints.ROW_LIST_CONSTRAINTS_SIMPLE)
                        .setIsRefresh(getTemplateWrapper().isRefresh())
                        .setIsHalfList(true)
                        .build();
        mContentView.setRowListContent(getTemplateContext(), rowListWrapper);

        updateMapSettings(mapTemplate);
        updateActionStrip(mapTemplate.getActionStrip());
    }

    // TODO(b/159908673): add tests for the lifecycle management logic in here.
    private void refreshViews() {
        // Destroy the previous MapView based on this presenter's currently lifecycle events.
        AbstractMapViewContainer previousMapContainer = mMapContainer;
        if (previousMapContainer != null) {
            previousMapContainer.getLifecycleRegistry().setCurrentState(State.DESTROYED);
            mRootView.removeView(previousMapContainer);
        }

        Lifecycle lifecycle = getLifecycle();
        if (lifecycle.getCurrentState() == State.DESTROYED) {
            // View already destroyed. Don't bother refreshing the views.
            return;
        }

        mMapContainer =
                mMapViewContainerFactory.create(getTemplateContext(), R.style.Theme_Template);

        if (mMapContainer != null) {
            mMapContainer.setTemplateContext(getTemplateContext());
            mMapContainer.setId(R.id.map_container);
            mRootView.addView(mMapContainer, 0);

            // Update the new MapView's lifecycle events to match this presenter's, as that is
            // required for the map instance to be initiated and shown.
            mMapContainer.getLifecycleRegistry().setCurrentState(lifecycle.getCurrentState());
        }

        PlaceListMapTemplate mapTemplate = (PlaceListMapTemplate) getTemplate();
        updateMapSettings(mapTemplate);
        updateActionStrip(mapTemplate.getActionStrip());
    }

    private void updateMapSettings(PlaceListMapTemplate mapTemplate) {
        TemplateContext templateContext = getTemplateContext();

        if (mMapContainer != null) {
            mMapContainer.setCurrentLocationEnabled(mapTemplate.isCurrentLocationEnabled());
            mMapContainer.setAnchor(mapTemplate.getAnchor());
            mMapContainer.setPlaces(templateContext.getPlaceMediator().getPlaces());
        }
    }

    private void updateActionStrip(@Nullable ActionStrip actionStrip) {
        mActionStripView.setActionStrip(
                getTemplateContext(), actionStrip, ActionsConstraints.ACTIONS_CONSTRAINTS_SIMPLE);
    }

    @SuppressLint("InflateParams")
    @SuppressWarnings({"methodref.receiver.bound.invalid", "nullness"})
    private PlaceListMapTemplatePresenter(
            TemplateContext templateContext,
            TemplateWrapper templateWrapper,
            MapViewContainerFactory mapViewContainerFactory) {
        super(templateContext, templateWrapper, StatusBarState.OVER_SURFACE);

        mRootView =
                (ViewGroup)
                        LayoutInflater.from(templateContext)
                                .inflate(R.layout.map_template_layout, null);
        mContentContainer = mRootView.findViewById(R.id.content_container);
        mHeaderView = mRootView.findViewById(R.id.header_view);
        mContentView = mRootView.findViewById(R.id.content_view);
        mActionStripView = mRootView.findViewById(R.id.action_strip);
        mMapViewContainerFactory = mapViewContainerFactory;
        mMapContainer =
                mMapViewContainerFactory.create(getTemplateContext(), R.style.Theme_Template);

        if (mMapContainer != null) {
            mMapContainer.setTemplateContext(templateContext);
            mRootView.addView(mMapContainer, 0);
        }

        // We should always show an ItemList.
        mContentContainer.setVisibility(View.VISIBLE);

        // Wire up a listener that sends any available locations to be displayed in the map view.
        // These may be published by other UI down the hierarchy such as lists of locations, or a
        // pane showing the details for a particular location.
        mPlaceMediatorListener =
                () -> {
                    if (mMapContainer != null) {
                        mMapContainer.setPlaces(templateContext.getPlaceMediator().getPlaces());
                    }
                };

        // Dynamically update the visible area inset. This allows the MapViewContainer to account
        // for the insets when adjusting zoom levels to show all the place markers.
        mGlobalLayoutListener =
                () -> {
                    Rect safeAreaInset = new Rect();
                    // The content container is always visible so just use its right.
                    safeAreaInset.left = mContentContainer.getRight();
                    safeAreaInset.top =
                            mActionStripView.getVisibility() == VISIBLE
                                    ? mActionStripView.getBottom()
                                    : mRootView.getTop() + mRootView.getPaddingTop();
                    safeAreaInset.bottom = mRootView.getBottom() - mRootView.getPaddingBottom();
                    safeAreaInset.right = mRootView.getRight() - mRootView.getPaddingRight();
                    templateContext.getSurfaceInfoProvider().setVisibleArea(safeAreaInset);
                };
    }
}
