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

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import com.android.car.libraries.apphost.input.CarEditable
import com.android.car.libraries.apphost.input.CarEditableListener

/**
 * A special EditText for use in car.
 *
 * This class implements [CarEditable] interface which is required for any editable view in the
 * host.
 */
@SuppressLint("AppCompatCustomView")
class CarEditText(context: Context, attrs: AttributeSet?) : EditText(context, attrs), CarEditable {

    private var lastSelEnd = 0
    private var lastSelStart = 0
    private var carEditableListener: CarEditableListener? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        carEditableListener?.onUpdateSelection(lastSelStart, lastSelEnd, selStart, selEnd)
        lastSelStart = selStart
        lastSelEnd = selEnd
    }

    override fun setCarEditableListener(listener: CarEditableListener) {
        carEditableListener = listener
    }

    override fun setInputEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }

    override fun checkInputConnectionProxy(view: View?): Boolean {
        return true
    }
}
