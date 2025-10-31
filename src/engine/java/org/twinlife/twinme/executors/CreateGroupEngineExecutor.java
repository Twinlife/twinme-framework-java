/*
 *  Copyright (c) 2018-2023 twinlife SA.
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
import org.twinlife.twinlife.ConversationService.GroupConversation;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeOutboundService.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.EngineCard.GroupProvisioning;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.HashMap;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.3
//

/**
 * Executor for the creation of a group on the engine.
 *
 * - we don't create the group and member twincodes since they are provided through the EngineCard,
 * - we have to bind the group twincode and member twincode,
 * - get the group member twincode outbound,
 * - get the group twincode outbound,
 * - create the group local object in the repository,
 * - create the group conversation object in the conversation service.
 *
 */
public class CreateGroupEngineExecutor {
    private static final String LOG_TAG = "CreateGroupEngineExecutor";
    private static final boolean DEBUG = false;

    private static final int BIND_MEMBER_TWINCODE = 1 << 2;
    private static final int BIND_MEMBER_TWINCODE_DONE = 1 << 3;
    private static final int GET_MEMBER_TWINCODE_OUTBOUND = 1 << 4;
    private static final int GET_MEMBER_TWINCODE_OUTBOUND_DONE = 1 << 5;
    private static final int BIND_GROUP_TWINCODE = 1 << 6;
    private static final int BIND_GROUP_TWINCODE_DONE = 1 << 7;
    private static final int GET_GROUP_TWINCODE_OUTBOUND = 1 << 8;
    private static final int GET_GROUP_TWINCODE_OUTBOUND_DONE = 1 << 9;
    private static final int CREATE_GROUP_OBJECT = 1 << 10;
    private static final int CREATE_GROUP_OBJECT_DONE = 1 << 11;

    private class TwinmeContextObserver extends TwinmeContext.DefaultObserver {

        @Override
        public void onTwinlifeReady() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeReady");
            }

