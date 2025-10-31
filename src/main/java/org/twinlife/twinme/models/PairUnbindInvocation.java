/*
 *  Copyright (c) 2015-2017 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.RepositoryObject;

import java.util.UUID;

//
// Synchronization based on copy-on-write pattern
//
// version: 1.2
//

public class PairUnbindInvocation extends Invocation {

    public PairUnbindInvocation(@NonNull UUID invocationId, @NonNull RepositoryObject receiver) {

        super(invocationId, receiver, true);
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "PairUnbindInvocation:\n" +
                " id=" + getId() + "\n" +
                " receiver=" + getReceiver() + "\n";
    }
}
