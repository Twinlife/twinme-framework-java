/*
 *  Copyright (c) 2018-2024 twinlife SA.
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
import org.twinlife.twinlife.BaseService.AttributeNameStringValue;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// version: 1.3
//

/*
 * Representation of a group for the UI.
 *
 * getName           -> name of the group
 * getAvatar         -> group picture
 * getIdentityName   -> name of current user within the group
 * getIdentityAvatar -> picture of current user within the group
 *
 * The group name and avatar are initialized from the group twincode with the setGroupTwincodeOutbound
 * operation.  The current user identity is initialized from the member twincode with the
 * setMemberTwincodeOutbound.  The group key cannot be null and it is always associated with the non null
 * group member twincode.
 *
 */

public class Group extends TwinmeRepositoryObject implements Originator {

    public static final UUID SCHEMA_ID = UUID.fromString("a70f964c-7147-4825-afe2-d14da222f181");
    public static final int SCHEMA_VERSION = 1;

    @Nullable
    private UUID mGroupTwincodeFactoryId;
    @Nullable
    private UUID mGroupCreatedByTwincodeOutboundId;
    @Nullable
    private UUID mInvitedByTwincodeOutboundId;
    @Nullable
    private Capabilities mCapabilities;
    private TwincodeOutbound mGroupTwincodeOutbound;
    @Nullable
    private Space mSpace;
    private boolean mIsLeaving;
    private boolean mIsDeleted;
    private double mUsageScore;
    private long mLastMessageDate;

    Group(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
          long creationDate, @Nullable String name, @Nullable String description,
          @Nullable List<BaseService.AttributeNameValue> attributes,
          long modificationDate) {
        super(identifier, uuid, creationDate, modificationDate);

        mUsageScore = 0.0;
        mLastMessageDate = 0;
        update(name, description, attributes, modificationDate);
    }