            CreateGroupEngineExecutor.this.onTwinlifeReady();
            onOperation();
        }

        @Override
        public void onTwinlifeOnline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeOnline");
            }

            CreateGroupEngineExecutor.this.onTwinlifeOnline();
            onOperation();
        }

        @Override
        public void onTwinlifeOffline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinlifeObserver.onTwinlifeOffline");
            }

            CreateGroupEngineExecutor.this.onTwinlifeOffline();
        }

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                CreateGroupEngineExecutor.this.onError(operationId, errorCode, errorParameter);
                onOperation();
            }
        }
    }

    private class RepositoryServiceObserver extends RepositoryService.DefaultServiceObserver {

        @Override
        public void onCreateObject(long requestId, @NonNull RepositoryService.Object object) {
            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryServiceObserver.onCreateObject: requestId=" + requestId + " object=" + object);
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                CreateGroupEngineExecutor.this.onCreateObject(object);
                onOperation();
            }
        }

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryServiceObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                CreateGroupEngineExecutor.this.onError(operationId, errorCode, errorParameter);
                onOperation();
            }
        }
    }

    @NonNull
    private final TwinmeContextImpl mTwinmeContextImpl;
    private final long mRequestId;
    private Group mGroup;
    private String mName;
    private final boolean mIsOwner;
    @NonNull
    private final GroupProvisioning mGroupProvisioning;
    @NonNull
    private final Space mSpace;
    private TwincodeOutbound mMemberTwincodeOutbound;
    private TwincodeOutbound mGroupTwincodeOutbound;

    private int mState = 0;
    @SuppressLint("UseSparseArrays")
    private final HashMap<Long, Integer> mRequestIds = new HashMap<>();
    private boolean mStopped = false;
    private boolean mRestarted = false;

    private final TwinmeContextObserver mTwinmeContextObserver;
    private final RepositoryServiceObserver mRepositoryServiceObserver;

    public CreateGroupEngineExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Space space,
                                     @NonNull GroupProvisioning groupProvisioning) {

        mTwinmeContextImpl = twinmeContextImpl;
        mRequestId = requestId;
        mSpace = space;
        mGroupProvisioning = groupProvisioning;
        mIsOwner = true;

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

            if ((mState & BIND_GROUP_TWINCODE) != 0 && (mState & BIND_GROUP_TWINCODE_DONE) == 0) {
                mState &= ~BIND_GROUP_TWINCODE;
            }
            if ((mState & BIND_MEMBER_TWINCODE) != 0 && (mState & BIND_MEMBER_TWINCODE_DONE) == 0) {
                mState &= ~BIND_MEMBER_TWINCODE;
            }
            if ((mState & GET_MEMBER_TWINCODE_OUTBOUND) != 0 && (mState & GET_MEMBER_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~GET_MEMBER_TWINCODE_OUTBOUND;
            }
            if ((mState & GET_GROUP_TWINCODE_OUTBOUND) != 0 && (mState & GET_GROUP_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~GET_GROUP_TWINCODE_OUTBOUND;
            }
            if ((mState & CREATE_GROUP_OBJECT) != 0 && (mState & CREATE_GROUP_OBJECT_DONE) == 0) {
                mState &= ~CREATE_GROUP_OBJECT;
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
        // Step 1: bind the group twincode inbound.
        //

        boolean completedStep1 = true;
        if ((mState & BIND_GROUP_TWINCODE) == 0) {
            mState |= BIND_GROUP_TWINCODE;
            completedStep1 = false;

            mTwinmeContextImpl.assertNotNull(LOG_TAG, mGroupProvisioning.groupTwincodeInId);

            long requestId = newOperation(BIND_GROUP_TWINCODE);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeInboundService.bindTwincode: requestId=" + requestId + " twincodeInboundId=" + mGroupProvisioning.groupTwincodeInId);
            }
            mTwinmeContextImpl.getTwincodeInboundService().bindTwincode(requestId, mGroupProvisioning.groupTwincodeInId,
                    this::onBindGroupTwincode);
        }
        if ((mState & BIND_GROUP_TWINCODE_DONE) == 0) {
            completedStep1 = false;
        }
        if (!completedStep1) {

            return;
        }

        //
        // Step 2: bind the group member twincode inbound.
        //

        boolean completedStep2 = true;
        if ((mState & BIND_MEMBER_TWINCODE) == 0) {
            mState |= BIND_MEMBER_TWINCODE;
            completedStep2 = false;

            mTwinmeContextImpl.assertNotNull(LOG_TAG, mGroupProvisioning.adminMemberTwincodeInId);

            long requestId = newOperation(BIND_MEMBER_TWINCODE);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeInboundService.bindTwincode: requestId=" + requestId + " twincodeInboundId=" + mGroupProvisioning.adminMemberTwincodeInId);
            }
            mTwinmeContextImpl.getTwincodeInboundService().bindTwincode(requestId, mGroupProvisioning.adminMemberTwincodeInId, this::onBindMemberTwincode);
        }
        if ((mState & BIND_MEMBER_TWINCODE_DONE) == 0) {
            completedStep2 = false;
        }
        if (!completedStep2) {

            return;
        }

        //
        // Step 3: get the group member twincode outbound.
        //

        boolean completedStep3 = true;
        if ((mState & GET_MEMBER_TWINCODE_OUTBOUND) == 0) {
            mState |= GET_MEMBER_TWINCODE_OUTBOUND;
            completedStep3 = false;

            mTwinmeContextImpl.assertNotNull(LOG_TAG, mGroupProvisioning.adminMemberTwincodeOutId);

            long requestId = newOperation(GET_MEMBER_TWINCODE_OUTBOUND);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeOutboundService.getTwincode: requestId=" + requestId
                        + " twincodeOutboundId=" + mGroupProvisioning.adminMemberTwincodeOutId);
            }
            mTwinmeContextImpl.getTwincodeOutboundService().getTwincode(requestId,
                    mGroupProvisioning.adminMemberTwincodeOutId, TwincodeOutboundService.REFRESH_PERIOD,
                    (ErrorCode status, TwincodeOutbound twincodeOutbound) -> {
                mRequestIds.remove(requestId);
                onGetMemberTwincodeOutbound(status, twincodeOutbound);
                onOperation();
            });
        }
        if ((mState & GET_MEMBER_TWINCODE_OUTBOUND_DONE) == 0) {
            completedStep3 = false;
        }
        if (!completedStep3) {

            return;
        }

        //
        // Step 5: get the group twincode outbound.
        //

        boolean completedStep5 = true;
        if ((mState & GET_GROUP_TWINCODE_OUTBOUND) == 0) {
            mState |= GET_GROUP_TWINCODE_OUTBOUND;
            completedStep5 = false;

            mTwinmeContextImpl.assertNotNull(LOG_TAG, mGroupProvisioning.groupTwincodeOutId);

            long requestId = newOperation(GET_GROUP_TWINCODE_OUTBOUND);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeOutboundService.getTwincode: requestId=" + requestId
                        + " twincodeOutboundId=" + mGroupProvisioning.groupTwincodeOutId);
            }
            mTwinmeContextImpl.getTwincodeOutboundService().getTwincode(requestId,
                    mGroupProvisioning.groupTwincodeOutId, TwincodeOutboundService.NO_REFRESH_PERIOD,
                    (ErrorCode status, TwincodeOutbound twincodeOutbound) -> {
                mRequestIds.remove(requestId);
                onGetGroupTwincodeOutbound(status, twincodeOutbound);
                onOperation();
            });
        }
        if ((mState & GET_GROUP_TWINCODE_OUTBOUND_DONE) == 0) {
            completedStep5 = false;
        }
        if (!completedStep5) {

            return;
        }

        //
        // Step 6: create the group object that links the group ID and the member twincode.
        //

        boolean completedStep6 = true;

        if ((mState & CREATE_GROUP_OBJECT) == 0) {
            mState |= CREATE_GROUP_OBJECT;
            completedStep6 = false;

            mTwinmeContextImpl.assertNotNull(LOG_TAG, mMemberTwincodeOutbound);

            Group group = new Group(mName, mGroupProvisioning.groupTwincodeOutId, null,
                    mMemberTwincodeOutbound.getId(),
                    mGroupProvisioning.adminMemberTwincodeInId,
                    mGroupProvisioning.adminMemberTwincodeOutId, mSpace);

            long requestId = newOperation(CREATE_GROUP_OBJECT);
            String content = group.serialize(mTwinmeContextImpl.getRepositoryService());
            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.createObject: requestId=" + requestId + " group=" + group);
            }
            mTwinmeContextImpl.getRepositoryService().createObject(requestId, RepositoryService.AccessRights.PRIVATE,
                    group.getSchemaId(), group.getSchemaVersion(),
                    group.getSerializer(), group.isImmutable(), group.getKey(), content, null);
        }
        if ((mState & CREATE_GROUP_OBJECT_DONE) == 0) {
            completedStep6 = false;
        }

        if (!completedStep6) {

            return;
        }

        //
        // Last Step: create the group conversation.
        //
        GroupConversation conversation = mTwinmeContextImpl.getConversationService().createGroup(mGroup.getTwincodeOutboundId(),
                mGroup.getTwincodeInboundId(), mGroup.getGroupTwincodeOutboundId(), mGroup.getId(), mIsOwner);

        mTwinmeContextImpl.assertNotNull(LOG_TAG, mGroup);

        mTwinmeContextImpl.onCreateGroup(mRequestId, mGroup, conversation);

        stop();
    }

    private void onBindGroupTwincode(@Nullable BaseService.ErrorCode status, @Nullable UUID twincodeInboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBindGroupTwincode: twincodeInboundId=" + twincodeInboundId);
        }

        if (status != ErrorCode.SUCCESS || twincodeInboundId == null) {
            onError(BIND_GROUP_TWINCODE, status, null);
            return;
        }

        mState |= BIND_GROUP_TWINCODE_DONE;
        onOperation();
    }

    private void onBindMemberTwincode(@Nullable BaseService.ErrorCode status, @Nullable UUID twincodeInboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBindMemberTwincode: twincodeInboundId=" + twincodeInboundId);
        }

        if (status != ErrorCode.SUCCESS || twincodeInboundId == null) {
            onError(BIND_MEMBER_TWINCODE, status, null);
            return;
        }

        mState |= BIND_MEMBER_TWINCODE_DONE;
        onOperation();
    }

    private void onGetGroupTwincodeOutbound(@NonNull ErrorCode status, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetGroupTwincodeOutbound: twincodeOutbound=" + twincodeOutbound);
        }

        if (status != ErrorCode.SUCCESS || twincodeOutbound == null) {
            onError(GET_GROUP_TWINCODE_OUTBOUND, status, mGroupProvisioning.groupTwincodeOutId.toString());
            return;
        }
        if ((mState & GET_GROUP_TWINCODE_OUTBOUND_DONE) != 0) {

            return;
        }
        mState |= GET_GROUP_TWINCODE_OUTBOUND_DONE;

        mTwinmeContextImpl.assertEqual(LOG_TAG, twincodeOutbound.getId(), mGroupProvisioning.groupTwincodeOutId);

        mGroupTwincodeOutbound = twincodeOutbound;
        mName = TwinmeAttributes.getName(twincodeOutbound);
    }

    private void onGetMemberTwincodeOutbound(@NonNull ErrorCode status, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetMemberTwincodeOutbound: twincodeOutbound=" + twincodeOutbound);
        }

        if (status != ErrorCode.SUCCESS || twincodeOutbound == null) {
            onError(GET_MEMBER_TWINCODE_OUTBOUND, status, mGroupProvisioning.adminMemberTwincodeOutId.toString());
            return;
        }
        if ((mState & GET_MEMBER_TWINCODE_OUTBOUND_DONE) != 0) {

            return;
        }
        mState |= GET_MEMBER_TWINCODE_OUTBOUND_DONE;

        mTwinmeContextImpl.assertEqual(LOG_TAG, twincodeOutbound.getId(), mGroupProvisioning.adminMemberTwincodeOutId);

        mMemberTwincodeOutbound = twincodeOutbound;
    }

    private void onCreateObject(@NonNull RepositoryService.Object object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateObject: object=" + object);
        }

        if ((mState & CREATE_GROUP_OBJECT_DONE) != 0) {

            return;
        }
        mState |= CREATE_GROUP_OBJECT_DONE;

        mGroup = Group.deserialize(mTwinmeContextImpl.getRepositoryService(), object);
        if (mGroup != null) {
            mGroup.setGroupTwincodeOutbound(mGroupTwincodeOutbound);
            mGroup.setMemberTwincodeOutbound(mMemberTwincodeOutbound);
            mGroup.setSpace(mSpace);
        } else {

            mTwinmeContextImpl.assertNotReached(LOG_TAG, "onCreateObject object=" + object);

            onError(CREATE_GROUP_OBJECT, ErrorCode.BAD_REQUEST, object.getId().toString());
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
