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
package com.android.car.libraries.apphost.distraction.constraints;

import static com.android.car.libraries.apphost.distraction.constraints.RowConstraints.ROW_CONSTRAINTS_CONSERVATIVE;
import static com.android.car.libraries.apphost.distraction.constraints.RowConstraints.ROW_CONSTRAINTS_FULL_LIST;
import static com.android.car.libraries.apphost.distraction.constraints.RowConstraints.ROW_CONSTRAINTS_PANE;
import static com.android.car.libraries.apphost.distraction.constraints.RowConstraints.ROW_CONSTRAINTS_SIMPLE;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Encapsulates the constraints to apply when rendering a row list under different contexts. */
public class RowListConstraints {
    /** Represents the type of list that a constraint applies to. */
    @IntDef(value = {DEFAULT_LIST, PANE, ROUTE_PREVIEW, GRID})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ListType {}

    /** The constraint applies to all lists of this type by default. */
    @ListType public static final int DEFAULT_LIST = 0;

    /** The constraint applies to a pane. */
    @ListType public static final int PANE = 1;

    /**
     * The constraint applies to a list representing the set of route previews in a navigation
     * template.
     */
    @ListType public static final int ROUTE_PREVIEW = 2;

    /** The constraint applies to a grid. */
    @ListType public static final int GRID = 3;

    /** Conservative constraints for all types lists. */
    public static final RowListConstraints ROW_LIST_CONSTRAINTS_CONSERVATIVE =
            RowListConstraints.builder()
                    .setRowListType(DEFAULT_LIST)
                    .setMaxActions(0)
                    .setRowConstraints(ROW_CONSTRAINTS_CONSERVATIVE)
                    .setAllowSelectableLists(false)
                    .build();

    /** Default constraints for heterogeneous pane of items, full width. */
    public static final RowListConstraints ROW_LIST_CONSTRAINTS_PANE =
            ROW_LIST_CONSTRAINTS_CONSERVATIVE
                    .newBuilder()
                    .setMaxActions(2)
                    .setRowListType(PANE)
                    .setRowConstraints(ROW_CONSTRAINTS_PANE)
                    .setAllowSelectableLists(false)
                    .build();

    /** Default constraints for uniform lists of items, no toggles. */
    public static final RowListConstraints ROW_LIST_CONSTRAINTS_SIMPLE =
            ROW_LIST_CONSTRAINTS_CONSERVATIVE
                    .newBuilder()
                    .setRowConstraints(ROW_CONSTRAINTS_SIMPLE)
                    .build();

    /** Default constraints for the route preview card. */
    public static final RowListConstraints ROW_LIST_CONSTRAINTS_ROUTE_PREVIEW =
            ROW_LIST_CONSTRAINTS_CONSERVATIVE
                    .newBuilder()
                    .setRowListType(ROUTE_PREVIEW)
                    .setRowConstraints(ROW_CONSTRAINTS_SIMPLE)
                    .setAllowSelectableLists(true)
                    .build();

    /** Default constraints for uniform lists of items, full width (simple + toggle support). */
    public static final RowListConstraints ROW_LIST_CONSTRAINTS_FULL_LIST =
            ROW_LIST_CONSTRAINTS_CONSERVATIVE
                    .newBuilder()
                    .setRowConstraints(ROW_CONSTRAINTS_FULL_LIST)
                    .setAllowSelectableLists(true)
                    .build();

    @ListType private final int mRowListType;
    private final int mMaxActions;
    private final RowConstraints mRowConstraints;
    private final boolean mAllowSelectableLists;

    /** A builder of {@link RowListConstraints}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder of {@link RowListConstraints} set up with the information from this
     * instance.
     */
    public Builder newBuilder() {
        return new Builder(this);
    }

    /** Returns the {@link ListType} for this constraint. */
    @ListType
    public int getRowListType() {
        return mRowListType;
    }

    /** Returns the maximum number of actions allowed to be added alongside the list. */
    public int getMaxActions() {
        return mMaxActions;
    }

    /** Returns the constraints to apply on individual rows. */
    public RowConstraints getRowConstraints() {
        return mRowConstraints;
    }

    /** Returns whether radio lists are allowed. */
    public boolean getAllowSelectableLists() {
        return mAllowSelectableLists;
    }

    private RowListConstraints(Builder builder) {
        mMaxActions = builder.mMaxActions;
        mRowConstraints = builder.mRowConstraints;
        mAllowSelectableLists = builder.mAllowSelectableLists;
        mRowListType = builder.mRowListType;
    }

    /** A builder of {@link RowListConstraints}. */
    public static class Builder {
        @ListType private int mRowListType;
        private int mMaxActions;
        private RowConstraints mRowConstraints = RowConstraints.UNCONSTRAINED;
        private boolean mAllowSelectableLists;

        /** Sets the {@link ListType} for this constraint. */
        public Builder setRowListType(@ListType int rowListType) {
            mRowListType = rowListType;
            return this;
        }

        /** Sets the maximum number of actions allowed to be added alongside the list. */
        public Builder setMaxActions(int maxActions) {
            mMaxActions = maxActions;
            return this;
        }

        /** Sets the constraints to apply on individual rows. */
        public Builder setRowConstraints(RowConstraints rowConstraints) {
            mRowConstraints = rowConstraints;
            return this;
        }

        /** Sets whether radio lists are allowed. */
        public Builder setAllowSelectableLists(boolean allowSelectableLists) {
            mAllowSelectableLists = allowSelectableLists;
            return this;
        }

        /** Constructs a {@link RowListConstraints} from this builder. */
        public RowListConstraints build() {
            return new RowListConstraints(this);
        }

        private Builder() {}

        private Builder(RowListConstraints constraints) {
            mMaxActions = constraints.mMaxActions;
            mRowConstraints = constraints.mRowConstraints;
            mAllowSelectableLists = constraints.mAllowSelectableLists;
            mRowListType = constraints.mRowListType;
        }
    }
}
