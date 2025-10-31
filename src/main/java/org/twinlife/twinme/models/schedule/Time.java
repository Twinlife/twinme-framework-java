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
import java.util.Locale;
import java.util.Objects;

public class Time implements Comparable<Time> {
    /**
     * This Time's hour, 24-hour based, e.g. 22 for 10:00PM.
     */
    public final int hour;

    /**
     * This Time's minute.
     */
    public final int minute;

    public Time(int hour, int minute) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("Invalid hour: " + hour);
        }
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("Invalid minute: " + minute);
        }
        this.hour = hour;
        this.minute = minute;
    }

    /**
     * Create a new {@link Time} from its serialized representation
     *
     * @param time a serialized Time in the format: "hour:minute"
     * @return a valid Time instance
     * @throws IllegalArgumentException if time is not a valid serialized Time
     */
    @NonNull
    public static Time from(@NonNull String time) {
        String[] split = time.split(":");
        if (split.length != 2) {
            throw new IllegalArgumentException("Invalid time: " + time);
        }

        int hour = Integer.parseInt(split[0]);
        int minute = Integer.parseInt(split[1]);

        return new Time(hour, minute);
    }

    @NonNull
    public static Time from(@NonNull Calendar cal){
        return new Time(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /**
     * @return this Time in the format: "hour:minute"
     */
    @Override
    @NonNull
    public String toString() {
        String hh = String.format(Locale.US, "%02d", this.hour);
        String mm = String.format(Locale.US, "%02d", this.minute);
        return hh + ":" + mm;
    }

    @Override
    public int compareTo(Time other) {
        if (this.getClass() != other.getClass()) {
            return -1;
        }
        int cmp = Integer.compare(this.hour, other.hour);
        if (cmp == 0) {
            cmp = Integer.compare(this.minute, other.minute);
        }
        return cmp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Time time = (Time) o;
        return hour == time.hour && minute == time.minute;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hour, minute);
    }
}
