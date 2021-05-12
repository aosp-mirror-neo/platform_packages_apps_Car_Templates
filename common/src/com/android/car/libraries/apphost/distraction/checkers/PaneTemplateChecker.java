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

import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A {@link TemplateChecker} implementation for {@link PaneTemplate} */
public class PaneTemplateChecker implements TemplateChecker<PaneTemplate> {
    /**
     * A new template is considered a refresh of the old if:
     *
     * <ul>
     *   <li>The template title has not changed, and
     *   <li>The previous pane is in a loading state, or the number of rows and the string contents
     *       (title, texts, not counting spans) of each row between the previous and new {@link
     *       Pane}s have not changed.
     * </ul>
     */
    @Override
    public boolean isRefresh(PaneTemplate newTemplate, PaneTemplate oldTemplate) {
        if (!Objects.equals(oldTemplate.getTitle(), newTemplate.getTitle())) {
            return false;
        }

        Pane oldPane = oldTemplate.getPane();
        Pane newPane = newTemplate.getPane();
        if (oldPane != null && oldPane.isLoading()) {
            return true;
        } else if (newPane != null && newPane.isLoading()) {
            return false;
        }

        List<Row> oldRows = oldPane == null ? Collections.emptyList() : oldPane.getRows();
        List<Row> newRows = newPane == null ? Collections.emptyList() : newPane.getRows();
        return CheckerUtils.itemsHaveSameContent(oldRows, newRows);
    }
}
