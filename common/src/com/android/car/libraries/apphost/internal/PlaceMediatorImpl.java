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
package com.android.car.libraries.apphost.internal;

import androidx.car.app.model.Place;

import com.android.car.libraries.apphost.common.PlaceMediator;
import com.android.car.libraries.apphost.common.ThreadUtils;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An implementation of {@link PlaceMediator}.
 *
 * <p>There's only one set of places available at any given time, so the last writer wins. This
 * class is not meant to be used with multiple publishers (e.g. shared between multiple apps) so
 * that is just fine.
 *
 * <p>This class is not safe for concurrent access.
 */
public class PlaceMediatorImpl implements PlaceMediator {
    private final CopyOnWriteArrayList<PlaceMediatorListener> mListeners =
            new CopyOnWriteArrayList<>();

    private List<Place> mPlaces = ImmutableList.of();

    /** Returns an instance of a {@link PlaceMediator}. */
    public static PlaceMediator create() {
        return new PlaceMediatorImpl();
    }

    @Override
    public List<Place> getPlaces() {
        return mPlaces;
    }

    @Override
    public void publish(List<Place> places) {
        ThreadUtils.ensureMainThread();
        if (mPlaces.equals(places)) {
            return;
        }
        L.v(LogTags.APP_HOST, "Publishing %d new places", places.size());
        mPlaces = places;
        for (PlaceMediatorListener listener : mListeners) {
            listener.onPlacesChanged();
        }
    }

    @Override
    public void addListener(PlaceMediatorListener listener) {
        mListeners.add(listener);
    }

    @Override
    public void removeListener(PlaceMediatorListener listener) {
        mListeners.remove(listener);
    }

    private PlaceMediatorImpl() {}
}
