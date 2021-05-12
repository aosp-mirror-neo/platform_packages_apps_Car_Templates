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

import android.util.Log
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.car.app.utils.ThreadUtils
import com.android.car.ui.FocusArea
import com.android.car.ui.FocusParkingView
import com.android.car.libraries.apphost.logging.LogTags
import kotlin.math.abs

/**
 * Handles the rotary events coming from the app library.
 *
 * @property rootView The root of the view hierarchy for which the focus should be managed.
 */
class RotaryHandler(private val rootView: ViewGroup) {
    private val focusFinder = FocusFinder.getInstance()

    /**
     * Moves the focus to the next focusable view inside current FocusArea.
     *
     * @param steps The number of times the focus should move.
     * @param isClockwise Whether the rotation is in clockwise direction.
     */
    fun onRotate(steps: Int, isClockwise: Boolean) {
        ThreadUtils.runOnMain {
            var currentFocus = rootView.findFocus() ?: run {
                Log.w(LogTags.APP_HOST, "Cannot focus the root view.")
                return@runOnMain
            }
            val focusArea = findFocusArea(currentFocus) ?: run {
                Log.w(LogTags.APP_HOST, "Focused view does not have a FocusArea parent.")
                return@runOnMain
            }

            val direction = rotationFocusDirection(focusArea.orientation, isClockwise)

            repeat(abs(steps)) {
                currentFocus = focusFinder.findNextFocus(focusArea, currentFocus, direction)
                    ?: return@runOnMain
            }
            currentFocus.requestFocus()
        }
    }

    /** Selects the currently focus view. */
    fun onSelect() {
        ThreadUtils.runOnMain {
            val focused = rootView.findFocus()
            focused?.callOnClick()
        }
    }

    /**
     * Moves the focus to the next FocusArea based on the given nudge [keyCode].
     *
     * @param keyCode The key which determines the focus direction.
     * @return True if manages to update the focus.
     */
    fun onNudgeKey(keyCode: Int): Boolean {
        val direction = focusDirection(keyCode) ?: return false
        var currentFocus = rootView.findFocus() ?: return false
        val focusArea = findFocusArea(currentFocus) ?: return false

        // Find the first view outside the current focus area.
        while (findFocusArea(currentFocus) == focusArea) {
            currentFocus = focusFinder.findNextFocus(rootView, currentFocus, direction)
                ?: return false
        }
        ThreadUtils.runOnMain {
            currentFocus.requestFocus()
        }
        return currentFocus !is FocusParkingView
    }

    /**
     * The direction in which the focus should move.
     *
     * @param orientation The orientation in which the focus direction is requested.
     * @param isClockwise The rotation direction.
     * @return The focus direction. Null if cannot find a proper mapping.
     */
    private fun rotationFocusDirection(orientation: Int, isClockwise: Boolean): Int {
        return if (orientation == FocusArea.HORIZONTAL) {
            if (isClockwise) View.FOCUS_RIGHT else View.FOCUS_LEFT
        } else {
            if (isClockwise) View.FOCUS_DOWN else View.FOCUS_UP
        }
    }

    /**
     * The direction in which the focus should move.
     * @param keyCode The keyCode for which the focus direction should be determined.
     * @return The focus direction for the given [keyCode]. Null if cannot find a proper mapping.
     */
    private fun focusDirection(keyCode: Int): Int? {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            else -> null
        }
    }

    /**
     * Traverses the view hierarchy starting from the [view] up to the [rootView] to find the
     * closest focus area.
     *
     * @param view The view to search from.
     * @return The closes focus area parent. Null if not found.
     */
    private fun findFocusArea(view: View?): FocusArea? {
        var current = view
        while (current != null && current !is FocusArea) {
            current = current.parent as? View
        }
        return current as? FocusArea
    }
}
