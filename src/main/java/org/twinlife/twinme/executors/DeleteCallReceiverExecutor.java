/*
 *  Copyright (c) 2023-2024 twinlife SA.
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
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.CallReceiver;

import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.3
//
// User foreground operation: must be connected with a timeout if connection does not work.
// BUT, when it is called from DeleteSpace, the timeout is set to infinity.

public class DeleteCallReceiverExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "DeleteCallReceiverExec";
    private static final boolean DEBUG = false;

    private static final int DELETE_TWINCODE = 1;
    private static final int DELETE_TWINCODE_DONE = 1 << 1;
    private static final int DELETE_IDENTITY_IMAGE = 1 << 2;
    private static final int DELETE_IDENTITY_IMAGE_DONE = 1 << 3;
    private static final int DELETE_OBJECT = 1 << 4;
    private static final int DELETE_OBJECT_DONE = 1 << 5;

    @NonNull
    private final CallReceiver mCallReceiver;
    @Nullable
    private final UUID mTwincodeFactoryId;
    @Nullable
    private final ImageId mAvatarId;

    public DeleteCallReceiverExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull CallReceiver callReceiver) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "DeleteContactExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId + " callReceiver=" + callReceiver);
        }

        mCallReceiver = callReceiver;
        mTwincodeFactoryId = callReceiver.getTwincodeFactoryId();
        mAvatarId = callReceiver.getAvatarId();
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & DELETE_TWINCODE) != 0 && (mState & DELETE_TWINCODE_DONE) == 0) {
                mState &= ~DELETE_TWINCODE;
            }
            if ((mState & DELETE_IDENTITY_IMAGE) != 0 && (mState & DELETE_IDENTITY_IMAGE_DONE) == 0) {
                mState &= ~DELETE_IDENTITY_IMAGE;
            }
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
        // Step 1: delete the twincode.
        //
        if (mTwincodeFactoryId != null) {

            if ((mState & DELETE_TWINCODE) == 0) {
                mState |= DELETE_TWINCODE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeFactoryService.deleteTwincode: twincodeFactoryId=" + mTwincodeFactoryId);
                }
                mTwinmeContextImpl.getTwincodeFactoryService().deleteTwincode(mTwincodeFactoryId, this::onDeleteTwincode);
                return;
            }
            if ((mState & DELETE_TWINCODE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2: delete the twincode avatarId
        //
        if (mAvatarId != null) {

            if ((mState & DELETE_IDENTITY_IMAGE) == 0) {
                mState |= DELETE_IDENTITY_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.deleteImage: imageId=" + mAvatarId);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.deleteImage(mAvatarId, (ErrorCode status, ImageId imageId) -> {
                    mState |= DELETE_IDENTITY_IMAGE_DONE;
                    onOperation();
                });
                return;
            }
            if ((mState & DELETE_IDENTITY_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3: delete the contact object.
        //
        if ((mState & DELETE_OBJECT) == 0) {
            mState |= DELETE_OBJECT;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.deleteObject: objectId=" + mCallReceiver.getId() + " schemaId=" + mCallReceiver.getSchemaId());
            }
            mTwinmeContextImpl.getRepositoryService().deleteObject(mCallReceiver, this::onDeleteObject);
            return;
        }
        if ((mState & DELETE_OBJECT_DONE) == 0) {
            return;
        }


        mTwinmeContextImpl.onDeleteCallReceiver(mRequestId, mCallReceiver.getId());

        stop();
    }

    private void onDeleteTwincode(@NonNull ErrorCode errorCode, @Nullable UUID twincodeFactoryId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteTwincode: twincodeFactoryId=" + twincodeFactoryId);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeFactoryId == null) {

            onOperationError(DELETE_TWINCODE, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_FACTORY_ID, twincodeFactoryId, mTwincodeFactoryId);

        mState |= DELETE_TWINCODE_DONE;
        onOperation();
    }

    private void onDeleteObject(@NonNull ErrorCode errorCode, @Nullable UUID objectId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteObject: objectId=" + objectId);
        }

        if (errorCode != ErrorCode.SUCCESS || objectId == null) {

            onOperationError(DELETE_OBJECT, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_FACTORY_ID, objectId, mCallReceiver.getId());

        mState |= DELETE_OBJECT_DONE;
        onOperation();
    }

    @Override
    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // The delete operation succeeds if we get an item not found error.
        if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
            switch (operationId) {
                case DELETE_TWINCODE:
                    mState |= DELETE_TWINCODE_DONE;
                    onOperation();
                    return;

                case DELETE_OBJECT:
                    mState |= DELETE_OBJECT_DONE;
                    onOperation();
                    return;

                default:
                    break;
            }
        }

        super.onOperationError(operationId, errorCode, errorParameter);
    }
}
