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
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.InvitationCode;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.twincode.outbound.InvitationCodeImpl;
import org.twinlife.twinlife.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Invitation to create a contact.
 *
 * The invitation has a specific twincode (different from the Profile twincode) that is sent by some mechanism to the invitee.
 * The invitation can have constraints such as:
 *
 * - it can be accepted only once,
 * - it can be accepted only if it has not expired,
 * - if can be accepted only by the group member to which the invitation was sent.
 *
 * Unlike sharing the Profile twincode, the invitation can be withdrawn.
 *
 * When the invitation is created, the current profile identity is used to create the invitation twincode.
 * The avatar and user's name are stored in the invitation twincode outbound id.  This is the information that
 * the invitee will see.
 *
 * When the invitation is accepted by the invitee, a PairInviteInvocation is made and received on the Invitation object.
 * We then create the contact by using the invitation twincode.
 *
 * The invitation can be associated with a twincode descriptor to invite a group member to become a contact.
 * We keep track of the lifetime of the twincode descriptor to remove the invitation in case the twincode descriptor
 * is removed.
 */
public class Invitation extends TwinmeRepositoryObject {

    public static final UUID SCHEMA_ID = UUID.fromString("1d1545d4-1912-492a-87db-60ffd68461ff");
    public static final int SCHEMA_VERSION = 1;

    @Nullable
    private UUID mGroupId;
    @Nullable
    private UUID mGroupMemberTwincodeId;
    @Nullable
    private DescriptorId mDescriptorId;
    @Nullable
    private Space mSpace;
    private long mPermissions;
    @Nullable
    private InvitationCode mInvitationCode;

    // Invitation kinds.
    public static final String CONTACT = "contact";
    // public static final String GROUP = "group";
    public static final String CHANNEL = "channel";

    Invitation(@NonNull DatabaseIdentifier identifier, @NonNull UUID id, long creationDate, @Nullable String name,
               @Nullable String description, @Nullable List<AttributeNameValue> attributes,
               long modificationDate) {
        super(identifier, id, creationDate, modificationDate);

        update(name, description, attributes, modificationDate);
    }

    void update(@Nullable String name, @Nullable String description, @Nullable List<AttributeNameValue> attributes,
                long modificationDate) {

        mName = name;
        mDescription = description;
        mModificationDate = modificationDate;
        String code = null;
        long codeCreationDate = -1;
        int codeValidityPeriod = -1;
        String codePublicKey = null;
        if (attributes != null) {
            for (AttributeNameValue attribute : attributes) {

                switch (attribute.name) {
                    case "groupId":
                        if (attribute instanceof BaseService.AttributeNameStringValue) {
                            mGroupId = Utils.UUIDFromString((String) attribute.value);
                        }
                        break;

                    case "groupMemberTwincodeId":
                        if (attribute instanceof BaseService.AttributeNameStringValue) {
                            mGroupMemberTwincodeId = Utils.UUIDFromString((String) attribute.value);
                        }
                        break;

                    case "descriptorId":
                        if (attribute instanceof BaseService.AttributeNameStringValue) {
                            mDescriptorId = DescriptorId.fromString((String) attribute.value);
                        }
                        break;

                    case "permissions":
                        if (attribute instanceof BaseService.AttributeNameLongValue) {
                            mPermissions = (Long) attribute.value;
                        }
                        break;

                    case "code":
                        if (attribute instanceof BaseService.AttributeNameStringValue) {
                            code = (String) attribute.value;
                        }
                        break;
                    case "codeCreationDate":
                        if (attribute instanceof BaseService.AttributeNameLongValue) {
                            codeCreationDate = (Long) attribute.value;
                        }
                        break;
                    case "codeValidityPeriod":
                        if (attribute instanceof BaseService.AttributeNameLongValue) {
                            codeValidityPeriod = ((Long) attribute.value).intValue();
                        }
                        break;
                    case "codePublicKey":
                        if (attribute instanceof BaseService.AttributeNameStringValue) {
                            codePublicKey = (String) attribute.value;
                        }
                        break;
                }
            }
        }

        if (code != null) {
            mInvitationCode = new InvitationCodeImpl(codeCreationDate, codeValidityPeriod, code, getTwincodeOutboundId(), codePublicKey);
        }
    }

    //
    // Implement RepositoryObject interface
    //

