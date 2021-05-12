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
package com.android.car.libraries.apphost;

import android.content.Intent;

import com.android.car.libraries.apphost.common.TemplateContext;

import com.google.common.base.Preconditions;

import java.io.PrintWriter;

/**
 * Abstract base class for {@link Host}s which implements some of the common host service
 * functionality.
 */
public abstract class AbstractHost implements Host {
    protected TemplateContext mTemplateContext;
    private boolean mIsValid = true;

    protected AbstractHost(TemplateContext templateContext) {
        mTemplateContext = templateContext;
    }

    @Override
    public void setTemplateContext(TemplateContext templateContext) {
        mTemplateContext = templateContext;
    }

    @Override
    public void invalidateHost() {
        mIsValid = false;
    }

    @Override
    public void onCarAppBound() {
        assertIsValid();
    }

    @Override
    public void onNewIntentDispatched() {
        assertIsValid();
    }

    @Override
    public void onBindToApp(Intent intent) {
        assertIsValid();
    }

    @Override
    public void reportStatus(PrintWriter pw, Pii piiHandling) {}

    /**
     * Asserts that the service is valid. All public facing APIs must invoke method to ensure the
     * service is valid before processing any requests.
     */
    protected void assertIsValid() {
        Preconditions.checkState(mIsValid, "Accessed a host service after it became invalidated");
    }
}
