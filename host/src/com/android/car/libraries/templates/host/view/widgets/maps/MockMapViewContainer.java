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
package com.android.car.libraries.templates.host.view.widgets.maps;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.model.Place;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.view.widget.map.AbstractMapViewContainer;
import com.android.car.libraries.templates.host.R;

import dagger.hilt.android.qualifiers.ApplicationContext;

import java.util.List;

/** A layout that wraps a single map view and encapsulates the logic to manipulate it. */
public class MockMapViewContainer extends AbstractMapViewContainer {

    @SuppressWarnings("nullness")
    private final LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(this);

    /**
     * Instantiates a new map view container.
     *
     * @see android.content.res.Resources.Theme#obtainStyledAttributes
     */
    @SuppressWarnings("nullness")
    public MockMapViewContainer(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mLifecycleRegistry.addObserver(this);
    }

    /** Instantiates a new map view container. */
    public MockMapViewContainer(@ApplicationContext Context context) {
        this(context, null);
    }

    /**
     * Instantiates a new map view container.
     *
     * @see android.content.res.Resources.Theme#obtainStyledAttributes
     */
    public MockMapViewContainer(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Instantiates a new map view container.
     *
     * @see android.content.res.Resources.Theme#obtainStyledAttributes
     */
    public MockMapViewContainer(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    /** Returns an AOSPMapViewContainer */
    public static AbstractMapViewContainer create(Context context, int theme) {
        return (AbstractMapViewContainer)
                View.inflate(
                        new ContextThemeWrapper(context, theme),
                        R.layout.aosp_map_view_container_layout,
                        null);
    }

    /** Returns an AOSPMapViewContainer */
    public static MockMapViewContainer create(Context context) {
        return (MockMapViewContainer)
                View.inflate(context, R.layout.aosp_map_view_container_layout, null);
    }

    @Override
    public void setTemplateContext(TemplateContext templateContext) {}

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycleRegistry;
    }

    @NonNull
    @Override
    public LifecycleRegistry getLifecycleRegistry() {
        return mLifecycleRegistry;
    }

    @Override
    public void onCreate(LifecycleOwner owner) {}

    @Override
    public void onDestroy(LifecycleOwner owner) {}

    @Override
    public void onStart(LifecycleOwner owner) {}

    @Override
    public void onStop(LifecycleOwner owner) {}

    @Override
    public void onResume(LifecycleOwner owner) {}

    @Override
    public void onPause(LifecycleOwner owner) {}

    @Override
    public void setCurrentLocationEnabled(boolean enable) {}

    @Override
    public void setAnchor(@Nullable Place anchor) {}

    @Override
    public void setPlaces(List<Place> places) {}
}
