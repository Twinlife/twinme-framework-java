/*
 *  Copyright (c) 2019-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;

import java.util.UUID;

/**
 * Group registered invocation that is received as a result of a group subscription.
 */
public class GroupRegisteredInvocation extends Invocation {

    @Nullable
    private final TwincodeOutbound mAdminTwincodeOutbound;
    private final long mMemberPermissions;
    private final long mAdminPermissions;

    public GroupRegisteredInvocation(@NonNull UUID invocationId, @NonNull RepositoryObject receiver,
                                     @Nullable TwincodeOutbound adminTwincodeOutbound,
                                     long adminPermissions, long memberPermissions) {

        super(invocationId, receiver, true);

        mAdminTwincodeOutbound = adminTwincodeOutbound;
        mMemberPermissions = memberPermissions;
        mAdminPermissions = adminPermissions;
    }

    @Nullable
    public TwincodeOutbound getAdminTwincodeOutbound() {

        return mAdminTwincodeOutbound;
    }

    public long getAdminPermissions() {

        return mAdminPermissions;
    }

    public long getPermissions() {

        return mMemberPermissions;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "GroupRegisteredInvocation:\n" +
                " id=" + getId() + "\n" +
                " receiver=" + getReceiver() + "\n" +
                " adminTwincodeOutbound=" + mAdminTwincodeOutbound + "\n" +
                " adminPermissions=" + mAdminPermissions + "\n" +
                " memberPermissions=" + mMemberPermissions + "\n";
    }
}
