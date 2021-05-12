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
package com.android.car.libraries.templates.host

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.car.app.activity.renderer.ICarAppActivity
import androidx.car.app.activity.renderer.IRendererService
import androidx.car.app.utils.ThreadUtils
import com.android.car.libraries.apphost.logging.LogTags
import com.android.car.libraries.apphost.view.TemplateConverterRegistry
import com.android.car.libraries.apphost.view.TemplatePresenterRegistry
import com.android.car.libraries.templates.host.internal.CarHostRepository
import com.android.car.libraries.templates.host.renderer.ScreenRenderer
import com.android.car.libraries.templates.host.renderer.ScreenRendererRepository
import com.android.car.libraries.templates.host.view.presenters.common.CommonTemplateConverter
import com.android.car.libraries.templates.host.view.presenters.common.CommonTemplatePresenterFactory
import com.android.car.libraries.templates.host.view.presenters.maps.MapsTemplatePresenterFactory
import com.android.car.libraries.templates.host.view.presenters.navigation.NavigationTemplatePresenterFactory
import com.android.car.libraries.templates.host.view_dev.presenters.CommonTemplateConverter as CommonTemplateConverterDev
import com.android.car.libraries.templates.host.view_dev.presenters.CommonTemplatePresenterFactory as CommonTemplatePresenterFactoryDev
import dagger.hilt.android.AndroidEntryPoint
import java.io.FileDescriptor
import java.io.PrintWriter
import javax.inject.Inject

/**
 * A service used to render content of a car app service inside a car app activity.
 */
@AndroidEntryPoint(Service::class)
class RendererService : Hilt_RendererService() {
    // TODO(b/182486338): Migrate the inject point to TemplateView
    @Inject
    lateinit var mapsTemplatePresenterFactory: MapsTemplatePresenterFactory
    private var isDevViewMode = false

    override fun onBind(intent: Intent): IBinder? {
        registerPresenters()
        return RendererServiceBinder(this)
    }

    override fun onUnbind(intent: Intent): Boolean {
        CarHostRepository.clear()
        ScreenRendererRepository.clear()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        CarHostRepository.clear()
        ScreenRendererRepository.clear()
        super.onDestroy()
    }

    private fun registerPresenters() {
        TemplatePresenterRegistry.get().clear()
        if (isDevViewMode) {
            TemplatePresenterRegistry.get().register(NavigationTemplatePresenterFactory.get())
            TemplatePresenterRegistry.get().register(CommonTemplatePresenterFactoryDev)
            TemplateConverterRegistry.get().register(CommonTemplateConverterDev)
        } else {
            TemplatePresenterRegistry.get().register(NavigationTemplatePresenterFactory.get())
            TemplatePresenterRegistry.get().register(CommonTemplatePresenterFactory.get())
            TemplateConverterRegistry.get().register(CommonTemplateConverter.get())
        }
        TemplatePresenterRegistry.get().register(mapsTemplatePresenterFactory)
    }

    override fun dump(
        fd: FileDescriptor?,
        writer: PrintWriter,
        args: Array<String?>?
    ) {
        // TODO(b/181072543): Remove once view_dev migration completed.
        // To switch the view mode:
        // adb shell dumpsys activity service <service_name> switchViewMode
        if (args?.contains("switchViewMode") == true) {
            isDevViewMode = !isDevViewMode
            if (isDevViewMode) {
                writer.println("Switched to dev view mode")
            } else {
                writer.println("Switched to default view mode")
            }
            registerPresenters()
            ScreenRendererRepository.getAll().forEach {
                it.onConfigurationChanged()
            }
        }
    }

    private inner class RendererServiceBinder(val context: Context) : IRendererService.Stub() {

        // TODO(b/169604451): Add version support logic
        override fun isVersionSupported(version: String): Boolean = true

        override fun initialize(
            carActivity: ICarAppActivity,
            serviceName: ComponentName,
            displayId: Int
        ): Boolean {
            if (!isValid(serviceName)) return false
            val renderer = findRenderer(serviceName, displayId)
            ThreadUtils.runOnMain {
                renderer?.onCreateActivity(carActivity)
            }
            return true
        }

        override fun terminate(serviceName: ComponentName) {
            if (!isValid(serviceName)) return
            ScreenRendererRepository.remove(serviceName)
        }

        override fun onNewIntent(
            intent: Intent,
            serviceName: ComponentName,
            displayId: Int
        ): Boolean {
            val renderer = findRenderer(serviceName, displayId)
            ThreadUtils.runOnMain {
                renderer?.onNewIntent(intent)
            }
            return renderer != null
        }

        private fun findRenderer(serviceName: ComponentName, displayId: Int): ScreenRenderer? {
            if (!isValid(serviceName)) return null
            val display =
                context.getSystemService(DisplayManager::class.java).getDisplay(displayId)

            return ScreenRendererRepository.computeIfAbsent(
                serviceName,
                context.applicationContext,
                display,
                ::ScreenRenderer
            )
        }

        private fun isValid(serviceName: ComponentName?): Boolean {
            if (serviceName == null) {
                Log.e(LogTags.APP_HOST, "Service name was not specified!")
                return false
            }
            val senderPackage = context.packageManager.getNameForUid(Binder.getCallingUid())
            if (senderPackage == null || senderPackage != serviceName.packageName) {
                Log.e(LogTags.APP_HOST, "Could not verify the caller!")
                return false
            }
            return true
        }
    }
}
