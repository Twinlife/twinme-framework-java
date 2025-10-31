/*
 *  Copyright (c) 2019-2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupRegisteredInvocation;

import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.1
//

/**
 * Executor to handle the last group registration phase on the subscriber side.
 */
public class GroupRegisteredExecutor extends AbstractTwinmeExecutor {
    private static final String LOG_TAG = "GroupRegisteredExecutor";
    private static final boolean DEBUG = false;

    private static final int SUBSCRIBE_MEMBER = 1;

    @NonNull
    private final UUID mInvocationId;
    @Nullable
    private final TwincodeOutbound mAdminTwincode;
    @NonNull
    private final Group mGroup;
    private final long mPermissions;
    private final long mAdminPermissions;

    public GroupRegisteredExecutor(@NonNull TwinmeContextImpl twinmeContextImpl,
                                   @NonNull GroupRegisteredInvocation invocation, @NonNull Group group) {
        super(twinmeContextImpl, BaseService.DEFAULT_REQUEST_ID, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "GroupRegisteredExecutor: twinmeContextImpl=" + twinmeContextImpl
                    + " invocation=" + invocation + " group=" + group);
        }

        mInvocationId = invocation.getId();
        mGroup = group;
        mAdminTwincode = invocation.getAdminTwincodeOutbound();
        mAdminPermissions = invocation.getAdminPermissions();
        mPermissions = invocation.getPermissions();
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;
        }
        onOperation();
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
        // Step 1: get the Group object.
        //

        if (mAdminTwincode != null && mGroup.getGroupTwincodeOutboundId() != null) {
            if ((mState & SUBSCRIBE_MEMBER) == 0) {
                mState |= SUBSCRIBE_MEMBER;

                long requestId = newOperation(SUBSCRIBE_MEMBER);
                if (DEBUG) {
                    Log.d(LOG_TAG, "ConversationService.subscribeGroup: requestId=" + requestId + " memberTwincodeId=" + mAdminTwincode);
                }

                ErrorCode result = mTwinmeContextImpl.getConversationService().registeredGroup(requestId,
                        mGroup, mAdminTwincode, mAdminPermissions, mPermissions);
            }
        }

        //
        // Last Step
        //

        stop();
    }

    protected void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        mTwinmeContextImpl.acknowledgeInvocation(mInvocationId, ErrorCode.SUCCESS);

        super.stop();
    }
}
