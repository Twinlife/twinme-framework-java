/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.actions.TwinmeAction;

/**
 * Abstract Timeout Twinme Executor
 *
 * Similar to the `AbstractConnectedTwinmeExecutor` but enforces a connection to the twinlife server
 * with a timeout:
 * - `onTwinlifeReady()` can be overridden to register specific observers.
 *   When overriding, it is mandatory to call `super.onTwinlifeReady()` for correct implementation.
 * - `onOperation()` must be implemented to perform the work, it will be called when the executor
 *   is ready to perform some operation.
 * - `fireTimeout()` is called when the executor timeout has occurred.
 */

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.0
//

public abstract class AbstractTimeoutTwinmeExecutor extends TwinmeAction {
    private static final String LOG_TAG = "AbsTimeoutExec";
    private static final boolean DEBUG = false;

    public static final long DEFAULT_TIMEOUT = TwinmeContext.DEFAULT_TIMEOUT;

    protected class ConversationServiceObserver extends ConversationService.DefaultServiceObserver {

        @Override
        public void onError(long requestId, BaseService.ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            int operationId = getOperation(requestId);
            if (operationId > 0) {
                AbstractTimeoutTwinmeExecutor.this.onOperationError(operationId, errorCode, errorParameter);
                onOperation();
            }
        }
    }

    protected boolean mConnected;
    protected int mState = 0;
    protected boolean mRestarted = false;
    protected volatile boolean mStopped = false;
    protected final long mRequestId;
    protected final String mTag;
    protected final TwinmeContextImpl mTwinmeContextImpl;
    private AbstractTwinmeExecutor.PendingRequest mRequestList;
    protected boolean mNeedOnline = true;

    public AbstractTimeoutTwinmeExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                         @NonNull String tag, long timeout) {
        super(twinmeContextImpl, timeout);
        if (DEBUG) {
            Log.d(LOG_TAG, "AbsTimeoutExec: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId);
        }

        mRequestId = requestId;
        mConnected = false;
        mTwinmeContextImpl = twinmeContextImpl;
        mTag = tag;
    }

    //
    // Protected methods
    //

    @Override
    public void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        super.onTwinlifeReady();

        // If we don't need to be online, start the operation now.
        if (!mNeedOnline) {
            onOperation();
        }
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        mConnected = true;
        super.onTwinlifeOnline();
    }

    @Override
    public void onTwinlifeOffline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOffline");
        }

        // Keep mConnected set for fireTimeout().
        mRestarted = true;
        super.onTwinlifeOffline();
    }

    @Override
    public void onError(long requestId, BaseService.ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwinmeContextObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        int operationId = getOperation(requestId);
        if (operationId > 0) {
            onOperationError(operationId, errorCode, errorParameter);
            onOperation();
        }
    }

    @Override
    public void fireTimeout() {
        if (DEBUG) {
            Log.d(LOG_TAG, "fireTimeout");
        }

        // If we were connected, we cannot interrupt the executor and we have to wait
        // for reconnection until everything completes.  We can report the timeout only
        // when we never reached connection.
        if (!mConnected) {
            fireError(BaseService.ErrorCode.TIMEOUT_ERROR);
        }
    }

    //
    // Protected methods
    //

    protected void fireError(@NonNull BaseService.ErrorCode errorCode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "fireError: errorCode=" + errorCode);
        }

        super.fireError(errorCode);
        stop();

        mTwinmeContext.fireOnError(mRequestId, errorCode, null);
    }

    protected long newOperation(int operationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "newOperation: operationId=" + operationId);
        }

        long requestId = mTwinmeContext.newRequestId();
        mRequestList = new AbstractTwinmeExecutor.PendingRequest(operationId, requestId, mRequestList);

        return requestId;
    }

    protected int getOperation(long requestId) {

        AbstractTwinmeExecutor.PendingRequest prev = null;
        AbstractTwinmeExecutor.PendingRequest item = mRequestList;
        while (item != null) {
            if (item.requestId == requestId) {
                if (prev == null) {
                    mRequestList = item.nextRequest;
                } else {
                    prev.nextRequest = item.nextRequest;
                }
                return item.operationId;
            }
            prev = item;
            item = item.nextRequest;
        }
        return 0;
    }

    protected void onOperationError(int operationId, BaseService.ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // Wait for reconnection
        if (errorCode == BaseService.ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;

            return;
        }

        // Mark the executor as stopped before calling fireOnError().
        stop();

        mTwinmeContext.fireOnError(mRequestId, errorCode, errorParameter);
    }

    protected void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        mStopped = true;
        onFinish();
    }
}
