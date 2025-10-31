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

public class Date implements Comparable<Date> {
    /**
     * This Date's year, e.g. 2023
     */
    public final int year;

    /**
     * This Date's month, e.g. 1 for January
     */
    public final int month;

    /**
     * This Date's day of the month. The first day of the month is 1.
     */
    public final int day;

    public Date(int year, int month, int day) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + month);
        }

        if (day < 1 || day > 31) {
            throw new IllegalArgumentException("Invalid day: " + day);
        }

        this.year = year;
        this.month = month;
        this.day = day;
    }

    /**
     * Create a new {@link Date} from its serialized representation.
     *
     * @param date a serialized Date in the format: "YYYY-MM-DD"
     * @return a valid Date instance
     * @throws IllegalArgumentException if date is not a valid serialized Date
     */
    @NonNull
    static Date from(@NonNull String date) {
        String[] split = date.split("-");
        if (split.length != 3) {
            throw new IllegalArgumentException("Invalid date: " + date);
        }

        int year = Integer.parseInt(split[0]);
        int month = Integer.parseInt(split[1]);
        int day = Integer.parseInt(split[2]);

        return new Date(year, month, day);
    }

    @NonNull
    public static Date from(@NonNull Calendar cal){
        return new Date(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH)+1, cal.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * @return this Date in the format: "YYYY-MM-DD"
     */
    @Override
    @NonNull
    public String toString() {
        String yy = String.format(Locale.US, "%04d", year);
        String mm = String.format(Locale.US, "%02d", month);
        String dd = String.format(Locale.US, "%02d", day);

        return yy + "-" + mm + "-" + dd;
    }

    @Override
    public int compareTo(Date other) {
        int cmp = Integer.compare(this.year, other.year);
        if (cmp == 0) {
            cmp = Integer.compare(this.month, other.month);
            if (cmp == 0) {
                cmp = Integer.compare(this.day, other.day);
            }
        }
        return cmp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Date date = (Date) o;
        return year == date.year && month == date.month && day == date.day;
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, month, day);
    }
}
