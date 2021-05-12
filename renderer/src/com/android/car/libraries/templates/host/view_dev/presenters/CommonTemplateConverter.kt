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
package com.android.car.libraries.templates.host.view_dev.presenters

import android.content.Context
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Template
import androidx.car.app.model.TemplateWrapper
import com.android.car.libraries.apphost.template.view.model.RowListWrapperTemplate
import com.android.car.libraries.apphost.view.TemplateConverter
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableSet

object CommonTemplateConverter : TemplateConverter {

    private val SUPPORTED_TEMPLATES: ImmutableCollection<Class<out Template>> =
        ImmutableSet.of(
            PaneTemplate::class.java,
            ListTemplate::class.java
        )

    override fun maybeConvertTemplate(
        context: Context,
        templateWrapper: TemplateWrapper
    ): TemplateWrapper {
        when (templateWrapper.template) {
            is PaneTemplate, is ListTemplate -> {
                val newTemplate = RowListWrapperTemplate.wrap(
                    context, templateWrapper.template, templateWrapper.isRefresh
                )
                val newWrapper = TemplateWrapper.wrap(newTemplate, templateWrapper.id)
                newWrapper.isRefresh = templateWrapper.isRefresh
                newWrapper.currentTaskStep = templateWrapper.currentTaskStep
                return newWrapper
            }
        }
        return templateWrapper
    }

    override fun getSupportedTemplates() = SUPPORTED_TEMPLATES
}
