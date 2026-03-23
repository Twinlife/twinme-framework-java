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

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.util.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Twinme Base Settings (readonly access to a list of properties).
 *
 * This class provides only readonly access to the properties to make sure we don't change a property on an
 * object by mistake, but we use specific method that also takes care of saving the object in the database.
 */
public class BaseSettings {
    @Nullable
    protected String mDescription;
    @Nullable
    protected Map<String, String> mProperties;

    public BaseSettings() {

        mDescription = null;
        mProperties = null;
    }

    public BaseSettings(@NonNull String description) {

        mDescription = description;
        mProperties = null;
    }

    public BaseSettings(@NonNull BaseSettings from) {

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

    public long getLong(@NonNull String name, long defaultValue) {

        if (mProperties == null) {

            return defaultValue;
        }

        String value = mProperties.get(name);
        if (value == null) {

            return defaultValue;
        }

        try {
            return Long.parseLong(value);

        } catch (IllegalArgumentException exception) {
            return defaultValue;
        }
    }

    /**
     * Export the list of properties to a data structure that can be serialized either
     * in XML or in binary form.
     *
     * @return null if there is no property or an attribute representing the properties to export/save.
     */
    @Nullable
    public BaseService.AttributeNameListValue export() {

        if (mProperties == null || mProperties.isEmpty()) {
            return null;
        }

        final List<BaseService.AttributeNameValue> props = new ArrayList<>();
        for (Map.Entry<String, String> entry : mProperties.entrySet()) {
            props.add(new BaseService.AttributeNameStringValue(entry.getKey(), entry.getValue()));
        }

        return new BaseService.AttributeNameListValue("properties", props);
    }

    protected void updateProperties(@NonNull BaseService.AttributeNameListValue list) {
        //noinspection unchecked
        final List<BaseService.AttributeNameValue> values = (List<BaseService.AttributeNameValue>)list.value;
        final Map<String, String> properties = new HashMap<>();

        for (BaseService.AttributeNameValue value : values) {
           properties.put(value.name, (String) value.value);
        }
        mProperties = properties;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "BaseSettings[" +
                " description=" + mDescription + "]";
    }
}
