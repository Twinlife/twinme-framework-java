/*
 *  Copyright (c) 2018-2025 twinlife SA.
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
        return getIdentityDescription();
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
}
