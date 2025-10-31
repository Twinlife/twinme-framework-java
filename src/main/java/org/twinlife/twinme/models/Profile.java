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

import org.twinlife.twinlife.BaseService.AttributeNameLongValue;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeInbound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// version: 3.4
//

/*
 * <pre>
 *
 * Invariant: Profile <<->> PublicIdentity
 *
 *  profile.publicIdentityId == null
 *  and profile.publicIdentity == null
 *   or
 *  profile.publicIdentityId != null
 *  and profile.publicIdentity != null
 *  and profile.publicIdentityId == publicIdentity.id
 * </pre>
 */

public class Profile extends TwinmeRepositoryObject {

    public static final UUID SCHEMA_ID = UUID.fromString("cfde3269-ce0f-4a8e-976c-4a9e504ff515");
    public static final int SCHEMA_VERSION = 3;

    public enum UpdateMode {
        // Do not update contact's identity when profile is changed
        NONE,

        // Update contact's identity that are synchronized with the profile.
        DEFAULT,

        // Update every contact's identity of the space associated with the profile.
        ALL
    }

    private long mPriority;
    @Nullable
    private Space mSpace;
    @Nullable
    private Capabilities mIdentityCapabilities; // What we grant to the peer when a contact is created.

    Profile(@NonNull DatabaseIdentifier identifier, @NonNull UUID id, long creationDate, @Nullable String name,
            @Nullable String description, @Nullable List<AttributeNameValue> attributes,
            long modificationDate) {
        super(identifier, id, creationDate, modificationDate);

        update(name, description, attributes, modificationDate);
    }

    synchronized void update(@Nullable String name, @Nullable String description, @Nullable List<AttributeNameValue> attributes,
                             long modificationDate) {

        mName = name;
        mDescription = description;
        mModificationDate = modificationDate;
        if (attributes != null) {
            for (AttributeNameValue attribute : attributes) {
                if (attribute.name.equals("priority")) {
                    if (attribute instanceof AttributeNameLongValue) {
                        mPriority = (Long) attribute.value;
                    }
                }
            }
        }
    }

    //
    // Implement RepositoryObject interface
    //

    @Override
    public synchronized void setTwincodeOutbound(@Nullable TwincodeOutbound identityTwincodeOutbound) {

        super.setTwincodeOutbound(identityTwincodeOutbound);
        if (identityTwincodeOutbound != null) {
            mName = identityTwincodeOutbound.getName();
            mDescription = identityTwincodeOutbound.getDescription();
            String capabilities = identityTwincodeOutbound.getCapabilities();
            if (capabilities != null) {
                mIdentityCapabilities = new Capabilities(capabilities);
            } else {
                mIdentityCapabilities = null;
            }
        } else {
            mIdentityCapabilities = null;
            mName = null;
            mDescription = null;
        }
    }

    @NonNull
    @Override
    public List<AttributeNameValue> getAttributes(boolean exportAll) {

        long priority;
        String name, description;
        TwincodeOutbound twincodeOutbound;
        TwincodeInbound twincodeInbound;
        Space space;
        synchronized (this) {
            priority = mPriority;
            name = mName;
            description = mDescription;
            twincodeOutbound = mTwincodeOutbound;
            twincodeInbound = mTwincodeInbound;
            space = mSpace;
        }

        List<AttributeNameValue> attributes = new ArrayList<>();
        if (exportAll) {
            exportAttributes(attributes, name, description, space, twincodeInbound, twincodeOutbound);
        }
        attributes.add(new AttributeNameLongValue("priority", priority));

        return attributes;
    }

    @Override
    public boolean isValid() {

        // The profile is valid if we have a twincode inbound and twincode outbound.
        // The profile will be deleted when this becomes invalid.
        return mTwincodeInbound != null && mTwincodeOutbound != null;
    }

    @Override
    public boolean canCreateP2P() {

        return false;
    }

    @Override
    @Nullable
    public RepositoryObject getOwner() {

        // The profile owner is the Space instance!
        return mSpace;
    }

    @Override
    public void setOwner(@Nullable RepositoryObject owner) {

        // Called when an object is loaded from the database and linked to its owner.
        // Take the opportunity to link back the Space to its profile if there is a match.
        if (owner instanceof Space) {
            Space space = (Space) owner;
            mSpace = space;
            if (mId.equals(space.getProfileId())) {
                space.setProfile(this);
            }
        }
    }

    //
    // Profile specific methods.
    //

    public long getPriority() {

        return mPriority;
    }

    public void setSpace(@NonNull Space space) {

        mSpace = space;
    }

    @Nullable
    public Space getSpace() {

        return mSpace;
    }

    @NonNull
    public Capabilities getIdentitiyCapabilities() {

        return mIdentityCapabilities != null ? mIdentityCapabilities : new Capabilities();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean checkInvariants() {

        //
        // Invariant: Profile <<->> PublicIdentity
        //

        UUID twincodeInboundId;
        TwincodeOutbound twincodeOutbound;
        synchronized (this) {
            twincodeInboundId = mTwincodeInbound == null ? null : mTwincodeInbound.getId();
            twincodeOutbound = mTwincodeOutbound;
        }

        boolean invariant = (twincodeInboundId == null && twincodeOutbound == null) ||
                (twincodeInboundId != null && twincodeOutbound != null);
        if (!invariant) {

            return false;
        }

        return invariant;
    }

    @Override
    @NonNull
    public String toString() {

        return "Profile[" + mDatabaseId +
                " id=" + mId +
                " twincodeInbound=" + mTwincodeInbound +
                " twincodeOutbound=" + mTwincodeOutbound +
                " priority=" + mPriority +
                (BuildConfig.ENABLE_DUMP ? " name=" + mName : "") + "]";
    }
}
