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

import static java.lang.Math.min;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleableRes;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.CarText;
import androidx.car.app.model.Place;
import androidx.car.app.model.PlaceMarker;
import androidx.car.app.model.Row;
import androidx.car.app.model.Toggle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.car.libraries.apphost.common.TemplateContext;
import com.android.car.libraries.apphost.distraction.constraints.RowConstraints;
import com.android.car.libraries.apphost.logging.LogTags;
import com.android.car.libraries.apphost.template.view.model.RowListWrapper;
import com.android.car.libraries.apphost.template.view.model.RowWrapper;
import com.android.car.libraries.apphost.template.view.model.SelectionGroup;
import com.android.car.libraries.apphost.view.common.CarTextParams;
import com.android.car.libraries.apphost.view.common.CarTextUtils;
import com.android.car.libraries.apphost.view.common.ImageUtils;
import com.android.car.libraries.apphost.view.common.ImageViewParams;
import com.android.car.libraries.templates.host.R;
import com.android.car.libraries.templates.host.view.widgets.common.RowHolder.RowListener;
import com.android.car.ui.recyclerview.CarUiContentListItem;
import com.android.car.ui.recyclerview.CarUiContentListItem.IconType;
import com.android.car.ui.recyclerview.CarUiHeaderListItem;
import com.android.car.ui.recyclerview.CarUiListItem;
import com.android.car.ui.recyclerview.CarUiListItemAdapter;

import java.util.ArrayList;
import java.util.List;

/** Adapter for {@link ContentView} to display {@link CarUiListItem}s. */
public class RowAdapter extends CarUiListItemAdapter {

    /**
     * Start id for non-Chassis items. This value should be higher than any view type in {@link
     * CarUiListItemAdapter}.
     */
    private static final int ROW_LIST_VIEW_TYPE_BASE = 1000;

    private static final int ROW_LIST_VIEW_TYPE_ACTION_BUTTON = ROW_LIST_VIEW_TYPE_BASE + 1;

    @ColorInt private final int mDefaultIconTint;
    @Nullable private final Drawable mPlaceholderDrawable;

    private RowListener mRowListener;
    private List<RowHolder> mRowHolders;
    private TemplateContext mTemplateContext;
    private final MarkerFactory mMarkerFactory;

    static RowAdapter create(
            Context context, List<CarUiListItem> items, MarkerFactory markerFactory) {
        return new RowAdapter(context, items, markerFactory);
    }

    private RowAdapter(Context context, List<CarUiListItem> items, MarkerFactory markerFactory) {
        super(items);

        @StyleableRes
        final int[] themeAttrs = {
            R.attr.templateRowDefaultIconTint, R.attr.templateRowImagePlaceholder,
        };

        TypedArray ta = context.obtainStyledAttributes(themeAttrs);
        mDefaultIconTint = ta.getColor(0, 0);
        mPlaceholderDrawable = ta.getDrawable(1);
        ta.recycle();

        mMarkerFactory = markerFactory;
    }

    public List<RowHolder> getRowHolders() {
        return mRowHolders;
    }

    /** Updates the rows of the adapter. */
    @SuppressWarnings("unchecked")
    public void setRows(
            TemplateContext templateContext, List<RowHolder> rowHolders, RowListener rowListener) {
        mTemplateContext = templateContext;
        mRowHolders = rowHolders;
        mRowListener = rowListener;

        List<CarUiListItem> items = new ArrayList<>(rowHolders.size());
        for (int index = 0; index < rowHolders.size(); index++) {
            RowHolder holder = rowHolders.get(index);
            RowWrapper rowWrapper = holder.getRowWrapper();
            CarUiListItem item = createCarUiListItem(templateContext, rowWrapper, index);
            if (item == null) {
                Log.e(LogTags.TEMPLATE, "Cannot create item for the row " + rowWrapper);
                continue;
            }
            items.add(item);
        }

        getItems().clear();
        ((List<CarUiListItem>) getItems()).addAll(items);
        notifyDataSetChanged();
    }

