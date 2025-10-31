/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinme.executors;

import org.twinlife.twinlife.AssertPoint;

public enum ExecutorAssertPoint implements AssertPoint {
    PARAMETER,
    NULL_SUBJECT,
    NULL_OBJECT,
    NULL_RESULT,
    CONTACT_INVARIANT,
    INVALID_SUBJECT,
    INVALID_TWINCODE,
    INVALID_FACTORY_ID,

    BIND_ACCOUNT_MIGRATION,
    CREATE_CONTACT1_NAME,
    CREATE_CONTACT2_NAME,
    CREATE_PROFILE_NAME,
    CREATE_PROFILE,

    CREATE_SPACE,
    PROCESS_INVOCATION,
    CREATE_INVITATION_CODE,
    PROCESS_PUSH_OBJECT_IQ,
    PROCESS_PUSH_FILE_IQ,
    PROCESS_UPDATE_DESCRIPTOR_IQ,
    SEND_ERROR_IQ,
    ON_DATA_CHANNEL_IQ;

    public int getIdentifier() {

        return this.ordinal() + BASE_VALUE;
    }

    private static final int BASE_VALUE = 2000;
}