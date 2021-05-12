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
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;

import androidx.annotation.Nullable;

import com.android.car.libraries.apphost.distraction.constraints.AppLimitsProvider;
import com.android.car.libraries.apphost.input.InputManager;
import com.android.car.libraries.apphost.logging.TelemetryHandler;

import java.io.PrintWriter;
import java.util.List;

/**
 * Context for various template components to retrieve important bits of information for presenting
 * content.
 */
public abstract class TemplateContext extends ContextWrapper {
    /**
     * Constructs an instance of a {@link TemplateContext} wrapping the given {@link Context}
     * object.
     */
    public TemplateContext(Context base) {
        super(base);
    }

    /**
     * Creates a {@link TemplateContext} that replaces the inner {@link Context} for the given
     * {@link TemplateContext}.
     *
     * <p>This is used for using an uiContext for view elements, since they may have a theme applied
     * on them.
     *
     * @param other the {@link TemplateContext} to wrap for all the getters
     * @param uiContext the {@link Context} that this instance will wrap
     */
    public static TemplateContext from(TemplateContext other, Context uiContext) {
        return new TemplateContext(uiContext) {
            @Override
            public CarAppPackageInfo getCarAppPackageInfo() {
                return other.getCarAppPackageInfo();
            }

            @Override
            public InputManager getInputManager() {
                return other.getInputManager();
            }

            @Override
            public ErrorHandler getErrorHandler() {
                return other.getErrorHandler();
            }

            @Override
            public ANRHandler getAnrHandler() {
                return other.getAnrHandler();
            }

            @Override
            public BackPressedHandler getBackPressedHandler() {
                return other.getBackPressedHandler();
            }

            @Override
            public StatusBarManager getStatusBarManager() {
                return other.getStatusBarManager();
            }

            @Override
            public SurfaceInfoProvider getSurfaceInfoProvider() {
                return other.getSurfaceInfoProvider();
            }

            @Override
            public EventManager getEventManager() {
                return other.getEventManager();
            }

            @Override
            public AppDispatcher getAppDispatcher() {
                return other.getAppDispatcher();
            }

            @Override
            public SystemClockWrapper getSystemClockWrapper() {
                return other.getSystemClockWrapper();
            }

            @Override
            public ToastController getToastController() {
                return other.getToastController();
            }

            @Override
            @Nullable
            public Context getAppConfigurationContext() {
                return other.getAppConfigurationContext();
            }

            @Override
            public PlaceMediator getPlaceMediator() {
                return other.getPlaceMediator();
            }

            @Override
            public CarAppManager getCarAppManager() {
                return other.getCarAppManager();
            }

            @Override
            public void updateConfiguration(Configuration configuration) {
                other.updateConfiguration(configuration);
            }

            @Override
            public TelemetryHandler getTelemetryHandler() {
                return other.getTelemetryHandler();
            }

            @Override
            public RoutingInfoState getRoutingInfoState() {
                return other.getRoutingInfoState();
            }

            @Override
            public AppLimitsProvider getAppLimitsProvider() {
                return other.getAppLimitsProvider();
            }

            @Override
            public CarHostConfig getCarHostConfig() {
                return other.getCarHostConfig();
            }

            @Override
            public HostResourceIds getHostResourceIds() {
                return other.getHostResourceIds();
            }
        };
    }

    /**
     * Provides the package information such as accent colors, component names etc. associated with
     * the 3p app.
     */
    public abstract CarAppPackageInfo getCarAppPackageInfo();

    /** Provides the {@link InputManager} for the current car activity to bring up the keyboard. */
    public abstract InputManager getInputManager();

    /** Provides the {@link ErrorHandler} for displaying errors to the user. */
    public abstract ErrorHandler getErrorHandler();

    /** Provides the {@link ANRHandler} for managing ANRs. */
    public abstract ANRHandler getAnrHandler();

    /** Provides the {@link BackPressedHandler} for dispatching back press events to the app. */
    public abstract BackPressedHandler getBackPressedHandler();

    /**
     * Provides the {@link StatusBarManager} to allow for overriding the status bar background and
     * text color.
     */
    public abstract StatusBarManager getStatusBarManager();

    /**
     * Provides the {@link SurfaceInfoProvider} to allow storing and retrieving safe area insets.
     */
    public abstract SurfaceInfoProvider getSurfaceInfoProvider();

    /**
     * Provides the {@link EventManager} to allow dispatching and subscribing to different events.
     */
    public abstract EventManager getEventManager();

    /** Provides the {@link AppDispatcher} which allows dispatching IPCs to the client app. */
    public abstract AppDispatcher getAppDispatcher();

    /** Returns the system {@link SystemClockWrapper}. */
    public abstract SystemClockWrapper getSystemClockWrapper();

    /** Returns the {@link ToastController} which allows clients to show toasts. */
    public abstract ToastController getToastController();

    /**
     * Returns a {@link Context} instance for the remote app, configured with this context's
     * configuration (which includes configuration from the car's resources, such as screen size and
     * DPI).
     *
     * <p>The theme in this context is also set to the application's theme id, so that attributes in
     * remote resources can be resolved using the that theme (see {@link
     * ColorUtils#loadThemeId(Context, ComponentName)}).
     *
     * <p>Use method to load drawable resources from app's APKs, so that they are returned with the
     * target DPI of the car screen, rather than the phone's. See b/159088813 for more details.
     *
     * @return the remote app's context, or {@code null} if unavailable due to an error (the logcat
     *     will contain a log with the error in this case).
     */
    @Nullable
    public abstract Context getAppConfigurationContext();

    /**
     * Provides the {@link PlaceMediator} for managing a list of {@link
     * androidx.car.app.model.Place} instances.
     */
    public abstract PlaceMediator getPlaceMediator();

    /** Returns the {@link CarAppManager} that is to be used for starting and finishing car apps. */
    public abstract CarAppManager getCarAppManager();

    /**
     * Updates the {@link Configuration} of the app configuration context that is retrieved via
     * {@link #getAppConfigurationContext}, and publishes a {@link
     * EventManager.EventType#CONFIGURATION_CHANGED} event using the {@link EventManager}.
     */
    public abstract void updateConfiguration(Configuration configuration);

    /** Returns the {@link TelemetryHandler} instance that allows reporting telemetry data. */
    public abstract TelemetryHandler getTelemetryHandler();

    /**
     * Returns the {@link RoutingInfoState} that keeps track of the routing information state across
     * template apps.
     */
    // TODO(b/169182143): Use a generic getService model to retrieve this object
    public abstract RoutingInfoState getRoutingInfoState();

    /**
     * Returns the {@link AppLimitsProvider} that can provide the limits associated with this car
     * app.
     */
    public abstract AppLimitsProvider getAppLimitsProvider();

    /**
     * Returns a {@link CarHostConfig} object containing a series of flags and configuration options
     */
    public abstract CarHostConfig getCarHostConfig();

    /** Produces a status report for this context, used for diagnostics and logging. */
    public void reportStatus(PrintWriter pw) {}

    /** Returns the {@link HostResourceIds} to use for this host implementation */
    public abstract HostResourceIds getHostResourceIds();

    /** Configuration options from the car host. */
    public interface CarHostConfig {
        /**
         * Returns how many seconds after the user leaves an app, should the system wait before
         * unbinding from it.
         */
        int getAppUnbindSeconds();

        /** Returns a list of intent extras to be stripped before binding to the client app. */
        List<String> getHostIntentExtrasToRemove();

        /** Returns whether the provided intent should be treated as a new task flow. */
        boolean isNewTaskFlowIntent(Intent intent);
    }
}
