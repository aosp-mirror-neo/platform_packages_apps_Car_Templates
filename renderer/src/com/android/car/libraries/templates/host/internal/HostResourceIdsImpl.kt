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

import com.android.car.libraries.apphost.common.HostResourceIds
import com.android.car.libraries.templates.host.R

object HostResourceIdsImpl : HostResourceIds {
    override fun getRedColor() = R.color.template_standard_red
    override fun getRedDarkColor() = R.color.template_standard_red_dark
    override fun getGreenColor() = R.color.template_standard_green
    override fun getGreenDarkColor() = R.color.template_standard_green_dark
    override fun getBlueColor() = R.color.template_standard_blue
    override fun getBlueDarkColor() = R.color.template_standard_blue_dark
    override fun getYellowColor() = R.color.template_standard_yellow
    override fun getYellowDarkColor() = R.color.template_standard_yellow_dark

    override fun getDefaultPrimaryColor() = R.color.default_primary_color
    override fun getDefaultPrimaryDarkColor() = R.color.default_primary_dark_color
    override fun getDefaultSecondaryColor() = R.color.default_secondary_color
    override fun getDefaultSecondaryDarkColor() = R.color.default_secondary_dark_color

    override fun getDistanceInFeetStringFormat() = R.string.feet_text
    override fun getDistanceInMilesStringFormat() = R.string.mile_text
    override fun getDistanceInYardsStringFormat() = R.string.yard_text
    override fun getDistanceInMetersStringFormat() = R.string.meter_text
    override fun getDistanceInKilometersStringFormat() = R.string.kilometer_text

    override fun getDurationInDaysStringFormat() = R.string.duration_in_days
    override fun getDurationInDaysAndHoursStringFormat() = R.string.duration_in_days_hours
    override fun getDurationInHoursStringFormat() = R.string.duration_in_hours
    override fun getDurationInHoursAndMinutesStringFormat() = R.string.duration_in_hours_minutes
    override fun getDurationInMinutesStringFormat() = R.string.duration_in_minutes

    override fun getAnrMessage() = R.string.anr_message
    override fun getAnrWaiting() = R.string.anr_waiting
    override fun getAnrWait() = R.string.anr_wait
    override fun getClientVersionIncompatibleText() = R.string.client_version_incompatible_text
    override fun getClientErrorText() = R.string.client_error_text
    override fun getMissingPermissionText() = R.string.missing_permission_text
    override fun getExitText() = R.string.exit_text

    override fun getTimeAtDestinationWithTimeZoneStringFormat() =
        R.string.time_at_destination_with_time_zone

    override fun getAlertIconDrawable() = R.drawable.alert_icon
    override fun getErrorIconDrawable() = R.drawable.error_icon
    override fun getBackIconDrawable() = R.drawable.back_icon
}
