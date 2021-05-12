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

import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import com.android.car.libraries.apphost.input.CarEditable
import com.android.car.libraries.apphost.input.CarEditableListener
import com.android.car.libraries.apphost.input.InputManager

/**
 * A wrapper for [EditText] to make it conform to CarEditable.
 */
class CarEditTextWrapper(
    private val editText: EditText,
    private val inputManager: InputManager
) : CarEditable {
    private var lastSelectionEnd = 0
    private var lastSelectionStart = 0
    private var carEditableListener: CarEditableListener? = null

    // Setup an accessibility delegate to get the text selection changes. This is required in order
    // to conform to the CarEditable which requires a text selection update listener.
    private val accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun sendAccessibilityEvent(host: View?, eventType: Int) {
            super.sendAccessibilityEvent(host, eventType)
            if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
                carEditableListener?.onUpdateSelection(
                    lastSelectionStart,
                    lastSelectionEnd,
                    editText.selectionStart,
                    editText.selectionEnd
                )
                lastSelectionStart = editText.selectionStart
                lastSelectionEnd = editText.selectionEnd
            }
        }
    }

    init {
        editText.accessibilityDelegate = accessibilityDelegate
        editText.setOnClickListener {
            inputManager.startInput(this@CarEditTextWrapper)
        }
        editText.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                inputManager.stopInput()
            }
        }
    }

    override fun setCarEditableListener(listener: CarEditableListener) {
        carEditableListener = listener
    }

    override fun setInputEnabled(enabled: Boolean) {
        editText.isEnabled = enabled
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo?): InputConnection {
        return editText.onCreateInputConnection(outAttrs)
    }
}