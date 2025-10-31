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

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.CallReceiver;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.2
//
// User foreground operation: must be connected with a timeout if connection does not work.

public class ChangeCallReceiverTwincodeExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "ChangeCallRecTwincodeEx";
    private static final boolean DEBUG = false;

    private static final int CREATE_TWINCODE = 1 << 2;
    private static final int CREATE_TWINCODE_DONE = 1 << 3;
    private static final int UPDATE_OBJECT = 1 << 6;
    private static final int UPDATE_OBJECT_DONE = 1 << 7;
    private static final int DELETE_TWINCODE = 1 << 8;
    private static final int DELETE_TWINCODE_DONE = 1 << 9;

    @NonNull
    private final CallReceiver mCallReceiver;
    @Nullable
    private final UUID mOldTwincodeFactoryId;
    @Nullable
    private final ExportedImageId mAvatarId;

    public ChangeCallReceiverTwincodeExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull CallReceiver callReceiver) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "ChangeCallReceiverTwincodeExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId
                    + " callReceiver=" + callReceiver);
        }

        mCallReceiver = callReceiver;
        mOldTwincodeFactoryId = mCallReceiver.getTwincodeFactoryId();
        ImageId avatarId = mCallReceiver.getAvatarId();
        if (avatarId != null) {
            mAvatarId = twinmeContextImpl.getImageService().getPublicImageId(avatarId);
        } else {
            mAvatarId = null;
        }
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & CREATE_TWINCODE) != 0 && (mState & CREATE_TWINCODE_DONE) == 0) {
                mState &= ~CREATE_TWINCODE;
            }
            if ((mState & DELETE_TWINCODE) != 0 && (mState & DELETE_TWINCODE_DONE) == 0) {
                mState &= ~DELETE_TWINCODE;
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
        // Step 1: create the public profile twincode.
        //

        if ((mState & CREATE_TWINCODE) == 0) {
            mState |= CREATE_TWINCODE;

            mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_SUBJECT, mCallReceiver, 116);

            List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
            PairProtocol.setTwincodeAttributePair(twincodeFactoryAttributes);

            List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
            TwinmeAttributes.setTwincodeAttributeName(twincodeOutboundAttributes, mCallReceiver.getIdentityName());

            if (mAvatarId != null) {
                TwinmeAttributes.setTwincodeAttributeAvatarId(twincodeOutboundAttributes, mAvatarId);
            }

            // Keep the original profile twincode attributes so that we preserve the capabilities
            // and possibly other attributes.
            TwincodeOutbound twincodeOutbound = mCallReceiver.getTwincodeOutbound();
            if (twincodeOutbound != null) {
                for (BaseService.AttributeNameValue attribute : twincodeOutbound.getAttributes()){
                    if (Twincode.NAME.equals(attribute.name)) {
                        continue;
                    }
                    if (Twincode.AVATAR_ID.equals(attribute.name)) {
                        continue;
                    }
                    twincodeOutboundAttributes.add(attribute);
                }
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: twincodeFactoryAttributes=" + twincodeFactoryAttributes
                        + " twincodeOutboundAttributes=" + twincodeOutboundAttributes);
            }
            mTwinmeContextImpl.getTwincodeFactoryService().createTwincode(twincodeFactoryAttributes,
                    null, twincodeOutboundAttributes, null,
                    CallReceiver.SCHEMA_ID, this::onCreateTwincodeFactory);
            return;
        }
        if ((mState & CREATE_TWINCODE_DONE) == 0) {
            return;
        }

        //
        // Step 2: update the profile.
        //

        if ((mState & UPDATE_OBJECT) == 0) {
            mState |= UPDATE_OBJECT;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.updateObject: object=" + mCallReceiver);
            }
            mTwinmeContextImpl.getRepositoryService().updateObject(mCallReceiver, this::onUpdateObject);
            return;
        }
        if ((mState & UPDATE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Step 3: delete the twincode.
        //
        if (mOldTwincodeFactoryId != null) {

            if ((mState & DELETE_TWINCODE) == 0) {
                mState |= DELETE_TWINCODE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeFactoryService.deleteTwincode: twincodeFactoryId=" + mOldTwincodeFactoryId);
                }
                mTwinmeContextImpl.getTwincodeFactoryService().deleteTwincode(mOldTwincodeFactoryId, this::onDeleteTwincodeFactory);
                return;
            }
            if ((mState & DELETE_TWINCODE_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.onChangeCallReceiverTwincode(mRequestId, mCallReceiver);

        stop();
    }

    private void onCreateTwincodeFactory(@NonNull ErrorCode errorCode, @Nullable TwincodeFactory twincodeFactory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateTwincodeFactory: twincodeFactory=" + twincodeFactory);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeFactory == null) {

            onOperationError(CREATE_TWINCODE, errorCode, null);
            return;
        }

        mState |= CREATE_TWINCODE_DONE;

        mCallReceiver.setTwincodeFactory(twincodeFactory);
        onOperation();
    }

    private void onUpdateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateObject: errorCode=" + errorCode + " object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || object == null) {

            onOperationError(DELETE_TWINCODE, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, object, mCallReceiver);

        mState |= UPDATE_OBJECT_DONE;
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

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_FACTORY_ID, twincodeFactoryId, mOldTwincodeFactoryId);

        mState |= DELETE_TWINCODE_DONE;
        onOperation();
    }

    @Override
    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // The delete operation succeeds if we get an item not found error.
        if (errorCode == ErrorCode.ITEM_NOT_FOUND && (operationId == DELETE_TWINCODE)) {
            mState |= DELETE_TWINCODE_DONE;
            return;
        }

        super.onOperationError(operationId, errorCode, errorParameter);
    }
}
