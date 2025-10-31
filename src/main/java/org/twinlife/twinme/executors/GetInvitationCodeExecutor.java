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
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContextImpl;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.0
//

public class GetInvitationCodeExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "GetInvitationCodeEx";
    private static final boolean DEBUG = false;

    private static final int GET_INVITATION_CODE = 1;
    private static final int GET_INVITATION_CODE_DONE = 1 << 1;

    @NonNull
    private final String mCode;

    @Nullable
    private TwincodeOutbound mTwincodeOutbound = null;

    @Nullable
    private String mPublicKey = null;

    public GetInvitationCodeExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull String code) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "GetInvitationCodeExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId + " code=" + code);
        }

        mCode = code;
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
        // Step 1: Get the invitation code
        //

        if ((mState & GET_INVITATION_CODE) == 0) {
            mState |= GET_INVITATION_CODE;

            long requestId = newOperation(GET_INVITATION_CODE);
            if (DEBUG) {
                Log.d(LOG_TAG, "GetInvitationCode: requestId=" + requestId);
            }
            mTwinmeContextImpl.getTwincodeOutboundService().getInvitationCode(mCode, (status, invitationInfo) -> {
                mState |= GET_INVITATION_CODE_DONE;

                if (status != ErrorCode.SUCCESS || invitationInfo == null) {
                    onOperationError(GET_INVITATION_CODE, status, null);
                    return;
                }

                mTwincodeOutbound = invitationInfo.first;
                mPublicKey = invitationInfo.second;

                onOperation();
            });
            return;
        }
        if ((mState & GET_INVITATION_CODE_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //
        if (mTwincodeOutbound != null) {
            mTwinmeContextImpl.onGetInvitationCode(mRequestId, mTwincodeOutbound, mPublicKey);
        } else {
            Log.e(LOG_TAG, "GET_INVITATION_CODE_DONE but mTwincodeOutbound is null");
        }

        stop();
    }


    protected void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        mStopped = true;

        super.stop();
    }
}
