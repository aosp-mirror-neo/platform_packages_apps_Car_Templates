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

import androidx.car.app.model.GridTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.model.TemplateWrapper
import com.android.car.libraries.apphost.common.TemplateContext
import com.android.car.libraries.apphost.template.view.model.RowListWrapperTemplate
import com.android.car.libraries.apphost.view.TemplatePresenter
import com.android.car.libraries.apphost.view.TemplatePresenterFactory
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableSet

/**
 * TemplatePresenterFactory for Android Automotive host, provides {@link TemplatePresenter}
 * instances for supported templates
 */
object CommonTemplatePresenterFactory : TemplatePresenterFactory {

    private val SUPPORTED_TEMPLATES: ImmutableCollection<Class<out Template>> =
        ImmutableSet.of(
            MessageTemplate::class.java,
            SearchTemplate::class.java,
            RowListWrapperTemplate::class.java,
            GridTemplate::class.java
        )

    override fun getSupportedTemplates() = SUPPORTED_TEMPLATES

    override fun createPresenter(
        templateContext: TemplateContext,
        template: TemplateWrapper
    ): TemplatePresenter? {
        return when (template.template) {
            is SearchTemplate -> SearchTemplatePresenter(templateContext, template)
            is MessageTemplate -> MessageTemplatePresenter(templateContext, template)
            is RowListWrapperTemplate -> RowListWrapperTemplatePresenter(templateContext, template)
            is GridTemplate -> GridTemplatePresenter(templateContext, template)
            else -> null
        }
    }
}
