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
package com.android.car.libraries.apphost.distraction.checkers;

import androidx.car.app.model.signin.InputSignInMethod;
import androidx.car.app.model.signin.SignInTemplate;
import androidx.car.app.model.signin.SignInTemplate.SignInMethod;

import java.util.Objects;

/** A {@link TemplateChecker} implementation for {@link SignInTemplate} */
public class SignInTemplateChecker implements TemplateChecker<SignInTemplate> {
    /**
     * Returns whether the new template is considered a refresh of the old one.
     *
     * <p>A new template is considered a refresh of a previous one if the title and messages have
     * not changed.
     */
    @Override
    public boolean isRefresh(SignInTemplate newTemplate, SignInTemplate oldTemplate) {
        if (!Objects.equals(oldTemplate.getTitle(), newTemplate.getTitle())) {
            return false;
        }

        if (!Objects.equals(oldTemplate.getInstructions(), newTemplate.getInstructions())) {
            return false;
        }

        if (!Objects.equals(oldTemplate.getAdditionalText(), newTemplate.getAdditionalText())) {
            return false;
        }

        SignInMethod oldSignIn = oldTemplate.getSignInMethod();
        SignInMethod newSignIn = newTemplate.getSignInMethod();

        // If the signin method types are different, they should not be considered as refreshes.
        if (!oldSignIn.getClass().equals(newSignIn.getClass())) {
            return false;
        }

        // For InputSignInMethod - we want to make sure the input details are the same.
        if (oldSignIn instanceof InputSignInMethod) {
            InputSignInMethod oldInputSignIn = (InputSignInMethod) oldSignIn;
            InputSignInMethod newInputSignIn = (InputSignInMethod) newSignIn;

            return oldInputSignIn.getInputType() == newInputSignIn.getInputType()
                    && oldInputSignIn.getKeyboardType() == newInputSignIn.getKeyboardType()
                    && Objects.equals(oldInputSignIn.getHint(), newInputSignIn.getHint());
        } else {
            // For pin and provider sign-in, we don't check that the pin or provider action stay the
            // same.
            return true;
        }
    }
}
