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
import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.TwincodeFactoryService.TwincodeFactory;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContext.Consumer;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.TwinmeEngineImpl;
import org.twinlife.twinme.models.EngineCard.GroupProvisioning;
import org.twinlife.twinme.models.GroupProvisioningDescriptor;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.3
//

/**
 * Executor for the creation of a group.
 *
 * - get the user default profile to get the member name and avatar,
 * - create the group member twincode with the member name, avatar and inviter member id (optional),
 * - get the group member twincode outbound,
 * - create the group twincode with the group name, the group member twincode and group avatar (optional),
 * - get the group twincode outbound,
 * - create the group local object in the repository,
 * - create the group conversation object in the conversation service.
 *
 * The step4 (create the group twincode) is optional and is not made when a user joins the group.
 *
 * The inviter's member twincode outbound id is stored in the group member's twincode in the "invited-by" attribute.
 * The group creator's member twincode outbound id is stored in the group twincode in the "created-by" attribute.
 */
public class CreateGroupProvisioningExecutor {
    private static final String LOG_TAG = "CreateGroupExecutor";
    private static final boolean DEBUG = false;

    private static final int CREATE_ADMIN_TWINCODE = 1;
    private static final int CREATE_ADMIN_TWINCODE_DONE = 1 << 2;
    private static final int CREATE_GROUP_TWINCODE = 1 << 3;
    private static final int CREATE_GROUP_TWINCODE_DONE = 1 << 4;

    private class TwinmeContextObserver extends TwinmeContext.DefaultObserver {

        @Override
        public void onTwinlifeReady() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeReady");
            }

