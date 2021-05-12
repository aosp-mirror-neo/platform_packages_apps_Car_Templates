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
package com.android.car.libraries.apphost.logging;

import android.content.ComponentName;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;

/** Internal representation of a telemetry event. */
@AutoValue
public abstract class TelemetryEvent {
    /** Types of actions to be reported */
    // Please update the related file
    public enum UiAction {
        APP_START,
        APP_RUNTIME,

        CAR_APP_API_SUCCESS,
        CAR_APP_API_FAILURE,

        CAR_APPS_AVAILABLE,

        CLIENT_SDK_VERSION,
        HOST_SDK_VERSION,

        TEMPLATE_FLOW_LIMIT_EXCEEDED,
        TEMPLATE_FLOW_INVALID_BACK,

        NAVIGATION_STARTED,
        NAVIGATION_TRIP_UPDATED,
        NAVIGATION_ENDED,
    }
    // LINT.ThenChange(//depot/google3/java/com/google/android/apps/auto/components/apphost/\
    //      internal/TelemetryHandlerImpl.java)

    /**
     * Returns the {@link UiAction} that represents the type of action associated with this event.
     */
    public abstract UiAction getAction();

    /** Returns the version of the app SDK. */
    public abstract Optional<String> getCarAppSdkVersion();

    /** Returns the duration of the event, in milliseconds. */
    public abstract Optional<Long> getDurationMs();

    /** Returns the {@link CarAppApi} associated with the event. */
    public abstract Optional<CarAppApi> getCarAppApi();

    /** Returns the {@link ComponentName} for the app the event is coming from. */
    public abstract Optional<ComponentName> getComponentName();

    /** Returns the {@link CarAppApiErrorType} if the event is an error. */
    public abstract Optional<CarAppApiErrorType> getErrorType();

    /**
     * Returns a new builder of {@link TelemetryEvent} set up with the information from this event.
     */
    public static Builder newBuilder(UiAction action) {
        return new AutoValue_TelemetryEvent.Builder().setAction(action);
    }

    /** UiLogEvent builder. */
    @AutoValue.Builder
    public abstract static class Builder {
        /**
         * Sets the {@link UiAction} that represents the type of action associated with this event.
         */
        public abstract Builder setAction(UiAction action);

        /** Sets the version of the app SDK. */
        public abstract Builder setCarAppSdkVersion(String carAppSdkVersion);

        /** Sets the duration of the event, in milliseconds. */
        public abstract Builder setDurationMs(long durationMillis);

        /** Sets the {@link CarAppApi} associated with the event. */
        public abstract Builder setCarAppApi(CarAppApi carAppApi);

        /** Sets the {@link ComponentName} for the app the event is coming from. */
        public abstract Builder setComponentName(ComponentName componentName);

        /** Sets the {@link CarAppApiErrorType} if the event is an error. */
        public abstract Builder setErrorType(CarAppApiErrorType errorType);

        /** Builds a {@link TelemetryEvent} from this builder. */
        public TelemetryEvent build() {
            return autoBuild();
        }

        /** Non-visible builder method for AutoValue to implement. */
        abstract TelemetryEvent autoBuild();
    }
}
