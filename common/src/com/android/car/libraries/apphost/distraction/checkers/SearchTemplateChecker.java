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

import androidx.car.app.model.SearchTemplate;

/** A {@link TemplateChecker} implementation for {@link SearchTemplate} */
public class SearchTemplateChecker implements TemplateChecker<SearchTemplate> {
    @Override
    public boolean isRefresh(SearchTemplate newTemplate, SearchTemplate oldTemplate) {
        // Always allow updating on search templates. Search results needs to be updated on the fly
        // as user searches.
        return true;
    }
}
