/*
 *  Copyright (c) 2019-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinme.TwinmeContextImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Twinme Space Settings
 *
 */
public class SpaceSettings extends Settings implements RepositoryObject {

    public static final UUID SCHEMA_ID = UUID.fromString("3ec683a9-1856-420a-a849-d47c48dd9111");
    static final int SCHEMA_VERSION = 3;
    private static SpaceSettings sDefaultSpaceSettings;

    @Nullable
    private final DatabaseIdentifier mDatabaseId;
    private final long mCreationDate;
    @Nullable
    private final UUID mId;
    private long mModificationDate;
    @NonNull
    private String mName;
    private boolean mIsSecret;
    private boolean mMessageCopyAllowed;
    private boolean mFileCopyAllowed;
    @Nullable
    private String mStyle;
    @Nullable
    private UUID mAvatarId;

    public static void setDefaultSpaceSettings(@NonNull SpaceSettings defaultSpaceSettings) {

        sDefaultSpaceSettings = defaultSpaceSettings;
    }

    public SpaceSettings(@NonNull String name) {

        mDatabaseId = null;
        mCreationDate = 0;
        mId = null;
        mName = name;
        mIsSecret = false;
        mMessageCopyAllowed = true;
        mFileCopyAllowed = true;
        mStyle = null;
    }

    public SpaceSettings(@NonNull SpaceSettings from) {
        super(from);

        mDatabaseId = from.mDatabaseId;
        mCreationDate = from.mCreationDate;
        mModificationDate = from.mModificationDate;
        mId = from.mId;
        mName = from.mName;
        mIsSecret = from.mIsSecret;
        mAvatarId = from.mAvatarId;
        mMessageCopyAllowed = from.mMessageCopyAllowed;
        mFileCopyAllowed = from.mFileCopyAllowed;
        mStyle = from.mStyle;
    }
    public void copy(@NonNull SpaceSettings from) {


        mName = from.mName;
        mIsSecret = from.mIsSecret;
        mStyle = from.mStyle;
        mFileCopyAllowed = from.mFileCopyAllowed;
        mMessageCopyAllowed = from.mMessageCopyAllowed;
        mAvatarId = from.mAvatarId;
        
        if (from.mProperties != null) {
            mProperties = new HashMap<>(from.mProperties);
        }
    }

    public SpaceSettings(@NonNull SpaceSettings target, @NonNull SpaceSettings from) {
        super(from);

        mId = target.mId;
        mCreationDate = target.mCreationDate;
        mDatabaseId = target.mDatabaseId;
        mName = from.mName;
        mIsSecret = from.mIsSecret;
        mAvatarId = from.mAvatarId;
        mMessageCopyAllowed = from.mMessageCopyAllowed;
        mFileCopyAllowed = from.mFileCopyAllowed;
        mStyle = from.mStyle;
    }

    SpaceSettings(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                  long creationDate, @Nullable String name, @Nullable String description,
                  @Nullable List<AttributeNameValue> attributes,
                  long modificationDate) {

        mDatabaseId = identifier;
        mId = uuid;
        mCreationDate = creationDate;
        update(name, description, attributes, modificationDate);
    }

    void update(@Nullable String name, @Nullable String description, @Nullable List<AttributeNameValue> attributes,
                long modificationDate) {

        mName = name == null ? "" : name;
        mModificationDate = modificationDate;
        setDescription(description);
        if (attributes != null) {
            for (BaseService.AttributeNameValue attribute : attributes) {
                switch (attribute.name) {
                    case "isSecret":
                        if (attribute instanceof BaseService.AttributeNameBooleanValue) {
                            mIsSecret = (Boolean) ((BaseService.AttributeNameBooleanValue) attribute).value;
                        }
                        break;

                    case "messageCopyAllowed":
                        if (attribute instanceof BaseService.AttributeNameBooleanValue) {
                            mMessageCopyAllowed = (Boolean) ((BaseService.AttributeNameBooleanValue) attribute).value;
                        }
                        break;

                    case "fileCopyAllowed":
                        if (attribute instanceof BaseService.AttributeNameBooleanValue) {
                            mFileCopyAllowed = (Boolean) ((BaseService.AttributeNameBooleanValue) attribute).value;
                        }
                        break;

                    case "style":
                        if (attribute instanceof BaseService.AttributeNameStringValue) {
                            mStyle = (String) ((BaseService.AttributeNameStringValue) attribute).value;
                        }
                        break;

                    case "avatarId":
                        if (attribute instanceof BaseService.AttributeNameUUIDValue) {
                            mAvatarId = (UUID) ((BaseService.AttributeNameUUIDValue) attribute).value;
                        }
                        break;

                    case "properties":
                        if (attribute instanceof BaseService.AttributeNameListValue) {
                            mProperties = new HashMap<>();
                            List<?> props = (List<?>) ((BaseService.AttributeNameListValue) attribute).value;
                            for (java.lang.Object value : props) {
                                if (value instanceof BaseService.AttributeNameStringValue) {
                                    BaseService.AttributeNameStringValue prop = (BaseService.AttributeNameStringValue)value;
                                    mProperties.put(prop.name, (String)prop.value);
                                }
                            }
                        }
                        break;
                }
            }
        }
    }

