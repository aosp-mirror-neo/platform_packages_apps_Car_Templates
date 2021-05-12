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

import android.content.ComponentName;
import android.graphics.Rect;
import android.os.RemoteException;

import androidx.annotation.Nullable;
import androidx.car.app.FailureResponse;
import androidx.car.app.ISurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.OnCheckedChangeDelegate;
import androidx.car.app.model.OnClickDelegate;
import androidx.car.app.model.OnInputCompletedDelegate;
import androidx.car.app.model.OnItemVisibilityChangedDelegate;
import androidx.car.app.model.OnSelectedDelegate;
import androidx.car.app.model.SearchCallbackDelegate;
import androidx.car.app.serialization.Bundleable;
import androidx.car.app.serialization.BundlerException;

import com.android.car.libraries.apphost.common.ANRHandler;
import com.android.car.libraries.apphost.common.AppDispatcher;
import com.android.car.libraries.apphost.common.CarAppError;
import com.android.car.libraries.apphost.common.ErrorHandler;
import com.android.car.libraries.apphost.common.OnDoneCallbackStub;
import com.android.car.libraries.apphost.common.OneWayIPC;
import com.android.car.libraries.apphost.internal.BlockingOneWayIPC.BlockingResponse;
import com.android.car.libraries.apphost.logging.CarAppApi;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.apphost.logging.TelemetryHandler;

/**
 * Class to set up safe remote callbacks to apps.
 *
 * <p>App interfaces to client are {@code oneway} so the calling thread does not block waiting for a
 * response.
 */
public class AppDispatcherImpl implements AppDispatcher {
    private final ComponentName mAppName;
    private final ErrorHandler mErrorHandler;
    private final ANRHandler mANRHandler;
    private final TelemetryHandler mTelemetryHandler;

    /** Creates an {@link AppDispatcher} instance for an app. */
    public static AppDispatcher create(
            ComponentName appName,
            ErrorHandler errorHandler,
            ANRHandler anrHandler,
            TelemetryHandler telemetryHandler) {
        return new AppDispatcherImpl(appName, errorHandler, anrHandler, telemetryHandler);
    }

    @Override
    public void dispatchSurfaceAvailable(
            ISurfaceCallback surfaceListener, SurfaceContainer surfaceContainer) {
        dispatch(
                anrToken ->
                        surfaceListener.onSurfaceAvailable(
                                Bundleable.create(surfaceContainer),
                                new OnDoneCallbackStub(
                                        mErrorHandler, mAppName, anrToken, mTelemetryHandler)),
                CarAppApi.ON_SURFACE_AVAILABLE);
    }

    @Override
    public void dispatchSurfaceDestroyed(
            ISurfaceCallback surfaceListener, SurfaceContainer surfaceContainer) {
        // onSurfaceDestroyed is called blocking since the OS expects that whenever we return
        // the call we are done using the Surface.
        BlockingResponse<Void> blockingResponse = new BlockingResponse<>();
        OneWayIPC ipc =
                anrToken ->
                        surfaceListener.onSurfaceDestroyed(
                                Bundleable.create(surfaceContainer),
                                new OnDoneCallbackStub(
                                        mErrorHandler, mAppName, anrToken, mTelemetryHandler) {
                                    @Override
                                    public void onSuccess(@Nullable Bundleable response) {
                                        blockingResponse.setResponse(null);
                                        super.onSuccess(response);
                                    }

                                    @Override
                                    public void onFailure(Bundleable failureResponse) {
                                        blockingResponse.setResponse(null);
                                        super.onFailure(failureResponse);
                                    }
                                });

        dispatch(new BlockingOneWayIPC<>(ipc, blockingResponse), CarAppApi.ON_SURFACE_DESTROYED);
    }

    @Override
    public void dispatchVisibleAreaChanged(ISurfaceCallback surfaceListener, Rect visibleArea) {
        dispatch(
                anrToken ->
                        surfaceListener.onVisibleAreaChanged(
                                visibleArea,
                                new OnDoneCallbackStub(
                                        mErrorHandler, mAppName, anrToken, mTelemetryHandler)),
                CarAppApi.ON_VISIBLE_AREA_CHANGED);
    }

    @Override
    public void dispatchStableAreaChanged(ISurfaceCallback surfaceListener, Rect stableArea) {
        dispatch(
                anrToken ->
                        surfaceListener.onStableAreaChanged(
                                stableArea,
                                new OnDoneCallbackStub(
                                        mErrorHandler, mAppName, anrToken, mTelemetryHandler)),
                CarAppApi.ON_STABLE_AREA_CHANGED);
    }

