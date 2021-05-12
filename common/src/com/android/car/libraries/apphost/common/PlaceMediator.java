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

package com.android.car.libraries.apphost.common;

import androidx.car.app.model.Place;

import java.util.List;

/**
 * A mediator that can receive {@link Place}s from different components in the UI hierarchy, and
 * forward them to others.
 */
public interface PlaceMediator {
    /** Returns the last set of available places, or an empty list if there are none. */
    List<Place> getPlaces();

    /**
     * Publishes a new set of places to the mediator, which will be broadcast to any listeners if
     * changed from the existing set.
     */
    void publish(List<Place> places);

    /**
     * Adds a listener to listen to mediator events. The listener will be called in the same thread
     * as the publisher, which should be the main thread.
     */
    void addListener(PlaceMediatorListener listener);

    /** Removes a listener added with {@link #addListener}. */
    void removeListener(PlaceMediatorListener listener);

    /** A listener of events related to the {@link PlaceMediator}. */
    interface PlaceMediatorListener {
        /** Notifies that the places available in the mediator have changed. */
        void onPlacesChanged();
    }
}
