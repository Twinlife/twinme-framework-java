/*
 *  Copyright (c) 2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration of the Twinroom engine to describe the engine capabilities:
 *
 * - the max number of room members,
 * - the max number of members for an audio or video call,
 * - the max size used on filesystem,
 *
 * To disable audio or video, the max number of audio or video members must be set to 0.
 */
public class RoomStat {

    @NonNull
    private final UUID mEngineId;
    @NonNull
    private final Map<String, Object> mStats;

    public RoomStat(@NonNull final UUID engineId) {

        this.mEngineId = engineId;
        this.mStats = new HashMap<>();
    }

    @NonNull
    public UUID getEngineId() {

        return mEngineId;
    }

    @NonNull
    public Map<String, Object> getStats() {

        return mStats;
    }

    public Long getLongValue(@NonNull String name) {

        Object value = mStats.get(name);
        if (!(value instanceof Long)) {
            return null;
        }

        return (Long) value;
    }

    public String getStringValue(@NonNull String name) {

        Object value = mStats.get(name);
        if (!(value instanceof String)) {
            return null;
        }

        return (String) value;
    }

    public void putValue(@NonNull String name, long value) {

        mStats.put(name, value);
    }

    public void putValue(@NonNull String name, @NonNull String value) {

        mStats.put(name, value);
    }
}