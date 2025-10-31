/*
 *  Copyright (c) 2015-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;

import java.util.UUID;

//
// Synchronization based on copy-on-write pattern
//
// version: 1.2
//

public class PairBindInvocation extends Invocation {

    @NonNull
    private final TwincodeOutbound mTwincodeOutbound;

    public PairBindInvocation(@NonNull UUID invocationId, @NonNull RepositoryObject receiver, @NonNull TwincodeOutbound twincodeOutbound) {

        super(invocationId, receiver, true);

        mTwincodeOutbound = twincodeOutbound;
    }

    public TwincodeOutbound getTwincodeOutbound() {

        return mTwincodeOutbound;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "PairBindInvocation:\n" +
                " id=" + getId() + "\n" +
                " receiver=" + getReceiver() + "\n" +
                " twincodeOutbound=" + mTwincodeOutbound + "\n";
    }
}
