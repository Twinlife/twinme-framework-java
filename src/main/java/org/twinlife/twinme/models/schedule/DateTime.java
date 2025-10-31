/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.models.schedule;

import android.content.Context;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

public class DateTime implements Comparable<DateTime> {
    @NonNull
    public final Date date;
    @NonNull
    public final Time time;

    public DateTime(int year, int month, int day, int hour, int minute) {
        this(new Date(year, month, day), new Time(hour, minute));
    }

    public DateTime(@NonNull Date date, @NonNull Time time) {
        this.date = date;
        this.time = time;
    }

    static DateTime from(@NonNull String dateTime) {
        String[] split = dateTime.split("T");

        if (split.length != 2) {
            throw new IllegalArgumentException("Invalid DateTime");
        }

        Date d = Date.from(split[0]);
        Time t = Time.from(split[1]);

        return new DateTime(d, t);
    }

    public static DateTime from(@NonNull Calendar cal) {
        return new DateTime(Date.from(cal), Time.from(cal));
    }

    public Calendar toCalendar(TimeZone timeZone) {
        Calendar cal = Calendar.getInstance(timeZone);
        cal.set(Calendar.YEAR, date.year);
        cal.set(Calendar.MONTH, date.month - 1);
        cal.set(Calendar.DAY_OF_MONTH, date.day);
        cal.set(Calendar.HOUR_OF_DAY, time.hour);
        cal.set(Calendar.MINUTE, time.minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        return cal;
    }

    /**
     * @return this DateTime in the format: "YYYY-MM-DD'T'HH:mm"
     */
    @Override
    @NonNull
    public String toString() {
        return date + "T" + time;
    }

    @NonNull
    public String formatDate() {

        Calendar calendar = toCalendar(TimeZone.getDefault());
        String formatDate = "dd MMM yyyy";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(formatDate, Locale.getDefault());
        return simpleDateFormat.format(calendar.getTime());
    }

    @NonNull
    public String formatTime(Context context) {

        Calendar calendar = toCalendar(TimeZone.getDefault());
        String formatTime;
        if (DateFormat.is24HourFormat(context)) {
            formatTime = "kk:mm";
        } else {
            formatTime = "hh:mm a";
        }

        SimpleDateFormat simpleTimeFormat = new SimpleDateFormat(formatTime, Locale.getDefault());
        return simpleTimeFormat.format(calendar.getTime());
    }

    @NonNull
    public String formatDateTime(Context context) {

        Calendar calendar = toCalendar(TimeZone.getDefault());
        String formatDate = "dd MMM yyyy";

        String formatTime;
        if (DateFormat.is24HourFormat(context)) {
            formatTime = "kk:mm";
        } else {
            formatTime = "hh:mm a";
        }

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(formatDate, Locale.getDefault());
        SimpleDateFormat simpleTimeFormat = new SimpleDateFormat(formatTime, Locale.getDefault());

        return simpleDateFormat.format(calendar.getTime()) + " " + simpleTimeFormat.format(calendar.getTime());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DateTime dateTime = (DateTime) o;
        return date.equals(dateTime.date) && time.equals(dateTime.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, time);
    }

    @Override
    public int compareTo(DateTime o) {
        int cmp = date.compareTo(o.date);
        if (cmp == 0) {
            cmp = time.compareTo(o.time);
        }
        return cmp;
    }
}
