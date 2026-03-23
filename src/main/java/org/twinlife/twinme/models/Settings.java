/*
 *  Copyright (c) 2020-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.UUID;

/**
 * Twinme Settings
 *
 */
public class Settings extends BaseSettings {

    public Settings() {
    }

    public Settings(@NonNull String description) {
        super(description);
    }

    public Settings(@NonNull Settings from) {
        super(from);
    }

    public void setDescription(String description) {

        mDescription = description;
    }

    public void setBoolean(@NonNull String name, boolean value) {

        if (mProperties == null) {
            mProperties = new HashMap<>();
        }

        mProperties.put(name, value ? "1" : "0");
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

    public void setUUID(@NonNull String name, @NonNull UUID value) {

        if (mProperties == null) {
            mProperties = new HashMap<>();
        }

        mProperties.put(name, value.toString());
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
