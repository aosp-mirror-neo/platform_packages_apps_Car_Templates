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

package com.android.car.libraries.apphost.common;

import static androidx.car.app.model.CarColor.TYPE_BLUE;
import static androidx.car.app.model.CarColor.TYPE_CUSTOM;
import static androidx.car.app.model.CarColor.TYPE_DEFAULT;
import static androidx.car.app.model.CarColor.TYPE_GREEN;
import static androidx.car.app.model.CarColor.TYPE_PRIMARY;
import static androidx.car.app.model.CarColor.TYPE_RED;
import static androidx.car.app.model.CarColor.TYPE_SECONDARY;
import static androidx.car.app.model.CarColor.TYPE_YELLOW;

import static com.android.car.libraries.apphost.common.ColorUtils.KEY_COLOR_PRIMARY;
import static com.android.car.libraries.apphost.common.ColorUtils.KEY_COLOR_PRIMARY_DARK;
import static com.android.car.libraries.apphost.common.ColorUtils.KEY_COLOR_SECONDARY;
import static com.android.car.libraries.apphost.common.ColorUtils.KEY_COLOR_SECONDARY_DARK;

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.Pair;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.model.CarColor;

import com.android.car.libraries.apphost.distraction.constraints.CarColorConstraints;
import com.android.car.libraries.apphost.logging.L;
import com.android.car.libraries.apphost.logging.LogTags;

/** Utilities for handling {@link CarColor} instances. */
public class CarColorUtils {
    /**
     * Resolves a standard color to a {@link ColorInt}.
     *
     * @return the resolved color or {@code defaultColor} if the input {@code carColor} is {@code
     *     null}, does not meet the constraints, or of the type {@link CarColor#DEFAULT}
     */
    @ColorInt
    public static int resolveColor(
            TemplateContext templateContext,
            @Nullable CarColor carColor,
            boolean isDark,
            @ColorInt int defaultColor,
            CarColorConstraints constraints) {
        if (carColor == null) {
            return defaultColor;
        }
        try {
            constraints.validateOrThrow(carColor);
        } catch (IllegalArgumentException e) {
            L.e(LogTags.TEMPLATE, e, "Validation failed for color %s, will use default", carColor);
            return defaultColor;
        }

        CarAppPackageInfo info = templateContext.getCarAppPackageInfo();
        CarAppColors carAppColors = info.getAppColors();
        HostResourceIds hostResourceIds = templateContext.getHostResourceIds();
        return resolveColor(
                templateContext, isDark, carColor, carAppColors, hostResourceIds, defaultColor);
    }

    /** Resolves a standard color to a {@link ColorInt}. */
    @ColorInt
    public static int resolveColor(
            Context context,
            boolean isDark,
            CarColor carColor,
            CarAppColors carAppColors,
            HostResourceIds resIds,
            @ColorInt int defaultColor) {
        int type = carColor.getType();
        Resources resources = context.getResources();
        switch (type) {
            case TYPE_DEFAULT:
                return defaultColor;
            case TYPE_PRIMARY:
                return isDark ? carAppColors.primaryDarkColor : carAppColors.primaryColor;
            case TYPE_SECONDARY:
                return isDark ? carAppColors.secondaryDarkColor : carAppColors.secondaryColor;
            case TYPE_RED:
                return resources.getColor(isDark ? resIds.getRedDarkColor() : resIds.getRedColor());
            case TYPE_GREEN:
                return resources.getColor(
                        isDark ? resIds.getGreenDarkColor() : resIds.getGreenColor());
            case TYPE_BLUE:
                return resources.getColor(
                        isDark ? resIds.getBlueDarkColor() : resIds.getBlueColor());
            case TYPE_YELLOW:
                return resources.getColor(
                        isDark ? resIds.getYellowDarkColor() : resIds.getYellowColor());
            case TYPE_CUSTOM:
                return isDark ? carColor.getColorDark() : carColor.getColor();
            default:
                L.e(LogTags.TEMPLATE, "Failed to resolve standard color id: %d", type);
                return defaultColor;
        }
    }

    /**
     * Returns the {@link CarAppColors} from the given app name if all primary and secondary colors
     * are present in the app's manifest, otherwise returns {@link CarAppColors#getDefault(Context,
     * HostResourceIds)}.
     */
    public static CarAppColors resolveAppColor(
            @NonNull Context context,
            @NonNull ComponentName appName,
            @NonNull HostResourceIds hostResourceIds) {
        String packageName = appName.getPackageName();
        CarAppColors defaultColors = CarAppColors.getDefault(context, hostResourceIds);

        int themeId = ColorUtils.loadThemeId(context, appName);
        if (themeId == 0) {
            L.w(LogTags.TEMPLATE, "Cannot get the app theme from %s", packageName);
            return defaultColors;
        }

        Context packageContext = ColorUtils.getPackageContext(context, packageName);
        if (packageContext == null) {
            L.w(LogTags.TEMPLATE, "Cannot get the app context from %s", packageName);
            return defaultColors;
        }
        packageContext.setTheme(themeId);

        Resources.Theme theme = packageContext.getTheme();
        Pair<Integer, Integer> primaryColorVariants =
                ColorUtils.getColorVariants(
                        theme,
                        packageName,
                        KEY_COLOR_PRIMARY,
                        KEY_COLOR_PRIMARY_DARK,
                        defaultColors.primaryColor,
                        defaultColors.primaryDarkColor);
        Pair<Integer, Integer> secondaryColorVariants =
                ColorUtils.getColorVariants(
                        theme,
                        packageName,
                        KEY_COLOR_SECONDARY,
                        KEY_COLOR_SECONDARY_DARK,
                        defaultColors.secondaryColor,
                        defaultColors.secondaryDarkColor);

        return new CarAppColors(
                primaryColorVariants.first,
                primaryColorVariants.second,
                secondaryColorVariants.first,
                secondaryColorVariants.second);
    }

    /**
     * Darkens the given color by a percentage of its brightness.
     *
     * @param originalColor the color to change the brightness of
     * @param percentage the percentage to decrement the brightness for, in the [0..1] range. For
     *     example, a value of 0.5 will make the color 50% less bright
     */
    @ColorInt
    public static int darkenColor(@ColorInt int originalColor, float percentage) {
        float[] hsv = new float[3];
        Color.colorToHSV(originalColor, hsv);
        hsv[2] *= 1.f - percentage;
        return Color.HSVToColor(hsv);
    }

    /**
     * Blends two colors using a SRC Porter-duff operator.
     *
     * <p>See <a href="http://ssp.impulsetrain.com/porterduff.html">Porter-Duff Compositing and
     * Blend Modes</a>
     *
     * <p>NOTE: this function ignores the alpha channel of the destination, and returns a fully
     * opaque color.
     */
    @ColorInt
    public static int blendColorsSrc(@ColorInt int source, @ColorInt int destination) {
        // Each color component is calculated like so:
        // output_color = (1 - alpha(source)) * destination + alpha_source * source
        float alpha = Color.alpha(source) / 255.f;
        return Color.argb(
                255,
                clampComponent(alpha * Color.red(source) + (1 - alpha) * Color.red(destination)),
                clampComponent(
                        alpha * Color.green(source) + (1 - alpha) * Color.green(destination)),
                clampComponent(alpha * Color.blue(source) + (1 - alpha) * Color.blue(destination)));
    }

    private static int clampComponent(float color) {
        return (int) Math.max(0, Math.min(255, color));
    }

    private CarColorUtils() {}
}
