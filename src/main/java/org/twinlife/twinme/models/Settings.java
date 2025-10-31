/*
 *  Copyright (c) 2020-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.util.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Twinme Settings
 *
 */
public class Settings {
    @Nullable
    protected String mDescription;
    @Nullable
    protected Map<String, String> mProperties;

    public Settings() {

        mDescription = null;
        mProperties = null;
    }

    public Settings(@NonNull String description) {

        mDescription = description;
        mProperties = null;
    }

    public Settings(@NonNull Settings from) {

        mDescription = from.mDescription;
        if (from.mProperties != null) {
            mProperties = new HashMap<>(from.mProperties);
        } else {
            mProperties = null;
        }
    }

    @NonNull
    public String getDescription() {

        return mDescription != null ? mDescription : "";
    }

    public void setDescription(String description) {

        mDescription = description;
    }

    public boolean getBoolean(@NonNull String name, boolean defaultValue) {

        if (mProperties == null) {

            return defaultValue;
        }

        String value = mProperties.get(name);
        if (value == null) {

            return defaultValue;
        }

        return "1".equals(value);
    }

    public void setBoolean(@NonNull String name, boolean value) {

        if (mProperties == null) {
            mProperties = new HashMap<>();
        }

        mProperties.put(name, value ? "1" : "0");
    }

    @NonNull
    public String getString(@NonNull String name, @NonNull String defaultValue) {

        if (mProperties == null) {

            return defaultValue;
        }

        String value = mProperties.get(name);
        if (value == null) {

            return defaultValue;
        }

        return value;
    }

    public void setString(@NonNull String name, @Nullable String value) {

        if (mProperties == null) {
            if (value == null) {
                return;
            }

            mProperties = new HashMap<>();
        }

        if (value == null) {
            mProperties.remove(name);
        } else {
            mProperties.put(name, value);
        }
    }

    @Nullable
    public UUID getUUID(@NonNull String name) {

        if (mProperties == null) {

            return null;
        }

        String value = mProperties.get(name);
        if (value == null) {

            return null;
        }

        return Utils.UUIDFromString(value);
    }

    public void setUUID(@NonNull String name, @NonNull UUID value) {

        if (mProperties == null) {
            mProperties = new HashMap<>();
        }

        mProperties.put(name, value.toString());
    }

    public int getColor(@NonNull String name, int defaultValue) {

        if (mProperties == null) {

            return defaultValue;
        }

        String value = mProperties.get(name);
        if (value == null) {

            return defaultValue;
        }

        try {
            return Integer.parseInt(value, 16) | 0xFF000000;

        } catch (IllegalArgumentException exception) {
            return defaultValue;
        }
    }

    public void setColor(@NonNull String name, int value) {

        if (mProperties == null) {
            mProperties = new HashMap<>();
        }

        mProperties.put(name, String.format("%06X", (0xFFFFFF & value)));
    }

    public void remove(@NonNull String name) {

        if (mProperties != null) {
            mProperties.remove(name);
        }
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "Settings[" +
                " description=" + mDescription + "]";
    }
}
