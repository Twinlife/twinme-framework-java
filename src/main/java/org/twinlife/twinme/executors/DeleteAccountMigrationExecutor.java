/*
 *  Copyright (c) 2014-2025 twinlife SA.
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
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.AccountMigration;
import org.twinlife.twinme.models.PairProtocol;

import java.util.UUID;

import static org.twinlife.twinlife.BaseService.DEFAULT_REQUEST_ID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.6
//

public class DeleteAccountMigrationExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "DeleteAccountMigration";
    private static final boolean DEBUG = false;

    private static final int UNBIND_TWINCODE_INBOUND = 1;
    private static final int UNBIND_TWINCODE_INBOUND_DONE = 1 << 1;
    private static final int INVOKE_TWINCODE_OUTBOUND = 1 << 2;
    private static final int INVOKE_TWINCODE_OUTBOUND_DONE = 1 << 3;
    private static final int DELETE_TWINCODE = 1 << 4;
    private static final int DELETE_TWINCODE_DONE = 1 << 5;
    private static final int DELETE_OBJECT = 1 << 6;
    private static final int DELETE_OBJECT_DONE = 1 << 7;

    @NonNull
    private final AccountMigration mAccountMigration;
    @Nullable
    private final TwincodeInbound mTwincodeInbound;
    @Nullable
    private final TwincodeOutbound mPeerTwincodeOutbound;
    @Nullable
    private final UUID mTwincodeFactoryId;
    @NonNull
    private final Consumer<UUID> mConsumer;

    public DeleteAccountMigrationExecutor(@NonNull TwinmeContextImpl twinmeContextImpl,
                                          @NonNull AccountMigration accountMigration, @NonNull Consumer<UUID> consumer) {
        super(twinmeContextImpl, DEFAULT_REQUEST_ID, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "DeleteDeviceMigrationExecutor: twinmeContextImpl=" + twinmeContextImpl + " deviceMigration=" + accountMigration);
        }

        mAccountMigration = accountMigration;
        mTwincodeInbound = accountMigration.getTwincodeInbound();
        mPeerTwincodeOutbound = accountMigration.getPeerTwincodeOutbound();
        mTwincodeFactoryId = accountMigration.getTwincodeFactoryId();
        mConsumer = consumer;
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
        // Step 1: unbind the inbound twincode.
        //
        if (mTwincodeInbound != null) {

            if ((mState & UNBIND_TWINCODE_INBOUND) == 0) {
                mState |= UNBIND_TWINCODE_INBOUND;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeInboundService.unbindTwincode: twincodeInboundId=" + mTwincodeInbound);
                }
                mTwinmeContextImpl.getTwincodeInboundService().unbindTwincode(mTwincodeInbound, this::onUnbindTwincodeInbound);
                return;
            }
            if ((mState & UNBIND_TWINCODE_INBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2: invoke peer to unbind the deviceMigration on its side.
        //
        if (mPeerTwincodeOutbound != null) {

            if ((mState & INVOKE_TWINCODE_OUTBOUND) == 0) {
                mState |= INVOKE_TWINCODE_OUTBOUND;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.invokeTwincode: peerTwincodeOutboundId=" + mPeerTwincodeOutbound);
                }
                mTwinmeContextImpl.getTwincodeOutboundService().invokeTwincode(mPeerTwincodeOutbound,
                        TwincodeOutboundService.INVOKE_URGENT, PairProtocol.ACTION_PAIR_UNBIND, null, this::onInvokeTwincode);
                return;
            }
            if ((mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3: delete the twincode.
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
        // Step 4: delete the contact object.
        //

        if ((mState & DELETE_OBJECT) == 0) {
            mState |= DELETE_OBJECT;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.deleteObject: objectId=" + mAccountMigration.getId());
            }
            mTwinmeContextImpl.getRepositoryService().deleteObject(mAccountMigration, this::onDeleteObject);

            //
            // Step 4e: remove the peer twincodes from the cache.
            //
            if (mPeerTwincodeOutbound != null) {
                mTwinmeContextImpl.getTwincodeOutboundService().evictTwincodeOutbound(mPeerTwincodeOutbound);
            }
            return;
        }
        if ((mState & DELETE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.onDeleteAccountMigration(DEFAULT_REQUEST_ID, mAccountMigration.getId());
        mConsumer.onGet(ErrorCode.SUCCESS, mAccountMigration.getId());

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

    private void onInvokeTwincode(@NonNull ErrorCode errorCode, @Nullable UUID invocationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvokeTwincode: invocationId=" + invocationId);
        }

        if (errorCode != ErrorCode.SUCCESS || invocationId == null) {

            onOperationError(INVOKE_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mAccountMigration, 245);

        mState |= INVOKE_TWINCODE_OUTBOUND_DONE;
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

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, objectId, mAccountMigration.getId());

        mState |= DELETE_OBJECT_DONE;
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

        // The delete operation succeeds if we get an item not found error.
        if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
            switch (operationId) {
                case UNBIND_TWINCODE_INBOUND:
                    mState |= UNBIND_TWINCODE_INBOUND_DONE;
                    onOperation();
                    return;

                case INVOKE_TWINCODE_OUTBOUND:
                    mState |= INVOKE_TWINCODE_OUTBOUND_DONE;
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

        mConsumer.onGet(errorCode, null);

        stop();
    }
}
