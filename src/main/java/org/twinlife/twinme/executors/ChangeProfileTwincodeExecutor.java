/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.5
//
// User foreground operation: must be connected with a timeout if connection does not work.

public class ChangeProfileTwincodeExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "ChangeProfileTwincodeEx";
    private static final boolean DEBUG = false;

    private static final int CREATE_TWINCODE = 1 << 2;
    private static final int CREATE_TWINCODE_DONE = 1 << 3;
    private static final int UPDATE_OBJECT = 1 << 6;
    private static final int UPDATE_OBJECT_DONE = 1 << 7;
    private static final int UNBIND_TWINCODE_INBOUND = 1 << 8;
    private static final int UNBIND_TWINCODE_INBOUND_DONE = 1 << 9;
    private static final int DELETE_TWINCODE = 1 << 10;
    private static final int DELETE_TWINCODE_DONE = 1 << 11;

    @NonNull
    private final Profile mProfile;
    @Nullable
    private final TwincodeInbound mOldTwincodeInbound;
    @Nullable
    private final UUID mOldTwincodeFactoryId;
    @Nullable
    private final ImageId mAvatarId;
    @NonNull
    private final Consumer<Profile> mComplete;

    public ChangeProfileTwincodeExecutor(@NonNull TwinmeContextImpl twinmeContextImpl,
                                         @NonNull Profile profile, @NonNull Consumer<Profile> complete) {
        super(twinmeContextImpl, BaseService.DEFAULT_REQUEST_ID, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "ChangeProfileTwincodeExecutor: twinmeContextImpl=" + twinmeContextImpl
                    + " profile=" + profile);
        }

        mTwinmeContext.assertNotNull(ExecutorAssertPoint.PARAMETER, profile, 78);

        mProfile = profile;
        mOldTwincodeInbound = mProfile.getTwincodeInbound();
        mOldTwincodeFactoryId = mProfile.getTwincodeFactoryId();
        mComplete = complete;

        mAvatarId = mProfile.getAvatarId();
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
            if ((mState & UNBIND_TWINCODE_INBOUND) != 0 && (mState & UNBIND_TWINCODE_INBOUND_DONE) == 0) {
                mState &= ~UNBIND_TWINCODE_INBOUND;
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

            List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
            PairProtocol.setTwincodeAttributeMetaPair(twincodeFactoryAttributes);

            List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
            TwinmeAttributes.setTwincodeAttributeName(twincodeOutboundAttributes, mProfile.getName());
            if (mAvatarId != null) {
                ExportedImageId avatarId = mTwinmeContext.getImageService().getPublicImageId(mAvatarId);
                if (avatarId != null) {
                    TwinmeAttributes.setTwincodeAttributeAvatarId(twincodeOutboundAttributes, avatarId);
                }
            }

            // Keep the original profile twincode attributes so that we preserve the capabilities
            // and possibly other attributes.
            TwincodeOutbound twincodeOutbound = mProfile.getTwincodeOutbound();
            if (twincodeOutbound != null) {
                for (BaseService.AttributeNameValue attribute : twincodeOutbound.getAttributes()){
                    if (Twincode.AVATAR_ID.equals(attribute.name)) {
                        continue;
                    }
                    if (Twincode.NAME.equals(attribute.name)) {
                        continue;
                    }
                    twincodeOutboundAttributes.add(attribute);
                }
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: twincodeFactoryAttributes=" + twincodeFactoryAttributes
                        + " twincodeOutboundAttributes=" + twincodeOutboundAttributes);
            }
            mTwinmeContext.getTwincodeFactoryService().createTwincode(twincodeFactoryAttributes,
                    null, twincodeOutboundAttributes, null,
                    Profile.SCHEMA_ID, this::onCreateTwincodeFactory);
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
                Log.d(LOG_TAG, "RepositoryService.createObject: object=" + mProfile);
            }
            mTwinmeContext.getRepositoryService().updateObject(mProfile, this::onUpdateObject);
            return;
        }
        if ((mState & UPDATE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Step 3: unbind the old twincode.
        //
        if (mOldTwincodeInbound != null) {

            if ((mState & UNBIND_TWINCODE_INBOUND) == 0) {
                mState |= UNBIND_TWINCODE_INBOUND;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeInboundService.unbindTwincode: twincodeInbound=" + mOldTwincodeInbound);
                }
                mTwinmeContext.getTwincodeInboundService().unbindTwincode(mOldTwincodeInbound, this::onUnbindTwincodeInbound);
                return;
            }
            if ((mState & UNBIND_TWINCODE_INBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 4: delete the old twincode.
        //
        if (mOldTwincodeFactoryId != null) {

            if ((mState & DELETE_TWINCODE) == 0) {
                mState |= DELETE_TWINCODE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeFactoryService.deleteTwincode: twincodeFactoryId=" + mOldTwincodeFactoryId);
                }
                mTwinmeContext.getTwincodeFactoryService().deleteTwincode(mOldTwincodeFactoryId, this::onDeleteTwincodeFactory);
                return;
            }
            if ((mState & DELETE_TWINCODE_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        // mTwinmeContextImpl.onChangeProfileTwincode(mRequestId, mProfile);
        mComplete.onGet(ErrorCode.SUCCESS, mProfile);

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

        mProfile.setTwincodeFactory(twincodeFactory);
        onOperation();
    }

    private void onUpdateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateObject: errorCode=" + errorCode + " object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || object == null) {

            onOperationError(UPDATE_OBJECT, errorCode, null);
            return;
        }

        mTwinmeContext.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, object, mProfile);

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

        mTwinmeContext.assertEqual(ExecutorAssertPoint.INVALID_FACTORY_ID, twincodeFactoryId, mOldTwincodeFactoryId);

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

                default:
                    break;
            }
        }

        // Wait for reconnection
        if (errorCode == BaseService.ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;

            return;
        }
        mComplete.onGet(errorCode, null);

        stop();
    }

    protected void fireError(@NonNull BaseService.ErrorCode errorCode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "fireError: errorCode=" + errorCode);
        }

        mComplete.onGet(errorCode, mProfile);
        stop();
    }
}
