/*
 *  Copyright (c) 2014-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.executors;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.PairProtocol;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.twinlife.twinlife.BaseService.DEFAULT_REQUEST_ID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// Called as background operation: must be connected and no timeout.

public class RebindContactExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "CreateContactPhase2E...";
    private static final boolean DEBUG = false;

    private static final int FIND_CONTACT = 1;
    private static final int FIND_CONTACT_DONE = 1 << 1;
    private static final int INVOKE_TWINCODE_OUTBOUND = 1 << 12;
    private static final int INVOKE_TWINCODE_OUTBOUND_DONE = 1 << 13;

    @Nullable
    private TwincodeOutbound mPeerTwincodeOutbound;
    @Nullable
    private TwincodeOutbound mContactTwincodeOutbound;
    private final UUID mPeerTwincodeOutboundId;
    private Contact mContact;
    private TwincodeOutbound mIdentityTwincodeOutbound;
    private boolean mUnbindContact;

    public RebindContactExecutor(@NonNull TwinmeContextImpl twinmeContextImpl,
                                 @NonNull UUID peerTwincodeId) {
        super(twinmeContextImpl, DEFAULT_REQUEST_ID, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "RebindContactExecutor: twinmeContextImpl=" + twinmeContextImpl + " peerTwincodeId=" + peerTwincodeId);
        }

        mPeerTwincodeOutboundId = peerTwincodeId;
        mUnbindContact = false;
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
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
        // Step 1: find the contact with the given peer twincode id (we must also consider this is the private peer twincode id).
        //
        if ((mState & FIND_CONTACT) == 0) {
            mState |= FIND_CONTACT;

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeOutboundService.findContacts: peerTwincodeOutboundId=" + mPeerTwincodeOutboundId);
            }

            final Filter<RepositoryObject> filter = new Filter<RepositoryObject>(null) {
                public boolean accept(@NonNull RepositoryObject object) {

                    if (!(object instanceof Contact)) {
                        return false;
                    }

                    final Contact contact = (Contact) object;
                    return contact.hasPrivatePeer() && mPeerTwincodeOutboundId.equals(contact.getPeerTwincodeOutboundId());
                }
            };
            mTwinmeContextImpl.findContacts(filter, (List<Contact> contacts) -> {
                if (contacts != null && !contacts.isEmpty()) {
                    // The contact was found, get the information to make a `pair::bind` as we do
                    // in the createContactPhase2Executor(): this should recover the contact setup on its side
                    // for the private peer twincode.
                    mContact = contacts.get(0);
                    mIdentityTwincodeOutbound = mContact.getTwincodeOutbound();
                    mContactTwincodeOutbound = mIdentityTwincodeOutbound;
                    mPeerTwincodeOutbound = mContact.getPeerTwincodeOutbound();
                }

                mState |= FIND_CONTACT_DONE;
                onOperation();
            });
            return;
        }

        if ((mState & FIND_CONTACT_DONE) == 0) {
            return;
        }

        //
        // Step 2: invoke the peer twincode to send our private identity twincode again (a first `pair::bind`
        // was made by `createContactPhase2` but it was not saved correctly on the peer's side.
        //
        if (mIdentityTwincodeOutbound != null && mContactTwincodeOutbound != null && mPeerTwincodeOutbound != null) {
            if ((mState & INVOKE_TWINCODE_OUTBOUND) == 0) {
                mState |= INVOKE_TWINCODE_OUTBOUND;

                List<AttributeNameValue> attributes;
                attributes = new ArrayList<>();
                PairProtocol.setInvokeTwincodeActionPairBindAttributeTwincodeId(attributes, mContactTwincodeOutbound.getId());
                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.invokeTwincode: peerTwincodeOutboundId=" + mPeerTwincodeOutbound.getId() +
                            " attributes=" + attributes);
                }
                if (mPeerTwincodeOutbound.isSigned()) {
                    // Unlike the `pair:bind` made in the CreateContactPhase2, we can use the identity twincode
                    // for the encryption because the peer trust it.
                    // We still send information about our contact twincode to give our public key.
                    mTwinmeContextImpl.getTwincodeOutboundService().secureInvokeTwincode(mIdentityTwincodeOutbound,
                            mContactTwincodeOutbound, mPeerTwincodeOutbound,
                            TwincodeOutboundService.INVOKE_URGENT | TwincodeOutboundService.CREATE_SECRET, PairProtocol.ACTION_PAIR_BIND,
                            attributes, this::onInvokeTwincode);
                } else {
                    mTwinmeContextImpl.getTwincodeOutboundService().invokeTwincode(mPeerTwincodeOutbound,
                            TwincodeOutboundService.INVOKE_URGENT, PairProtocol.ACTION_PAIR_BIND,
                            attributes, this::onInvokeTwincode);
                }
                return;
            }
            if ((mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //
        if (mUnbindContact) {
            mTwinmeContextImpl.unbindContact(DEFAULT_REQUEST_ID, null, mContact);
        }

        stop();
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

        if (operationId == INVOKE_TWINCODE_OUTBOUND) {
            if (errorCode == ErrorCode.ITEM_NOT_FOUND || errorCode == ErrorCode.NO_PRIVATE_KEY
                    || errorCode == ErrorCode.INVALID_PUBLIC_KEY || errorCode == ErrorCode.INVALID_PRIVATE_KEY) {

                mState |= INVOKE_TWINCODE_OUTBOUND_DONE;

                mUnbindContact = true;
                onOperation();
                return;
            }
        }

        super.onOperationError(operationId, errorCode, errorParameter);
    }
}
