/*
 *  Copyright (c) 2020-2024 twinlife SA.
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
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Account migration.
 *
 * The account migration is a specific object used for the migration between two devices.
 *
 */
public class AccountMigration extends TwinmeRepositoryObject {

    public static final UUID SCHEMA_ID = UUID.fromString("86A86B53-0E2C-4BA2-AD74-DDFB3F6FBB2C");
    public static final int SCHEMA_VERSION = 1;

    private boolean mIsBound;
    private TwincodeOutbound mPeerTwincodeOutbound;

    AccountMigration(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                     long creationDate, @Nullable String name, @Nullable String description,
                     @Nullable List<BaseService.AttributeNameValue> attributes,
                     long modificationDate) {
        super(identifier, uuid, creationDate, modificationDate);

        mName = name;
        mIsBound = false;
        update(name, description, attributes, modificationDate);
    }

    synchronized void update(@Nullable String name, @Nullable String description, @Nullable List<AttributeNameValue> attributes,
                             long modificationDate) {

        mName = name;
        mDescription = description;
        mModificationDate = modificationDate;
        if (attributes != null) {
            for (AttributeNameValue attribute : attributes) {
                if ("isBound".equals(attribute.name)) {
                    if (attribute instanceof BaseService.AttributeNameBooleanValue) {
                        mIsBound = (Boolean)((BaseService.AttributeNameBooleanValue) attribute).value;
                    }
                }
            }
        }
    }

    //
    // Implement RepositoryObject interface
    //
    @Override
    public boolean isValid() {

        // The account migration is valid if it has its connection twincode.
        return mTwincodeInbound != null && mTwincodeOutbound != null;
    }

    @NonNull
    public List<AttributeNameValue> getAttributes(boolean exportAll) {

        TwincodeInbound twincodeInbound;
        TwincodeOutbound twincodeOutbound;
        TwincodeOutbound peerTwincodeOutbound;
        boolean isBound;

        synchronized (this) {
            twincodeInbound = mTwincodeInbound;
            twincodeOutbound = mTwincodeOutbound;
            peerTwincodeOutbound = mTwincodeOutbound;
            isBound = isBound();
        }

        final List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
        if (exportAll) {
            exportAttributes(attributes, null, null, null, twincodeInbound, twincodeOutbound);
            if (peerTwincodeOutbound != null) {
                attributes.add(new BaseService.AttributeNameStringValue("peerTwincodeOutboundId", peerTwincodeOutbound.getId().toString()));
            }
        }
        attributes.add(new BaseService.AttributeNameBooleanValue("isBound", isBound));

        return attributes;
    }

    //
    // AccountMigration specific methods.
    //

    public boolean canCreateP2P() {

        return true;
    }

    public boolean isBound() {

        return mIsBound;
    }

    @Nullable
    public synchronized UUID getPeerTwincodeOutboundId() {

        return mPeerTwincodeOutbound == null ? null : mPeerTwincodeOutbound.getId();
    }

    @Override
    @Nullable
    public TwincodeOutbound getPeerTwincodeOutbound() {

        return mPeerTwincodeOutbound;
    }

    @Override
    public synchronized void setPeerTwincodeOutbound(@Nullable TwincodeOutbound twincodeOutbound) {

        mPeerTwincodeOutbound = twincodeOutbound;
    }

    public synchronized void setBound(boolean isBound) {

        mIsBound = isBound;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "AccountMigration[" +
                " id=" + mDatabaseId +
                " isBound=" + mIsBound +
                " twincodeOutbound=" + mTwincodeOutbound +
                " peerTwincodeOutboundId=" + mPeerTwincodeOutbound + "]";
    }
}
