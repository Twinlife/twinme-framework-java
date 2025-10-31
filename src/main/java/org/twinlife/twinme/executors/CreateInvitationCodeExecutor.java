/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.executors;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.InvitationCode;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Invitation;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.0
//

public class CreateInvitationCodeExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "CreateInvitationCodeEx";
    private static final boolean DEBUG = false;

    private static final int CREATE_INVITATION = 1;
    private static final int CREATE_INVITATION_DONE = 1 << 1;
    private static final int CREATE_INVITATION_CODE = 1 << 2;
    private static final int CREATE_INVITATION_CODE_DONE = 1 << 3;
    private static final int UPDATE_INVITATION = 1 << 4;
    private static final int UPDATE_INVITATION_DONE = 1 << 5;

    private final int mValidityPeriod;

    @Nullable
    private InvitationCode mInvitationCode = null;
    @Nullable
    private Invitation mInvitation = null;

    public CreateInvitationCodeExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, int validityPeriod) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "CreateInvitationCodeExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId + " validityPeriod=" + validityPeriod);
        }

        mValidityPeriod = validityPeriod;
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;

            // Restart everything!
            mState = 0;
        }
        super.onTwinlifeOnline();
    }

    //
    // Private methods
    //

    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        if (mStopped) {

            return;
        }

        //
        // Step 1: Create the invitation
        //

        if ((mState & CREATE_INVITATION) == 0) {
            mState |= CREATE_INVITATION;

            long requestId = newOperation(CREATE_INVITATION);

            mTwinmeContext.createInvitation(requestId, null);

            return;
        }

        if ((mState & CREATE_INVITATION_DONE) == 0) {
            return;
        }

        //
        // Step 2: Create the invitation code
        //

        if ((mState & CREATE_INVITATION_CODE) == 0) {
            mState |= CREATE_INVITATION_CODE;

            long requestId = newOperation(CREATE_INVITATION_CODE);
            if (DEBUG) {
                Log.d(LOG_TAG, "CreateInvitation: requestId=" + requestId);
            }

            if (mInvitation == null || mInvitation.getTwincodeOutbound() == null) {
                mTwinmeContextImpl.assertion(ExecutorAssertPoint.CREATE_INVITATION_CODE, null);
                return;
            }

            mTwinmeContextImpl.getTwincodeOutboundService().createInvitationCode(mInvitation.getTwincodeOutbound(), mValidityPeriod, (status, invitationCode) -> {
                if (status != ErrorCode.SUCCESS || invitationCode == null) {
                    onOperationError(CREATE_INVITATION_CODE, status, null);
                    return;
                }

                mState |= CREATE_INVITATION_CODE_DONE;
                mInvitationCode = invitationCode;
                mInvitation.setInvitationCode(mInvitationCode);
                onOperation();
            });
            return;
        }
        if ((mState & CREATE_INVITATION_CODE_DONE) == 0) {
            return;
        }

        //
        // Step 3: Update the invitation to persist the code
        //

        if ((mState & UPDATE_INVITATION) == 0) {
            mState |= UPDATE_INVITATION;

            if (mInvitation == null) {
                mTwinmeContextImpl.assertion(ExecutorAssertPoint.CREATE_INVITATION_CODE, null);
                return;
            }

            mTwinmeContext.updateInvitation(mInvitation, (status, invitation) -> {
                mState |= UPDATE_INVITATION_DONE;
                mInvitation = invitation;
                onOperation();
            });
            return;
        }

        if ((mState & UPDATE_INVITATION_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //
        if (mInvitation != null) {
            mTwinmeContextImpl.onCreateInvitationWithCode(mRequestId, mInvitation);
        } else {
            Log.e(LOG_TAG, "UPDATE_INVITATION_DONE but mInvitation is null");
        }

        stop();
    }

    @Override
    public void onCreateInvitation(long requestId, @NonNull Invitation invitation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateInvitation: requestId=" + requestId + " invitation=" + invitation);
        }

        if (getOperation(requestId) > 0) {
            mState |= CREATE_INVITATION_DONE;
            mInvitation = invitation;

            onOperation();
        }
    }
}
