/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <p>
 * A Call Receiver is a special kind of invitation, which allows the device to receive
 * an audio/video call from a guest user through their browser.
 *
 * <p>
 * The call receiver has a specific twincode (different from the Profile twincode) that is sent by some mechanism to the guest user.
 * <p>
 * Unlike sharing the Profile twincode, the call receiver can be withdrawn.
 * <p>
 * Call receivers may have a custom avatar and name, otherwise the current profile's identity is used.
 * In both cases, the avatar and name are stored in the call receiver twincode outbound id.
 * This is the information that the guest user will see.
 * <p>
 */
public class CallReceiver extends TwinmeRepositoryObject implements Originator, Comparable<CallReceiver> {

    public static final UUID SCHEMA_ID = UUID.fromString("3b74a66c-db31-4c93-b0ac-f2c08ff3cf31");
    public static final int SCHEMA_VERSION = 1;

    public static final UUID DUMMY_PEER_TWINCODE_OUTBOUND_ID = new UUID(0L, 0L);

    @Nullable
    private Space mSpace;

    CallReceiver(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                 long creationDate, @Nullable String name, @Nullable String description,
                 @Nullable List<BaseService.AttributeNameValue> attributes,
                 long modificationDate) {
        super(identifier, uuid, creationDate, modificationDate);

        mName = name;
        update(name, description, attributes, modificationDate);
    }

    void update(@Nullable String name, @Nullable String description, @Nullable List<BaseService.AttributeNameValue> attributes,
                long modificationDate) {

        mName = name;
        mDescription = description;
        mModificationDate = modificationDate;
    }

    //
    // Implement RepositoryObject interface
    //

    @NonNull
    public List<BaseService.AttributeNameValue> getAttributes(boolean exportAll) {

        TwincodeOutbound twincodeOutbound;
        TwincodeInbound twincodeInbound;
        Space space;
        String name;
        String description;

        synchronized (this) {
            name = mName;
            description = mDescription;
            space = mSpace;
            twincodeInbound = mTwincodeInbound;
            twincodeOutbound = mTwincodeOutbound;
        }

        final List<BaseService.AttributeNameValue> attributes = new ArrayList<>();

        if (exportAll) {
            exportAttributes(attributes, name, description, space, twincodeInbound, twincodeOutbound);
        }

        return attributes;
    }

    @Override
    public boolean isValid() {

        // The CallReceiver is valid if we have an identity twincode (inbound and outbound) and it has an associated space.
        // The CallReceiver will be deleted when this becomes invalid.
        return mTwincodeInbound != null && mTwincodeOutbound != null && mSpace != null;
    }

    @Override
    public boolean canCreateP2P() {

        return false;
    }

    @Override
    public boolean canAcceptP2P(@Nullable UUID twincodeId) {

        // For the click-to-call, we accept every peer.
        return true;
    }

    @Override
    @Nullable
    public RepositoryObject getOwner() {

        // The call receiver owner is the Space instance!
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
    // CallReceiver specific methods.
    //

    @Nullable
    @Override
    public String getPeerDescription() {
        return null;
    }

    @Nullable
    @Override
    public String getIdentityName() {
        return mTwincodeOutbound == null ? null : mTwincodeOutbound.getName();
    }

    @Nullable
    @Override
    public String getIdentityDescription() {
        return mTwincodeOutbound == null ? null : mTwincodeOutbound.getDescription();
    }

    @Nullable
    @Override
    public ImageId getIdentityAvatarId() {
        return getAvatarId();
    }

    @Nullable
    public Space getSpace() {

        return mSpace;
    }

    @Override
    public double getUsageScore() {
        return 0;
    }

    @Override
    public long getLastMessageDate() {
        return 0;
    }

    @Override
    public boolean isSpace(@Nullable Space space) {
        return mSpace != null && mSpace.equals(space);
    }

    /**
     * @return true if this Call Receiver accepts group calls (i.e. multiple participants using the same CallReceiver).
     */
    @Override
    public boolean isGroup() {
        return getCapabilities().hasGroupCall();
    }

    @Override
    public boolean hasPeer() {
        return true;
    }

    @Override
    public boolean hasPrivatePeer() {
        return true;
    }

    @NonNull
    @Override
    public Capabilities getCapabilities() {
        String capabilities = mTwincodeOutbound == null ? null : mTwincodeOutbound.getCapabilities();
        Capabilities res = capabilities == null ? new Capabilities() : new Capabilities(capabilities);
        res.setKind(TwincodeKind.CALL_RECEIVER);
        return res;
    }

    @Nullable
    @Override
    public UUID getPeerTwincodeOutboundId() {
        return DUMMY_PEER_TWINCODE_OUTBOUND_ID;
    }

    @Override
    public Type getType() {
        return Type.CALL_RECEIVER;
    }

    public synchronized void setSpace(@NonNull Space space) {

        mSpace = space;
    }

    public boolean isTransfer(){
        return getCapabilities().hasTransfer();
    }

    //
    // Override Object methods
    //

    @Override
    public int compareTo(CallReceiver o) {
        return Long.compare(mModificationDate, o.mModificationDate);
    }

    @Override
    @NonNull
    public String toString() {

        return "CallReceiver[" +
                " id=" + mDatabaseId +
                (BuildConfig.ENABLE_DUMP ? " name=" + mName : "") +
                " space=" + mSpace +
                " twincodeOutboundId=" + mTwincodeOutbound + "]";
    }
}