    /** Updates row at index {@code index} of the adapter. */
    @SuppressWarnings("unchecked")
    public void updateRow(int index) {
        if (index < 0 || index >= mRowHolders.size()) {
            Log.e(LogTags.TEMPLATE, "Index out of bound " + index);
            return;
        }
        RowWrapper rowWrapper = mRowHolders.get(index).getRowWrapper();
        CarUiListItem item = createCarUiListItem(mTemplateContext, rowWrapper, index);
        if (item == null) {
            Log.e(LogTags.TEMPLATE, "Cannot create item for the row " + rowWrapper);
            return;
        }
        ((List<CarUiListItem>) getItems()).set(index, item);
        notifyItemChanged(index);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ROW_LIST_VIEW_TYPE_ACTION_BUTTON) {
            ActionButtonListView actionButtonListView =
                    new ActionButtonListView(parent.getContext());
            return new ActionButtonViewHolder(actionButtonListView);
        } else {
            return super.onCreateViewHolder(parent, viewType);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (getItems().get(position) instanceof ActionButtonListItem) {
            return ROW_LIST_VIEW_TYPE_ACTION_BUTTON;
        } else {
            return super.getItemViewType(position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (holder.getItemViewType() == ROW_LIST_VIEW_TYPE_ACTION_BUTTON) {
            CarUiListItem item = getItems().get(position);
            if (!(item instanceof ActionButtonListItem)) {
                Log.e(LogTags.TEMPLATE, "Incorrect item view type for item " + item);
                return;
            }
            if (!(holder instanceof ActionButtonViewHolder)) {
                Log.e(LogTags.TEMPLATE, "Incorrect view holder type for list item.");
                return;
            }

            ((ActionButtonViewHolder) holder).bind((ActionButtonListItem) item);
        } else {
            super.onBindViewHolder(holder, position);
        }
    }

    /** Converts a {@link Row} to a {@link CarUiListItem}. */
    @Nullable
    public CarUiListItem createCarUiListItem(
            TemplateContext templateContext, RowWrapper rowWrapper, int index) {
        if (rowWrapper.getRow() instanceof Row) {
            return createRowCarUiListItem(templateContext, rowWrapper, index);
        } else if (ActionListUtils.isActionList(rowWrapper.getRow())) {
            return createActionsCarUiListItem(templateContext, rowWrapper);
        } else {
            Log.i(LogTags.TEMPLATE, "Unknown row type ${rowWrapper.row.javaClass.name}");
            return null;
        }
    }

    /** Converts the {@link Row} to a header */
    private CarUiHeaderListItem createCarUiHeaderListItem(
            TemplateContext templateContext, Row row) {
        return new CarUiHeaderListItem(
                CarTextUtils.toCharSequenceOrEmpty(templateContext, row.getTitle()));
    }

    @Nullable
    private CarUiListItem createActionsCarUiListItem(
            TemplateContext templateContext, RowWrapper rowWrapper) {
        List<Action> actions = ActionListUtils.getActionList(rowWrapper.getRow());
        int maxActions = rowWrapper.getRowConstraints().getMaxActionsExclusive();

        return new RowAdapter.ActionButtonListItem(templateContext, actions, maxActions);
    }

    private CarUiListItem createRowCarUiListItem(
            TemplateContext templateContext, RowWrapper rowWrapper, int index) {
        Row row = (Row) rowWrapper.getRow();

        // If this row is a header, create a header item instead
        if ((rowWrapper.getRowFlags() & RowWrapper.ROW_FLAG_SECTION_HEADER) != 0) {
            return createCarUiHeaderListItem(templateContext, row);
        }

        CarUiContentListItem.Action action = createAction(rowWrapper);
        CarUiContentListItem item = new CarUiContentListItem(action);
        updateItemText(item, templateContext, rowWrapper, index);

        // Only update the item image if there is no place marker.
        if (!updateItemPlaceMarker(item, templateContext, rowWrapper)) {
            updateItemImage(item, templateContext, rowWrapper, index);
        }
        updateItemToggle(item, rowWrapper);
        updateItemSelection(item, rowWrapper, index);

        item.setOnItemClickedListener(
                v -> {
                    if (mRowListener != null) {
                        mRowListener.onRowClicked(index);
                    }
                });

        item.setOnCheckedChangeListener(
                (v, checked) -> {
                    if (mRowListener != null) {
                        mRowListener.onCheckedChange(index);
                    }
                });

        return item;
    }

    /** Updates the text fields of the item using properties of the {@link RowWrapper}. */
    private void updateItemText(
            CarUiContentListItem item,
            TemplateContext templateContext,
            RowWrapper rowWrapper,
            int index) {
        Row row = (Row) rowWrapper.getRow();
        RowConstraints constraints = rowWrapper.getRowConstraints();
        int listFlags = rowWrapper.getListFlags();

        boolean renderTitleAsSecondaryText =
                (listFlags & RowListWrapper.LIST_FLAGS_RENDER_TITLE_AS_SECONDARY) != 0;

        // Create a copy because the row model returns unmodifiable list.
        List<CarText> texts = new ArrayList<>(row.getTexts());

        CarText title = row.getTitle();
        if (title != null) {
            CharSequence titleString = CarTextUtils.toCharSequenceOrEmpty(templateContext, title);
            if (titleString.length() > 0) {
                if (renderTitleAsSecondaryText) {
                    texts.add(0, title);
                } else {
                    item.setTitle(titleString);
                }
            }
        }

        int lineCount = texts.size();
        int maxLineCount = min(constraints.getMaxTextLinesPerRow(), lineCount);

        if (maxLineCount < lineCount) {
            Log.d(
                    LogTags.TEMPLATE,
                    "Number of secondary text lines "
                            + lineCount
                            + " over limit of "
                            + maxLineCount);
        }

        while (!texts.isEmpty() && texts.size() > maxLineCount) {
            texts.remove(texts.size() - 1);
        }

        // Update the text to show when the row is selected, if any.
        CarText selectedText = rowWrapper.getSelectedText();
        if (selectedText != null) {
            SelectionGroup selectionGroup = rowWrapper.getSelectionGroup();
            if (selectionGroup != null && selectionGroup.isSelected(index)) {
                texts.add(selectedText);
            }
        }

        if (!texts.isEmpty()) {
            item.setBody(flattenRowBodyText(templateContext, texts));
        }
    }

    /**
     * Updates the place marker image of the item using properties of the {@link RowWrapper}.
     *
     * <p>Returns true iff a place marker was found.
     */
    private boolean updateItemPlaceMarker(
            CarUiContentListItem item, TemplateContext templateContext, RowWrapper rowWrapper) {
        Place place = rowWrapper.getMetadata().getPlace();
        if (place == null) {
            return false;
        }

        PlaceMarker marker = place.getMarker();
        if (marker == null) {
            return false;
        }

        Bitmap bitmap = mMarkerFactory.getContentForListMarker(templateContext, marker);
        if (bitmap == null) {
            return false;
        }

        item.setPrimaryIconType(IconType.STANDARD);
        item.setIcon(new BitmapDrawable(templateContext.getResources(), bitmap));
        return true;
    }

    /** Updates the image of the item using properties of the {@link RowWrapper}. */
    private void updateItemImage(
            CarUiContentListItem item,
            TemplateContext templateContext,
            RowWrapper rowWrapper,
            int index) {
        Row row = (Row) rowWrapper.getRow();
        CarIcon image = row.getImage();
        if (image == null) {
            return;
        }

        CarUiContentListItem.IconType iconType = convertImageTypeToIconType(row.getRowImageType());
        if (iconType == null) {
            Log.e(LogTags.TEMPLATE, "Unknown icon type for row " + row);
            return;
        }
        item.setPrimaryIconType(iconType);
        int iconSize = (int) getIconSize(iconType);

        ImageViewParams imageParams =
                ImageViewParams.builder()
                        .setPlaceholderDrawable(mPlaceholderDrawable)
                        .setDefaultTint(mDefaultIconTint)
                        .setForceTinting(row.getRowImageType() == Row.IMAGE_TYPE_ICON)
                        .setCarIconConstraints(
                                rowWrapper.getRowConstraints().getCarIconConstraints())
                        .build();
        ImageUtils.setImageTargetSrc(
                templateContext,
                row.getImage(),
                drawable -> {
                    item.setIcon(drawable);
                    notifyItemChanged(index);
                },
                imageParams,
                iconSize,
                iconSize);
    }

    /** Updates the toggle state of the item if necessary. */
    private void updateItemToggle(CarUiContentListItem item, RowWrapper rowWrapper) {
        Row row = (Row) rowWrapper.getRow();
        RowConstraints constraints = rowWrapper.getRowConstraints();

        if (row == null || row.getToggle() == null || !constraints.isToggleAllowed()) {
            return;
        }

        // Show the toggle button at the end if needed.
        Toggle toggle = row.getToggle();
        if (toggle != null && constraints.isToggleAllowed()) {
            item.setChecked(rowWrapper.isToggleChecked());
        }
    }

    /** Updates the selection state of the item if necessary. */
    private void updateItemSelection(CarUiContentListItem item, RowWrapper rowWrapper, int index) {
        SelectionGroup selectionGroup = rowWrapper.getSelectionGroup();
        boolean isSelected = selectionGroup != null && selectionGroup.getSelectedIndex() == index;
        boolean useRadioButton = item.getAction() == CarUiContentListItem.Action.RADIO_BUTTON;
        item.setChecked(isSelected && useRadioButton);
        item.setActivated(isSelected && !useRadioButton && shouldHighlightSelectedRow(rowWrapper));
    }

    @Nullable
    private CarUiContentListItem.IconType convertImageTypeToIconType(int imageType) {
        switch (imageType) {
            case Row.IMAGE_TYPE_LARGE:
                return IconType.CONTENT;
            case Row.IMAGE_TYPE_SMALL:
                return IconType.STANDARD;
            case Row.IMAGE_TYPE_ICON:
                return IconType.AVATAR;
            default:
                return null;
        }
    }

    private float getIconSize(CarUiContentListItem.IconType imageType) {
        Resources res = mTemplateContext.getResources();
        switch (imageType) {
            case CONTENT:
                return res.getDimension(
                        com.android.car.ui.R.dimen.car_ui_list_item_content_icon_width);
            case STANDARD:
                return res.getDimension(com.android.car.ui.R.dimen.car_ui_list_item_icon_size);
            case AVATAR:
                return res.getDimension(
                        com.android.car.ui.R.dimen.car_ui_list_item_avatar_icon_width);
        }
        Log.e(LogTags.TEMPLATE, "Unknown imageType: " + imageType);
        return res.getDimension(com.android.car.ui.R.dimen.car_ui_list_item_icon_size);
    }

    /** Returns proper {@link CarUiContentListItem.Action} for a given {@link RowWrapper}. */
    private CarUiContentListItem.Action createAction(RowWrapper rowWrapper) {
        if (!(rowWrapper.getRow() instanceof Row)) {
            return CarUiContentListItem.Action.NONE;
        }

        Row row = (Row) rowWrapper.getRow();
        if (row.isBrowsable()) {
            return CarUiContentListItem.Action.CHEVRON;
        } else if (row.getToggle() != null) {
            return CarUiContentListItem.Action.SWITCH;
        } else if (rowWrapper.getSelectionGroup() != null && shouldUseRadioButtons(rowWrapper)) {
            return CarUiContentListItem.Action.RADIO_BUTTON;
        } else {
            return CarUiContentListItem.Action.NONE;
        }
    }

    /**
     * Returns true if the flag for using radio buttons is enabled for the provided {@link
     * RowWrapper}.
     */
    private boolean shouldUseRadioButtons(RowWrapper rowWrapper) {
        return (rowWrapper.getListFlags() & RowListWrapper.LIST_FLAGS_SELECTABLE_USE_RADIO_BUTTONS)
                != 0;
    }

    /** Returns true if the flag for highlighting the currently selected row is enabled */
    private boolean shouldHighlightSelectedRow(RowWrapper rowWrapper) {
        return (rowWrapper.getListFlags() & RowListWrapper.LIST_FLAGS_SELECTABLE_HIGHLIGHT_ROW)
                != 0;
    }

    /**
     * Merges all the carTexts into one string.
     *
     * <p>{@link CarUiListItem} only supports a single CharSequence as body, but a Row can contain a
     * list of text body strings.
     */
    private CharSequence flattenRowBodyText(
            TemplateContext templateContext, List<CarText> carTexts) {
        CarTextParams textParams =
                CarTextParams.builder().setAllowColorSpans(true).setMaxImages(0).build();
        List<CharSequence> lines = new ArrayList<>();
        for (CarText carText : carTexts) {
            lines.add(CarTextUtils.toCharSequenceOrEmpty(templateContext, carText, textParams));
        }
        return joinLines(lines);
    }

    /**
     * Joins a list of lines by adding a line break between them.
     *
     * <p>Unlike {@code TextUtils.join} this method preserves the span data.
     */
    // TODO(b/182490952): Use CarUiContentListItem API instead.
    private CharSequence joinLines(List<CharSequence> texts) {
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        String delimiter = "";
        for (CharSequence text : texts) {
            stringBuilder.append(delimiter).append(text);
            delimiter = "\n";
        }
        return stringBuilder;
    }

    static class ActionButtonViewHolder extends RecyclerView.ViewHolder {

        ActionButtonViewHolder(View view) {
            super(view);
        }

        void bind(ActionButtonListItem item) {
            if (!(itemView instanceof ActionButtonListView)) {
                return;
            }
            ActionButtonListView actionButtonListView = (ActionButtonListView) itemView;
            actionButtonListView.setActionList(
                    item.getTemplateContext(), item.getActions(), item.getMaxActions());
        }
    }

    /** Definition of list item action buttons that can be inserted into {@link RowAdapter}. */
    public static class ActionButtonListItem extends CarUiListItem {
        private final TemplateContext mTemplateContext;
        private final List<Action> mActionList;
        private final int mMaxActions;

        ActionButtonListItem(
                TemplateContext templateContext, List<Action> actionList, int maxActions) {
            mActionList = actionList;
            mTemplateContext = templateContext;
            mMaxActions = maxActions;
        }

        /** Returns a list of {@link Action}s */
        List<Action> getActions() {
            return mActionList;
        }

        /** Returns the associated {@link TemplateContext} */
        TemplateContext getTemplateContext() {
            return mTemplateContext;
        }

        /** Returns the maximum number of actions allowed. */
        int getMaxActions() {
            return mMaxActions;
        }
    }
}
