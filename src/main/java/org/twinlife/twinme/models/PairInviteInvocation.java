/*
 *  Copyright (c) 2015-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;

import java.util.UUID;

//
// Synchronization based on copy-on-write pattern
//
// version: 1.3
//

public class PairInviteInvocation extends Invocation {

    @NonNull
    private final TwincodeOutbound mTwincodeOutbound;

    public PairInviteInvocation(@NonNull UUID invocationId,
                                @NonNull RepositoryObject receiver, @NonNull TwincodeOutbound twincodeOutbound) {

        super(invocationId, receiver, true);

        mTwincodeOutbound = twincodeOutbound;
    }

    @NonNull
    public TwincodeOutbound getTwincodeOutbound() {

        return mTwincodeOutbound;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "PairInviteInvocation:\n" +
                " id=" + getId() + "\n" +
                " receiver=" + getReceiver() + "\n" +
                " twincodeOutbound=" + mTwincodeOutbound + "\n";
    }
}
