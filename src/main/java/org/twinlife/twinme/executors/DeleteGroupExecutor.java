/*
 *  Copyright (c) 2018-2024 twinlife SA.
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
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Group;

import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.7
//
// User foreground operation: must be connected with a timeout if connection does not work.
// BUT, when it is called from DeleteSpace, the timeout is set to infinity.

/**
 * Executor to delete the group object with the group member.
 */
public class DeleteGroupExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "DeleteGroupExecutor";
    private static final boolean DEBUG = false;

    private static final int UNBIND_MEMBER_TWINCODE_INBOUND = 1;
    private static final int UNBIND_MEMBER_TWINCODE_INBOUND_DONE = 1 << 1;
    private static final int DELETE_MEMBER_TWINCODE = 1 << 2;
    private static final int DELETE_MEMBER_TWINCODE_DONE = 1 << 3;
    private static final int DELETE_IDENTITY_IMAGE = 1 << 4;
    private static final int DELETE_IDENTITY_IMAGE_DONE = 1 << 5;
    private static final int DELETE_GROUP_TWINCODE = 1 << 6;
    private static final int DELETE_GROUP_TWINCODE_DONE = 1 << 7;
    private static final int DELETE_GROUP_IMAGE = 1 << 8;
    private static final int DELETE_GROUP_IMAGE_DONE = 1 << 9;
    private static final int DELETE_OBJECT = 1 << 10;
    private static final int DELETE_OBJECT_DONE = 1 << 11;

    @NonNull
    private final Group mGroup;
    private final TwincodeInbound mTwincodeInbound;
    @Nullable
    private final ImageId mIdentityAvatarId;
    @Nullable
    private final ImageId mGroupAvatarId;
    @Nullable
    private final UUID mGroupTwincodeFactoryId;
    @Nullable
    private final UUID mMemberTwincodeFactoryId;
    @Nullable
    private final TwincodeOutbound mGroupTwincodeOutbound;

    public DeleteGroupExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Group group, long timeout) {
        super(twinmeContextImpl, requestId, LOG_TAG, timeout);
        if (DEBUG) {
            Log.d(LOG_TAG, "DeleteGroupExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId + " group=" + group);
        }

        mGroup = group;
        mTwincodeInbound = group.getTwincodeInbound();
        mIdentityAvatarId = group.getIdentityAvatarId();
        mGroupAvatarId = group.getAvatarId();
        mMemberTwincodeFactoryId = group.getMemberTwincodeFactoryId();
        mGroupTwincodeFactoryId = group.getGroupTwincodeFactoryId();
        mGroupTwincodeOutbound = group.getGroupTwincodeOutbound();
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & UNBIND_MEMBER_TWINCODE_INBOUND) != 0 && (mState & UNBIND_MEMBER_TWINCODE_INBOUND_DONE) == 0) {
                mState &= ~UNBIND_MEMBER_TWINCODE_INBOUND;
            }
            if ((mState & DELETE_MEMBER_TWINCODE) != 0 && (mState & DELETE_MEMBER_TWINCODE_DONE) == 0) {
                mState &= ~DELETE_MEMBER_TWINCODE;
            }
            if ((mState & DELETE_IDENTITY_IMAGE) != 0 && (mState & DELETE_IDENTITY_IMAGE_DONE) == 0) {
                mState &= ~DELETE_IDENTITY_IMAGE;
            }
            if ((mState & DELETE_GROUP_TWINCODE) != 0 && (mState & DELETE_GROUP_TWINCODE_DONE) == 0) {
                mState &= ~DELETE_GROUP_TWINCODE;
            }
            if ((mState & DELETE_GROUP_IMAGE) != 0 && (mState & DELETE_GROUP_IMAGE_DONE) == 0) {
                mState &= ~DELETE_GROUP_IMAGE;
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
        // Step 1: unbind the group member inbound twincode.
        //
        if (mTwincodeInbound != null) {
            if ((mState & UNBIND_MEMBER_TWINCODE_INBOUND) == 0) {
                mState |= UNBIND_MEMBER_TWINCODE_INBOUND;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeInboundService.unbindTwincode: twincodeInboundId=" + mGroup.getTwincodeInboundId());
                }
                mTwinmeContextImpl.getTwincodeInboundService().unbindTwincode(mTwincodeInbound, this::onUnbindTwincodeInbound);
                return;
            }
            if ((mState & UNBIND_MEMBER_TWINCODE_INBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2a: delete the group member twincode.
        //
        if (mMemberTwincodeFactoryId != null) {
            if ((mState & DELETE_MEMBER_TWINCODE) == 0) {
                mState |= DELETE_MEMBER_TWINCODE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeFactoryService.deleteTwincode: twincodeFactoryId=" + mMemberTwincodeFactoryId);
                }
                mTwinmeContextImpl.getTwincodeFactoryService().deleteTwincode(mMemberTwincodeFactoryId, this::onDeleteMemberTwincode);
                return;
            }
            if ((mState & DELETE_MEMBER_TWINCODE_DONE) == 0) {
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
        // Step 3a: delete the group twincode.
        //

        if (mGroupTwincodeFactoryId != null) {
            if ((mState & DELETE_GROUP_TWINCODE) == 0) {
                mState |= DELETE_GROUP_TWINCODE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeFactoryService.deleteTwincode: twincodeFactoryId=" + mGroupTwincodeFactoryId);
                }
                mTwinmeContextImpl.getTwincodeFactoryService().deleteTwincode(mGroupTwincodeFactoryId, this::onDeleteGroupTwincode);
                return;
            }
            if ((mState & DELETE_GROUP_TWINCODE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3b: delete the group image (either local or local+remote).
        //
        if (mGroupAvatarId != null) {

            if ((mState & DELETE_GROUP_IMAGE) == 0) {
                mState |= DELETE_GROUP_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.deleteImage: imageId=" + mGroupAvatarId);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.deleteImage(mGroupAvatarId, (ErrorCode status, ImageId imageId) -> {
                    mState |= DELETE_GROUP_IMAGE_DONE;
                    onOperation();
                });
                return;
            }
            if ((mState & DELETE_GROUP_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 4: delete the group object.
        //

        if ((mState & DELETE_OBJECT) == 0) {
            mState |= DELETE_OBJECT;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.deleteObject: objectId=" + mGroup.getId());
            }
            mTwinmeContextImpl.getRepositoryService().deleteObject(mGroup, this::onDeleteObject);

            // Remove the group twincode from the cache.
            if (mGroupTwincodeOutbound != null) {
                mTwinmeContextImpl.getTwincodeOutboundService().evictTwincodeOutbound(mGroupTwincodeOutbound);
            }
            return;
        }
        if ((mState & DELETE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.onDeleteGroup(mRequestId, mGroup.getId());

        stop();
    }

    private void onUnbindTwincodeInbound(@NonNull ErrorCode errorCode, @Nullable UUID twincodeInboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUnbindTwincodeInbound: twincodeInboundId=" + twincodeInboundId);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeInboundId == null) {

            onOperationError(UNBIND_MEMBER_TWINCODE_INBOUND, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeInboundId, mGroup.getTwincodeInboundId());

        mState |= UNBIND_MEMBER_TWINCODE_INBOUND_DONE;
        onOperation();
    }

    private void onDeleteMemberTwincode(@NonNull ErrorCode errorCode, @Nullable UUID twincodeFactoryId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteMemberTwincode: twincodeFactoryId=" + twincodeFactoryId);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeFactoryId == null) {

            onOperationError(DELETE_MEMBER_TWINCODE, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_FACTORY_ID, twincodeFactoryId, mGroup.getMemberTwincodeFactoryId());

        mState |= DELETE_MEMBER_TWINCODE_DONE;
        onOperation();
    }

    private void onDeleteGroupTwincode(@NonNull ErrorCode errorCode, @Nullable UUID twincodeFactoryId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteGroupTwincode: twincodeFactoryId=" + twincodeFactoryId);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeFactoryId == null) {

            onOperationError(DELETE_GROUP_TWINCODE, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_FACTORY_ID, twincodeFactoryId, mGroup.getGroupTwincodeFactoryId());

        mState |= DELETE_GROUP_TWINCODE_DONE;
        onOperation();
    }

    private void onDeleteObject(@NonNull ErrorCode errorCode, @Nullable UUID objectId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteObject: objectId=" + objectId);
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, objectId, mGroup.getId());

        mState |= DELETE_OBJECT_DONE;
        mGroup.markDeleted();
        onOperation();
    }

    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // The delete operation succeeds if we get an item not found error.
        if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
            switch (operationId) {

                case UNBIND_MEMBER_TWINCODE_INBOUND:
                    mState |= UNBIND_MEMBER_TWINCODE_INBOUND_DONE;
                    onOperation();
                    return;

                case DELETE_MEMBER_TWINCODE:
                    mState |= DELETE_MEMBER_TWINCODE_DONE;
                    onOperation();
                    return;

                case DELETE_GROUP_TWINCODE:
                    mState |= DELETE_GROUP_TWINCODE_DONE;
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
