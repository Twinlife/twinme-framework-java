/*
 *  Copyright (c) 2014-2025 twinlife SA.
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

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameStringValue;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// version: 1.15
//

/*
 * <pre>
 *
 * Invariant: Contact <<->> PrivateIdentity
 *
 *  contact.privateIdentityId == null
 *  and contact.privateIdentity == null
 *   or
 *  contact.privateIdentityId != null
 *  and contact.privateIdentity != null
 *  and contact.privateIdentityId == contact.privateIdentity.id
 *
 *
 * Invariant: Contact <<->> PeerTwincodeOutbound
 *
 *  contact.publicPeerTwincodeOutboundId == null
 *  and contact.privatePeerTwincodeOutboundId == null
 *  and contact.peerTwincodeOutbound == null
 *   or
 *  contact.privatePeerTwincodeOutboundId != null
 *  and contact.peerTwincodeOutbound != null
 *  and contact.privatePeerTwincodeOutboundId == contact.peerTwincodeOutbound.id
 *   or
 *  contact.publicPeerTwincodeOutboundId != null
 *  and contact.privatePeerTwincodeOutboundId == null
 *  and contact.peerTwincodeOutbound != null
 *  and contact.publicPeerTwincodeOutboundId == contact.peerTwincodeOutbound.id
 * </pre>
 */

public class Contact extends TwinmeRepositoryObject implements Originator {

    public static final UUID SCHEMA_ID = UUID.fromString("52872aa7-73a9-47f2-b4ad-83bcb412dc4c");
    public static final int SCHEMA_VERSION = 1;

    @Nullable
    private UUID mPublicPeerTwincodeOutboundId;
    @Nullable
    private TwincodeOutbound mPeerTwincodeOutbound;
    private double mUsageScore;
    private long mLastMessageDate;
    @Nullable
    private UUID mGroupId;
    @Nullable
    private UUID mGroupMemberTwincodeId;
    @Nullable
    private Space mSpace;
    @Nullable
    private Capabilities mCapabilities; // What the peer accepts.
    @Nullable
    private Capabilities mIdentityCapabilities; // What we grant to the peer.
    private boolean mHasPrivatePeer;
    @Nullable
    private Capabilities mPrivateCapabilities; // What we grant to the peer without disclosing it to them.

    Contact(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
            long creationDate, @Nullable String name, @Nullable String description,
            @Nullable List<BaseService.AttributeNameValue> attributes,
            long modificationDate) {
        super(identifier, uuid, creationDate, modificationDate);

        mName = name;
        mUsageScore = 0.0;
        mLastMessageDate = 0;
        mGroupId = null;
        mGroupMemberTwincodeId = null;
        mHasPrivatePeer = true;
        update(name, description, attributes, modificationDate);
    }

