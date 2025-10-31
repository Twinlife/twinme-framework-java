/*
 *  Copyright (c) 2014-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.util;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.AttributeNameStringValue;
import org.twinlife.twinlife.BaseService.AttributeNameImageIdValue;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinlife.util.Version;

import java.util.List;
import java.util.UUID;

public class TwinmeAttributes {

    //
    // Twincode Attributes
    //
    private static final String TWINCODE_ATTRIBUTE_CREATED_BY = "created-by";
    private static final String TWINCODE_ATTRIBUTE_INVITED_BY = "invited-by";
    private static final String TWINCODE_ATTRIBUTE_INVITATION_KIND = "invitationKind";
    private static final String TWINCODE_ATTRIBUTE_CHANNEL = "channel";
    private static final String TWINCODE_ATTRIBUTE_PERMISSIONS = "permissions";
    private static final String TWINCODE_ATTRIBUTE_ACCOUNT_MIGRATION = "accountMigration";

    private static final String TWINCODE_ATTRIBUTE_TWINCODE_OUTBOUND_ID = "twincodeOutboundId";

    //
    // Twincode Attributes
    //

    public static void setTwincodeAttributeName(@NonNull List<AttributeNameValue> attributes, @NonNull String name) {

        attributes.add(new AttributeNameStringValue(Twincode.NAME, name));
    }

    public static void setTwincodeAttributeAvatarId(@NonNull List<AttributeNameValue> attributes, @NonNull ExportedImageId avatarId) {

        attributes.add(new AttributeNameImageIdValue(Twincode.AVATAR_ID, avatarId));
    }

    public static void setTwincodeAttributeCreatedBy(@NonNull List<AttributeNameValue> attributes, @NonNull UUID twincodeId) {

        attributes.add(new AttributeNameStringValue(TWINCODE_ATTRIBUTE_CREATED_BY, twincodeId.toString()));
    }

    public static void setTwincodeAttributeInvitedBy(@NonNull List<AttributeNameValue> attributes, @NonNull UUID twincodeId) {

        attributes.add(new AttributeNameStringValue(TWINCODE_ATTRIBUTE_INVITED_BY, twincodeId.toString()));
    }

    public static void setTwincodeAttributeInvitationKind(@NonNull List<AttributeNameValue> attributes, @NonNull String kind) {

        attributes.add(new AttributeNameStringValue(TWINCODE_ATTRIBUTE_INVITATION_KIND, kind));
    }

    public static void setTwincodeAttributeChannel(@NonNull List<AttributeNameValue> attributes, @NonNull UUID channel) {

        attributes.add(new AttributeNameStringValue(TWINCODE_ATTRIBUTE_CHANNEL, channel.toString()));
    }

    public static void setTwincodeAttributeDescription(@NonNull List<AttributeNameValue> attributes, @NonNull String name) {

        attributes.add(new AttributeNameStringValue(Twincode.DESCRIPTION, name));
    }

    public static void setTwincodeAttributeAccountMigration(@NonNull List<AttributeNameValue> attributes,
                                                            @NonNull String name, boolean hasRelations) {

        attributes.add(new AttributeNameStringValue(TWINCODE_ATTRIBUTE_ACCOUNT_MIGRATION, name + (hasRelations ? ":1" : ":0")));
    }

    @NonNull
    public static Pair<Version, Boolean> getTwincodeAttributeAccountMigration(@NonNull Twincode twincode) {

        final Object value = twincode.getAttribute(TWINCODE_ATTRIBUTE_ACCOUNT_MIGRATION);
        if (value instanceof String) {
            final String[] items = ((String) value).split(":");
            final Version version = new Version(items[0]);
            if (items.length < 2) {
                return new Pair<>(version, Boolean.TRUE);
            }
            return new Pair<>(version, "1".equals(items[1]));
        } else {
            return new Pair<>(new Version(0, 0), Boolean.TRUE);
        }
    }

    @SuppressWarnings("unused")
    @Nullable
    public static UUID getTwincodeOutboundId(@NonNull Twincode twincode) {

        return Utils.UUIDFromString((String) twincode.getAttribute(TWINCODE_ATTRIBUTE_TWINCODE_OUTBOUND_ID));
    }

    @Nullable
    public static UUID getInvitedBy(@NonNull Twincode twincode) {

        return Utils.UUIDFromString((String) twincode.getAttribute(TWINCODE_ATTRIBUTE_INVITED_BY));
    }

    @Nullable
    public static UUID getCreatedBy(@NonNull Twincode twincode) {

        return Utils.UUIDFromString((String) twincode.getAttribute(TWINCODE_ATTRIBUTE_CREATED_BY));
    }

    @Nullable
    public static UUID getChannel(@NonNull Twincode twincode) {

        return Utils.UUIDFromString((String) twincode.getAttribute(TWINCODE_ATTRIBUTE_CHANNEL));
    }

    @Nullable
    public static String getInvitationKind(@NonNull Twincode twincode) {

        return (String) twincode.getAttribute(TWINCODE_ATTRIBUTE_INVITATION_KIND);
    }

    @Nullable
    public static String getPermissions(@NonNull Twincode twincode) {

        return (String) twincode.getAttribute(TWINCODE_ATTRIBUTE_PERMISSIONS);
    }

    public static void setCapabilities(@NonNull List<AttributeNameValue> attributes, @NonNull String name) {

        attributes.add(new AttributeNameStringValue(Twincode.CAPABILITIES, name));
    }
}
