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

import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;

import java.util.UUID;

/**
 * Group subscribe invocation to ask to join a group.
 */
public class GroupSubscribeInvocation extends Invocation {

    @NonNull
    private final TwincodeOutbound mMemberTwincodeOutbound;

    public GroupSubscribeInvocation(@NonNull UUID invocationId, @NonNull RepositoryObject receiver,
                                    @NonNull TwincodeOutbound memberTwincodeOutbound) {

        super(invocationId, receiver, true);

        mMemberTwincodeOutbound = memberTwincodeOutbound;
    }

    @NonNull
    public TwincodeOutbound getTwincodeOutbound() {

        return mMemberTwincodeOutbound;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "GroupSubscribeInvocation:\n" +
                " id=" + getId() + "\n" +
                " receiver=" + getReceiver() + "\n" +
                " mMemberTwincodeOutbound=" + mMemberTwincodeOutbound + "\n";
    }
}
