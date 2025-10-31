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

import org.twinlife.twinlife.AccountService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinme.TwinmeContextImpl;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.3
//

public class DeleteAccountExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "DeleteAccountExecutor";
    private static final boolean DEBUG = false;

    private static final int DELETE_ACCOUNT = 1;
    private static final int DELETE_ACCOUNT_DONE = 1 << 1;

    private class AccountServiceObserver extends AccountService.DefaultServiceObserver {

        @Override
        public void onDeleteAccount(long requestId) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onDeleteAccount: requestId=" + requestId);
            }

            int operationId = getOperation(requestId);
            if (operationId > 0) {
                DeleteAccountExecutor.this.onDeleteAccount();
            }
        }

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryServiceObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            int operationId = getOperation(requestId);
            if (operationId > 0) {
                DeleteAccountExecutor.this.onOperationError(operationId, errorCode, errorParameter);
                onOperation();
            }
        }
    }

    private final AccountServiceObserver mAccountServiceObserver;

    public DeleteAccountExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "DeleteAccountExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId);
        }

        mAccountServiceObserver = new AccountServiceObserver();
    }

    @Override
    public void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        mTwinmeContextImpl.getAccountService().addServiceObserver(mAccountServiceObserver);
        super.onTwinlifeReady();
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;

            // Restart everything!
            mState = 0;
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
        // Step 1: delete the account (all contact unbind and twincode undeploy is handled by the server).
        //

        if ((mState & DELETE_ACCOUNT) == 0) {
            mState |= DELETE_ACCOUNT;

            long requestId = newOperation(DELETE_ACCOUNT);
            if (DEBUG) {
                Log.d(LOG_TAG, "AccountService.deleteAccount: requestId=" + requestId);
            }
            mTwinmeContextImpl.getAccountService().deleteAccount(requestId);
            return;
        }
        if ((mState & DELETE_ACCOUNT_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //
        mTwinmeContextImpl.onDeleteAccount(mRequestId);

        stop();
    }

    private void onDeleteAccount() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteAccount");
        }

        mState |= DELETE_ACCOUNT_DONE;
        onOperation();
    }

    protected void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        mStopped = true;

        mTwinmeContextImpl.getAccountService().removeServiceObserver(mAccountServiceObserver);

        super.stop();
    }
}
