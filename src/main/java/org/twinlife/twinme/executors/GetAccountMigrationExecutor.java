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
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.AccountMigration;
import org.twinlife.twinme.models.AccountMigrationFactory;

import java.util.List;
import java.util.UUID;

import static org.twinlife.twinlife.BaseService.DEFAULT_REQUEST_ID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.3
//

public class GetAccountMigrationExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "GetDeviceMigration..";
    private static final boolean DEBUG = false;

    private static final int GET_OBJECT = 1;
    private static final int GET_OBJECT_DONE = 1 << 1;
    private static final int REFRESH_PEER_TWINCODE_OUTBOUND = 1 << 2;
    private static final int REFRESH_PEER_TWINCODE_OUTBOUND_DONE = 1 << 3;

    @NonNull
    private final UUID mAccountMigrationId;
    @NonNull
    private final Consumer<AccountMigration> mConsumer;
    @Nullable
    private TwincodeOutbound mPeerTwincodeOutbound;
    private boolean mToBeDeleted = false;
    @Nullable
    private AccountMigration mAccountMigration;

    public GetAccountMigrationExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, @NonNull UUID deviceMigrationId,
                                       @NonNull Consumer<AccountMigration> consumer) {
        super(twinmeContextImpl, DEFAULT_REQUEST_ID, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "GetAccountMigrationExecutor: twinmeContextImpl=" + twinmeContextImpl + " deviceMigrationId=" + deviceMigrationId);
        }

        mAccountMigrationId = deviceMigrationId;
        mConsumer = consumer;

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.PARAMETER, mAccountMigrationId, 69);
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & REFRESH_PEER_TWINCODE_OUTBOUND) != 0 && (mState & REFRESH_PEER_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~REFRESH_PEER_TWINCODE_OUTBOUND;
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
        // Step 1: get the object from the repository.
        //
        if ((mState & GET_OBJECT) == 0) {
            mState |= GET_OBJECT;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.getObject: bjectId=" + mAccountMigrationId + " schemaId=" + AccountMigration.SCHEMA_ID);
            }
            mTwinmeContextImpl.getRepositoryService().getObject(mAccountMigrationId, AccountMigrationFactory.INSTANCE, this::onGetObject);
            return;
        }
        if ((mState & GET_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Step 2: refresh the peer twincode.
        //
        if (mPeerTwincodeOutbound != null) {

            if ((mState & REFRESH_PEER_TWINCODE_OUTBOUND) == 0) {
                mState |= REFRESH_PEER_TWINCODE_OUTBOUND;

                mTwinmeContextImpl.getTwincodeOutboundService().refreshTwincode(mPeerTwincodeOutbound,
                        this::onRefreshTwincodeOutbound);
                return;
            }
            if ((mState & REFRESH_PEER_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 4: if the device migration was interrupted and must be canceled.
        //
        if (mToBeDeleted) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContext.deleteObject: objectId=" + mAccountMigrationId);
            }

            mTwinmeContextImpl.getAccountMigrationService().cancelMigration(mAccountMigrationId);
            if (mAccountMigration != null) {
                mTwinmeContextImpl.deleteAccountMigration(mAccountMigration, (ErrorCode status, UUID accountMigrationId) -> {
                });
            }

            // And report an ItemNotFound error.
            mConsumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
            stop();
            return;
        }

        //
        // Last Step
        //

        mConsumer.onGet(ErrorCode.SUCCESS, mAccountMigration);

        stop();
    }

    private void onGetObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetObject: errorCode=" + errorCode + " object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof AccountMigration)) {

            onOperationError(GET_OBJECT, errorCode, mAccountMigrationId.toString());
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, object.getId(), mAccountMigrationId);

        mState |= GET_OBJECT_DONE;
        mAccountMigration = (AccountMigration) object;
        mPeerTwincodeOutbound = mAccountMigration.getPeerTwincodeOutbound();
        onOperation();
    }

    private void onRefreshTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable List<BaseService.AttributeNameValue> previousAttributes) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRefreshTwincodeOutbound: errorCode=" + errorCode + " previousAttributes=" + previousAttributes);
        }

        if (errorCode != ErrorCode.SUCCESS) {

            onOperationError(REFRESH_PEER_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mState |= REFRESH_PEER_TWINCODE_OUTBOUND_DONE;

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

        // Peer's twincode is now invalid, delete the account migration object.
        if (errorCode == ErrorCode.ITEM_NOT_FOUND && operationId == REFRESH_PEER_TWINCODE_OUTBOUND) {
            mState |= REFRESH_PEER_TWINCODE_OUTBOUND;
            mToBeDeleted = true;
            return;
        }

        mConsumer.onGet(errorCode, null);

        stop();
    }
}
