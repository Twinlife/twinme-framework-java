/*
 *  Copyright (c) 2019-2023 twinlife SA.
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
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Twinme Space
 *
 */
public class Space extends TwinmeRepositoryObject {

    public static final UUID SCHEMA_ID = UUID.fromString("71637589-5fb0-4ec0-b11a-e56accaa60a0");
    public static final int SCHEMA_VERSION = 1;

    @NonNull
    private SpaceSettings mSpaceSettings;
    @Nullable
    private UUID mProfileId;
    @Nullable
    private Profile mProfile;
    private long mPermissions;
    @Nullable
    private UUID mSpaceCardId;
    @Nullable
    private TwincodeOutbound mSpaceTwincode;

    public enum Permission {
        SHARE_SPACE_CARD,
        CREATE_CONTACT,
        MOVE_CONTACT,
        CREATE_GROUP,
        MOVE_GROUP,
        COPY_ALLOWED,
        UPDATE_IDENTITY;

        @NonNull
        public static String toString(long permissions) {

            if (permissions == -1) {
                return "all";
            }
            StringBuilder sb = new StringBuilder();
            if ((permissions & (1L << SHARE_SPACE_CARD.ordinal())) != 0) {
                sb.append("shareSpaceCard ");
            }
            if ((permissions & (1L << CREATE_CONTACT.ordinal())) != 0) {
                sb.append("createContact ");
            }
            if ((permissions & (1L << MOVE_CONTACT.ordinal())) != 0) {
                sb.append("moveContact ");
            }
            if ((permissions & (1L << CREATE_GROUP.ordinal())) != 0) {
                sb.append("createGroup ");
            }
            if ((permissions & (1L << MOVE_GROUP.ordinal())) != 0) {
                sb.append("moveGroup ");
            }
            if ((permissions & (1L << COPY_ALLOWED.ordinal())) != 0) {
                sb.append("copyAllowed ");
            }
            if ((permissions & (1L << UPDATE_IDENTITY.ordinal())) != 0) {
                sb.append("updateIdentity ");
            }
            return sb.toString();
        }

        public static long toPermission(@NonNull String value) {
            if ("all".equals(value)) {
                return -1L;
            }

            long result = 0;
            String[] permissions = value.split(" ");
            for (String permission : permissions) {
                if ("shareSpaceCard".equals(permission)) {
                    result |= (1L << SHARE_SPACE_CARD.ordinal());
                } else if ("createContact".equals(permission)) {
                    result |= (1L << CREATE_CONTACT.ordinal());
                } else if ("moveContact".equals(permission)) {
                    result |= (1L << MOVE_CONTACT.ordinal());
                } else if ("createGroup".equals(permission)) {
                    result |= (1L << CREATE_GROUP.ordinal());
                } else if ("moveGroup".equals(permission)) {
                    result |= (1L << MOVE_GROUP.ordinal());
                } else if ("copyAllowed".equals(permission)) {
                    result |= (1L << COPY_ALLOWED.ordinal());
                } else if ("updateIdentity".equals(permission)) {
                    result |= (1L << UPDATE_IDENTITY.ordinal());
                }
            }
            return result;
        }
    }

    Space(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
          long creationDate, @Nullable String name, @Nullable String description,
          @Nullable List<BaseService.AttributeNameValue> attributes,
          long modificationDate) {
        super(identifier, uuid, creationDate, modificationDate);

        mProfileId = null;
        mSpaceCardId = null;
        mPermissions = -1L; // permissions are retrieved from the spaceTwincode.
        update(name, description, attributes, modificationDate);
    }

    void update(@Nullable String name, @Nullable String description, @Nullable List<AttributeNameValue> attributes,
                long modificationDate) {

        // mName = name;
        mDescription = description;
        mModificationDate = modificationDate;
        if (attributes != null) {
            for (AttributeNameValue attribute : attributes) {
                switch (attribute.name) {

                    case "spaceCardId":
                        if (attribute instanceof BaseService.AttributeNameStringValue) {
                            mSpaceCardId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                        }
                        break;

                    case "profileId":
                        if (attribute instanceof BaseService.AttributeNameStringValue) {
                            mProfileId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                        }
                        break;

                }
            }
        }
    }

    //
    // Implement RepositoryObject interface
    //

    @Override
    @NonNull
    public List<AttributeNameValue> getAttributes(boolean exportAll) {

        UUID profileId;
        UUID spaceCardId;
        SpaceSettings settings;
        TwincodeOutbound spaceTwincode;

        synchronized (this) {
            settings = mSpaceSettings;
            spaceTwincode = mSpaceTwincode;
            profileId = mProfileId;
            spaceCardId = mSpaceCardId;
        }

        final List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
        if (exportAll) {
            if (settings.getId() != null) {
                attributes.add(new BaseService.AttributeNameStringValue("settingsId", settings.getId().toString()));
            }
            if (spaceTwincode != null) {
                attributes.add(new BaseService.AttributeNameStringValue("spaceTwincodeId", spaceTwincode.getId().toString()));
            }
        }
        if (spaceCardId != null) {
            attributes.add(new BaseService.AttributeNameStringValue("spaceCardId", spaceCardId.toString()));
        }
        if (profileId != null) {
            attributes.add(new BaseService.AttributeNameStringValue("profileId", profileId.toString()));
        }
        return attributes;
    }

