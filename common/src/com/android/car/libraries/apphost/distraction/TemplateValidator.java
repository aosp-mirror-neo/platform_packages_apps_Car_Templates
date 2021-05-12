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
package com.android.car.libraries.apphost.distraction;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Template;
import androidx.car.app.model.TemplateInfo;
import androidx.car.app.model.TemplateWrapper;
import androidx.car.app.navigation.model.NavigationTemplate;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.distraction.checkers.TemplateChecker;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * A class for validating whether an app's template flow abide by the flow rules.
 *
 * <p>The host should call {@link #validateFlow} to check whether a new template is allowed in the
 * flow, governed by the following rules:
 *
 * <ul>
 *   <li>BACK: if the new template contains the same ID and type as another template that have
 *       already been seen, it is considered a back operation, and the step count will be reset to
 *       the value used for the previously-seen template.
 *   <li>REFRESH: if the new template does not contain different immutable contents compared to the
 *       most recent template, it is considered a refresh and the step count will not increased.
 *   <li>NEW: Otherwise, the template is considered a new view and is only allowed if the given step
 *       limit has not been reached. If the template is allowed and is a consumption view, as
 *       defined by {@link #isConsumptionView}, the step count is reset and the next new template
 *       will start from a step count of zero again.
 * </ul>
 *
 * <p>See go/watevra-distraction-part1 for more details.
 */
public final class TemplateValidator {
    private final int mStepLimit;
    @Nullable private TemplateWrapper mLastTemplateWrapper;
    private final Deque<TemplateStackItem> mTemplateItemStack = new ArrayDeque<>();

    /**
     * When set to the true, the next template received for validation will have its step reset tot
     * zero (e.g. the template will be considered the start of a new task).
     */
    private boolean mIsReset;

    private int mNextStep;
    private final Map<Class<? extends Template>, TemplateChecker<? extends Template>>
            mTemplateCheckerMap = new HashMap<>();

    /** Constructs a {@link TemplateValidator} instance with a given maximum number of steps. */
    public static TemplateValidator create(int stepLimit) {
        return new TemplateValidator(stepLimit);
    }

    /**
     * Registers a {@link TemplateChecker} to be used for the template type during the {@link
     * #validateFlow} operation.
     */
    public <T extends Template> void registerTemplateChecker(
            Class<T> templateClass, TemplateChecker<T> checker) {
        mTemplateCheckerMap.put(templateClass, checker);
    }

    /** Reset the current step count on the next template received. */
    public void reset() {
        // Note that we don't clear the stack here. The host needs to keep track of the templates
        // it has seen, so that it can compare the list of TemplateInfo inside TemplateWrapper,
        // and not count them after the refresh. See b/179085934 for more details.
        mIsReset = true;
        mLastTemplateWrapper = null;
    }

    /** Returns the step count that should be used for the next Template. */
    @VisibleForTesting
    public int getNextStep() {
        return mNextStep;
    }

    /** Returns whether the validator will reset the step count on the next template received. */
    @VisibleForTesting
    public boolean isPendingReset() {
        return mIsReset;
    }

    @Override
    public String toString() {
        return "[ step limit: " + mStepLimit + ", next step: " + mNextStep + "]";
    }

    /**
     * Validates whether the application has the required permissions for this template.
     *
     * @throws SecurityException if the application is missing any required permission
     */
    @SuppressWarnings("unchecked")
    public void validateHasRequiredPermissions(
            TemplateContext templateContext, TemplateWrapper templateWrapper) {
        Template template = templateWrapper.getTemplate();

        @SuppressWarnings("rawtypes")
        TemplateChecker checker = mTemplateCheckerMap.get(template.getClass());
        if (checker == null) {
            throw new IllegalStateException(
                    "Permission check failed. No checker has been registered for the template"
                            + " type: "
                            + template);
        }

        Context appConfigurationContext = templateContext.getAppConfigurationContext();
        if (appConfigurationContext == null) {
            L.d(
                    LogTags.DISTRACTION,
                    "Permission check failed. No app configuration context is registered.");
            // If we do not have a context for the car app do not allow due to missing
            // permissions, this is a bad state.
            throw new IllegalStateException(
                    "Could not validate whether the app has required permissions");
        }

        checker.checkPermissions(appConfigurationContext, template);
    }

    /**
     * Validates whether the given {@link TemplateWrapper} meets the flow restriction requirements.
     *
     * @throws FlowViolationException if the new template contains the same ID as a previously seen
     *     template but is of a different template type
     * @throws FlowViolationException if the step limit has been reached and the template is not
     */
    public void validateFlow(TemplateWrapper templateWrapper) throws FlowViolationException {
        fillInBackStackIfNeeded(templateWrapper);

        if (validateRefreshFlow(templateWrapper) || validateBackFlow(templateWrapper)) {
            mLastTemplateWrapper = templateWrapper;
            return;
        }

        throwIfNewTemplateDisallowed(templateWrapper);

        L.d(
                LogTags.DISTRACTION,
                "NEW template detected. Task step currently at %d of %d. %s",
                mNextStep,
                mStepLimit,
                templateWrapper);

        Template template = templateWrapper.getTemplate();
        mNextStep = resetTaskStepIfNeeded(mNextStep, template.getClass());
        templateWrapper.setCurrentTaskStep(mNextStep++);
        mTemplateItemStack.push(
                new TemplateStackItem(
                        templateWrapper.getId(),
                        template.getClass(),
                        templateWrapper.getCurrentTaskStep()));
        mLastTemplateWrapper = templateWrapper;
    }

    private void fillInBackStackIfNeeded(TemplateWrapper templateWrapper) {
        // The template infos are ordered as follows:  top, second, third, bottom
        // Look through our known stack, if there are more than 1 templates in the top that we do
        // not currently have, we need to add them to our stack.
        //
        // If there is 1 extra template, it'll be handled by the pushing logic in validateFlow.
        //
        // If there the top template ids are the same, it will be handled by the logic in
        // validateRefreshFlow.
        //
        // If there are less in the client provided stack, it will be handled by the logic in
        // validateBackFlow.
        Deque<TemplateInfo> newTemplates = new ArrayDeque<>();
        for (TemplateInfo templateInfo : templateWrapper.getTemplateInfosForScreenStack()) {
            // For each not known template push it onto a separate stack, so that after all the
            // pushes, it will be ordered as follows:
            //
            // i.e. if the client has 3 new templates that the host does not know about this
            // temporary stack will be third, second, top
            if (findExistingTemplateStackItem(templateInfo.getTemplateId()) == null) {
                newTemplates.push(templateInfo);
            } else {
                break;
            }
        }

        // At this point the "newTemplates" stack contains any values they are new templates that
        // the host does not know about.
        // We do not need to push the bottom of this new stack, as that is the new template which
        // will be handled by validateFlow.
        while (newTemplates.size() > 1) {
            // Set last template wrapper to null so that we don't check if the new one is possibly a
            // refresh since we are preseeding templates in between the current top and the new top.
            mLastTemplateWrapper = null;

            TemplateInfo templateInfo = newTemplates.pop();
            mNextStep = resetTaskStepIfNeeded(mNextStep, templateInfo.getTemplateClass());
            mTemplateItemStack.push(
                    new TemplateStackItem(
                            templateInfo.getTemplateId(),
                            templateInfo.getTemplateClass(),
                            mNextStep++));
        }
    }

    /**
     * Returns {@code true} if the given {@link TemplateWrapper} is a refresh of the last-sent
     * template based on the registered {@link TemplateChecker}, or {@code false otherwise}.
     *
     * <p>Note that if a {@link TemplateChecker} is not available for a template type, the {@link
     * #validateFlow} operation will return false by default.
     *
     * <p>A template is considered a refresh if it is of the same template type and does not have
     * data that we consider immutable as compared to the previous template. If the input template
     * is deemed a refresh, the task step count will be changed.
     */
    @SuppressWarnings({"RestrictTo", "unchecked"})
    private boolean validateRefreshFlow(TemplateWrapper templateWrapper) {
        TemplateWrapper lastTemplateWrapper = mLastTemplateWrapper;
        TemplateStackItem lastTemplateStackItem = mTemplateItemStack.peek();
        if (lastTemplateWrapper == null || lastTemplateStackItem == null) {
            return false;
        }

        Template lastTemplate = lastTemplateWrapper.getTemplate();
        Template newTemplate = templateWrapper.getTemplate();

        @SuppressWarnings("rawtypes")
        TemplateChecker checker = mTemplateCheckerMap.get(newTemplate.getClass());
        if (checker == null) {
            L.d(
                    LogTags.DISTRACTION,
                    "REFRESH check failed. No checker has been registered for the template type:"
                            + " %s",
                    newTemplate.getClass());
            return false;
        }

        if (lastTemplate.getClass() != newTemplate.getClass()) {
            L.d(
                    LogTags.DISTRACTION,
                    "REFRESH check failed. Template type differs (previous: %s, new: %s).",
                    lastTemplateWrapper,
                    templateWrapper);
            return false;
        }

        if (checker.isRefresh(newTemplate, lastTemplate)) {
            mNextStep =
                    resetTaskStepIfNeeded(lastTemplateStackItem.getStep(), newTemplate.getClass());
            templateWrapper.setCurrentTaskStep(mNextStep++);
            templateWrapper.setRefresh(true);

            // We push the new template as a new stack item so that we can keep track of the refresh
            // stack. This is needed to handle a case where if a template is refreshed across
            // multiple screens (e.g. same template content, different template ids), when the app
            // pops back to a previous screen and sends the previous template, the host will
            // recognize the id in the stack and consider it a back operation. (See b/160892144 for
            // more context).
            mTemplateItemStack.push(
                    new TemplateStackItem(
                            templateWrapper.getId(),
                            newTemplate.getClass(),
                            templateWrapper.getCurrentTaskStep()));

            L.d(
                    LogTags.DISTRACTION,
                    "REFRESH detected. Task step currently at %d of %d. %s",
                    templateWrapper.getCurrentTaskStep(),
                    mStepLimit,
                    templateWrapper);
            return true;
        }

        return false;
    }

    /**
     * Checks whether the given {@link TemplateWrapper} is a back operation. Returns {@code true} if
     * so, {@code false otherwise}.
     *
     * <p>A template is considered a back operation if it is of the same template type and the same
     * ID as a template that is already on the stack. If the input template is deemed to be a back
     * operation, method will pop any templates on the stack above the target template we are going
     * back to, and reset the task step count to the value held by the target template.
     *
     * @throws FlowViolationException if the target template with the matching ID is of a different
     *     template type
     */
    private boolean validateBackFlow(TemplateWrapper templateWrapper)
            throws FlowViolationException {
        String id = templateWrapper.getId();
        Template template = templateWrapper.getTemplate();

        // This detects the case where the app is popping screens (e.g. going back).
        // If there is a template with a matching ID and type in the stack, pop everything
        // above the found item, then update the template and set the task step to the value at that
        // found item.
        TemplateStackItem foundItem = findExistingTemplateStackItem(id);
        if (foundItem != null) {
            if (foundItem.getTemplateClass() != template.getClass()) {
                throw new BackFlowViolationException(
                        String.format(
                                "BACK operation failed. Template types differ (previous: %s, new:"
                                        + " %s).",
                                foundItem, templateWrapper));
            }

            while (foundItem != mTemplateItemStack.peek()) {
                mTemplateItemStack.pop();
            }

            L.d(
                    LogTags.DISTRACTION,
                    "BACK detected. Task step currently at %d of %d. %s",
                    foundItem.getStep(),
                    mStepLimit,
                    templateWrapper);

            mNextStep = resetTaskStepIfNeeded(foundItem.getStep(), template.getClass());
            templateWrapper.setCurrentTaskStep(mNextStep++);
            return true;
        }

        return false;
    }

    /**
     * Returns the {@link TemplateStackItem} currently in the stack with the given ID, or {@code
     * null} if none is found.
     */
    @Nullable
    private TemplateStackItem findExistingTemplateStackItem(String id) {
        TemplateStackItem foundItem = null;
        for (TemplateStackItem stackItem : mTemplateItemStack) {
            if (stackItem.getTemplateId().equals(id)) {
                foundItem = stackItem;
                break;
            }
        }

        return foundItem;
    }

    /**
     * Validates that we still have budget for a new template, throw otherwise. If it is the last
     * step in the flow, also validates that only certain template classes are allowed, throw
     * otherwise.
     */
    private void throwIfNewTemplateDisallowed(TemplateWrapper templateWrapper)
            throws OverLimitFlowViolationException {
        // Check that we still have quota.
        if (mNextStep >= mStepLimit) {
            throw new OverLimitFlowViolationException(
                    String.format(
                            "No template allowed after %d templates. %s",
                            mStepLimit, templateWrapper));
        }

        // Special case for the last step - only certain template types are supported.
        if (mNextStep == mStepLimit - 1) {
            Class<? extends Template> templateClass = templateWrapper.getTemplate().getClass();
            if (!(templateClass.equals(NavigationTemplate.class)
                    || templateClass.equals(PaneTemplate.class)
                    || templateClass.equals(MessageTemplate.class))) {
                throw new OverLimitFlowViolationException(
                        String.format(
                                "Unsupported template type as the last step in a task. %s",
                                templateWrapper));
            }
        }
    }

    private TemplateValidator(int stepLimit) {
        mStepLimit = stepLimit;
    }

    /**
     * Returns the task step that should be used for the next template, resetting it to zero if a
     * reset has been requested or if the template is a "consumption view".
     */
    private int resetTaskStepIfNeeded(int taskStep, Class<? extends Template> templateClass) {
        if (mIsReset || isConsumptionView(templateClass)) {
            taskStep = 0;
            L.d(
                    LogTags.DISTRACTION,
                    "Resetting task step to %d. %s",
                    taskStep,
                    templateClass.getName());
            mIsReset = false;
        }

        return taskStep;
    }

    /**
     * Returns whether the given {@link Template} is a "consumption view".
     *
     * <p>Consumption views are defined as “sit-and-stay” experiences. In our library's context,
     * these is the {@link NavigationTemplate}, and can be extended to other templates such as media
     * playback and in-call view templates in the future when we support them.
     */
    private static boolean isConsumptionView(Class<? extends Template> templateClass) {
        boolean isConsumptionTemplate = NavigationTemplate.class.equals(templateClass);
        if (isConsumptionTemplate) {
            L.d(LogTags.DISTRACTION, "Consumption template detected. %s", templateClass.getName());
        }
        return isConsumptionTemplate;
    }

    /** Structure contain the template information to be stored onto the stack. */
    private static class TemplateStackItem {
        private final String mTemplateid;
        private final Class<? extends Template> mTemplateClass;
        private final int mStep;

        TemplateStackItem(String templateid, Class<? extends Template> templateClass, int step) {
            mTemplateid = templateid;
            mTemplateClass = templateClass;
            mStep = step;
        }

        String getTemplateId() {
            return mTemplateid;
        }

        Class<? extends Template> getTemplateClass() {
            return mTemplateClass;
        }

        int getStep() {
            return mStep;
        }
    }
}
