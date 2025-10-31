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

import org.twinlife.twinlife.RepositoryService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;

//
// version: 1.0
//

/**
 * Representation of a schedule defined by the user. It can be applied to a contact, group, call receiver
 * or a whole profile to filter calls outside of the schedule.
 * It is made up of one or more {@link TimeRange}s and a timezone.
 */

public class Schedule {


    public static final UUID SCHEMA_ID = UUID.fromString("9e5cd508-6bbf-43b4-b49e-36dae0ecc98c");
    private static final int SCHEMA_VERSION = 1;

    private static final UUID NO_ID = new UUID(0L, 0L);

    private static final String CAP_NAME_ID = "id";
    private static final String CAP_NAME_ENABLED = "en";
    private static final String CAP_NAME_TIMEZONE = "tz";
    private static final String CAP_NAME_TIME_RANGE = "tr";

    @NonNull
    private final UUID mId;
    @NonNull
    private final UUID mSchemaId;
    private final int mSchemaVersion;
    private final long mCreationDate;
    @NonNull
    private final String mSerializer;
    @Nullable
    private String mName;

    private boolean mEnabled;

    private boolean mPrivate;

    @NonNull
    private final List<TimeRange> mTimeRanges = new ArrayList<>();

    @NonNull
    private TimeZone mTimeZone;

    public Schedule(@NonNull TimeZone timeZone, @NonNull TimeRange timeRange){
        this(false, timeZone, Collections.singletonList(timeRange));
    }

    public Schedule(boolean isPrivate, @NonNull TimeZone timeZone, @NonNull List<TimeRange> timeRanges){
        this(null, null, isPrivate, timeZone, timeRanges);
    }

    public Schedule(@Nullable UUID id, @Nullable String name, boolean isPrivate, @NonNull TimeZone timeZone, @NonNull List<TimeRange> timeRanges) {

        mId = id != null ? id : NO_ID;
        mSchemaId = SCHEMA_ID;
        mSchemaVersion = SCHEMA_VERSION;
        mSerializer = RepositoryService.XML_SERIALIZER;
        mCreationDate = System.currentTimeMillis();
        mName = name;
        mEnabled = true;
        mPrivate = isPrivate;
        setTimeRanges(timeRanges);
        mTimeZone = timeZone;
    }

    /**
     * Checks whether the current time is allowed by this Schedule.
     * All times are converted to this Schedule's timezone before checking.
     *
     * @return true if one of the schedule's time ranges is valid for the timestamp, or if the schedule is disabled.
     */
    public boolean isNowInRange(){
        return isTimestampInRange(System.currentTimeMillis());
    }

    /**
     * Checks whether the timestamp is allowed by this Schedule.
     * All times are converted to this Schedule's timezone before checking.
     *
     * @param timestamp The epoch timestamp to check
     *
     * @return true if one of the schedule's time ranges is valid for the timestamp.
     */
    public boolean isTimestampInRange(long timestamp){
        if(!mEnabled){
            return true;
        }

        for(TimeRange timeRange: mTimeRanges){
            if(timeRange.isTimestampInRange(timestamp, mTimeZone)){
                return true;
            }
        }
        return false;
    }

    private static final String CAP_SEPARATOR = ";";

    /**
     * Serializes this Schedule to its capability form.
     * @return the capability value of this Schedule
     */
    @NonNull
    public String toCapability() {
        final UUID id;
        final boolean enabled;
        final TimeZone timeZone;
        final List<TimeRange> timeRanges;

        synchronized (this) {
            id = mId;
            enabled = mEnabled;
            timeZone = mTimeZone;
            timeRanges = new ArrayList<>(mTimeRanges);
        }

        StringBuilder builder = new StringBuilder();
        if (!id.equals(NO_ID)) {
            builder.append(CAP_NAME_ID).append("=").append(id);
            builder.append(CAP_SEPARATOR);
        }

        builder.append(CAP_NAME_ENABLED).append("=").append(enabled ? "1":"0");
        builder.append(CAP_SEPARATOR);

        builder.append(CAP_NAME_TIMEZONE).append("=").append(timeZone.getID());

        for (TimeRange timeRange : timeRanges) {
            builder.append(CAP_SEPARATOR);
            builder.append(CAP_NAME_TIME_RANGE).append("=").append(timeRange.serialize());
        }

        return builder.toString();
    }

