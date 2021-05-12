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
package com.android.car.libraries.templates.host.view_dev.widget

import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import com.android.car.libraries.apphost.distraction.constraints.ActionsConstraints
import com.google.common.collect.ImmutableList
import java.util.HashSet
import kotlin.jvm.Throws

/** A set of utility methods for the header view. */
object HeaderViewUtils {
    /**
     * Validates the [ActionStrip] against the [ActionsConstraints] instance's required
     * types.
     *
     * @throws ValidationException if the action strip does not meet the required type
     * constraints.
     */
    @Throws(ValidationException::class)
    fun validateRequiredTypes(actionStrip: ActionStrip?, constraints: ActionsConstraints) {
        // Check for any missing required types.
        val requiredActionTypes = constraints.requiredActionTypes
        if (requiredActionTypes.isEmpty()) {
            return
        }

        val actions = actionStrip?.actions ?: ImmutableList.of()
        val requiredTypes = HashSet(requiredActionTypes)
        for (action in actions) {
            requiredTypes.remove(action.type)
        }
        if (requiredTypes.isEmpty()) {
            return
        }
        val missingTypeError = StringBuilder()
        for (type in requiredTypes) {
            missingTypeError.append("${Action.typeToString(type)};")
        }
        throw ValidationException("Missing required action types: $missingTypeError")
    }

    /** An exception thrown if the action strip validation fails.  */
    internal class ValidationException internal constructor(errorMessage: String) :
        Exception(errorMessage)
}