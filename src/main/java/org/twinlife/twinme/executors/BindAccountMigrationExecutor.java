/*
 *  Copyright (c) 2020-2025 twinlife SA.
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

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.AccountMigration;
import org.twinlife.twinme.models.PairInviteInvocation;
import org.twinlife.twinme.models.PairProtocol;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.7
//
// Called as background operation: must be connected and no timeout.
public class BindAccountMigrationExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "BindAccountMigration..";
    private static final boolean DEBUG = false;

    private static final int UPDATE_TWINCODE_INBOUND = 1 << 2;
    private static final int UPDATE_TWINCODE_INBOUND_DONE = 1 << 3;
    private static final int UPDATE_OBJECT = 1 << 4;
    private static final int UPDATE_OBJECT_DONE = 1 << 5;
    private static final int INVOKE_TWINCODE_OUTBOUND = 1 << 6;
    private static final int INVOKE_TWINCODE_OUTBOUND_DONE = 1 << 7;

    @Nullable
    private final UUID mInvocationId;
    @NonNull
    private final AccountMigration mAccountMigration;
    @Nullable
    private final TwincodeInbound mTwincodeInbound;
    @NonNull
    private final TwincodeOutbound mPeerTwincodeOutbound;
    private final boolean mInvokePeer;

    @Nullable
    private final Consumer<AccountMigration> mConsumer;

    public BindAccountMigrationExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, @NonNull PairInviteInvocation invocation,
                                        @NonNull AccountMigration accountMigration) {
        super(twinmeContextImpl, BaseService.DEFAULT_REQUEST_ID, LOG_TAG);

        mInvocationId = invocation.getId();
        mAccountMigration = accountMigration;
        mPeerTwincodeOutbound = invocation.getTwincodeOutbound();
        mTwincodeInbound = accountMigration.getTwincodeInbound();
        mInvokePeer = !accountMigration.isBound();
        if (mTwincodeInbound == null) {

            mTwinmeContextImpl.assertion(ExecutorAssertPoint.BIND_ACCOUNT_MIGRATION,
                    AssertPoint.create(accountMigration).putInvocationId(mInvocationId));
            mStopped = true;
        }
        mConsumer = null;
    }

    public BindAccountMigrationExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, @NonNull AccountMigration accountMigration,
                                        @NonNull TwincodeOutbound peerTwincodeOutbound, @NonNull Consumer<AccountMigration> consumer) {
        super(twinmeContextImpl, BaseService.DEFAULT_REQUEST_ID, LOG_TAG);

        mInvocationId = null;
        mAccountMigration = accountMigration;
        mTwincodeInbound = accountMigration.getTwincodeInbound();
        mPeerTwincodeOutbound = peerTwincodeOutbound;
        mInvokePeer = true;
        mConsumer = consumer;
    }

    public void start() {
        if (DEBUG) {
            Log.d(LOG_TAG, "start");
        }

        if (!mStopped) {
            super.start();
        } else {
            stop();

            mTwinmeContextImpl.fireOnError(mRequestId, ErrorCode.BAD_REQUEST, null);
        }
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;

            if ((mState & UPDATE_TWINCODE_INBOUND) != 0 && (mState & UPDATE_TWINCODE_INBOUND_DONE) == 0) {
                mState &= ~UPDATE_TWINCODE_INBOUND;
            }
            if ((mState & INVOKE_TWINCODE_OUTBOUND) != 0 && (mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~INVOKE_TWINCODE_OUTBOUND;
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
        // Step 2
        //

        if ((mState & UPDATE_TWINCODE_INBOUND) == 0) {
            mState |= UPDATE_TWINCODE_INBOUND;

            mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mTwincodeInbound, 152);

            List<AttributeNameValue> attributes = new ArrayList<>();
            PairProtocol.setTwincodeAttributePairTwincodeId(attributes, mPeerTwincodeOutbound.getId());

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeInboundService.updateTwincode: twincodeInboundId=" + mTwincodeInbound + " attributes=" + attributes);
            }
            mTwinmeContextImpl.getTwincodeInboundService().updateTwincode(mTwincodeInbound, attributes, null, this::onUpdateTwincodeInbound);
            return;
        }
        if ((mState & UPDATE_TWINCODE_INBOUND_DONE) == 0) {
            return;
        }

        if ((mState & UPDATE_OBJECT) == 0) {
            mState |= UPDATE_OBJECT;

            if (mInvocationId != null) {
                mAccountMigration.setBound(true);
            }
            mAccountMigration.setPeerTwincodeOutbound(mPeerTwincodeOutbound);

            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContext.updateObject: deviceMigration=" + mAccountMigration);
            }
            mTwinmeContextImpl.getRepositoryService().updateObject(mAccountMigration, this::onUpdateObject);
            return;
        }
        if ((mState & UPDATE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Step 4: invoke the peer device migration twincode to bind with the device migration on the other side.
        //
        if (mInvokePeer) {
            if (mAccountMigration.getTwincodeOutboundId() != null) {

                if ((mState & INVOKE_TWINCODE_OUTBOUND) == 0) {
                    mState |= INVOKE_TWINCODE_OUTBOUND;

                    List<BaseService.AttributeNameValue> attributes;
                    attributes = new ArrayList<>();
                    PairProtocol.setInvokeTwincodeActionPairBindAttributeTwincodeId(attributes, mAccountMigration.getTwincodeOutboundId());

                    if (DEBUG) {
                        Log.d(LOG_TAG, "TwincodeOutboundService.invokeTwincode: peerTwincodeOutboundId=" + mPeerTwincodeOutbound.getId() +
                                " attributes=" + attributes);
                    }
                    mTwinmeContextImpl.getTwincodeOutboundService().invokeTwincode(mPeerTwincodeOutbound, TwincodeOutboundService.INVOKE_URGENT, PairProtocol.ACTION_PAIR_INVITE,
                            attributes, this::onInvokeTwincode);
                    return;
                }
                if ((mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                    return;
                }
            }
        }

        //
        // Last Step
        //
        if (mConsumer != null) {
            mConsumer.onGet(ErrorCode.SUCCESS, mAccountMigration);
        }
        mTwinmeContextImpl.onUpdateAccountMigration(mRequestId, mAccountMigration);

        stop();
    }

    private void onUpdateTwincodeInbound(@NonNull ErrorCode errorCode, @Nullable TwincodeInbound twincodeInbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateTwincodeInbound twincodeInbound=" + twincodeInbound);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeInbound == null) {

            onOperationError(UPDATE_TWINCODE_INBOUND, errorCode, null);
            return;
        }

        mState |= UPDATE_TWINCODE_INBOUND_DONE;
        onOperation();
    }

    private void onUpdateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateObject: object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || object == null) {

            onOperationError(UPDATE_OBJECT, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mAccountMigration, 248);
        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, object, mAccountMigration);

        mState |= UPDATE_OBJECT_DONE;
        onOperation();
    }

    private void onInvokeTwincode(@NonNull ErrorCode errorCode, @Nullable UUID invocationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvokeTwincode: invocationId=" + invocationId);
        }

        if (errorCode != ErrorCode.SUCCESS || invocationId == null) {

            onOperationError(INVOKE_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mState |= INVOKE_TWINCODE_OUTBOUND_DONE;
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

        if (operationId == INVOKE_TWINCODE_OUTBOUND) {
            if (errorCode == ErrorCode.ITEM_NOT_FOUND) {

                mState |= INVOKE_TWINCODE_OUTBOUND_DONE;

                return;
            }
        }

        // Mark the executor as stopped before calling the result method either fireOnError() or onGet().
        stop();

        if (mConsumer != null) {
            mConsumer.onGet(errorCode, null);
        } else {
            mTwinmeContextImpl.fireOnError(mRequestId, errorCode, errorParameter);
        }

    }

    protected void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        if (mInvocationId != null) {
            mTwinmeContextImpl.acknowledgeInvocation(mInvocationId, ErrorCode.SUCCESS);
        }

        super.stop();
    }
}