    @Override
    public void dispatchSearchTextChanged(
            SearchCallbackDelegate searchCallbackDelegate, String searchText) {
        dispatch(
                anrToken ->
                        searchCallbackDelegate.sendSearchTextChanged(
                                searchText,
                                new OnDoneCallbackStub(
                                        mErrorHandler, mAppName, anrToken, mTelemetryHandler)),
                CarAppApi.ON_SEARCH_TEXT_CHANGED);
    }

    @Override
    public void dispatchInputSubmitted(
            OnInputCompletedDelegate onInputCompletedDelegate, String inputText) {
        dispatch(
                anrToken ->
                        onInputCompletedDelegate.sendInputCompleted(
                                inputText,
                                new OnDoneCallbackStub(
                                        mErrorHandler, mAppName, anrToken, mTelemetryHandler)),
                CarAppApi.ON_INPUT_SUBMITTED);
    }

    @Override
    public void dispatchSearchSubmitted(
            SearchCallbackDelegate searchCallbackDelegate, String searchText) {
        dispatch(
                anrToken ->
                        searchCallbackDelegate.sendSearchSubmitted(
                                searchText,
                                new OnDoneCallbackStub(
                                        mErrorHandler, mAppName, anrToken, mTelemetryHandler)),
                CarAppApi.ON_SEARCH_SUBMITTED);
    }

    @Override
    public void dispatchItemVisibilityChanged(
            OnItemVisibilityChangedDelegate onItemVisibilityChangedDelegate,
            int startIndexInclusive,
            int endIndexExclusive) {
        dispatch(
                anrToken ->
                        onItemVisibilityChangedDelegate.sendItemVisibilityChanged(
                                startIndexInclusive,
                                endIndexExclusive,
                                new OnDoneCallbackStub(
                                        mErrorHandler, mAppName, anrToken, mTelemetryHandler)),
                CarAppApi.ON_ITEM_VISIBILITY_CHANGED);
    }

    @Override
    public void dispatchSelected(OnSelectedDelegate onSelectedDelegate, int index) {
        dispatch(
                anrToken ->
                        onSelectedDelegate.sendSelected(
                                index,
                                new OnDoneCallbackStub(
                                        mErrorHandler, mAppName, anrToken, mTelemetryHandler)),
                CarAppApi.ON_SELECTED);
    }

    @Override
    public void dispatchCheckedChanged(
            OnCheckedChangeDelegate onCheckedChangeDelegate, boolean isChecked) {
        dispatch(
                anrToken ->
                        onCheckedChangeDelegate.sendCheckedChange(
                                isChecked,
                                new OnDoneCallbackStub(
                                        mErrorHandler, mAppName, anrToken, mTelemetryHandler)),
                CarAppApi.ON_CHECKED_CHANGED);
    }

    @Override
    public void dispatchClick(OnClickDelegate onClickDelegate) {
        dispatch(
                anrToken ->
                        onClickDelegate.sendClick(
                                new OnDoneCallbackStub(
                                        mErrorHandler, mAppName, anrToken, mTelemetryHandler)),
                CarAppApi.ON_CLICK);
    }

    @Override
    public void dispatch(OneWayIPC ipc, CarAppApi carAppApi) {
        dispatch(ipc, mErrorHandler::showError, carAppApi);
    }

    @Override
    public void dispatch(OneWayIPC ipc, ExceptionHandler exceptionHandler, CarAppApi carAppApi) {
        L.d(LogTags.APP_HOST, "Dispatching call %s", carAppApi.name());

        mANRHandler.callWithANRCheck(
                carAppApi,
                anrToken -> {
                    try {
                        ipc.send(anrToken);
                    } catch (RemoteException | BundlerException | RuntimeException e) {
                        mTelemetryHandler.logCarAppApiFailureTelemetry(
                                mAppName, carAppApi, new FailureResponse(e));

                        exceptionHandler.handle(
                                CarAppError.builder(mAppName)
                                        .setCause(e)
                                        .setDebugMessage(
                                                "Remote call " + carAppApi.name() + " failed.")
                                        .build());
                    }
                });
    }

    private AppDispatcherImpl(
            ComponentName appName,
            ErrorHandler errorHandler,
            ANRHandler anrHandler,
            TelemetryHandler telemetryHandler) {
        mAppName = appName;
        mErrorHandler = errorHandler;
        mANRHandler = anrHandler;
        mTelemetryHandler = telemetryHandler;
    }
}
