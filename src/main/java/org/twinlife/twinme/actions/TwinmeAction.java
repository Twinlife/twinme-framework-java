/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.actions;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinme.TwinmeContext;

/**
 * Base class of actions that provide high level operations on top of TwinmeContext API.
 *
 * - A TwinmeAction implements the TwinmeContext observer to simplify getting the results,
 * - It defines a deadline time after which the action must be canceled.
 * - The onOperation() must be implemented by derived classes.
 * - TwinmeAction are sorted on their Comparable so that the first one to expire can be identified.
 * - The TwinmeContext records the pending actions through the startAction() method which is invoked
 * when the start() method is called.
 * - When an action is finished, the TwinmeContext finishAction() is called.
 * - The TwinmeContext manages timeouts for the actions and calls fireTimeout().
 */
public abstract class TwinmeAction extends TwinmeContext.DefaultObserver implements Comparable<TwinmeAction> {
    private static final String LOG_TAG = "TwinmeAction";
    private static final boolean DEBUG = false;

    protected final long mStartTime;
    protected final long mDeadlineTime;
    @NonNull
    protected final TwinmeContext mTwinmeContext;
    protected final long mActionRequestId;
    protected boolean mIsOnline;
    @Nullable
    private ActionConsumer<ErrorCode> mError;

    public interface ActionConsumer<T> {
        void accept(@NonNull TwinmeAction action, @NonNull T object);
    }

    public TwinmeAction(@NonNull TwinmeContext twinmeContext, long timeLimit) {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwinmeAction");
        }

        mTwinmeContext = twinmeContext;
        mActionRequestId = twinmeContext.newRequestId();
        mStartTime = System.currentTimeMillis();
        mDeadlineTime = (timeLimit <= 0 ? Long.MAX_VALUE : mStartTime + timeLimit);
        mIsOnline = false;
    }

    public void start() {
        if (DEBUG) {
            Log.d(LOG_TAG, "start");
        }

        mTwinmeContext.startAction(this);
    }

    @Override
    public void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        // Do not start the operation: wait for the onTwinlifeOnline().
        if (!mTwinmeContext.isConnected()) {
            mTwinmeContext.connect();
        }
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        mIsOnline = true;
        onOperation();
    }

    @Override
    public void onTwinlifeOffline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOffline");
        }

        mIsOnline = false;
    }

    public long getDeadline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDeadline");
        }

        return mDeadlineTime;
    }

    @Override
    public int compareTo(TwinmeAction action) {

        if (mDeadlineTime < action.mDeadlineTime) {
            return -1;
        }

        if (mDeadlineTime > action.mDeadlineTime) {
            return 1;
        }

        if (mActionRequestId == action.mActionRequestId) {

            return 0;
        } else {

            return mActionRequestId < action.mActionRequestId ? -1 : 1;
        }
    }

    protected abstract void onOperation();

    protected void fireError(@NonNull ErrorCode errorCode) {

        if (mError != null) {
            mError.accept(this, errorCode);
        }

        onFinish();
    }

    public void fireTimeout() {

        fireError(ErrorCode.TIMEOUT_ERROR);
    }

    protected void onFinish() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onFinish");
        }

        mError = null;
        mTwinmeContext.finishAction(this);
    }

    protected long newRequestId() {

        return mTwinmeContext.newRequestId();
    }

    @NonNull
    public TwinmeAction onError(@NonNull ActionConsumer<ErrorCode> handler) {

        mError = handler;
        return this;
    }
}