    synchronized void update(@Nullable String name, @Nullable String description, @Nullable List<AttributeNameValue> attributes,
                             long modificationDate) {

        mName = name;
        mDescription = description;
        mModificationDate = modificationDate;
        mHasPrivatePeer = true;
        if (attributes != null) {
            for (AttributeNameValue attribute : attributes) {
                switch (attribute.name) {

                    case "publicPeerTwincodeOutboundId":
                        if (attribute instanceof AttributeNameStringValue) {
                            mPublicPeerTwincodeOutboundId = Utils.UUIDFromString((String) attribute.value);
                        }
                        break;

                    case "contactFromGroupId":
                        if (attribute instanceof AttributeNameStringValue) {
                            mGroupId = Utils.UUIDFromString((String) attribute.value);
                        }
                        break;

                    case "contactFromGroupMemberId":
                        if (attribute instanceof AttributeNameStringValue) {
                            mGroupMemberTwincodeId = Utils.UUIDFromString((String) attribute.value);
                        }
                        break;

                    case "noPrivatePeer":
                        if (attribute instanceof BaseService.AttributeNameBooleanValue) {
                            mHasPrivatePeer = false;
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

        UUID publicPeerTwincodeOutboundId;
        UUID groupId;
        UUID groupMemberTwincodeId;
        TwincodeInbound twincodeInbound;
        TwincodeOutbound twincodeOutbound;
        TwincodeOutbound peerTwincodeOutbound;
        Space space;
        String name, description;
        boolean hasPrivatePeer;
        synchronized (this) {
            publicPeerTwincodeOutboundId = mPublicPeerTwincodeOutboundId;
            groupId = mGroupId;
            groupMemberTwincodeId = mGroupMemberTwincodeId;
            space = mSpace;
            name = mName;
            description = mDescription;
            twincodeInbound = mTwincodeInbound;
            twincodeOutbound = mTwincodeOutbound;
            hasPrivatePeer = mHasPrivatePeer;
            peerTwincodeOutbound = mPeerTwincodeOutbound;
        }

        List<AttributeNameValue> attributes = new ArrayList<>();
        if (exportAll) {
            exportAttributes(attributes, name, description, space, twincodeInbound, twincodeOutbound);

            if (peerTwincodeOutbound != null) {
                attributes.add(new AttributeNameStringValue("privatePeerTwincodeOutboundId", peerTwincodeOutbound.getId().toString()));
            }
        }

        if (publicPeerTwincodeOutboundId != null) {
            attributes.add(new AttributeNameStringValue("publicPeerTwincodeOutboundId", publicPeerTwincodeOutboundId.toString()));
        }
        if (groupId != null) {
            attributes.add(new AttributeNameStringValue("contactFromGroupId", groupId.toString()));
        }
        if (groupMemberTwincodeId != null) {
            attributes.add(new AttributeNameStringValue("contactFromGroupMemberId", groupMemberTwincodeId.toString()));
        }
        if (!hasPrivatePeer) {
            attributes.add(new BaseService.AttributeNameBooleanValue("noPrivatePeer", true));
        }

        return attributes;
    }

    @Override
    public boolean isValid() {

        // The contact is valid if we have an identity twincode (inbound and outbound) and it has an associated space.
        // The contact will be deleted when this becomes invalid.
        return mTwincodeInbound != null && mTwincodeOutbound != null && mSpace != null;
    }

    @Override
    public boolean canCreateP2P() {

        return hasPrivatePeer();
    }

    @Override
    public synchronized boolean canAcceptP2P(@Nullable UUID twincodeId) {

        // The contact must know the peer private identity.
        return mHasPrivatePeer && mPeerTwincodeOutbound != null
                // If there is no peer twincode, the peer twincode must not be signed.
                && ((twincodeId == null && !mPeerTwincodeOutbound.isSigned())
                // And if we have a peer twincode, it must match what we have.
                || (mPeerTwincodeOutbound.getId().equals(twincodeId)));
    }

    @Override
    @Nullable
    public RepositoryObject getOwner() {

        // The contact owner is the Space instance!
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
    @Nullable
    public TwincodeOutbound getPeerTwincodeOutbound() {

        return mPeerTwincodeOutbound;
    }

    @Override
    public synchronized void setPeerTwincodeOutbound(@Nullable TwincodeOutbound peerTwincodeOutbound) {

        if (peerTwincodeOutbound != null) {
            mPeerTwincodeOutbound = peerTwincodeOutbound;
            String capabilities = peerTwincodeOutbound.getCapabilities();
            if (capabilities != null) {
                mCapabilities = new Capabilities(capabilities);
            } else {
                mCapabilities = null;
            }
        } else {
            mPeerTwincodeOutbound = null;
            mPublicPeerTwincodeOutboundId = null;
            mCapabilities = null;
        }
    }

    @Override
    public synchronized void setTwincodeOutbound(@Nullable TwincodeOutbound identityTwincodeOutbound) {

        mTwincodeOutbound = identityTwincodeOutbound;
        if (identityTwincodeOutbound != null) {
            String capabilities = identityTwincodeOutbound.getCapabilities();
            if (capabilities != null) {
                mIdentityCapabilities = new Capabilities(capabilities);
            } else {
                mIdentityCapabilities = null;
            }
        } else {
            mIdentityCapabilities = null;
        }
    }

    //
    // Contact specific methods.
    //

    @Override
    @Nullable
    public UUID getPeerTwincodeOutboundId() {

        return !mHasPrivatePeer || mPeerTwincodeOutbound == null ? null : mPeerTwincodeOutbound.getId();
    }

    public double getUsageScore() {

        return mUsageScore;
    }

    @Override
    public long getLastMessageDate() {

        return mLastMessageDate;
    }

    @Nullable
    @Override
    public Space getSpace() {

        return mSpace;
    }

    public synchronized void setSpace(@Nullable Space space) {

        mSpace = space;
    }

    @Nullable
    public UUID getPublicPeerTwincodeOutboundId() {

        return mPublicPeerTwincodeOutboundId;
    }

    public void setPublicPeerTwincodeOutbound(@Nullable TwincodeOutbound peerTwincodeOutbound) {

        if (peerTwincodeOutbound != null) {
            mHasPrivatePeer = false;
            mPublicPeerTwincodeOutboundId = peerTwincodeOutbound.getId();
            setPeerTwincodeOutbound(peerTwincodeOutbound);
        } else {
            mPublicPeerTwincodeOutboundId = null;
        }
    }

    public synchronized boolean updatePeerTwincodeOutbound(@Nullable TwincodeOutbound peerTwincodeOutbound) {

        if (mPeerTwincodeOutbound != peerTwincodeOutbound) {
            mHasPrivatePeer = true;
            setPeerTwincodeOutbound(peerTwincodeOutbound);
            return true;
        } else {
            return false;
        }
    }

    public void setGroupInformation(@NonNull UUID groupId, @NonNull UUID groupMemberTwincodeId) {

        mGroupId = groupId;
        mGroupMemberTwincodeId = groupMemberTwincodeId;
    }

    @Override
    public Type getType() {
        return Type.CONTACT;
    }

    @Nullable
    public String getPeerName() {

        return mPeerTwincodeOutbound == null ? null : mPeerTwincodeOutbound.getName();
    }

    public synchronized TwincodeOutbound getIdentityTwincodeOutbound() {

        return mTwincodeOutbound;
    }

    public synchronized TwincodeInbound getTwincodeInbound() {

        return mTwincodeInbound;
    }

    public synchronized void setTwincodeInbound(@Nullable TwincodeInbound twincodeInbound) {

        if (twincodeInbound != null) {
            mTwincodeInbound = twincodeInbound;
            String capabilities = twincodeInbound.getCapabilities();
            if (capabilities != null) {
                mPrivateCapabilities = new Capabilities(capabilities);
            } else {
                mPrivateCapabilities = null;
            }
        } else {
            mTwincodeInbound = null;
            mPrivateCapabilities = null;
        }
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
    public ImageId getAvatarId() {

        return mPeerTwincodeOutbound == null ? null : mPeerTwincodeOutbound.getAvatarId();
    }

    @Override
    public boolean hasPeer() {

        return mPeerTwincodeOutbound != null || mPublicPeerTwincodeOutboundId != null;
    }

    public synchronized boolean hasPrivatePeer() {

        return mPeerTwincodeOutbound != null && mHasPrivatePeer;
    }

    @Nullable
    public synchronized String getIdentityName() {

        return mTwincodeOutbound == null ? null : mTwincodeOutbound.getName();
    }

    @Nullable
    public synchronized ImageId getIdentityAvatarId() {

        return mTwincodeOutbound == null ? null : mTwincodeOutbound.getAvatarId();
    }

    @Nullable
    public UUID getSpaceId() {

        return mSpace == null ? null : mSpace.getId();
    }

    @Override
    public boolean isGroup() {

        return false;
    }

    @Override
    public boolean isSpace(@Nullable Space space) {

        return mSpace == space;
    }

    @Override
    @NonNull
    public Capabilities getCapabilities() {

        return mCapabilities != null ? mCapabilities : new Capabilities();
    }

    @Override
    @NonNull
    public Capabilities getIdentityCapabilities() {

        return mIdentityCapabilities != null ? mIdentityCapabilities : new Capabilities();
    }

    public Capabilities getPrivateCapabilities() {
        return mPrivateCapabilities != null ? mPrivateCapabilities : new Capabilities();
    }

    @Override
    @Nullable
    public String getPeerDescription() {

        return mPeerTwincodeOutbound == null ? null : mPeerTwincodeOutbound.getDescription();
    }

    @Override
    @Nullable
    public String getIdentityDescription() {

        return mTwincodeOutbound == null ? null : mTwincodeOutbound.getDescription();
    }

    @NonNull
    public synchronized CertificationLevel getCertificationLevel() {

        final boolean peerSigned = mPeerTwincodeOutbound != null && mPeerTwincodeOutbound.isSigned();
        final boolean identitySigned = mTwincodeOutbound != null && mTwincodeOutbound.isSigned();
        if (!peerSigned || !identitySigned) {
            return CertificationLevel.LEVEL_0;
        }

        final boolean isTrusted = mCapabilities != null && mCapabilities.isTrusted(mTwincodeOutbound.getId());
        final boolean isPeerTrusted = mIdentityCapabilities != null && mIdentityCapabilities.isTrusted(mPeerTwincodeOutbound.getId()) && mPeerTwincodeOutbound.isTrusted();
        if (!isPeerTrusted) {
            // If the peer twincode is not marked as TRUSTED but was obtained from an invitation code we can almost
            // trust its public key and indicate the Level_3.
            return isTrusted ? CertificationLevel.LEVEL_2 : (mPeerTwincodeOutbound.getTrustMethod() == TrustMethod.INVITATION_CODE ? CertificationLevel.LEVEL_3 : CertificationLevel.LEVEL_1);
        }
        return isTrusted ? CertificationLevel.LEVEL_4 : CertificationLevel.LEVEL_3;
    }

    public boolean isTwinroom() {

        return getCapabilities().getKind() == TwincodeKind.TWINROOM;
    }

    public boolean checkInvariants() {

        //
        // Invariant: Contact <<->> PrivateIdentity
        //

        UUID publicPeerTwincodeOutboundId;
        UUID privatePeerTwincodeOutboundId;
        UUID twincodeInboundId;
        UUID twincodeOutboundId;
        TwincodeOutbound peerTwincodeOutbound;
        synchronized (this) {
            publicPeerTwincodeOutboundId = mPublicPeerTwincodeOutboundId;
            peerTwincodeOutbound = mPeerTwincodeOutbound;
            privatePeerTwincodeOutboundId = mHasPrivatePeer && peerTwincodeOutbound != null ? peerTwincodeOutbound.getId() : null;
            twincodeInboundId = mTwincodeInbound == null ? null : mTwincodeInbound.getId();
            twincodeOutboundId = mTwincodeOutbound == null ? null : mTwincodeOutbound.getId();
        }

        boolean invariant = (twincodeInboundId == null && twincodeOutboundId == null) ||
                (twincodeInboundId != null && twincodeOutboundId != null);
        if (!invariant) {

            return false;
        }

        //
        // Invariant: Contact <<->> PeerTwincodeOutbound
        //

        invariant = (publicPeerTwincodeOutboundId == null && privatePeerTwincodeOutboundId == null && peerTwincodeOutbound == null) ||
                (privatePeerTwincodeOutboundId != null && privatePeerTwincodeOutboundId.equals(peerTwincodeOutbound.getId())) ||
                (publicPeerTwincodeOutboundId != null && privatePeerTwincodeOutboundId == null && peerTwincodeOutbound != null &&
                        publicPeerTwincodeOutboundId.equals(peerTwincodeOutbound.getId()));
        if (!invariant) {

            return false;

        }

        return invariant;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "Contact[" + mDatabaseId +
                " id=" + mId +
                (BuildConfig.ENABLE_DUMP ? " name=" + mName : "") +
                " publicPeerTwincodeOutboundId=" + mPublicPeerTwincodeOutboundId +
                " peerTwincodeOutboundId=" + mPeerTwincodeOutbound +
                " twincodeInbound=" + mTwincodeInbound +
                " twincodeOutbound=" + mTwincodeOutbound +
                " capabilities=" + mCapabilities + "]";
    }
}
