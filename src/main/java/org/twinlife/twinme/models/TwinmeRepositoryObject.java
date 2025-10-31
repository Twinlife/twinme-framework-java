/*
 *  Copyright (c) 2015-2023 twinlife SA.
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
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeInbound;

import java.util.List;
import java.util.UUID;

public abstract class TwinmeRepositoryObject implements RepositoryObject {
    @NonNull
    protected final DatabaseIdentifier mDatabaseId;
    @NonNull
    protected final UUID mId;
    protected final long mCreationDate;
    protected long mModificationDate;
    @Nullable
    protected String mName;
    @Nullable
    protected TwincodeOutbound mTwincodeOutbound;
    @Nullable
    protected TwincodeInbound mTwincodeInbound;
    @Nullable
    protected String mDescription;

    TwinmeRepositoryObject(@NonNull DatabaseIdentifier identifier, @NonNull UUID id, long creationDate, long modificationDate) {

        mDatabaseId = identifier;
        mId = id;
        mCreationDate = creationDate;
        mModificationDate = modificationDate;
    }

    @NonNull
    public final UUID getSchemaId() {

        return mDatabaseId.getSchemaId();
    }

    @Override
    @NonNull
    public final DatabaseIdentifier getDatabaseId() {

        return mDatabaseId;
    }

    @Override
    @NonNull
    public final UUID getId() {

        return mId;
    }

    @Override
    public boolean isValid() {

        // By default, every object is valid.
        return true;
    }

    @NonNull
    public String getName() {

        return mName == null ? "" : mName;
    }

    public void setName(@Nullable String name) {

        mName = name;
    }

    @Nullable
    public ImageId getAvatarId() {

        return mTwincodeOutbound == null ? null : mTwincodeOutbound.getAvatarId();
    }

    @Override
    public synchronized void setTwincodeInbound(@Nullable TwincodeInbound identityTwincodeInbound) {

        mTwincodeInbound = identityTwincodeInbound;
    }

    @Override
    public synchronized void setTwincodeOutbound(@Nullable TwincodeOutbound identityTwincodeOutbound) {

        mTwincodeOutbound = identityTwincodeOutbound;
    }

    @Override
    public synchronized void setPeerTwincodeOutbound(@Nullable TwincodeOutbound peerTwincodeOutbound) {

        // No peer twincode outbound by default.
    }

    public synchronized void setTwincodeFactory(@NonNull TwincodeFactory twincodeFactory) {

        mTwincodeInbound = twincodeFactory.getTwincodeInbound();
        setTwincodeOutbound(twincodeFactory.getTwincodeOutbound());
    }

    @Override
    @Nullable
    public synchronized TwincodeOutbound getTwincodeOutbound() {

        return mTwincodeOutbound;
    }

    @Override
    @Nullable
    public synchronized TwincodeInbound getTwincodeInbound() {

        return mTwincodeInbound;
    }

    @Override
    @Nullable
    public TwincodeOutbound getPeerTwincodeOutbound() {

        // No peer twincode by default.
        return null;
    }

    @Nullable
    public synchronized UUID getTwincodeInboundId() {

        return mTwincodeInbound == null ? null : mTwincodeInbound.getId();
    }

    @Nullable
    public synchronized UUID getTwincodeOutboundId() {

        return mTwincodeOutbound == null ? null : mTwincodeOutbound.getId();
    }

    @Nullable
    public synchronized UUID getTwincodeFactoryId() {

        return mTwincodeInbound == null ? null : mTwincodeInbound.getTwincodeFactoryId();
    }

    public final long getCreationDate() {

        return mCreationDate;
    }

    public final long getModificationDate() {

        return mModificationDate;
    }

    public void setDescription(@Nullable String description) {

        mDescription = description;
    }

    @NonNull
    @Override
    public String getDescription() {

        return mDescription;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TwinmeRepositoryObject that = (TwinmeRepositoryObject) o;
        return mDatabaseId.equals(that.getDatabaseId());
    }

    @Override
    public int hashCode() {
        return mDatabaseId.hashCode();
    }

    protected void exportAttributes(@NonNull List<BaseService.AttributeNameValue> attributes,
                                    @Nullable String name, @Nullable String description, @Nullable Space space,
                                    @Nullable TwincodeInbound twincodeInbound, @Nullable TwincodeOutbound twincodeOutbound) {
        if (space != null) {
            attributes.add(new BaseService.AttributeNameStringValue("spaceId", space.getId().toString()));
        }
        if (name != null) {
            attributes.add(new BaseService.AttributeNameStringValue("name", name));
        }
        if (description != null) {
            attributes.add(new BaseService.AttributeNameStringValue("description", description));
        }
        if (twincodeInbound != null) {
            attributes.add(new BaseService.AttributeNameStringValue("twincodeInboundId", twincodeInbound.getId().toString()));
            attributes.add(new BaseService.AttributeNameStringValue("twincodeFactoryId", twincodeInbound.getTwincodeFactoryId().toString()));
        }
        if (twincodeOutbound != null) {
            attributes.add(new BaseService.AttributeNameStringValue("twincodeOutboundId", twincodeOutbound.getId().toString()));
        }
    }
}
