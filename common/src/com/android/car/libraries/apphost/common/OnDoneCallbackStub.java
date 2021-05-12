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

import android.content.ComponentName;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.car.app.FailureResponse;
import androidx.car.app.IOnDoneCallback;
import androidx.car.app.OnDoneCallback;
import androidx.car.app.serialization.Bundleable;
import androidx.car.app.serialization.BundlerException;

import com.android.car.libraries.apphost.logging.TelemetryHandler;

/**
 * Default {@link IOnDoneCallback} that will log telemetry for API success and failure, handle ANR,
 * as well as release the blocking thread, by setting a {@code null} on the blocking response for
 * any api that blocks for this callback.
 */
public class OnDoneCallbackStub extends IOnDoneCallback.Stub implements OnDoneCallback {
    private final ErrorHandler mErrorHandler;
    private final ComponentName mAppName;
    private final ANRHandler.ANRToken mANRToken;
    private final TelemetryHandler mTelemetryHandler;

    /**
     * Constructs an {@link OnDoneCallbackStub} that will release the given {@link
     * ANRHandler.ANRToken} when {@link #onSuccess} or {@link #onFailure} is called.
     */
    public OnDoneCallbackStub(TemplateContext templateContext, ANRHandler.ANRToken anrToken) {
        this(
                templateContext.getErrorHandler(),
                templateContext.getCarAppPackageInfo().getComponentName(),
                anrToken,
                templateContext.getTelemetryHandler());
    }

    /**
     * Constructs an {@link OnDoneCallbackStub} that will release the given {@link
     * ANRHandler.ANRToken} when {@link #onSuccess} or {@link #onFailure} is called.
     */
    public OnDoneCallbackStub(
            ErrorHandler errorHandler,
            ComponentName appName,
            ANRHandler.ANRToken anrToken,
            TelemetryHandler telemetryHandler) {
        mErrorHandler = errorHandler;
        mAppName = appName;
        mANRToken = anrToken;
        mTelemetryHandler = telemetryHandler;
    }

    @CallSuper
    @Override
    public void onSuccess(@Nullable Bundleable response) {
        mANRToken.dismiss();
        mTelemetryHandler.logCarAppApiSuccessTelemetry(mAppName, mANRToken.getCarAppApi());
    }

    @CallSuper
    @Override
    public void onFailure(Bundleable failureResponse) {
        mANRToken.dismiss();
        FailureResponse failure;
        try {
            failure = (FailureResponse) failureResponse.get();

            mTelemetryHandler.logCarAppApiFailureTelemetry(
                    mAppName, mANRToken.getCarAppApi(), failure);

            mErrorHandler.showError(
                    CarAppError.builder(mAppName).setDebugMessage(failure.getStackTrace()).build());
        } catch (BundlerException e) {
            mErrorHandler.showError(CarAppError.builder(mAppName).setCause(e).build());

            // If we fail to unbundle the response, log telemetry as a failed IPC due to bundling.
            mTelemetryHandler.logCarAppApiFailureTelemetry(
                    mAppName, mANRToken.getCarAppApi(), new FailureResponse(e));
        }
    }
}
