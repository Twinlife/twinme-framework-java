/*
 *  Copyright (c) 2016-2023 twinlife SA.
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
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Profile;

import java.util.UUID;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.9
//
// User foreground operation: must be connected with a timeout if connection does not work.
// BUT, when it is called from DeleteSpace, the timeout is set to infinity.

public class DeleteProfileExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "DeleteProfileExecutor";
    private static final boolean DEBUG = false;

    private static final int UNBIND_TWINCODE_INBOUND = 1;
    private static final int UNBIND_TWINCODE_INBOUND_DONE = 1 << 1;
    private static final int DELETE_TWINCODE = 1 << 2;
    private static final int DELETE_TWINCODE_DONE = 1 << 3;
    private static final int DELETE_IDENTITY_IMAGE = 1 << 4;
    private static final int DELETE_IDENTITY_IMAGE_DONE = 1 << 5;
    private static final int DELETE_OBJECT = 1 << 8;
    private static final int DELETE_OBJECT_DONE = 1 << 9;

    @NonNull
    private final Profile mProfile;
    @Nullable
    private final TwincodeInbound mTwincodeInbound;
    @Nullable
    private final UUID mTwincodeFactoryId;
    @Nullable
    private final ImageId mIdentityAvatarId;

    public DeleteProfileExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Profile profile, long timeout) {
        super(twinmeContextImpl, requestId, LOG_TAG, timeout);
        if (DEBUG) {
            Log.d(LOG_TAG, "DeleteProfileExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId + " profile=" + profile);
        }

        mProfile = profile;
        mTwincodeInbound = profile.getTwincodeInbound();
        mTwincodeFactoryId = profile.getTwincodeFactoryId();
        mIdentityAvatarId = profile.getAvatarId();
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & UNBIND_TWINCODE_INBOUND) != 0 && (mState & UNBIND_TWINCODE_INBOUND_DONE) == 0) {
                mState &= ~UNBIND_TWINCODE_INBOUND;
            }
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
        // Step 1: unbind the twincode.
        //
        if (mTwincodeInbound != null) {

            if ((mState & UNBIND_TWINCODE_INBOUND) == 0) {
                mState |= UNBIND_TWINCODE_INBOUND;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeInboundService.unbindTwincode: twincodeInbound=" + mTwincodeInbound);
                }
                mTwinmeContextImpl.getTwincodeInboundService().unbindTwincode(mTwincodeInbound, this::onUnbindTwincodeInbound);
                return;
            }
            if ((mState & UNBIND_TWINCODE_INBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2a: delete the twincode.
        //
        if (mTwincodeFactoryId != null) {

            if ((mState & DELETE_TWINCODE) == 0) {
                mState |= DELETE_TWINCODE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeFactoryService.deleteTwincode: twincodeFactoryId=" + mTwincodeFactoryId);
                }
                mTwinmeContextImpl.getTwincodeFactoryService().deleteTwincode(mTwincodeFactoryId, this::onDeleteTwincodeFactory);
                return;
            }
            if ((mState & DELETE_TWINCODE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2b: delete the twincode avatarId.
        //
        if (mIdentityAvatarId != null) {

            if ((mState & DELETE_IDENTITY_IMAGE) == 0) {
                mState |= DELETE_IDENTITY_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.deleteImage: imageId=" + mIdentityAvatarId);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.deleteImage(mIdentityAvatarId, (ErrorCode status, ImageId imageId) -> {
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
        // Step 4: delete the profile object.
        //

        if ((mState & DELETE_OBJECT) == 0) {
            mState |= DELETE_OBJECT;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.deleteObject: objectId=" + mProfile.getId() + " schemaId=" + mProfile.getSchemaId());
            }
            mTwinmeContextImpl.getRepositoryService().deleteObject(mProfile, this::onDeleteProfileObject);
            return;
        }
        if ((mState & DELETE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.onDeleteProfile(mRequestId, mProfile.getId());

        stop();
    }

    private void onDeleteProfileObject(@NonNull ErrorCode errorCode, @Nullable UUID objectId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteObject: objectId=" + objectId);
        }

        if (errorCode != ErrorCode.SUCCESS || objectId == null) {

            onOperationError(DELETE_OBJECT, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, objectId, mProfile.getId());

        mState |= DELETE_OBJECT_DONE;
        onOperation();
    }

    private void onDeleteTwincodeFactory(@NonNull ErrorCode errorCode, @Nullable UUID twincodeFactoryId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteTwincodeFactory: twincodeFactoryId=" + twincodeFactoryId);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeFactoryId == null) {

            onOperationError(DELETE_TWINCODE, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_FACTORY_ID, twincodeFactoryId, mTwincodeFactoryId);

        mState |= DELETE_TWINCODE_DONE;
        onOperation();
    }

    private void onUnbindTwincodeInbound(@NonNull ErrorCode errorCode, @Nullable UUID twincodeInboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUnbindTwincodeInbound: twincodeInboundId=" + twincodeInboundId);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeInboundId == null) {

            onOperationError(UNBIND_TWINCODE_INBOUND, errorCode, null);
            return;
        }

        mState |= UNBIND_TWINCODE_INBOUND_DONE;
        onOperation();
    }

    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // The delete operation succeeds if we get an item not found error.
        if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
            switch (operationId) {
                case UNBIND_TWINCODE_INBOUND:
                    mState |= UNBIND_TWINCODE_INBOUND_DONE;
                    onOperation();
                    return;

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
