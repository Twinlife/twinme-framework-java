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
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.RepositoryObject;

import java.util.List;
import java.util.UUID;

//
// Synchronization based on copy-on-write pattern
//
// version: 1.3
//

public class PairRefreshInvocation extends Invocation {

    @Nullable
    public final List<BaseService.AttributeNameValue> invocationAttributes;

    public PairRefreshInvocation(@NonNull UUID invocationId, @NonNull RepositoryObject receiver,
                                 @Nullable List<BaseService.AttributeNameValue> invocationAttributes) {

        super(invocationId, receiver, true);

        this.invocationAttributes = invocationAttributes;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "PairRefreshInvocation:" + "\n" +
                " id=" + getId() + "\n" +
                " receiver=" + getReceiver() + "\n";
    }
}