    @NonNull
    public List<AttributeNameValue> getAttributes(boolean exportAll) {

        Space space;
        UUID groupId;
        UUID groupMemberTwincodeId;
        DescriptorId descriptorId;
        long permissions;
        TwincodeOutbound twincodeOutbound;
        TwincodeInbound twincodeInbound;
        String name, description;
        String code = null;
        long codeCreationDate = -1;
        int codeValidityPeriod = -1;
        String codePublicKey = null;

        synchronized (this) {
            space = mSpace;
            twincodeInbound = mTwincodeInbound;
            twincodeOutbound = mTwincodeOutbound;
            groupId = mGroupId;
            descriptorId = mDescriptorId;
            groupMemberTwincodeId = mGroupMemberTwincodeId;
            permissions = mPermissions;
            name = mName;
            description = mDescription;
            if (mInvitationCode != null) {
                code = mInvitationCode.getCode();
                codeCreationDate = mInvitationCode.getCreationDate();
                codeValidityPeriod = mInvitationCode.getValidityPeriod();
                codePublicKey = mInvitationCode.getPublicKey();
            }
        }

        final List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
        if (exportAll) {
            exportAttributes(attributes, name, description, space, twincodeInbound, twincodeOutbound);
        }
        if (groupId != null) {
            attributes.add(new BaseService.AttributeNameStringValue("groupId", groupId.toString()));
        }
        if (groupMemberTwincodeId != null) {
            attributes.add(new BaseService.AttributeNameStringValue("groupMemberTwincodeId", groupMemberTwincodeId.toString()));
        }
        if (descriptorId != null) {
            attributes.add(new BaseService.AttributeNameStringValue("descriptorId", descriptorId.toString()));
        }
        if (permissions != 0) {
            attributes.add(new BaseService.AttributeNameLongValue("permissions", permissions));
        }
        if (code != null) {
            attributes.add(new BaseService.AttributeNameStringValue("code", code));
        }
        if (codeCreationDate != -1) {
            attributes.add(new BaseService.AttributeNameLongValue("codeCreationDate", codeCreationDate));
        }
        if (codeValidityPeriod != -1) {
            attributes.add(new BaseService.AttributeNameLongValue("codeValidityPeriod", (long) codeValidityPeriod));
        }
        if (codePublicKey != null) {
            attributes.add(new BaseService.AttributeNameStringValue("codePublicKey", codePublicKey));
        }

        return attributes;
    }

    @Override
    public boolean isValid() {

        // The Invitation is valid if we have an identity twincode (inbound and outbound) and it has an associated space.
        // The Invitation will be deleted when this becomes invalid.
        return mTwincodeInbound != null && mTwincodeOutbound != null && mSpace != null;
    }

    @Override
    public boolean canCreateP2P() {

        return false;
    }

    @Override
    @Nullable
    public RepositoryObject getOwner() {

        // The invitation owner is the Space instance!
        return mSpace;
    }

    @Override
    public void setOwner(@Nullable RepositoryObject owner) {

        // Called when an object is loaded from the database and linked to its owner.
        if (owner instanceof Space) {
            mSpace = (Space) owner;
        }
    }

    //
    // Invitation specific methods.
    //

    @Nullable
    public DescriptorId getDescriptorId() {

        return mDescriptorId;
    }

    public synchronized void setDescriptorId(DescriptorId descriptorId) {

        mDescriptorId = descriptorId;
    }

    @Override
    public synchronized void setTwincodeOutbound(@Nullable TwincodeOutbound identityTwincodeOutbound) {

        mTwincodeOutbound = identityTwincodeOutbound;
        if (identityTwincodeOutbound != null) {
            mName = identityTwincodeOutbound.getName();
            mDescription = identityTwincodeOutbound.getDescription();
        } else {
            mName = "";
            mDescription = "";
        }
    }

    @Nullable
    public UUID getSpaceId() {

        return mSpace == null ? null : mSpace.getId();
    }

    @Nullable
    public Space getSpace() {

        return mSpace;
    }

    public boolean isSpace(@Nullable Space space) {

        return mSpace == space;
    }

    public synchronized void setSpace(@NonNull Space space) {

        mSpace = space;
    }

    @Nullable
    public UUID getGroupId() {

        return mGroupId;
    }

    @Nullable
    public UUID getGroupMemberTwincodeId() {

        return mGroupMemberTwincodeId;
    }

    public long getPermissions() {

        return mPermissions;
    }

    @Nullable
    public InvitationCode getInvitationCode() {
        return mInvitationCode;
    }

    public void setInvitationCode(@Nullable InvitationCode invitationCode) {
        this.mInvitationCode = invitationCode;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "Invitation[" +
                " id=" + mDatabaseId +
                (BuildConfig.ENABLE_DUMP ? " name=" + mName : "") +
                " twincodeOutbound=" + mTwincodeOutbound +
                " descriptorId=" + mDescriptorId +
                " invitationCode=" + mInvitationCode + "]";
    }
}
