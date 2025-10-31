/*
 *  Copyright (c) 2019 twinlife SA.
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

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.DateCard;
import org.twinlife.twinme.models.EngineCard;
import org.twinlife.twinme.models.SpaceCard;

import java.util.HashMap;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.0
//

public class GetEngineCardExecutor {
    private static final String LOG_TAG = "GetEngineCardExec";
    private static final boolean DEBUG = false;

    private static final int GET_OBJECT = 1;
    private static final int GET_OBJECT_DONE = 1 << 1;

    private class TwinmeContextObserver extends TwinmeContext.DefaultObserver {

        @Override
        public void onTwinlifeReady() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeReady");
            }

            GetEngineCardExecutor.this.onTwinlifeReady();
            onOperation();
        }

        @Override
        public void onTwinlifeOnline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeOnline");
            }

            GetEngineCardExecutor.this.onTwinlifeOnline();
            onOperation();
        }

        @Override
        public void onTwinlifeOffline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinlifeObserver.onTwinlifeOffline");
            }

            GetEngineCardExecutor.this.onTwinlifeOffline();
        }

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                GetEngineCardExecutor.this.onError(operationId, errorCode, errorParameter);
                onOperation();
            }
        }
    }

    class RepositoryServiceObserver extends RepositoryService.DefaultServiceObserver {

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryServiceObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                GetEngineCardExecutor.this.onError(operationId, errorCode, errorParameter);
                onOperation();
            }
        }
    }

    @NonNull
    private final TwinmeContextImpl mTwinmeContextImpl;
    @NonNull
    private final UUID mObjectId;
    @NonNull
    private final TwinmeContext.Consumer<EngineCard> mConsumer;
    private EngineCard mEngineCard;

    private int mState = 0;
    @SuppressLint("UseSparseArrays")
    private final HashMap<Long, Integer> mRequestIds = new HashMap<>();
    private final long mRequestId;
    private boolean mRestarted = false;
    private boolean mStopped = false;

    private final TwinmeContextObserver mTwinmeContextObserver;
    private final RepositoryServiceObserver mRepositoryServiceObserver;

    public GetEngineCardExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull UUID objectId,
                                @NonNull TwinmeContext.Consumer<EngineCard> consumer) {

        mTwinmeContextImpl = twinmeContextImpl;
        mObjectId = objectId;
        mConsumer = consumer;
        mRequestId = requestId;

        mTwinmeContextObserver = new TwinmeContextObserver();
        mRepositoryServiceObserver = new RepositoryServiceObserver();
    }

    public void start() {
        if (DEBUG) {
            Log.d(LOG_TAG, "start");
        }

        mTwinmeContextImpl.setObserver(mTwinmeContextObserver);
    }

    //
    // Private methods
    //

    private long newOperation(int operationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "newOperation: operationId=" + operationId);
        }

        long requestId = mTwinmeContextImpl.newRequestId();
        mRequestIds.put(requestId, operationId);

        return requestId;
    }

    private void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        mTwinmeContextImpl.getRepositoryService().addServiceObserver(mRepositoryServiceObserver);
    }

    private void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;

            if ((mState & GET_OBJECT) != 0 && (mState & GET_OBJECT_DONE) == 0) {
                mState &= ~GET_OBJECT;
            }
        }
    }

    private void onTwinlifeOffline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOffline");
        }

        mRestarted = true;
    }

    private void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        if (mStopped) {

            return;
        }

        //
        // Step 1
        //

        boolean completedStep1 = true;

        if ((mState & GET_OBJECT) == 0) {
            mState |= GET_OBJECT;
            completedStep1 = false;

            mTwinmeContextImpl.assertNotNull(LOG_TAG, mObjectId);

            long requestId = newOperation(GET_OBJECT);
            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.getObject: requestId=" + requestId + " objectId=" + mObjectId);
            }
            mTwinmeContextImpl.getRepositoryService().getObject(requestId, mObjectId, EngineCard.SCHEMA_ID,
                    (ErrorCode status, RepositoryService.Object object) -> {
                mRequestIds.remove(requestId);
                onGetObject(GET_OBJECT, object);
                onOperation();
            });
        }
        if ((mState & GET_OBJECT_DONE) == 0) {
            completedStep1 = false;
        }

        if (!completedStep1) {

            return;
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.assertNotNull(LOG_TAG, mEngineCard);

        mConsumer.accept(mEngineCard);

        stop();
    }

    private void onGetObject(int operationId, @NonNull RepositoryService.Object object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetObject: operationId=" + operationId + " object=" + object);
        }

        mTwinmeContextImpl.assertEqual(LOG_TAG, object.getId(), mObjectId);
        mTwinmeContextImpl.assertEqual(LOG_TAG, object.getSchemaId(), EngineCard.SCHEMA_ID);

        if ((mState & GET_OBJECT_DONE) != 0) {

            return;
        }
        mState |= GET_OBJECT_DONE;

        mEngineCard = EngineCard.deserialize(mTwinmeContextImpl.getRepositoryService(), object);
        if (mEngineCard == null) {
            mTwinmeContextImpl.assertNotReached(LOG_TAG, "onGetObject object=" + object);

            onError(operationId, BaseService.ErrorCode.BAD_REQUEST, object.getId().toString());
        }
    }

    private void onError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // Wait for reconnection
        if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;

            return;
        }

        mTwinmeContextImpl.fireOnError(mRequestId, errorCode, errorParameter);

        stop();
    }

    private void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        mStopped = true;

        mTwinmeContextImpl.getRepositoryService().removeServiceObserver(mRepositoryServiceObserver);

        mTwinmeContextImpl.removeObserver(mTwinmeContextObserver);
    }
}