            CreateGroupProvisioningExecutor.this.onTwinlifeReady();
            onOperation();
        }

        @Override
        public void onTwinlifeOnline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onTwinlifeOnline");
            }

            CreateGroupProvisioningExecutor.this.onTwinlifeOnline();
            onOperation();
        }

        @Override
        public void onTwinlifeOffline() {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinlifeObserver.onTwinlifeOffline");
            }

            CreateGroupProvisioningExecutor.this.onTwinlifeOffline();
        }

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            Integer operationId = mRequestIds.remove(requestId);
            if (operationId != null) {
                CreateGroupProvisioningExecutor.this.onError(operationId, errorCode, errorParameter);
                onOperation();
            }
        }
    }

    @NonNull
    private final TwinmeContextImpl mTwinmeContextImpl;
    private final long mRequestId;
    private final TwinmeEngineImpl mTwinmeEngineImpl;
    @NonNull
    private final String mName;
    @NonNull
    private final String mIdentityName;
    @Nullable
    private final Bitmap mIdentityAvatar;
    @Nullable
    private final Bitmap mAvatar;
    @NonNull
    private final Consumer<GroupProvisioning> mConsumer;
    private TwincodeFactory mGroupTwincodeFactory;
    private TwincodeFactory mAdminTwincodeFactory;

    private int mState = 0;
    @SuppressLint("UseSparseArrays")
    private final HashMap<Long, Integer> mRequestIds = new HashMap<>();
    private boolean mStopped = false;
    private boolean mRestarted = false;

    private final TwinmeContextObserver mTwinmeContextObserver;

    public CreateGroupProvisioningExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                           @NonNull TwinmeEngineImpl twinmeEngineImpl,
                                           @NonNull GroupProvisioningDescriptor group,
                                           @NonNull Consumer<GroupProvisioning> consumer) {

        mTwinmeContextImpl = twinmeContextImpl;
        mTwinmeEngineImpl = twinmeEngineImpl;
        mRequestId = requestId;
        mName = group.groupName;
        mAvatar = group.groupAvatar;
        mIdentityName = group.groupName;
        mIdentityAvatar = group.groupAvatar;
        mConsumer = consumer;

        mTwinmeContextObserver = new TwinmeContextObserver();
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
    }

    private void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;
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
        // Step 1: create the group admin twincode.
        //

        boolean completedStep1 = true;

        if ((mState & CREATE_ADMIN_TWINCODE) == 0) {
            mState |= CREATE_ADMIN_TWINCODE;
            completedStep1 = false;

            mTwinmeContextImpl.assertNotNull(LOG_TAG, mIdentityName);

            List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
            PairProtocol.setTwincodeAttributePair(twincodeFactoryAttributes);

            List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
            TwinmeAttributes.setTwincodeAttributeName(twincodeOutboundAttributes, mIdentityName);
            // if (mIdentityAvatar != null) {
            //    TwinmeAttributes.setTwincodeAttributeImage(twincodeOutboundAttributes, mIdentityAvatar);
            // }

            long requestId = newOperation(CREATE_ADMIN_TWINCODE);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: requestId=" + requestId
                        + " twincodeFactoryAttributes=" + twincodeFactoryAttributes
                        + "twincodeInboundAttributes=null" + "twincodeOutboundAttributes=" + twincodeOutboundAttributes);
            }
            mTwinmeEngineImpl.createTwincode(requestId, twincodeFactoryAttributes, null,
                    twincodeOutboundAttributes, null,
                    this::onCreateAdminTwincode);
        }
        if ((mState & CREATE_ADMIN_TWINCODE_DONE) == 0) {
            completedStep1 = false;
        }

        if (!completedStep1) {

            return;
        }

        //
        // Step 2: create the group twincode.
        //

        boolean completedStep2 = true;

        if ((mState & CREATE_GROUP_TWINCODE) == 0) {
            mState |= CREATE_GROUP_TWINCODE;
            completedStep2 = false;

            mTwinmeContextImpl.assertNotNull(LOG_TAG, mIdentityName);

            List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
            PairProtocol.setTwincodeAttributePair(twincodeFactoryAttributes);

            List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
            TwinmeAttributes.setTwincodeAttributeName(twincodeOutboundAttributes, mName);
            //if (mAvatar != null) {
            //    TwinmeAttributes.setTwincodeAttributeImage(twincodeOutboundAttributes, mAvatar);
            //}

            long requestId = newOperation(CREATE_GROUP_TWINCODE);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: requestId=" + requestId
                        + " twincodeFactoryAttributes=" + twincodeFactoryAttributes
                        + "twincodeInboundAttributes=null" + "twincodeOutboundAttributes=" + twincodeOutboundAttributes);
            }
            mTwinmeEngineImpl.createTwincode(requestId, twincodeFactoryAttributes, null,
                    twincodeOutboundAttributes, null,
                    this::onCreateGroupTwincode);
        }
        if ((mState & CREATE_GROUP_TWINCODE_DONE) == 0) {
            completedStep2 = false;
        }

        if (!completedStep2) {

            return;
        }

        GroupProvisioning group = new GroupProvisioning(mGroupTwincodeFactory.getTwincodeInboundId(),
                mGroupTwincodeFactory.getTwincodeOutboundId(),
                mAdminTwincodeFactory.getTwincodeInboundId(),
                mAdminTwincodeFactory.getTwincodeOutboundId(), -1L);
        mConsumer.accept(group);

        stop();
    }

    private void onCreateGroupTwincode(@Nullable BaseService.ErrorCode status, @Nullable TwincodeFactory twincodeFactory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateGroupTwincode: factory=" + twincodeFactory);
        }

        if (status != ErrorCode.SUCCESS || twincodeFactory == null) {
            onError(CREATE_GROUP_TWINCODE, status, null);
            return;
        }

        mState |= CREATE_GROUP_TWINCODE_DONE;
        mGroupTwincodeFactory = twincodeFactory;
        onOperation();
    }

    private void onCreateAdminTwincode(@Nullable BaseService.ErrorCode status, @Nullable TwincodeFactory twincodeFactory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateAdminTwincode: factory=" + twincodeFactory);
        }

        if (status != ErrorCode.SUCCESS || twincodeFactory == null) {
            onError(CREATE_ADMIN_TWINCODE, status, null);
            return;
        }

        mState |= CREATE_ADMIN_TWINCODE_DONE;
        mAdminTwincodeFactory = twincodeFactory;
        onOperation();
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

        mTwinmeContextImpl.removeObserver(mTwinmeContextObserver);
    }
}
