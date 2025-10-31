/*
 *  Copyright (c) 2020-2024 twinlife SA.
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
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContextImpl;

/**
 * Abstract Twinme Executor
 *
 * The AbstractExecutor is the base class of executors and it provides common methods used by executors.
 * It maintains the Twinme context, the global state, the pending requests.
 *
 * Mandatory operations:
 * - onTwinlifeOnline() must be implemented to update the state and prepare after we are connected to the server.
 * - onOperation() must be implemented to perform the work, it will be called when the executor
 * is ready to perform some operation.
 *
 * Optional operations:
 * - onTwinlifeReady() can be overridden to register specific observers,
 * - onOperationError() can be overridden to handle specific error,
 * - stop() need to be overridden when some service observer must be removed.
 *
 * The AbstractExecutor provides default observer classes that are ready to handle errors.
 * These observer classes must be overridden when necessary to handle specific observer methods
 * (such as onUpdateObject, onDeleteObject, ...).
 *
 * The AbstractConnectedTwinmeExecutor is similar but it checks whether we are connected and triggers a
 * reconnected if necessary.  It waits for the `onTwinlifeOnline()` to be called before calling the first `onOperation()`.
 *
 * The AbstractTimeoutTwinmeExecutor is intended to be used on executors that perform operations using
 * the server and require a timeout for their execution.
 */

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.0
//

public abstract class AbstractTwinmeExecutor extends TwinmeContext.DefaultObserver {
    private static final String LOG_TAG = "AbstractExecutor";
    private static final boolean DEBUG = false;

    @NonNull
    protected final TwinmeContextImpl mTwinmeContextImpl;
    protected final long mRequestId;
    protected final long mStartTime;
    private final String mTag;

    protected int mState = 0;
    protected boolean mRestarted = false;
    protected volatile boolean mStopped = false;

    static class PendingRequest {
        final long requestId;
        final int operationId;
        PendingRequest nextRequest;

        PendingRequest(int operationId, long requestId, PendingRequest nextRequest) {
            this.operationId = operationId;
            this.requestId = requestId;
            this.nextRequest = nextRequest;
        }
    }
    private PendingRequest mRequestList;

    public AbstractTwinmeExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull String tag) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Executor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId);
        }

        mTwinmeContextImpl = twinmeContextImpl;
        mRequestId = requestId;
        mStartTime = System.currentTimeMillis();
        mTag = tag;
    }

    public void start() {
        if (DEBUG) {
            Log.d(LOG_TAG, "start");
        }

        mTwinmeContextImpl.setObserver(this);
    }

    @Override
    public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwinmeContextObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        int operationId = getOperation(requestId);
        if (operationId > 0) {
            onOperationError(operationId, errorCode, errorParameter);
            onOperation();
        }
    }

    //
    // Protected methods
    //

    protected long newOperation(int operationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "newOperation: operationId=" + operationId);
        }

        long requestId = mTwinmeContextImpl.newRequestId();
        mRequestList = new PendingRequest(operationId, requestId, mRequestList);

        return requestId;
    }

    protected int getOperation(long requestId) {

        PendingRequest prev = null;
        PendingRequest item = mRequestList;
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

    protected void finishOperation(long requestId) {

        PendingRequest prev = null;
        PendingRequest item = mRequestList;
        while (item != null) {
            if (item.requestId == requestId) {
                if (prev == null) {
                    mRequestList = item.nextRequest;
                } else {
                    prev.nextRequest = item.nextRequest;
                }
                return;
            }
            prev = item;
            item = item.nextRequest;
        }
    }

    @Override
    public void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        onOperation();
    }

    abstract protected void onOperation();

    @Override
    public void onTwinlifeOffline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOffline");
        }

        mRestarted = true;
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

        // Mark the executor as stopped before calling fireOnError().
        stop();

        mTwinmeContextImpl.fireOnError(mRequestId, errorCode, errorParameter);
    }

    protected void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        mStopped = true;
        mTwinmeContextImpl.removeObserver(this);
        EventMonitor.event(mTag, mStartTime);
    }
}
