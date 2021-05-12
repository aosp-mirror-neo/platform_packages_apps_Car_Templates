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
package com.android.car.libraries.templates.host.view.widgets.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.annotation.VisibleForTesting;
import androidx.car.app.model.Action;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.templates.host.R;

import java.util.List;

/** Displays a list of {@link Action}s as buttons in a single horizontal layout. */
public class ActionButtonListView extends LinearLayout {
    private final int mActionMargin;
    private final int mActionVerticalMargin;
    private final int mActionInset;

    public ActionButtonListView(Context context) {
        this(context, null);
    }

    public ActionButtonListView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionButtonListView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressWarnings({
            "ResourceType",
            "method.invocation.invalid",
            "assignment.type.incompatible",
            "argument.type.incompatible"
    })
    public ActionButtonListView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        @StyleableRes
        final int[] themeAttrs = {
            R.attr.templateActionButtonMargin,
            R.attr.templateActionButtonVerticalMargin,
            R.attr.templateActionButtonInset
        };
        TypedArray ta = context.obtainStyledAttributes(themeAttrs);
        mActionMargin = ta.getDimensionPixelOffset(0, 0);
        mActionVerticalMargin = ta.getDimensionPixelOffset(1, 0);
        mActionInset = ta.getDimensionPixelOffset(2, 0);
        ta.recycle();
    }

    /** Returns the {@link ActionButtonView} for testing. */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public ActionButtonView getActionButtonView(int index) {
        int maxIndex = getChildCount() - 1;
        if (index > maxIndex || index < 0) {
            throw new IndexOutOfBoundsException(
                    "Action index is not within bounds of [0, " + maxIndex + "]");
        }

        View child = getChildAt(index);
        if (child instanceof ActionButtonView) {
            return (ActionButtonView) child;
        }
        throw new IllegalStateException(
                "Found unexpected type of view in action list: " + child.getClass());
    }

    /** Returns the size of the list for testing. */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public int size() {
        return getChildCount();
    }

    /** Sets the {@link Action}s that will be mapped into buttons. */
    public void setActionList(
            TemplateContext templateContext, List<Action> actionList, int maxActions) {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        removeAllViews();

        if (actionList == null || actionList.isEmpty()) {
            this.setVisibility(View.GONE);
            return;
        }

        setVisibility(View.VISIBLE);
        int count = 0;
        for (Action action : actionList) {
            if (count >= maxActions) {
                L.w(
                        LogTags.TEMPLATE,
                        "Maximum number of actions reached in row, skipping the rest");
                break;
            }

            View view = inflater.inflate(R.layout.action_button_view, this, false);
            ((ActionButtonView) view).setAction(templateContext, action);

            // The insets in each button adds to the margin between their visible areas. Subtract
            // them here.
            LayoutParams layoutParams = (LayoutParams) view.getLayoutParams();
            layoutParams.topMargin = mActionVerticalMargin - mActionInset;
            layoutParams.bottomMargin = mActionVerticalMargin - mActionInset;
            if (count > 0) {
                layoutParams.setMarginStart(2 * (mActionMargin - mActionInset));
            }
            addView(view, layoutParams);
            ++count;
        }
    }
}
