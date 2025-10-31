/*
 *  Copyright (c) 2018-2023 twinlife SA.
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
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Group member or Twinroom member representation for the UI.
 *
 * getName           -> name of the member
 * getAvatar         -> picture of the member
 * getIdentityName   -> name of the member
 * getIdentityAvatar -> picture of the member
 *
 * The group member information is initialized from the group member twincode passed in the constructor.
 * The GroupMember instance is not stored in the repository but it is cached in the Twinme context.
 */

public class GroupMember implements Originator {
    public static final UUID SCHEMA_ID = UUID.fromString("1f3b4ea2-0863-4eec-885e-b9d17efd84b7");

    @Nullable
    private String mMemberName;
    // The current member twincode outbound id.
    //@Nullable
    //private final UUID mMemberTwincodeOutboundId;
    @Nullable
    private final UUID mInvitedByMemberTwincodeOutboundId;
    @NonNull
    private final Originator mOwner;
    @NonNull
    private final TwincodeOutbound mPeerTwincodeOutbound;
    @Nullable
    private ImageId mMemberAvatarId;

    public GroupMember(@NonNull Originator owner, @NonNull TwincodeOutbound twincodeOutbound) {

        mPeerTwincodeOutbound = twincodeOutbound;
        mOwner = owner;
        // mMemberTwincodeOutboundId = twincodeOutbound.getId();
        mMemberName = twincodeOutbound.getName();
        mMemberAvatarId = twincodeOutbound.getAvatarId();
        mInvitedByMemberTwincodeOutboundId = TwinmeAttributes.getInvitedBy(twincodeOutbound);
    }

    @Override
    @NonNull
    public UUID getId() {

        return mPeerTwincodeOutbound.getId();
    }

    @NonNull
    @Override
    public synchronized String getName() {

        return mPeerTwincodeOutbound.getName();
    }

    @Override
    @Nullable
    public ImageId getAvatarId() {

        return mPeerTwincodeOutbound.getAvatarId();
    }

    @Override
    public boolean hasPeer() {

        return mPeerTwincodeOutbound != null;
    }

    @Override
    @Nullable
    public String getIdentityName() {

        return mOwner.getIdentityName();
    }

    @Nullable
    @Override
    public String getIdentityDescription() {
        return mOwner.getIdentityDescription();
    }

    @Override
    @Nullable
    public ImageId getIdentityAvatarId() {

        return mOwner.getIdentityAvatarId();
    }

    @Override
    @Nullable
    public TwincodeInbound getTwincodeInbound() {

        return mOwner.getTwincodeInbound();
    }

    @Override
    @Nullable
    public UUID getTwincodeInboundId() {

        return mOwner.getTwincodeInboundId();
    }

    @Override
    @Nullable
    public UUID getTwincodeOutboundId() {

        return mOwner.getTwincodeOutboundId();
    }

    @Override
    public TwincodeOutbound getTwincodeOutbound() {

        return mOwner.getTwincodeOutbound();
    }

    @Override
    @Nullable
    public UUID getPeerTwincodeOutboundId() {

        return mPeerTwincodeOutbound.getId();
    }

    @Override
    @Nullable
    public TwincodeOutbound getPeerTwincodeOutbound() {

        return mPeerTwincodeOutbound;
    }

    @Override
    public Type getType() {
        return Type.GROUP_MEMBER;
    }

    @NonNull
    public Originator getGroup() {

        return mOwner;
    }

    @Override
    public double getUsageScore() {

        return mOwner.getUsageScore();
    }

    @Override
    public long getLastMessageDate() {

        return mOwner.getLastMessageDate();
    }

    public UUID getInvitedByMemberTwincodeOutboundId() {

        return mInvitedByMemberTwincodeOutboundId;
    }

    @Override
    public boolean isGroup() {

        return mOwner.isGroup();
    }

    @Override
    @Nullable
    public String getPeerDescription() {

        return mOwner.getPeerDescription();
    }

    @Override
    @NonNull
    public Capabilities getCapabilities() {

        return mOwner.getCapabilities();
    }

    public synchronized void setMemberTwincodeOutbound(@Nullable TwincodeOutbound twincodeOutbound) {

        if (twincodeOutbound != null) {
            mMemberName = twincodeOutbound.getName();
            mMemberAvatarId = twincodeOutbound.getAvatarId();
        } else {
            mMemberName = null;
            mMemberAvatarId = null;
        }
    }

    @Nullable
    @Override
    public Space getSpace() {

        return mOwner.getSpace();
    }

    @Override
    public boolean isSpace(@Nullable Space space) {

        return mOwner.isSpace(space);
    }

    @NonNull
    public DatabaseIdentifier getDatabaseId() {

        return mOwner.getDatabaseId();
    }

    @Override
    public boolean isValid() {

        return mOwner.isValid();
    }

    @Override
    public boolean canCreateP2P() {

        return true;
    }

    public boolean canAcceptP2P(@Nullable UUID twincodeId) {

        // We don't look at the peer twincode Id to accept the incoming P2P.
        return true;
    }

    @Override
    public long getModificationDate() {

        return mOwner.getModificationDate();
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

        return "GroupMember[" +
                (BuildConfig.ENABLE_DUMP ? " name=" + mMemberName : "") +
                " peerTwincodeOutbound=" + mPeerTwincodeOutbound + "]";
    }
}
