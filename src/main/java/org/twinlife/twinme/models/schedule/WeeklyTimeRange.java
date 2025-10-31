/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.models.schedule;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

/**
 * A weekly time range defined by the user, e.g. "Monday from 14:00 to 16:30".
 * Times are timezone-neutral, timezone is defined for the whole schedule.
 */
public class WeeklyTimeRange extends TimeRange {
    protected static final String SERIALIZATION_PREFIX = "weekly";

    @NonNull
    public final List<DayOfWeek> days = new ArrayList<>();
    @NonNull
    public final Time start;
    @NonNull
    public final Time end;

    public WeeklyTimeRange(@NonNull DayOfWeek day, @NonNull Time start, @NonNull Time end) {
        this(Collections.singletonList(day), start, end);
    }

    public WeeklyTimeRange(@NonNull List<DayOfWeek> days, @NonNull Time start, @NonNull Time end) {
        if (days.isEmpty()) {
            days = Arrays.asList(DayOfWeek.ENUMS);
        }
        this.days.addAll(days);
        Collections.sort(this.days);
        this.start = start;
        this.end = end;
    }

    WeeklyTimeRange(@NonNull String timeRange) {

        String[] split = timeRange.split(",");

        if (split.length != 4) {
            throw new IllegalArgumentException("Invalid weekly time range: " + timeRange);
        }

        for (String day : split[1].split("-")) {
            this.days.add(DayOfWeek.from(Integer.parseInt(day)));
        }

        start = Time.from(split[2]);
        end = Time.from(split[3]);
    }

    @Override
    public String serialize() {
        StringBuilder sb = new StringBuilder(SERIALIZATION_PREFIX);
        sb.append(",");

        for (DayOfWeek d : days) {
            sb.append(d.getValue());
            sb.append("-");
        }
        sb.deleteCharAt(sb.length() - 1);

        sb.append(",").append(start).append(",").append(end);

        return sb.toString();
    }

    @Override
    boolean isTimestampInRange(long timestamp, TimeZone timeZone){
        Calendar cal = Calendar.getInstance(timeZone);
        cal.setTimeInMillis(timestamp);

        DayOfWeek calDay = DayOfWeek.from(cal);

        if(!days.contains(calDay)){
            return false;
        }

        Calendar startCal = (Calendar) cal.clone();
        startCal.set(Calendar.HOUR_OF_DAY, start.hour);
        startCal.set(Calendar.MINUTE, start.minute);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);

        Calendar endCal = (Calendar) cal.clone();
        endCal.set(Calendar.HOUR_OF_DAY, end.hour);
        endCal.set(Calendar.MINUTE, end.minute);
        endCal.set(Calendar.SECOND, 0);
        endCal.set(Calendar.MILLISECOND, 0);

        return cal.after(startCal) && cal.before(endCal);
    }

        @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WeeklyTimeRange timeRange = (WeeklyTimeRange) o;

        return days.equals(timeRange.days) && start.equals(timeRange.start) && end.equals(timeRange.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(days, start, end);
    }

    @Override
    public int compareTo(TimeRange o) {
        if(o instanceof DateTimeRange){
            // DateTimeRanges come before WeeklyTimeRanges
            return 1;
        }

        if (o instanceof WeeklyTimeRange) {
            WeeklyTimeRange other = (WeeklyTimeRange) o;

            int cmp = Integer.compare(this.days.get(0).getValue(), other.days.get(0).getValue());
            if (cmp == 0) {
                cmp = this.start.compareTo(other.start);
                if (cmp == 0) {
                    cmp = this.end.compareTo(other.end);
                }
            }
            return cmp;
        }

        return -1;
    }

    @Override
    @NonNull
    public String toString() {
        return "WeeklyTimeRange{" +
                "day=" + days +
                ", start=" + start +
                ", end=" + end +
                '}';
    }

    /**
     * Partial copy of {@link java.time.DayOfWeek}, since it's not available on SDK < 26
     */
    public enum DayOfWeek {
        MONDAY(Calendar.MONDAY),
        TUESDAY(Calendar.TUESDAY),
        WEDNESDAY(Calendar.WEDNESDAY),
        THURSDAY(Calendar.THURSDAY),
        FRIDAY(Calendar.FRIDAY),
        SATURDAY(Calendar.SATURDAY),
        SUNDAY(Calendar.SUNDAY);

        final int calendarValue;

        DayOfWeek(int calendarDayOfWeek){
            this.calendarValue = calendarDayOfWeek;
        }

        private static final DayOfWeek[] ENUMS = values();

        public static DayOfWeek from(int dayOfWeek) {
            if (dayOfWeek >= 1 && dayOfWeek <= 7) {
                return ENUMS[dayOfWeek - 1];
            } else {
                throw new IllegalArgumentException("Invalid value for DayOfWeek: " + dayOfWeek);
            }
        }

        public static DayOfWeek from(Calendar calendar){
            for(DayOfWeek dayOfWeek: ENUMS){
                if(dayOfWeek.calendarValue == calendar.get(Calendar.DAY_OF_WEEK)){
                    return dayOfWeek;
                }
            }
            throw new IllegalStateException("This should not happen...");
        }

        public int getValue() {
            return this.ordinal() + 1;
        }
    }
}
