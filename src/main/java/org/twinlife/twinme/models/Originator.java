/*
 *  Copyright (c) 2018-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinme.TwinmeContext;

import java.util.UUID;


/**
 * Interface that describes the originator of a notification event.
 * <p>
 * This interface is implemented by Contact, CallReceiver, Group, GroupMember, InvitedGroupMember and PreviousContact.
 */

public interface Originator extends RepositoryObject {

    enum Type{
        CONTACT,
        CALL_RECEIVER,
        GROUP,
        GROUP_MEMBER,
        INVITED_GROUP_MEMBER
    }

    @NonNull
    UUID getId();

    @NonNull
    String getName();

    @Nullable
    String getPeerDescription();

    @Nullable
    ImageId getAvatarId();

    @Nullable
    String getIdentityName();

    @Nullable
    String getIdentityDescription();

    @NonNull
    default String getDescription(){
        String identityDescription = getIdentityDescription();
        return identityDescription == null ? "" : identityDescription;
    }

    @Nullable
    ImageId getIdentityAvatarId();

    @Nullable
    UUID getTwincodeInboundId();

    @Nullable
    UUID getTwincodeOutboundId();

    @Nullable
    default UUID getPeerTwincodeOutboundId(){
        return null;
    }

    @Nullable
    Space getSpace();

    double getUsageScore();

    long getLastMessageDate();

    boolean isSpace(@Nullable Space space);

    boolean isGroup();

    boolean hasPeer();

    /**
     * Check whether it is possible to accept incoming P2P connection with the given peer twincode Id.
     *
     * @param twincodeId the peer twincode id.
     * @return true if this originator is ready to accept incoming P2P.
     */
    boolean canAcceptP2P(@Nullable UUID twincodeId);

    @NonNull
    Capabilities getCapabilities();

    @NonNull
    default Capabilities getIdentityCapabilities(){
        return getCapabilities();
    }

    default boolean hasPrivatePeer(){
        return false;
    }

    Type getType();

    default String getShortcutId() {
        return getType() + "_" + getId();
    }

    /**
     * Get a subject property identified by the name.
     * @param name the property name.
     * @param defaultValue the default value when the property is not found .
     * @return the subject property value or the default value.
     */
    @NonNull
    String getString(@NonNull String name, @NonNull String defaultValue);
    long getLong(@NonNull String name, long defaultValue);
    boolean getBoolean(@NonNull String name, boolean defaultValue);

    /**
     * Set the subject property to the given value and save the subject properties locally in the database.
     * @param name the property name.
     * @param value the value to update.
     * @param twinmeContext the twinme context to access the repository service.
     */
    void putString(@NonNull String name, @NonNull String value, @NonNull TwinmeContext twinmeContext);
    default void putBoolean(@NonNull String name, @NonNull Boolean value, @NonNull TwinmeContext twinmeContext) {
        putString(name, value ? "1" : "0", twinmeContext);
    }
    default void putLong(@NonNull String name, long value, @NonNull TwinmeContext twinmeContext) {
        putString(name, String.valueOf(value), twinmeContext);
    }
}