    //
    // Implement RepositoryObject interface
    //

    @NonNull
    public List<AttributeNameValue> getAttributes(boolean exportAll) {

        String style, name, description;
        boolean messageCopyAllowed;
        boolean fileCopyAllowed;
        boolean isSecret;
        UUID avatarId;

        synchronized (this) {
            style = mStyle;
            name = mName;
            description = mDescription;
            messageCopyAllowed = mMessageCopyAllowed;
            fileCopyAllowed = mFileCopyAllowed;
            isSecret = mIsSecret;
            avatarId = mAvatarId;
        }

        final List<AttributeNameValue> attributes = new ArrayList<>();

        if (exportAll) {
            if (name != null) {
                attributes.add(new BaseService.AttributeNameStringValue("name", name));
            }
            if (description != null) {
                attributes.add(new BaseService.AttributeNameStringValue("description", description));
            }
        }
        attributes.add(new BaseService.AttributeNameBooleanValue("messageCopyAllowed", messageCopyAllowed));
        attributes.add(new BaseService.AttributeNameBooleanValue("fileCopyAllowed", fileCopyAllowed));
        attributes.add(new BaseService.AttributeNameBooleanValue("isSecret", isSecret));

        if (style != null) {
            attributes.add(new BaseService.AttributeNameStringValue("style", style));
        }
        if (avatarId != null) {
            attributes.add(new BaseService.AttributeNameUUIDValue("avatarId", avatarId));
        }

        if (mProperties != null) {
            List<BaseService.AttributeNameValue> props = new ArrayList<>();

            for (Map.Entry<String, String> entry : mProperties.entrySet()) {
                props.add(new BaseService.AttributeNameStringValue(entry.getKey(), entry.getValue()));
            }

            attributes.add(new BaseService.AttributeNameListValue("properties", props));
        }

        return attributes;
    }

    @Override
    public boolean isValid() {

        // The space settings is always valid.
        return true;
    }

    @Override
    public boolean canCreateP2P() {

        return false;
    }

    @Override
    public long getModificationDate() {

        return mModificationDate;
    }

    @Override
    @NonNull
    public DatabaseIdentifier getDatabaseId() {

        return mDatabaseId;
    }

    @NonNull
    public UUID getId() {

        return mId;
    }

    @NonNull
    public String getName() {

        return mName;
    }

    public void setName(String name) {

        mName = name;
    }

    @Nullable
    public UUID getAvatarId() {

        return mAvatarId;
    }

    public void setAvatarId(UUID avatarId) {

        mAvatarId = avatarId;
    }

    public boolean isSecret() {

        return mIsSecret;
    }

    public void setSecret(boolean value) {

        mIsSecret = value;
    }

    public boolean messageCopyAllowed() {

        return mMessageCopyAllowed;
    }

    public void setMessageCopyAllowed(boolean value) {

        mMessageCopyAllowed = value;
    }

    public boolean fileCopyAllowed() {

        return mFileCopyAllowed;
    }

    public void setFileCopyAllowed(boolean value) {

        mFileCopyAllowed = value;
    }

    public boolean imageCopyAllowed() {

        return fileCopyAllowed();
    }

    public boolean audioCopyAllowed() {

        return fileCopyAllowed();
    }

    public boolean videoCopyAllowed() {

        return fileCopyAllowed();
    }

    public String getStyle() {

        return mStyle;
    }

    public void setStyle(String style) {

        if ((mStyle == null && style != null) || (mStyle != null && !mStyle.equals(style))) {
            mStyle = style;
        }
    }

    /**
     * Internal method to check if the Space name must be changed to use a new application default (ie, "Default" -> "General").
     *
     * @param twinmeContext the twinme context.
     * @return true if the space name was changed.
     */
    public boolean fixSpaceSettings(@NonNull TwinmeContextImpl twinmeContext) {

        if (isSecret()) {
            return false;
        }

        if (mName.isEmpty() || mName.equals(twinmeContext.getOldDefaultSpaceLabel())) {
            SpaceSettings settings = twinmeContext.getDefaultSpaceSettings();

            mName = settings.getName();
            return true;
        }

        return false;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "SpaceSettings[" + mDatabaseId +
                (BuildConfig.ENABLE_DUMP ? " name=" + mName : "") +
                " style=" + mStyle +
                " isSecret=" + mIsSecret + "]";
    }
}
