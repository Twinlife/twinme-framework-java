/*
 *  Copyright (c) 2014-2024 twinlife SA.
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
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.PairProtocol;

import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.19
//
// User foreground operation: must be connected with a timeout if connection does not work.
// BUT, when it is called from DeleteSpace or due to an Unbind, the timeout is set to infinity.

public class DeleteContactExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "DeleteContactExecutor";
    private static final boolean DEBUG = false;

    private static final int UNBIND_TWINCODE_INBOUND = 1;
    private static final int UNBIND_TWINCODE_INBOUND_DONE = 1 << 1;
    private static final int INVOKE_TWINCODE_OUTBOUND = 1 << 2;
    private static final int INVOKE_TWINCODE_OUTBOUND_DONE = 1 << 3;
    private static final int DELETE_TWINCODE = 1 << 4;
    private static final int DELETE_TWINCODE_DONE = 1 << 5;
    private static final int DELETE_IDENTITY_IMAGE = 1 << 6;
    private static final int DELETE_IDENTITY_IMAGE_DONE = 1 << 7;
    private static final int DELETE_OBJECT = 1 << 12;
    private static final int DELETE_OBJECT_DONE = 1 << 13;

    @NonNull
    private final Contact mContact;
    private final TwincodeInbound mTwincodeInbound;
    private final TwincodeOutbound mPeerTwincodeOutbound;
    @Nullable
    private final UUID mPublicPeerTwincodeOutboundId;
    private final UUID mTwincodeFactoryId;
    @Nullable
    private final ImageId mIdentityAvatarId;
    @Nullable
    private final UUID mInvocationId;

    public DeleteContactExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Contact contact,
                                 @Nullable UUID invocationId, long timeout) {
        super(twinmeContextImpl, requestId, LOG_TAG, timeout);
        if (DEBUG) {
            Log.d(LOG_TAG, "DeleteContactExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId + " contact=" + contact);
        }

        mContact = contact;
        mInvocationId = invocationId;
        mTwincodeInbound = contact.getTwincodeInbound();
        mPeerTwincodeOutbound = contact.hasPrivatePeer() ? contact.getPeerTwincodeOutbound() : null;
        mPublicPeerTwincodeOutboundId = contact.getPublicPeerTwincodeOutboundId();
        mTwincodeFactoryId = contact.getTwincodeFactoryId();
        mIdentityAvatarId = contact.getIdentityAvatarId();
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
            if ((mState & INVOKE_TWINCODE_OUTBOUND) != 0 && (mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~INVOKE_TWINCODE_OUTBOUND;
            }
            if ((mState & DELETE_TWINCODE) != 0 && (mState & DELETE_TWINCODE_DONE) == 0) {
                mState &= ~DELETE_TWINCODE;
            }
            if ((mState & DELETE_IDENTITY_IMAGE) != 0 && (mState & DELETE_IDENTITY_IMAGE_DONE) == 0) {
                mState &= ~DELETE_IDENTITY_IMAGE;
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
                    Log.d(LOG_TAG, "TwincodeInboundService.unbindTwincode: twincodeInbound=" + mTwincodeInbound);
                }
                mTwinmeContextImpl.getTwincodeInboundService().unbindTwincode(mTwincodeInbound, this::onUnbindTwincodeInbound);
                return;
            }
            if ((mState & UNBIND_TWINCODE_INBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2: invoke peer to unbind the contact on its side.
        //
        if (mPeerTwincodeOutbound != null) {

            if ((mState & INVOKE_TWINCODE_OUTBOUND) == 0) {
                mState |= INVOKE_TWINCODE_OUTBOUND;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.invokeTwincode: peerTwincodeOutboundId=" + mPeerTwincodeOutbound);
                }
                mTwinmeContextImpl.getTwincodeOutboundService().invokeTwincode(mPeerTwincodeOutbound,
                        TwincodeOutboundService.INVOKE_WAKEUP, PairProtocol.ACTION_PAIR_UNBIND, null,
                        this::onInvokeTwincode);
                return;
            }
            if ((mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3a: delete the twincode.
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
        // Step 3b: delete the twincode avatarId
        //
        if (mIdentityAvatarId != null) {

            if ((mState & DELETE_IDENTITY_IMAGE) == 0) {
                mState |= DELETE_IDENTITY_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.deleteImage: imageId=" + mIdentityAvatarId);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.deleteImage(mIdentityAvatarId, (ErrorCode status, ImageId imageId) -> {
                    mState |= DELETE_IDENTITY_IMAGE_DONE;
                    onOperation();
                });
                return;
            }
            if ((mState & DELETE_IDENTITY_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 4: delete the contact object.
        //
        if ((mState & DELETE_OBJECT) == 0) {
            mState |= DELETE_OBJECT;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.deleteObject: objectId=" + mContact.getId());
            }
            mTwinmeContextImpl.getRepositoryService().deleteObject(mContact, this::onDeleteObject);

            //
            // remove the peer twincodes and its image from the cache.
            //
            if (mPeerTwincodeOutbound != null) {
                mTwinmeContextImpl.getTwincodeOutboundService().evictTwincodeOutbound(mPeerTwincodeOutbound);
            }
            if (mPublicPeerTwincodeOutboundId != null) {
                mTwinmeContextImpl.getTwincodeOutboundService().evictTwincode(mPublicPeerTwincodeOutboundId);
            }
            return;
        }
        if ((mState & DELETE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.onDeleteContact(mRequestId, mContact.getId());

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

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mContact, 279);

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

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, objectId, mContact.getId());

        mState |= DELETE_OBJECT_DONE;
        onOperation();
    }

    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
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

        super.onOperationError(operationId, errorCode, errorParameter);
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