    @Override
    public boolean isValid() {

        // The space is always valid.
        return true;
    }

    @Override
    public boolean canCreateP2P() {

        return false;
    }

    @Override
    @Nullable
    public RepositoryObject getOwner() {

        // The space owner is the SpaceSettings instance!
        return mSpaceSettings;
    }

    @Override
    public void setOwner(@Nullable RepositoryObject owner) {

        // Called when an object is loaded from the database and linked to its owner.
        if (owner instanceof SpaceSettings) {
            mSpaceSettings = (SpaceSettings) owner;
        }
    }

    @Override
    @NonNull
    public synchronized String getName() {

        return mSpaceSettings.getName();
    }

    @NonNull
    @Override
    public String getDescription() {

        return mSpaceTwincode != null ? mSpaceTwincode.getDescription() : mSpaceSettings.getDescription();
    }

    @Override
    public synchronized void setPeerTwincodeOutbound(@Nullable TwincodeOutbound twincodeOutbound) {

        mSpaceTwincode = twincodeOutbound;
        if (twincodeOutbound != null) {
            mSpaceSettings.setName(mName);

            String value = TwinmeAttributes.getPermissions(twincodeOutbound);
            if (value != null) {
                mPermissions = Permission.toPermission(value);
            }
        } else {
            mDescription = null;
        }
    }

    @NonNull
    public synchronized SpaceSettings getSpaceSettings() {

        return new SpaceSettings(mSpaceSettings);
    }

    @Nullable
    public synchronized UUID getSpaceSettingsId() {

        return mSpaceSettings.getId();
    }

    public synchronized boolean isSecret() {

        return mSpaceSettings.isSecret();
    }

    public synchronized boolean messageCopyAllowed() {

        return hasPermission(Permission.COPY_ALLOWED) && mSpaceSettings.messageCopyAllowed();
    }

    public synchronized boolean fileCopyAllowed() {

        return hasPermission(Permission.COPY_ALLOWED) && mSpaceSettings.fileCopyAllowed();
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

    public synchronized String getStyle() {

        return mSpaceSettings.getStyle();
    }

    @Nullable
    public ImageId getAvatarId() {

        return mSpaceTwincode != null ? mSpaceTwincode.getAvatarId() : null;
    }

    @Nullable
    public UUID getSpaceAvatarId() {

        return mSpaceSettings.getAvatarId();
    }

    public boolean hasSpaceAvatar() {

        return mSpaceSettings.getAvatarId() != null || (mSpaceTwincode != null && mSpaceTwincode.getAvatarId() != null);
    }

    @Nullable
    public UUID getSpaceTwincodeId() {

        return mSpaceTwincode != null ? mSpaceTwincode.getId() : null;
    }

    @Nullable
    public UUID getProfileId() {

        return mProfileId;
    }

    @Nullable
    public Profile getProfile() {

        return mProfile;
    }

    public synchronized void setProfile(@Nullable Profile profile) {

        mProfile = profile;
        if (profile != null) {
            mProfileId = profile.getId();
            mProfile.setSpace(this);
        } else {
            mProfileId = null;
        }
    }

    public synchronized void setSpaceSettings(@NonNull SpaceSettings settings) {

        // Deep copy of the settings.
        if (mSpaceSettings != null) {
            mSpaceSettings = new SpaceSettings(mSpaceSettings, settings);
        } else {
            mSpaceSettings = new SpaceSettings(settings);
        }
    }

    public boolean isOwner(Originator originator) {

        Space space = originator.getSpace();
        return this == space;
    }

    public boolean isManagedSpace() {

        return mSpaceTwincode != null;
    }

    public boolean isSameSpace(@NonNull UUID spaceTwincodeId) {

        return mSpaceTwincode != null && mSpaceTwincode.getId().equals(spaceTwincodeId);
    }

    public boolean hasPermission(Permission permission) {

        return (mPermissions & (1L << permission.ordinal())) != 0;
    }

    @Nullable
    public synchronized UUID getSpaceCardId() {

        if (hasPermission(Permission.SHARE_SPACE_CARD)) {
            return mSpaceCardId;
        } else {
            return null;
        }
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "Space[" + mDatabaseId +
                " id=" + mId +
                " settings=" + mSpaceSettings +
                " profileId=" + mProfileId +
                " permissions=" + Permission.toString(mPermissions) +
                " spaceCardId=" + mSpaceCardId +
                " spaceTwincode=" + mSpaceTwincode + "]";
    }
}