    /**
     * Deserializes a schedule capability
     * @param capability the capability value to deserialize
     * @return a new Schedule containing the capability's data
     */
    @Nullable
    public static Schedule ofCapability(@NonNull String capability) {
        UUID id = null;
        boolean enabled = true;
        TimeZone timeZone = null;
        List<TimeRange> timeRanges = new ArrayList<>();

        for (String attr : capability.split(CAP_SEPARATOR)) {
            if (attr.startsWith(CAP_NAME_ID)) {
                id = UUID.fromString(getRawCapValue(attr));
            }

            if(attr.startsWith(CAP_NAME_ENABLED)){
                String enabledStr = getRawCapValue(attr);
                enabled = enabledStr.equals("1");
            }

            if (attr.startsWith(CAP_NAME_TIMEZONE)) {
                String tz = getRawCapValue(attr);
                timeZone = TimeZone.getTimeZone(tz);
            }

            if (attr.startsWith(CAP_NAME_TIME_RANGE)) {
                timeRanges.add(TimeRange.from(getRawCapValue(attr)));
            }
        }

        if (timeZone != null && !timeRanges.isEmpty()) {
            Schedule schedule = new Schedule(id, null, false, timeZone, timeRanges);
            schedule.setEnabled(enabled);
            return schedule;
        }
        return null;
    }

    @NonNull
    public UUID getId() {

        return mId;
    }

    @NonNull
    public UUID getSchemaId() {

        return mSchemaId;
    }

    public int getSchemaVersion() {

        return mSchemaVersion;
    }

    @NonNull
    public String getSerializer() {

        return mSerializer;
    }

    @Nullable
    public synchronized String getName() {

        return mName;
    }

    public synchronized void setName(@NonNull String name) {

        mName = name;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(boolean enabled) {
        this.mEnabled = enabled;
    }

    public synchronized boolean isPrivate() {
        return mPrivate;
    }

    public synchronized void setPrivate(boolean isPrivate) {
        mPrivate = isPrivate;
    }

    public synchronized TimeZone getTimeZone() {

        return mTimeZone;
    }

    public synchronized void setTimeZone(@NonNull TimeZone timeZone) {
        mTimeZone = timeZone;
    }

    public synchronized List<TimeRange> getTimeRanges() {
        return new ArrayList<>(mTimeRanges);
    }

    public synchronized void addTimeRange(@NonNull TimeRange timeRange) {
        mTimeRanges.add(timeRange);
        Collections.sort(mTimeRanges);
    }

    public synchronized void setTimeRanges(@NonNull List<TimeRange> timeRanges) {
        mTimeRanges.clear();
        mTimeRanges.addAll(timeRanges);
        Collections.sort(mTimeRanges);
    }

    //
    // Override Object methods
    //


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schedule schedule = (Schedule) o;

        if(mEnabled != schedule.mEnabled || !Objects.equals(mTimeZone, schedule.mTimeZone) || mTimeRanges.size() != schedule.mTimeRanges.size()){
            return false;
        }

        for(int i = 0; i< mTimeRanges.size(); i++){
            if(!mTimeRanges.get(i).equals(schedule.mTimeRanges.get(i))){
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEnabled, mTimeRanges, mTimeZone);
    }

    @Override
    @NonNull
    public String toString() {
        return "Schedule{" +
                "mId=" + mId +
                ", mCreationDate=" + mCreationDate +
                ", mName='" + mName + '\'' +
                ", mTimeZone=" + mTimeZone.getID() +
                ", mTimeRanges=" + mTimeRanges +
                '}';
    }

    @NonNull
    private static String getRawCapValue(@NonNull String line) {
        final String[] split = line.split("=");

        if (split.length != 2) {
            throw new IllegalArgumentException("Invalid schedule capability: " + line);
        }

        return split[1];
    }
}
