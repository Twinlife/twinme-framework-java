/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.RepositoryObjectFactory;

import java.util.UUID;

/**
 * Factory used by the RepositoryService to create Twinme objects.
 */
public class TwinmeObjectFactory {

    private final UUID mSchemaId;
    private final int mSchemaVersion;
    private final int mTwincodeUsage;
    private final RepositoryObjectFactory<?> mOwnerFactory;

    TwinmeObjectFactory(@NonNull UUID schemaId, int schemaVersion, int twincodeUsage,
                        @Nullable RepositoryObjectFactory<?> ownerFactory) {

        mSchemaId = schemaId;
        mSchemaVersion = schemaVersion;
        mTwincodeUsage = twincodeUsage;
        mOwnerFactory = ownerFactory;
    }

    @NonNull
    public UUID getSchemaId() {

        return mSchemaId;
    }

    public int getSchemaVersion() {

        return mSchemaVersion;
    }

    public int getTwincodeUsage() {

        return mTwincodeUsage;
    }

    public boolean isLocal() {

        return true;
    }

    public boolean isImmutable() {

        return false;
    }

    @Nullable
    public RepositoryObjectFactory<?> getOwnerFactory() {

        return mOwnerFactory;
    }

    @Override
    @NonNull
    public String toString() {

        return "F:" + getClass().getSimpleName();
    }
}
