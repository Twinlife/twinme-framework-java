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

import java.util.UUID;

//
// Synchronization based on copy-on-write pattern
//
// version: 1.2
//

public class Invocation {

    @NonNull
    private final UUID mId;
    @NonNull
    private final RepositoryObject mReceiver;
    private final boolean mBackground;

    Invocation(@NonNull UUID invocationId, @NonNull RepositoryObject receiver, boolean background) {

        mId = invocationId;
        mReceiver = receiver;
        mBackground = background;
    }

    @NonNull
    public UUID getId() {

        return mId;
    }

    @NonNull
    public RepositoryObject getReceiver() {

        return mReceiver;
    }

    public boolean getBackground() {

        return mBackground;
    }
}
