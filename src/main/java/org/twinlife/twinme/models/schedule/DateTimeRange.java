/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.models.schedule;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;

/**
 * A specific time range defined by the user, e.g. "From 14:00 to 16:30 on October 17, 2023".
 * It may span multiple days.
 * Times are timezone-neutral, timezone is defined for the whole schedule.
 */
public class DateTimeRange extends TimeRange {
    protected static final String SERIALIZATION_PREFIX = "dateTime";

    @NonNull
    public final DateTime start;

    @NonNull
    public final DateTime end;

    public DateTimeRange(@NonNull DateTime start, @NonNull DateTime end) {
        if (start.compareTo(end) <= 0) {
            this.start = start;
            this.end = end;
        } else {
            this.start = end;
            this.end = start;
        }
    }

    DateTimeRange(@NonNull String timeRange) {
        String[] split = timeRange.split(",");

        if (split.length != 3) {
            throw new IllegalArgumentException("Invalid date-time range: " + timeRange);
        }

        DateTime date1 = DateTime.from(split[1]);
        DateTime date2 = DateTime.from(split[2]);

        if (date1.compareTo(date2) <= 0) {
            this.start = date1;
            this.end = date2;
        } else {
            this.start = date2;
            this.end = date1;
        }
    }

    @Override
    public String serialize() {
        return SERIALIZATION_PREFIX + "," +
                start + "," +
                end;
    }

    @Override
    boolean isTimestampInRange(long timestamp, TimeZone timeZone) {
        Calendar cal = Calendar.getInstance(timeZone);
        cal.setTimeInMillis(timestamp);

        Calendar startCal = start.toCalendar(timeZone);
        Calendar endCal = end.toCalendar(timeZone);

        return cal.after(startCal) && cal.before(endCal);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DateTimeRange timeRange = (DateTimeRange) o;

        return start.equals(timeRange.start) && end.equals(timeRange.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    @Override
    public int compareTo(TimeRange timeRange) {

        if (timeRange instanceof WeeklyTimeRange) {
            // DateTimeRanges come before WeeklyTimeRanges
            return -1;
        }

        if (timeRange instanceof DateTimeRange) {
            DateTimeRange o = (DateTimeRange) timeRange;

            int cmp = this.start.compareTo(o.start);

            if (cmp == 0) {
                cmp = this.end.compareTo(o.end);
            }

            return cmp;
        }

        return -1;
    }

    @Override
    @NonNull
    public String toString() {
        return "DateTimeRange{" +
                "start=" + start +
                ", end=" + end +
                '}';
    }
}
