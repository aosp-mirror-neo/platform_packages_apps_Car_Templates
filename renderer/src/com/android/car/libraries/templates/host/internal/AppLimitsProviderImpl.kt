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
package com.android.car.libraries.templates.host.internal

import android.content.Context
import com.android.car.libraries.apphost.distraction.constraints.AppLimitsProvider
import com.android.car.libraries.templates.host.R

/** Provides different limit values for the car app. */
class AppLimitsProviderImpl(private val context: Context) : AppLimitsProvider {
    override fun getListMaxLength(rowListType: Int): Int {
        return context.resources.getInteger(R.integer.list_max_length)
    }

    override fun getTemplateStackMaxSize(): Int {
        return context.resources.getInteger(R.integer.template_stack_max_size)
    }
}
