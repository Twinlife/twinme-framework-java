/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeURI;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.ContactFactory;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.1
//

public class VerifyContactExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "VerifyContactExecutor";
    private static final boolean DEBUG = false;

    private static final int FIND_CONTACT = 1;
    private static final int UPDATE_TWINCODE_OUTBOUND = 1 << 1;
    private static final int UPDATE_TWINCODE_OUTBOUND_DONE = 1 << 2;
    private static final int INVOKE_TWINCODE_OUTBOUND = 1 << 3;
    private static final int INVOKE_TWINCODE_OUTBOUND_DONE = 1 << 4;

    @NonNull
    private final TwincodeURI mTwincodeURI;
    @NonNull
    private final TwinmeContext.ConsumerWithError<Contact> mComplete;
    @NonNull
    private final TrustMethod mTrustMethod;
    @Nullable
    private Contact mContact;
    @Nullable
    private TwincodeOutbound mPeerTwincodeOutbound;
    @Nullable
    private TwincodeOutbound mTwincodeOutbound;

    public VerifyContactExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, @NonNull TwincodeURI twincodeURI,
                                 @NonNull TrustMethod trustMethod,
                                 @NonNull TwinmeContext.ConsumerWithError<Contact> complete) {
        super(twinmeContextImpl, BaseService.DEFAULT_REQUEST_ID, LOG_TAG, DEFAULT_TIMEOUT);

        mTwincodeURI = twincodeURI;
        mTrustMethod = trustMethod;
        mComplete = complete;
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & UPDATE_TWINCODE_OUTBOUND) != 0 && (mState & UPDATE_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~UPDATE_TWINCODE_OUTBOUND;
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
        // Step 1: find the contact knowing the authenticate signature.
        //
        if ((mState & FIND_CONTACT) == 0) {
            mState |= FIND_CONTACT;

            final RepositoryObjectFactory<?>[] factories = {
                    ContactFactory.INSTANCE
            };
            final RepositoryService.FindResult result;
            if (mTwincodeURI.kind == TwincodeURI.Kind.Authenticate && mTwincodeURI.pubKey != null) {
                result = mTwinmeContextImpl.getRepositoryService().findWithSignature(mTwincodeURI.pubKey, factories);
            } else {
                result = RepositoryService.FindResult.error(ErrorCode.BAD_REQUEST);
            }
            if (result.errorCode != ErrorCode.SUCCESS) {
                mComplete.onGet(result.errorCode, null);
                stop();
                return;
            }
            if (!(result.object instanceof Contact)) {
                mComplete.onGet(ErrorCode.LIBRARY_ERROR, null);
                stop();
                return;
            }

            // This is verified and the contact was found.
            mContact = (Contact) result.object;
            mTwincodeOutbound = mContact.getTwincodeOutbound();
            mPeerTwincodeOutbound = mContact.getPeerTwincodeOutbound();

            // Check if the authenticate URL was created by us or by the peer.
            mTwinmeContextImpl.getTwincodeOutboundService().createURI(TwincodeURI.Kind.Authenticate, mTwincodeOutbound,
                    (ErrorCode errorCode, TwincodeURI twincodeURI) -> {
                if (errorCode != ErrorCode.SUCCESS || twincodeURI == null) {
                    mComplete.onGet(errorCode, null);
                    stop();
                    return;
                }
                // Authenticate URL was signed by us: it does not prove we trust the peer.
                if (mTwincodeURI.uri.equals(twincodeURI.uri)) {
                    mComplete.onGet(errorCode, mContact);
                    stop();
                    return;
                }

                // Authenticate URL was signed by the peer: we can trust it now.
                onOperation();
            });
            return;
        }

        //
        // Step 2: update identity twincode to indicate to the peer that we trust its twincode.
        //
        if (mTwincodeOutbound != null && mPeerTwincodeOutbound != null && mContact != null) {
            if ((mState & UPDATE_TWINCODE_OUTBOUND) == 0) {
                mState |= UPDATE_TWINCODE_OUTBOUND;

                // Mark the relation as certified now with the given trust method.
                mTwinmeContextImpl.getTwincodeOutboundService().setCertified(mTwincodeOutbound, mPeerTwincodeOutbound, mTrustMethod);

                if (mPeerTwincodeOutbound.isTrusted() && mTwincodeOutbound.isTrusted()) {
                    final Capabilities capabilities = mContact.getIdentityCapabilities();
                    capabilities.setTrusted(mPeerTwincodeOutbound.getId());

                    final String value = capabilities.toAttributeValue();
                    if (!Utils.equals(value, mPeerTwincodeOutbound.getCapabilities())) {
                        final List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
                        if (value != null) {
                            TwinmeAttributes.setCapabilities(attributes, value);
                        }

                        if (DEBUG) {
                            Log.d(LOG_TAG, "TwincodeOutboundService.updateTwincode: twincodeOutboundId=" + mTwincodeOutbound);
                        }
                        mTwinmeContextImpl.getTwincodeOutboundService().updateTwincode(mTwincodeOutbound, attributes, null,
                                this::onUpdateTwincodeOutbound);
                        return;
                    }
                }
                // Identity twincode is not modified, no need to inform the peer.
                mState |= UPDATE_TWINCODE_OUTBOUND_DONE;
                mState |= INVOKE_TWINCODE_OUTBOUND | INVOKE_TWINCODE_OUTBOUND_DONE;
            }
            if ((mState & UPDATE_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 4: invoke a refresh on the peer twincode if we have updated our identity.
        //
        if ((mState & INVOKE_TWINCODE_OUTBOUND) == 0 && mPeerTwincodeOutbound != null) {
            mState |= INVOKE_TWINCODE_OUTBOUND;

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeOutboundService.invokeTwincode: peerTwincodeOutbound="
                        + mPeerTwincodeOutbound);
            }
            mTwinmeContextImpl.getTwincodeOutboundService().invokeTwincode(mPeerTwincodeOutbound,
                    TwincodeOutboundService.INVOKE_WAKEUP, PairProtocol.ACTION_PAIR_REFRESH,
                    null, this::onInvokeTwincode);
            return;
        }
        if ((mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //
        mComplete.onGet(ErrorCode.SUCCESS, mContact);

        stop();
    }

    private void onUpdateTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateTwincodeOutbound: twincodeOutbound=" + twincodeOutbound);
        }
        if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null || mContact == null) {

            onOperationError(UPDATE_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeOutbound, mTwincodeOutbound);

        mState |= UPDATE_TWINCODE_OUTBOUND_DONE;
        mContact.setTwincodeOutbound(mTwincodeOutbound);
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

    protected void onOperationError(int operationId, BaseService.ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // Wait for reconnection
        if (errorCode == BaseService.ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;

            return;
        }

        mComplete.onGet(errorCode, null);
        stop();
    }
}
