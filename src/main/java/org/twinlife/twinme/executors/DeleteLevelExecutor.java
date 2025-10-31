/*
 *  Copyright (c) 2017-2023 twinlife SA.
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

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Space;

import java.util.List;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.3
//

public class DeleteLevelExecutor extends AbstractTwinmeExecutor {
    private static final String LOG_TAG = "DeleteLevelExecutor";
    private static final boolean DEBUG = false;

    private static final int GET_SPACE = 1; // Get the space whose name matches the level.
    private static final int GET_SPACE_DONE = 1 << 1;
    private static final int GET_NODE = 1 << 2;
    private static final int GET_NODE_DONE = 1 << 3;
    private static final int GET_SPACE_LEVEL = 1 << 4; // Get the space with a profile having the levelId.
    private static final int GET_SPACE_LEVEL_DONE = 1 << 5;
    private static final int DELETE_SPACE = 1 << 6;
    private static final int DELETE_SPACE_DONE = 1 << 7;
    private static final int DELETE_NODE = 1 << 8;
    private static final int DELETE_NODE_DONE = 1 << 9;

    @NonNull
    private final String mName;
    @Nullable
    private Space mSpace;

    public DeleteLevelExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull String name) {
        super(twinmeContextImpl, requestId, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "DeleteLevelExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId + " name=" + name);
        }

        mName = name;
    }

    @Override
    public void onDeleteSpace(long requestId, @NonNull UUID spaceId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteSpace: requestId=" + requestId + " spaceId=" + spaceId);
        }

        int operationId = getOperation(requestId);
        if (operationId > 0) {
            onDeleteSpace(spaceId);
            onOperation();
        }
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
        // Step 1: find the space with the given name.
        //

        if ((mState & GET_SPACE) == 0) {
            mState |= GET_SPACE;

            long requestId = newOperation(GET_SPACE);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContext.findSpaces: requestId=" + requestId);
            }
            mTwinmeContextImpl.findSpaces((Space space) -> mName.equals(space.getName()), (ErrorCode errorCode, List<Space> spaces) -> {
                if (spaces != null && !spaces.isEmpty()) {
                    mSpace = spaces.get(0);
                }
                mState |= GET_SPACE_DONE;
                onOperation();
            });
            return;
        }

        if ((mState & GET_SPACE_DONE) == 0) {
            return;
        }

        //
        // Step 4: delete the space
        //
        if (mSpace != null) {

            if ((mState & DELETE_SPACE) == 0) {
                mState |= DELETE_SPACE;

                long requestId = newOperation(DELETE_SPACE);
                if (DEBUG) {
                    Log.d(LOG_TAG, "TwinmeContext.deleteSpace: requestId=" + requestId);
                }
                mTwinmeContextImpl.deleteSpace(requestId, mSpace);
                return;
            }

            if ((mState & DELETE_SPACE_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.onDeleteLevel(mRequestId, mName);

        stop();
    }

    private void onDeleteSpace(@NonNull UUID spaceId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteSpace: spaceId=" + spaceId);
        }

        if (mSpace != null) {
            mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, spaceId, mSpace.getId());
        }

        mState |= DELETE_SPACE_DONE;
    }

    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // Wait for reconnection
        if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;

            return;
        }

        if (errorCode == ErrorCode.ITEM_NOT_FOUND || errorCode == ErrorCode.BAD_REQUEST) {
            switch (operationId) {
                case GET_NODE:
                    mState |= GET_NODE_DONE;
                    return;

                case DELETE_NODE:
                    mState |= DELETE_NODE_DONE;
                    return;

                case DELETE_SPACE:
                    mState |= DELETE_SPACE_DONE;
                    return;

                default:
                    break;
            }
        }

        super.onOperationError(operationId, errorCode, errorParameter);
    }
}
