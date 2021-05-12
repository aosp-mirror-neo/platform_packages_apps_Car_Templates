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
package com.android.car.libraries.templates.host.internal;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;

/** Uses {@link CarUxRestrictionsManager} API to track current UX restrictions */
public class CarDrivingStateMonitor {

    private final CarUxRestrictionsManager mCarUxRestrictionsManager;

    private static CarDrivingStateMonitor sInstance;

    private CarDrivingStateMonitor(Context context) {
        Car car = Car.createCar(context);
        mCarUxRestrictionsManager =
                (CarUxRestrictionsManager) car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
    }

    /** Gets the singleton instance of {@link CarDrivingStateMonitor} */
    public static synchronized CarDrivingStateMonitor getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new CarDrivingStateMonitor(context);
        }
        return sInstance;
    }

    public boolean isSetupRestricted() {
        return (mCarUxRestrictionsManager.getCurrentCarUxRestrictions().getActiveRestrictions()
                        & CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP)
                != 0;
    }
}
