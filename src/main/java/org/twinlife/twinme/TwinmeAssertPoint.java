/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinme;

import org.twinlife.twinlife.AssertPoint;

public enum TwinmeAssertPoint implements AssertPoint {
    PROCESS_INVOCATION,
    INCOMING_PEER_CONNECTION,
    ON_POP_DESCRIPTOR,
    ON_UPDATE_DESCRIPTOR,
    ON_UPDATE_ANNOTATION,
    CREATE_CONTACT1_NAME,
    CREATE_CONTACT2_NAME,
    CREATE_PROFILE_NAME,
    CREATE_PROFILE,

    CREATE_SPACE;

    public int getIdentifier() {

        return this.ordinal() + BASE_VALUE;
    }

    private static final int BASE_VALUE = 3000;
}