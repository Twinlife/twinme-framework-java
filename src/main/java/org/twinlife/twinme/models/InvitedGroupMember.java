/*
 *  Copyright (c) 2018-2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.ImageId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a contact that was invited to join a group.
 *
 * Objects of this class are not stored in the repository nor cached in the Twinme context.
 * They are created on demand by the UI by looking at the pending invitation and the contact.
 *
 * The pending invitation only concern the invitations sent by the current user (not the invitation
 * send by other group members).
 */
public class InvitedGroupMember implements Originator {


    // The contact that was invited in the group by the current user.
    @NonNull
    private final Contact mContact;
    private final DescriptorId mInvitation;

    @SuppressWarnings("SameParameterValue")
    public InvitedGroupMember(@NonNull Contact contact, @NonNull DescriptorId invitation) {

        mContact = contact;
        mInvitation = invitation;
    }

    @Override
    @NonNull
    public UUID getId() {

        return mContact.getId();
    }

    @NonNull
    @Override
    public String getName() {

        return mContact.getName();
    }

    @Override
    @Nullable
    public ImageId getAvatarId() {

        return mContact.getAvatarId();
    }

    @Override
    public boolean hasPeer() {

        return mContact.hasPeer();
    }

    @Override
    @Nullable
    public String getIdentityName() {

        return mContact.getIdentityName();
    }

    @Nullable
    @Override
    public String getIdentityDescription() {
        return mContact.getIdentityDescription();
    }

    @Override
    @Nullable
    public ImageId getIdentityAvatarId() {

        return mContact.getIdentityAvatarId();
    }

    @Nullable
    public UUID getTwincodeInboundId() {

        return mContact.getTwincodeInboundId();
    }

    @Nullable
    public UUID getTwincodeOutboundId() {

        return mContact.getTwincodeOutboundId();
    }

    @Nullable
    @Override
    public Space getSpace() {

        return mContact.getSpace();
    }

    @Override
    public Type getType() {
        return Type.INVITED_GROUP_MEMBER;
    }

    @Override
    public boolean isSpace(@Nullable Space space) {

        return mContact.isSpace(space);
    }

    @Override
    public boolean isGroup() {

        return true; // SCz false?
    }

    @Override
    public double getUsageScore() {

        return mContact.getUsageScore();
    }

    @Override
    public long getLastMessageDate() {

        return mContact.getLastMessageDate();
    }

    @Override
    @Nullable
    public String getPeerDescription() {

        return null;
    }

    @Override
    @NonNull
    public Capabilities getCapabilities() {

        return mContact.getCapabilities();
    }

    public DescriptorId getInvitation() {

        return mInvitation;
    }

    @NonNull
    public DatabaseIdentifier getDatabaseId() {

        return mContact.getDatabaseId();
    }

    @Override
    public boolean isValid() {

        return mContact.isValid();
    }

    @Override
    public boolean canCreateP2P() {

        return mContact.canCreateP2P();
    }

    @Override
    public boolean canAcceptP2P(@Nullable UUID twincodeId) {

        return false;
    }

    @Override
    public long getModificationDate() {

        return mContact.getModificationDate();
    }

    @Override
    @NonNull
    public List<BaseService.AttributeNameValue> getAttributes(boolean exportAll) {

        return new ArrayList<>();
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "InvitedGroupMember:\n" + mContact + "\n";
    }
}
