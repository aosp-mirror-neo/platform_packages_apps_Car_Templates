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
package com.android.car.libraries.templates.host.renderer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.RemoteException
import android.util.Log
import android.view.Display
import android.view.View
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.activity.renderer.ICarAppActivity
import androidx.car.app.activity.renderer.surface.ISurfaceListener
import androidx.car.app.activity.renderer.surface.SurfaceWrapper
import androidx.car.app.model.TemplateWrapper
import androidx.car.app.serialization.Bundleable
import androidx.car.app.utils.ThreadUtils
import com.android.car.libraries.apphost.CarHost
import com.android.car.libraries.apphost.common.BackPressedHandler
import com.android.car.libraries.apphost.common.CarAppManager
import com.android.car.libraries.apphost.common.StatusBarManager
import com.android.car.libraries.apphost.logging.L
import com.android.car.libraries.apphost.logging.LogTags
import com.android.car.libraries.apphost.nav.NavigationHost
import com.android.car.libraries.apphost.template.AppHost
import com.android.car.libraries.apphost.template.UIController
import com.android.car.libraries.apphost.view.SurfaceProvider
import com.android.car.libraries.templates.host.internal.CarHostRepository
import com.android.car.libraries.templates.host.internal.InputManagerImpl
import com.android.car.libraries.templates.host.internal.CarAppServiceInfo
import com.android.car.libraries.templates.host.internal.NavigationStateCallbackImpl
import com.android.car.libraries.templates.host.internal.RendererCallback
import com.android.car.libraries.templates.host.internal.RotaryHandler
import com.android.car.libraries.templates.host.internal.StartCarAppUtil
import com.android.car.libraries.templates.host.internal.TemplateContextImpl
import com.android.car.libraries.templates.host.view.TemplateView
import com.google.common.base.Supplier

/**
 * A class used to handle rendering of a single car app screen.
 *
 * <p>Once the activity is ready the [onCreateActivity] should be called to start the rendering.
 *
 * @property appName Points to the car app service which provides the data for the screen.
 * @param display The display on which the content should be displayed.
 */
