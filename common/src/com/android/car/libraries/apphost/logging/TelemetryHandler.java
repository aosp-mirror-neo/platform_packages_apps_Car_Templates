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

import androidx.annotation.Nullable;
import androidx.car.app.FailureResponse;

import com.android.car.libraries.apphost.logging.TelemetryEvent.UiAction;

/**
 * Telemetry service abstraction. Implementations are expected to convert these events to their own
 * representation and send the information to their corresponding backend.
 */
public abstract class TelemetryHandler {
    /**
     * Logs a telemetry event with the given {@link UiAction}, and the provided {@link
     * ComponentName}.
     */
    public void logCarAppTelemetry(UiAction action, ComponentName appName) {
        logCarAppTelemetry(createCarAppTelemetryEvent(action, appName));
    }

    /**
     * Logs a telemetry event with the given {@link UiAction}, the provided {@link ComponentName},
     * and the provided {@link CarAppApi}.
     */
    public void logCarAppTelemetry(UiAction action, ComponentName appName, CarAppApi carAppApi) {
        logCarAppTelemetry(createCarAppTelemetryEvent(action, appName).setCarAppApi(carAppApi));
    }

    private void logCarAppTelemetry(
            UiAction uiAction,
            ComponentName appName,
            CarAppApi carAppApi,
            @Nullable CarAppApiErrorType errorType) {
        TelemetryEvent.Builder builder =
                createCarAppTelemetryEvent(uiAction, appName).setCarAppApi(carAppApi);

        if (errorType != null) {
            builder.setErrorType(errorType);
        }

        logCarAppTelemetry(builder);
    }

    /** Logs a telemetry event for the given {@link TelemetryEvent.Builder}. */
    public abstract void logCarAppTelemetry(TelemetryEvent.Builder logEventBuilder);

    /**
     * Logs a telemetry event with the given {@link UiAction}, the provided {@link ComponentName},
     * and the provided {@link CarAppApi}.
     */
    public void logCarAppApiSuccessTelemetry(ComponentName appName, CarAppApi carAppApi) {
        logCarAppTelemetry(UiAction.CAR_APP_API_SUCCESS, appName, carAppApi, /* errorType= */ null);
    }

    /**
     * Logs a telemetry event with the given {@link UiAction}, the provided {@link ComponentName},
     * the provided {@link CarAppApi}, and the provided {@link FailureResponse}.
     */
    public void logCarAppApiFailureTelemetry(
            ComponentName appName, CarAppApi carAppApi, FailureResponse failureResponse) {
        logCarAppApiFailureTelemetry(appName, carAppApi, getErrorType(failureResponse));
    }

    /**
     * Logs a telemetry event with the given {@link UiAction}, the provided {@link ComponentName},
     * the provided {@link CarAppApi}, and the provided {@link CarAppApiErrorType}.
     */
    public void logCarAppApiFailureTelemetry(
            ComponentName appName, CarAppApi carAppApi, CarAppApiErrorType errorType) {
        logCarAppTelemetry(UiAction.CAR_APP_API_FAILURE, appName, carAppApi, errorType);
    }

    /**
     * Creates a {@link TelemetryEvent.Builder} for car app telemetry with the given {@link
     * UiAction}, and the provided {@link ComponentName}.
     */
    public TelemetryEvent.Builder createCarAppTelemetryEvent(
            UiAction action, ComponentName appName) {
        return createCarAppTelemetryEvent(action).setComponentName(appName);
    }

    /**
     * Creates a {@link TelemetryEvent.Builder} for car app telemetry with the given {@link
     * UiAction}.
     */
    public TelemetryEvent.Builder createCarAppTelemetryEvent(UiAction action) {
        return TelemetryEvent.newBuilder(action);
    }

    private static CarAppApiErrorType getErrorType(FailureResponse failure) {
        switch (failure.getErrorType()) {
            case FailureResponse.BUNDLER_EXCEPTION:
                return CarAppApiErrorType.BUNDLER_EXCEPTION;
            case FailureResponse.ILLEGAL_STATE_EXCEPTION:
                return CarAppApiErrorType.ILLEGAL_STATE_EXCEPTION;
            case FailureResponse.INVALID_PARAMETER_EXCEPTION:
                return CarAppApiErrorType.INVALID_PARAMETER_EXCEPTION;
            case FailureResponse.SECURITY_EXCEPTION:
                return CarAppApiErrorType.SECURITY_EXCEPTION;
            case FailureResponse.RUNTIME_EXCEPTION:
                return CarAppApiErrorType.RUNTIME_EXCEPTION;
            case FailureResponse.REMOTE_EXCEPTION:
                return CarAppApiErrorType.REMOTE_EXCEPTION;
            case FailureResponse.UNKNOWN_ERROR:
            default:
                // fall-through
        }
        return CarAppApiErrorType.UNKNOWN_ERROR;
    }
}
