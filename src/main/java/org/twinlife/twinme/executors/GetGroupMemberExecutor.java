/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.graphics.Bitmap;
import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupMember;
import org.twinlife.twinme.models.Originator;

import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.15
//

/**
 * Executor to retrieve the identification of a group member from the group member twincode.
 *
 * Do not use the AbstractConnectedExecutor because we rely on the database cache.
 */
public class GetGroupMemberExecutor extends AbstractTwinmeExecutor {
    private static final String LOG_TAG = "GetGroupMemberExecutor";
    private static final boolean DEBUG = false;

    private static final int GET_MEMBER_TWINCODE_OUTBOUND = 1;
    private static final int GET_MEMBER_TWINCODE_OUTBOUND_DONE = 1 << 1;
    private static final int GET_MEMBER_IMAGE = 1 << 2;
    private static final int GET_MEMBER_IMAGE_DONE = 1 << 3;

    @NonNull
    private final UUID mMemberTwincodeOutboundId;
    @NonNull
    private final Originator mOwner;
    @NonNull
    private final Consumer<GroupMember> mConsumer;
    private GroupMember mGroupMember;
    private ImageId mMemberAvatarId;

    public GetGroupMemberExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, @NonNull Originator owner,
                                  @NonNull UUID memberTwincodeId, @NonNull Consumer<GroupMember> consumer) {
        super(twinmeContextImpl, 0, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "GetGroupMemberExecutor: twinmeContextImpl=" + twinmeContextImpl
                    + " memberTwincodeId=" + memberTwincodeId);
        }

        mOwner = owner;
        mMemberTwincodeOutboundId = memberTwincodeId;
        mConsumer = consumer;
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;
            mState = 0;
        }
        onOperation();
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
        // Step 1: get the twincode that corresponds to the group member.
        //

        if ((mState & GET_MEMBER_TWINCODE_OUTBOUND) == 0) {
            mState |= GET_MEMBER_TWINCODE_OUTBOUND;

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeOutboundService.getTwincode: twincodeOutboundId=" + mMemberTwincodeOutboundId);
            }
            mTwinmeContextImpl.getTwincodeOutboundService().getTwincode(mMemberTwincodeOutboundId, TwincodeOutboundService.REFRESH_PERIOD,
                    this::onGetTwincodeOutbound);
            return;
        }
        if ((mState & GET_MEMBER_TWINCODE_OUTBOUND_DONE) == 0) {
            return;
        }

        //
        // Step 2: get the group member image so that we have it in the cache when we are done.
        //
        if (mMemberAvatarId != null) {

            if ((mState & GET_MEMBER_IMAGE) == 0) {
                mState |= GET_MEMBER_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.getImage: imageId=" + mMemberAvatarId);
                }
                mTwinmeContextImpl.getImageService().getImageFromServer(mMemberAvatarId, ImageService.Kind.THUMBNAIL, (ErrorCode error, Bitmap avatar) -> {});
                mState |= GET_MEMBER_IMAGE_DONE;
                onOperation();
                return;
            }
            if ((mState & GET_MEMBER_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.onGetGroupMember(mGroupMember, mConsumer);

        stop();
    }

    private void onGetTwincodeOutbound(@NonNull ErrorCode status, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetTwincodeOutbound: twincodeOutbound=" + twincodeOutbound);
        }

        if (status != ErrorCode.SUCCESS || twincodeOutbound == null) {

            onOperationError(GET_MEMBER_TWINCODE_OUTBOUND, status, mMemberTwincodeOutboundId.toString());
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeOutbound.getId(), mMemberTwincodeOutboundId);

        mState |= GET_MEMBER_TWINCODE_OUTBOUND_DONE;

        mGroupMember = new GroupMember(mOwner, twincodeOutbound);
        mMemberAvatarId = mGroupMember.getAvatarId();
        onOperation();
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

        if (operationId == GET_MEMBER_TWINCODE_OUTBOUND && errorCode == ErrorCode.ITEM_NOT_FOUND) {
            UUID uuid = Utils.UUIDFromString(errorParameter);

            mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, mMemberTwincodeOutboundId, uuid);

            mState |= GET_MEMBER_TWINCODE_OUTBOUND_DONE;

            if (mOwner.isGroup()) {
                Group group = (Group) mOwner;
                // The member twincode is invalid, remove the member from the group.
                mTwinmeContextImpl.getConversationService().leaveGroup(BaseService.DEFAULT_REQUEST_ID, group, mMemberTwincodeOutboundId);
            }
        }

        mConsumer.onGet(errorCode, null);

        stop();
    }
}