class ScreenRenderer(
    private val context: Context,
    private val appName: ComponentName,
    display: Display
) : BackPressedHandler, StatusBarManager {
    private var surfaceController: SurfaceController? = null
    private lateinit var carActivity: ICarAppActivity
    private var templateView = TemplateView.create(context)
    private val carAppManager = CarAppManagerImpl()
    private val carAppServiceInfo = CarAppServiceInfo(context, appName)
    private val isNavigationApp = carAppServiceInfo.isNavigationService()
    private var lastTemplate: TemplateWrapper? = null

    private val inputManagerListener = object : InputManagerImpl.InputManagerListener {
        override fun onStartInput() {
            carActivity.onStartInput()
        }

        override fun onStopInput() {
            carActivity.onStopInput()
        }
    }

    private val inputManager = InputManagerImpl(inputManagerListener)

    private val templateContext = TemplateContextImpl.create(
            context,
            appName,
            display,
            this,
            this,
            inputManager,
            carAppManager,
            isNavigationApp
    )

    private val uiController = object : UIController {
        override fun getSurfaceProvider(appName: ComponentName?): SurfaceProvider {
            return templateView.surfaceProvider
        }

        override fun setTemplate(appName: ComponentName?, template: TemplateWrapper?) {
            ThreadUtils.runOnMain {
                lastTemplate = template
                templateView.setTemplate(template)
            }
        }
    }

    private val rotaryHandler = RotaryHandler(templateView)

    override fun onBackPressed() {
        val carHost = CarHostRepository.get(appName)
        val appHost = carHost?.getHostOrThrow(CarContext.APP_SERVICE) as? AppHost
        appHost?.onBackPressed()
    }

    override fun setStatusBarState(
        statusBarState: StatusBarManager.StatusBarState?,
        rootView: View?
    ) {
        // TODO: Not yet implemented
        Log.v(
                LogTags.APP_HOST, "StatusBar state updated to $statusBarState. " +
                "RootView is $rootView."
        )
    }

    private fun createBinderIntent(intent: Intent): Intent {
        val binderIntent = Intent(CarAppService.SERVICE_INTERFACE)
        binderIntent.component = appName
        intent.extras?.let { extras ->
            binderIntent.putExtras(extras)
        }
        binderIntent.data = intent.data
        binderIntent.type = intent.type
        return binderIntent
    }

    fun onCreateActivity(carActivity: ICarAppActivity) {
        this.carActivity = carActivity
        val carHost = CarHostRepository.computeIfAbsent(
                appName,
                Supplier {
                    CarHost.create(templateContext)
                }
        )
        carHost.registerHostService(CarContext.APP_SERVICE) { appBinding ->
            AppHost.create(uiController, appBinding, templateContext)
        }

        // Register the navigation host service only if the app is a navigation app. An
        // exception will be thrown if non-nav apps try to request access to the
        // navigation host service.
        if (templateContext.carAppPackageInfo.isNavigationApp) {
            L.d(LogTags.NAVIGATION, "Registering navigation service")
            carHost.registerHostService(
                    CarContext.NAVIGATION_SERVICE
            ) { appBinding: Any? ->
                NavigationHost.create(
                        appBinding,
                        templateContext,
                        NavigationStateCallbackImpl(templateContext)
                )
            }
        }
        templateView.setParentLifecycle(carHost.lifecycle)
        templateView.setTemplateContext(templateContext)

        val surfaceListener = surfaceListener(carActivity, carHost)
        carActivity.setSurfaceListener(surfaceListener)
    }

    fun onNewIntent(intent: Intent) {
        val binderIntent = createBinderIntent(intent)
        CarHostRepository.get(appName)?.bindToApp(binderIntent)
    }

    /** Reloads the latest template to accommodate configuration changes. */
    fun onConfigurationChanged() {
        ThreadUtils.runOnMain {
            lastTemplate?.let { templateView.setTemplate(it) }
        }
    }

    private fun surfaceListener(carActivity: ICarAppActivity, carHost: CarHost): ISurfaceListener {
        return object : ISurfaceListener.Stub() {
            override fun onSurfaceAvailable(surfaceWrapperBundleable: Bundleable) {
                val surfaceWrapper = surfaceWrapperBundleable.get()
                if (surfaceWrapper !is SurfaceWrapper) {
                    Log.e(LogTags.APP_HOST,
                            "onSurfaceAvailable event invoked with unexpected type: $surfaceWrapper"
                    )
                    // TODO(b/181775931): Better handle error case
                    return
                }

                val width = surfaceWrapper.width
                val height = surfaceWrapper.height
                ThreadUtils.runOnMain {
                    val surfaceController = createSurfaceController(surfaceWrapper)
                    inflate(surfaceController, width, height)
                    this@ScreenRenderer.surfaceController = surfaceController
                    try {
                        carActivity.run {
                            setSurfacePackage(surfaceController.surfacePackage())
                            registerRendererCallback(
                                    RendererCallback(carHost, inputManager, rotaryHandler))
                        }
                    } catch (e: RemoteException) {
                        Log.e(LogTags.APP_HOST, "Binder invocation failed", e)
                    }
                }
            }

            override fun onSurfaceChanged(surfaceWrapperBundleable: Bundleable) {
                val surfaceWrapper = surfaceWrapperBundleable.get()
                if (surfaceWrapper !is SurfaceWrapper) {
                    Log.e(LogTags.APP_HOST,
                            "onSurfaceChanged event invoked with unexpected type: $surfaceWrapper"
                    )
                    return
                }

                ThreadUtils.runOnMain {
                    val width = surfaceWrapper.width
                    val height = surfaceWrapper.height
                    surfaceController?.relayout(width, height)
                }
            }
        }
    }

    private fun createSurfaceController(surfaceWrapper: SurfaceWrapper): SurfaceController {
        if (SUPPORTS_SURFACE_VIEW_HOST_WRAPPER && surfaceWrapper.hostToken != null) {
            return SurfaceControlViewHostController(context, surfaceWrapper)
        } else {
            return LegacySurfaceController(context)
        }
    }

    private fun inflate(viewHost: SurfaceController, width: Int, height: Int) {
        viewHost.setView(templateView, width, height)
    }

    private inner class CarAppManagerImpl : CarAppManager {
        override fun startCarApp(intent: Intent) {
            StartCarAppUtil.validateStartCarAppIntent(
                    context,
                    appName.packageName,
                    intent,
                    isNavigationApp
            )
            carActivity.startCarApp(intent)
        }

        override fun finishCarApp() {
            CarHostRepository.remove(appName)
            carActivity.finishCarApp()
        }
    }

    companion object {
        private val SUPPORTS_SURFACE_VIEW_HOST_WRAPPER =
                Build.VERSION.SDK_INT > Build.VERSION_CODES.R
    }
}