    void update(@Nullable String name, @Nullable String description, @Nullable List<AttributeNameValue> attributes,
                long modificationDate) {

        mName = name;
        mDescription = description;
        mModificationDate = modificationDate;
        if (attributes != null) {
            for (AttributeNameValue attribute : attributes) {
                switch (attribute.name) {

                    case "leaving":
                        if (attribute instanceof BaseService.AttributeNameBooleanValue) {
                            mIsLeaving = (Boolean) attribute.value;
                        }
                        break;

                    case "groupTwincodeFactoryId":
                        if (attribute instanceof AttributeNameStringValue) {
                            mGroupTwincodeFactoryId = Utils.UUIDFromString((String) (attribute).value);
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

        UUID groupTwincodeFactoryId;
        TwincodeInbound twincodeInbound;
        TwincodeOutbound twincodeOutbound;
        TwincodeOutbound groupTwincodeOutbound;
        String name, description;
        Space space;
        boolean isLeaving;
        synchronized (this) {
            name = mName;
            description = mDescription;
            space = mSpace;
            twincodeInbound = mTwincodeInbound;
            twincodeOutbound = mTwincodeOutbound;
            groupTwincodeOutbound = mGroupTwincodeOutbound;
            groupTwincodeFactoryId = mGroupTwincodeFactoryId;
            isLeaving = mIsLeaving;
        }

        final List<AttributeNameValue> attributes = new ArrayList<>();
        if (exportAll) {
            exportAttributes(attributes, name, description, space, twincodeInbound, twincodeOutbound);
            if (groupTwincodeOutbound != null) {
                attributes.add(new AttributeNameStringValue("groupTwincodeOutboundId", groupTwincodeOutbound.getId().toString()));
            }
        }
        if (groupTwincodeFactoryId != null) {
            attributes.add(new AttributeNameStringValue("groupTwincodeFactoryId", groupTwincodeFactoryId.toString()));
        }
        if (isLeaving) {
            attributes.add(new BaseService.AttributeNameBooleanValue("leaving", Boolean.TRUE));
        }

        return attributes;
    }

    @Override
    public boolean isValid() {

        // The Group is valid if we have an identity twincode (inbound and outbound) and it has an associated space.
        // The Group will be deleted when this becomes invalid.
        return mTwincodeInbound != null && mTwincodeOutbound != null && mSpace != null;
    }

    @Override
    public boolean canCreateP2P() {

        // We must be able to create the P2P even if we are leaving.
        return true;
    }

    public boolean canAcceptP2P(@Nullable UUID twincodeId) {

        return true;
    }

    @Override
    @Nullable
    public RepositoryObject getOwner() {

        // The group owner is the Space instance!
        return mSpace;
    }

    @Override
    public void setOwner(@Nullable RepositoryObject owner) {

        // Called when an object is loaded from the database and linked to its owner.
        if (owner instanceof Space) {
            mSpace = (Space) owner;
        }
    }

    @Override
    public synchronized void setTwincodeOutbound(@Nullable TwincodeOutbound twincodeOutbound) {

        super.setTwincodeOutbound(twincodeOutbound);

        if (twincodeOutbound != null) {
            mInvitedByTwincodeOutboundId = TwinmeAttributes.getInvitedBy(twincodeOutbound);
        } else {
            mInvitedByTwincodeOutboundId = null;
        }
    }

    @Override
    public synchronized void setPeerTwincodeOutbound(@Nullable TwincodeOutbound twincodeOutbound) {

        setGroupTwincodeOutbound(twincodeOutbound);
    }

    @Override
    @Nullable
    public TwincodeOutbound getPeerTwincodeOutbound() {

        return mGroupTwincodeOutbound;
    }

    //
    // Group specific methods.
    //

    @Nullable
    public String getGroupName() {

        return mGroupTwincodeOutbound == null ? null : mGroupTwincodeOutbound.getName();
    }

    @NonNull
    public UUID getMemberTwincodeOutboundId() {

        return getTwincodeOutboundId();
    }

    @Nullable
    public UUID getGroupTwincodeOutboundId() {

        return mGroupTwincodeOutbound == null ? null : mGroupTwincodeOutbound.getId();
    }

    @Nullable
    public ImageId getAvatarId() {

        return mGroupTwincodeOutbound == null ? null : mGroupTwincodeOutbound.getAvatarId();
    }

    public synchronized boolean updatePeerName(@Nullable TwincodeOutbound peerTwincodeOutbound, @Nullable String oldName) {

        if (peerTwincodeOutbound == null || oldName == null) {

            return false;
        }

        String newName = peerTwincodeOutbound.getName();
        if (newName == null || !oldName.equals(mName) || oldName.equals(newName)) {

            return false;
        }
        mName = newName;

        return true;
    }

    @Nullable
    public synchronized String getIdentityName() {

        return mTwincodeOutbound == null ? null : mTwincodeOutbound.getName();
    }

    @Nullable
    @Override
    public String getIdentityDescription() {
        return getPeerDescription();
    }

    @Nullable
    @Override
    public synchronized ImageId getIdentityAvatarId() {

        return mTwincodeOutbound == null ? null : mTwincodeOutbound.getAvatarId();
    }

    @Override
    public boolean hasPeer() {

        return !mIsLeaving;
    }

    @Override
    public double getUsageScore() {

        return mUsageScore;
    }

    @Override
    public long getLastMessageDate() {

        return mLastMessageDate;
    }

    @Override
    @Nullable
    public String getPeerDescription() {

        return mGroupTwincodeOutbound == null ? null : mGroupTwincodeOutbound.getDescription();
    }

    @Override
    @NonNull
    public Capabilities getCapabilities() {

        return mCapabilities != null ? mCapabilities : new Capabilities();
    }

    public synchronized void setGroupTwincodeOutbound(@Nullable TwincodeOutbound twincodeOutbound) {

        mGroupTwincodeOutbound = twincodeOutbound;
        if (twincodeOutbound != null) {
            mGroupCreatedByTwincodeOutboundId = TwinmeAttributes.getCreatedBy(twincodeOutbound);
            String capabilities = twincodeOutbound.getCapabilities();
            if (capabilities != null) {
                mCapabilities = new Capabilities(capabilities);
            } else {
                mCapabilities = null;
            }
        }
    }

    public synchronized TwincodeOutbound getGroupTwincodeOutbound() {

        return mGroupTwincodeOutbound;
    }

    public synchronized TwincodeOutbound getMemberTwincodeOutbound() {

        return mTwincodeOutbound;
    }

    public synchronized UUID getCreatedByMemberTwincodeOutboundId() {

        return mGroupCreatedByTwincodeOutboundId;
    }

    public synchronized UUID getInvitedByMemberTwincodeOutboundId() {

        return mInvitedByTwincodeOutboundId;
    }

    public synchronized GroupMember getCurrentMember() {

        if (mTwincodeOutbound == null) {
            return null;
        } else {
            return new GroupMember(this, mTwincodeOutbound);
        }
    }

    @Nullable
    public UUID getMemberTwincodeFactoryId() {

        return getTwincodeFactoryId();
    }

    @Nullable
    public UUID getGroupTwincodeFactoryId() {

        return mGroupTwincodeFactoryId;
    }

    public synchronized void setGroupTwincodeFactory(@NonNull TwincodeFactory twincodeFactory) {

        mGroupTwincodeFactoryId = twincodeFactory.getId();
        setPeerTwincodeOutbound(twincodeFactory.getTwincodeOutbound());
    }

    @Nullable
    @Override
    public Space getSpace() {

        return mSpace;
    }

    @Override
    public Type getType() {
        return Type.GROUP;
    }

    @Override
    public boolean isSpace(@Nullable Space space) {

        return mSpace == space;
    }

    public synchronized void setSpace(@Nullable Space space) {

        mSpace = space;
    }

    @Override
    public boolean isGroup() {

        return true;
    }

    public boolean isLeaving() {

        return mIsLeaving;
    }

    public void markLeaving() {

        mIsLeaving = true;
    }

    public void clearLeaving() {

        mIsLeaving = false;
    }

    public boolean isDeleted() {

        return mIsDeleted;
    }

    public void markDeleted() {

        mIsDeleted = true;
    }

    public boolean isOwner() {

        return mTwincodeOutbound != null && mGroupCreatedByTwincodeOutboundId != null && mGroupCreatedByTwincodeOutboundId.equals(mTwincodeOutbound.getId());
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "Group[" +
                " id=" + mId +
                " twincodeInbound=" + mTwincodeInbound +
                (BuildConfig.ENABLE_DUMP ? " name=" + mName : "") +
                " memberTwincodeOutboundId=" + mTwincodeOutbound +
                " groupTwincodeOutbandId=" + mGroupTwincodeOutbound +
                " isLeaving=" + mIsLeaving + "]";
    }
}
