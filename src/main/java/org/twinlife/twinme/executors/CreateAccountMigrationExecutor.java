/*
 *  Copyright (c) 2020-2025 twinlife SA.
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

import org.twinlife.twinlife.AccountMigrationService;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.AccountMigration;
import org.twinlife.twinme.models.AccountMigrationFactory;
import org.twinlife.twinme.models.CallReceiver;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.twinlife.twinlife.BaseService.DEFAULT_REQUEST_ID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.6
//

public class CreateAccountMigrationExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "CreateAccountMigrat...";
    private static final boolean DEBUG = false;

    private static final int GET_OBJECT_IDS = 1;
    private static final int GET_OBJECT_IDS_DONE = 1 << 1;
    private static final int CREATE_TWINCODE = 1 << 4;
    private static final int CREATE_TWINCODE_DONE = 1 << 5;
    private static final int CREATE_OBJECT = 1 << 8;
    private static final int CREATE_OBJECT_DONE = 1 << 9;
    private static final int UPDATE_TWINCODE = 1 << 10;
    private static final int UPDATE_TWINCODE_DONE = 1 << 11;

    @NonNull
    private final Consumer<AccountMigration> mConsumer;
    @Nullable
    private TwincodeFactory mTwincodeFactory;
    @Nullable
    private AccountMigration mAccountMigration;
    private boolean mHasRelations;

    public CreateAccountMigrationExecutor(@NonNull TwinmeContextImpl twinmeContextImpl,
                                          @NonNull Consumer<AccountMigration> consumer) {
        super(twinmeContextImpl, DEFAULT_REQUEST_ID, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "CreateAccountMigrationExecutor: twinmeContextImpl=" + twinmeContextImpl);
        }

        mConsumer = consumer;
        mHasRelations = false;
    }

    //
    // Private methods
    //

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & CREATE_TWINCODE) != 0 && (mState & CREATE_TWINCODE_DONE) == 0) {
                mState &= ~CREATE_TWINCODE;
            }

            if ((mState & UPDATE_TWINCODE) != 0 && (mState & UPDATE_TWINCODE_DONE) == 0) {
                mState &= ~UPDATE_TWINCODE;
            }
        }
        super.onTwinlifeOnline();
    }

    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        if (mStopped) {

            return;
        }

        //
        // Step 1: get the list of account migration objects to remove them.
        //
        if ((mState & GET_OBJECT_IDS) == 0) {
            mState |= GET_OBJECT_IDS;

            // Look if we have some contacts, or groups, or click-to-call.
            // We don't care for Space, Profile and other objects.
            final RepositoryService repositoryService = mTwinmeContextImpl.getRepositoryService();
            mHasRelations = repositoryService.hasObjects(Contact.SCHEMA_ID);
            if (!mHasRelations) {
                mHasRelations = repositoryService.hasObjects(Group.SCHEMA_ID);
                if (!mHasRelations) {
                    mHasRelations = repositoryService.hasObjects(CallReceiver.SCHEMA_ID);
                }
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.listObjects: schemaId=" + AccountMigration.SCHEMA_ID);
            }
            repositoryService.listObjects(AccountMigrationFactory.INSTANCE, null,
                    this::onGetAccountMigrationObjects);
            return;
        }
        if ((mState & GET_OBJECT_IDS_DONE) == 0) {
            return;
        }

        //
        // Step 2a: create the device migration twincode and indicate our version of AccountMigrationService.
        //
        if ((mState & CREATE_TWINCODE) == 0) {
            mState |= CREATE_TWINCODE;

            List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
            PairProtocol.setTwincodeAttributePair(twincodeFactoryAttributes);

            List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
            TwinmeAttributes.setTwincodeAttributeAccountMigration(twincodeOutboundAttributes, AccountMigrationService.VERSION, mHasRelations);

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: twincodeFactoryAttributes=" + twincodeFactoryAttributes
                        + " twincodeOutboundAttributes=" + twincodeOutboundAttributes);
            }
            mTwinmeContextImpl.getTwincodeFactoryService().createTwincode(twincodeFactoryAttributes, null,
                    twincodeOutboundAttributes, null,
                    AccountMigration.SCHEMA_ID, this::onCreateTwincodeFactory);
            return;
        }
        if ((mState & CREATE_TWINCODE_DONE) == 0) {
            return;
        }

        //
        // Step 2b: Update the device migration twincode and indicate our version of AccountMigrationService,
        //          to make sure we won't send an old (and incompatible) version number to the peer.
        //
        if ((mState & UPDATE_TWINCODE) == 0) {
            mState |= UPDATE_TWINCODE;

            if (mAccountMigration == null || mAccountMigration.getTwincodeOutbound() == null) {
                // We should have an AccountMigration with a twincode at this point.
                // Skip twincode update and hope for the best...
                Log.e(LOG_TAG, "UPDATE_TWINCODE step required but mAccountMigration or its twincodeOutbound is null.");
                mState |= UPDATE_TWINCODE_DONE;
            } else {

                TwincodeOutbound twincodeOutbound = mAccountMigration.getTwincodeOutbound();

                List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
                TwinmeAttributes.setTwincodeAttributeAccountMigration(twincodeOutboundAttributes, AccountMigrationService.VERSION, mHasRelations);

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.updateTwincode: twincodeOutboundAttributes=" + twincodeOutboundAttributes);
                }

                mTwinmeContextImpl.getTwincodeOutboundService().updateTwincode(twincodeOutbound, twincodeOutboundAttributes, null, this::onUpdateTwincodeOutbound);

                return;
            }
        }
        if ((mState & UPDATE_TWINCODE_DONE) == 0) {
            return;
        }

        //
        // Step 3: create the DeviceMigration object.
        //
        if (mTwincodeFactory != null) {

            if ((mState & CREATE_OBJECT) == 0) {
                mState |= CREATE_OBJECT;

                mTwinmeContextImpl.getRepositoryService().createObject(AccountMigrationFactory.INSTANCE,
                        RepositoryService.AccessRights.PRIVATE, (RepositoryObject object) -> {
                            AccountMigration accountMigration = (AccountMigration) object;
                            accountMigration.setTwincodeFactory(mTwincodeFactory);
                        }, this::onCreateObject);
                return;
            }
            if ((mState & CREATE_OBJECT_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mConsumer.onGet(ErrorCode.SUCCESS, mAccountMigration);

        stop();
    }

    private void onGetAccountMigrationObjects(@NonNull ErrorCode errorCode, @Nullable List<RepositoryObject> objectIds) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetAccountMigrationObjects: errorCode=" + errorCode + " objectIds=" + objectIds);
        }

        mState |= GET_OBJECT_IDS_DONE;

        if (objectIds != null) {
            for (RepositoryObject object : objectIds) {
                if (object instanceof AccountMigration) {
                    AccountMigration accountMigration = (AccountMigration) object;

                    // If this account migration object is not bound and has no associated peer twincode, we can use it.
                    if (!accountMigration.isBound() && accountMigration.getPeerTwincodeOutboundId() == null
                            && mAccountMigration == null && accountMigration.getTwincodeOutbound() != null) {
                        mState |= CREATE_TWINCODE | CREATE_TWINCODE_DONE | CREATE_OBJECT | CREATE_OBJECT_DONE;
                        mAccountMigration = accountMigration;
                    } else {
                        mTwinmeContextImpl.deleteAccountMigration(accountMigration, (ErrorCode delStatus, UUID accountMigrationId) -> {
                        });
                    }
                }
            }
        }

        if (mAccountMigration == null) {
            // No existing migration twincode => nothing to update.
            mState |= UPDATE_TWINCODE | UPDATE_TWINCODE_DONE;
        }

        onOperation();
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

        mTwincodeFactory = twincodeFactory;
        onOperation();
    }

    private void onUpdateTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateTwincodeOutbound: errorCode=" + errorCode + " twincodeOutbound=" + twincodeOutbound);
        }

        if (errorCode == ErrorCode.ITEM_NOT_FOUND && mAccountMigration != null) {
            // It can happen that the twincode has been deleted but we still have the AccountMigration object.
            // This occurs during a successful migration because we are sending the database to the peer and
            // once the migration is done, we delete and unbind the twincode on the first device.  When we start
            // again on the second device, we still see the AccountMigration object but its twincode is now invalid.
            mTwinmeContextImpl.deleteAccountMigration(mAccountMigration, (ErrorCode delStatus, UUID accountMigrationId) -> {
                // We have to wait for the delete operation to complete and restart the whole process.
                mAccountMigration = null;
                mState = 0;
                onOperation();
            });
            return;

        } else if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {

            onOperationError(UPDATE_TWINCODE, errorCode, null);
            return;
        }

        if (mAccountMigration == null) {
            Log.e(LOG_TAG, "mAccountMigration is null, can't update twincodeOutbound");
        } else {
            mAccountMigration.setTwincodeOutbound(twincodeOutbound);
        }

        mState |= UPDATE_TWINCODE_DONE;

        onOperation();
    }

    private void onCreateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateObject: errorCode=" + errorCode + " object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof AccountMigration)) {

            onOperationError(CREATE_OBJECT, errorCode, null);
            return;
        }

        mState |= CREATE_OBJECT_DONE;
        mAccountMigration = (AccountMigration) object;
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

        mConsumer.onGet(errorCode, null);

        stop();
    }
}
