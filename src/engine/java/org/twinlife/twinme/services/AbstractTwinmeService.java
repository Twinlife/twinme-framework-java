/*
 *  Copyright (c) 2020-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.services;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.NotificationService;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TwincodeFactoryService;
import org.twinlife.twinlife.TwincodeInboundService;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinme.TwinmeContext;

import java.util.HashMap;

/**
 * Twinme Executor
 *
 */
public abstract class AbstractTwinmeService extends TwinmeContext.DefaultObserver {
    private static final String LOG_TAG = "AbstractTwinmeService";
    private static final boolean DEBUG = false;

    protected class RepositoryServiceObserver extends RepositoryService.DefaultServiceObserver {

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryServiceObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            Integer operationId = getOperation(requestId);
            if (operationId != null) {
                AbstractTwinmeService.this.onError(operationId, errorCode, errorParameter);
                onOperation();
            }
        }
    }

    @NonNull
    protected final TwinmeContext mTwinmeContext;
    private final String mTag;

    protected int mState = 0;
    @SuppressLint("UseSparseArrays")
    protected final HashMap<Long, Integer> mRequestIds = new HashMap<>();
    protected boolean mRestarted = false;
    protected boolean mIsReady = false;

    public AbstractTwinmeService(@NonNull TwinmeContext twinmeContextImpl, @NonNull String tag) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Executor: twinmeContextImpl=" + twinmeContextImpl);
        }

        mTwinmeContext = twinmeContextImpl;
        mTag = tag;
    }

    public void start() {
        if (DEBUG) {
            Log.d(LOG_TAG, "start");
        }

        mTwinmeContext.setObserver(this);
    }

    @Override
    public void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        mIsReady = true;
        onOperation();
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        onOperation();
    }

    @Override
    public void onTwinlifeOffline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOffline");
        }

        mRestarted = true;
    }

    @Override
    public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        Integer operationId = getOperation(requestId);
        if (operationId != null) {
            onError(operationId, errorCode, errorParameter);
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

        long requestId = mTwinmeContext.newRequestId();
        mRequestIds.put(requestId, operationId);

        return requestId;
    }

    protected Integer getOperation(long requestId) {

        return mRequestIds.remove(requestId);
    }

    abstract protected void onOperation();

    protected void onError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // Wait for reconnection
        if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;

            return;
        }
    }

    protected void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        // mStopped = true;
        mTwinmeContext.removeObserver(this);
    }
}
