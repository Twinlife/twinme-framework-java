/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.models.schedule;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.TimeZone;

public abstract class TimeRange implements Comparable<TimeRange> {

    static TimeRange from(@NonNull String timeRange) {
        if (timeRange.startsWith(WeeklyTimeRange.SERIALIZATION_PREFIX)) {
            return new WeeklyTimeRange(timeRange);
        }

        if (timeRange.startsWith(DateTimeRange.SERIALIZATION_PREFIX)) {
            return new DateTimeRange(timeRange);
        }

        throw new IllegalArgumentException("Unknown time range type: " + timeRange);
    }

    abstract String serialize();

    abstract boolean isTimestampInRange(long timestamp, TimeZone timeZone);

    @Override
    public abstract boolean equals(@Nullable Object obj);

    @Override
    public abstract int hashCode();
}
