/*
 *  Copyright (c) 2020-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContextImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Twinme Executor
 *
 * The Executor is the base class of executors that should have a single instance at a given time.
 * The executor handles the base operations used by all executors and it also maintains the state of the executor.
 * The executor handles a list of blocks that are executed as soon as the executor has finished and is stopped successfully or not.
 *
 * The execute() method is used to add such execution block to the queue.
 * The stop() method will handle the execution of all blocks that have been queued.
 *
 * The pattern to create a single executor of a given kind is maintained in TwinmeContext and follows:
 * - the executor instance is associated with a unique name and stored in the 'executors' dictionary.
 * - the synchronization is made on the 'executors' instance to protect lookup, insertion and removal.
 * - the dictionary is look at for the unique name and the instance is used if it existed.
 * - a new instance is created if it did not exist and added to the 'executors' dictionary.
 * - the specific work is queued by running the execute() operation on the Executor instance.
 *  In most cases, that specific work involves calling a resolveFindXXX() with a predicate and consumer block.
 * - if a new instance was created, it is started.
 *
 * When the Executor finished, it calls some onXXX() operation on the TwinmeContext and that operation is then
 * responsible for removing the Executor instance from the 'executors' dictionary.
 *
 * The execute() and stop() are protected against concurrent accesses. It is possible that execute() is called and the executor
 * is stopped. In that case, the execute() operation will execute the block immediately.
 *
 * The Executor is useful to retrieve:
 * - the list of spaces,
 * - the list of contacts,
 * - the list of groups,
 * - the list of notifications
 */

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.0
//

public abstract class Executor {
    private static final String LOG_TAG = "Executor";
    private static final boolean DEBUG = false;

    protected class TwinmeContextObserver extends TwinmeContext.DefaultObserver {

        @Override
        public void onTwinlifeReady() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeReady");
            }

            Executor.this.onTwinlifeReady();
            onOperation();
        }

        @Override
        public void onTwinlifeOnline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeOnline");
            }

            Executor.this.onTwinlifeOnline();
            onOperation();
        }

        @Override
        public void onTwinlifeOffline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinlifeObserver.onTwinlifeOffline");
            }

            Executor.this.onTwinlifeOffline();
        }

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                Executor.this.onError(operationId, errorCode, errorParameter);
                onOperation();
            }
        }
    }

    @NonNull
    protected final TwinmeContextImpl mTwinmeContextImpl;
    protected final long mRequestId;
    protected final long mStartTime;
    private final String mTag;

    protected int mState = 0;
    @SuppressLint("UseSparseArrays")
    protected final HashMap<Long, Integer> mRequestIds = new HashMap<>();
    protected boolean mRestarted = false;
    protected volatile boolean mStopped = false;
    private final List<Runnable> mExecutors = new ArrayList<>();

    public Executor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull String tag) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Executor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId);
        }

        mTwinmeContextImpl = twinmeContextImpl;
        mRequestId = requestId;
        mStartTime = System.currentTimeMillis();
        mTag = tag;
    }

    public abstract void start();

    /**
     * Execute the given runnable as soon as the executor has finished.
     *
     * @param runnable the runnable to execute.
     */
    public void execute(@NonNull Runnable runnable) {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute");
        }

        boolean isStopped;
        synchronized (mExecutors) {
            isStopped = mStopped;
            if (!isStopped) {
                mExecutors.add(runnable);
            }
        }

        // If we are stopped, we must run the block immediately (nobody else will do it).
        if (isStopped) {
            runnable.run();
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
        mRequestIds.put(requestId, operationId);

        return requestId;
    }

    protected void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

    }

    abstract protected void onOperation();

    abstract protected void onTwinlifeOnline();

    protected void onTwinlifeOffline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOffline");
        }

        mRestarted = true;
    }

    protected void onError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
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

        while (true) {
            Runnable item;

            synchronized (mExecutors) {
                mStopped = true;
                if (mExecutors.isEmpty()) {
                    EventMonitor.event(mTag, mStartTime);
                    return;
                }
                item = mExecutors.remove(mExecutors.size() - 1);
            }
            item.run();
        }
    }
}
