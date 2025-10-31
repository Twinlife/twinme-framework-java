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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.graphics.Bitmap;
import android.util.Log;

import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.PairBindInvocation;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.16
//
// Called as background operation: must be connected and no timeout.

public class BindContactExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "BindContactExecutor";
    private static final boolean DEBUG = false;

    private static final int UPDATE_TWINCODE_OUTBOUND = 1;
    private static final int UPDATE_TWINCODE_OUTBOUND_DONE = 1 << 1;
    private static final int UPDATE_TWINCODE_INBOUND = 1 << 2;
    private static final int UPDATE_TWINCODE_INBOUND_DONE = 1 << 3;
    private static final int UPDATE_OBJECT = 1 << 4;
    private static final int UPDATE_OBJECT_DONE = 1 << 5;
    private static final int INVOKE_TWINCODE_OUTBOUND = 1 << 6;
    private static final int INVOKE_TWINCODE_OUTBOUND_DONE = 1 << 7;
    private static final int GET_PEER_TWINCODE_IMAGE = 1 << 8;
    private static final int GET_PEER_TWINCODE_IMAGE_DONE = 1 << 9;

    @Nullable
    private PairBindInvocation mInvocation;
    @NonNull
    private final Contact mContact;
    @Nullable
    private final TwincodeInbound mTwincodeInbound;
    @Nullable
    private final TwincodeOutbound mPreviousPeerTwincodeOutbound;
    @NonNull
    private final TwincodeOutbound mPeerTwincodeOutbound;
    @Nullable
    private final TwincodeOutbound mTwincodeOutbound;
    @Nullable
    private final ImageId mAvatarId;
    private boolean mModified;

    public BindContactExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, @NonNull PairBindInvocation invocation,
                               @NonNull Contact contact) {
        super(twinmeContextImpl, BaseService.DEFAULT_REQUEST_ID, LOG_TAG);

        mInvocation = invocation;
        mContact = contact;
        mPeerTwincodeOutbound = invocation.getTwincodeOutbound();
        mTwincodeInbound = contact.getTwincodeInbound();
        mTwincodeOutbound = contact.getTwincodeOutbound();
        mPreviousPeerTwincodeOutbound = contact.getPeerTwincodeOutbound();
        mAvatarId = mPeerTwincodeOutbound.getAvatarId();
        mModified = false;
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
            if ((mState & UPDATE_TWINCODE_INBOUND) != 0 && (mState & UPDATE_TWINCODE_INBOUND_DONE) == 0) {
                mState &= ~UPDATE_TWINCODE_INBOUND;
            }
            if ((mState & INVOKE_TWINCODE_OUTBOUND) != 0 && (mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~INVOKE_TWINCODE_OUTBOUND;
            }
            if ((mState & GET_PEER_TWINCODE_IMAGE) != 0 && (mState & GET_PEER_TWINCODE_IMAGE_DONE) == 0) {
                mState &= ~GET_PEER_TWINCODE_IMAGE;
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
        // Step 1: update identity twincode to indicate to the peer that we trust its twincode.
        //
        if (mTwincodeOutbound != null) {
            if ((mState & UPDATE_TWINCODE_OUTBOUND) == 0) {
                mState |= UPDATE_TWINCODE_OUTBOUND;

                if (mPreviousPeerTwincodeOutbound != null && mPreviousPeerTwincodeOutbound.isSigned()) {
                    mTwinmeContextImpl.getTwincodeOutboundService().associateTwincodes(mTwincodeOutbound, mPreviousPeerTwincodeOutbound, mPeerTwincodeOutbound);
                }
                if (mPeerTwincodeOutbound.isTrusted() && mTwincodeOutbound.isTrusted()) {
                    final Capabilities capabilities = mContact.getIdentityCapabilities();
                    capabilities.setTrusted(mPeerTwincodeOutbound.getId());

                    final String value = capabilities.toAttributeValue();
                    final List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
                    if (value != null) {
                        TwinmeAttributes.setCapabilities(attributes, value);
                    }

                    if (DEBUG) {
                        Log.d(LOG_TAG, "TwincodeOutboundService.updateTwincode: twincodeOutboundId="
                                + mTwincodeOutbound);
                    }
                    mTwinmeContextImpl.getTwincodeOutboundService().updateTwincode(mTwincodeOutbound, attributes, null,
                            this::onUpdateTwincodeOutbound);
                    return;
                }
                // Identity twincode is not modified, no need to inform the peer.
                mState |= UPDATE_TWINCODE_OUTBOUND_DONE;
                mState |= INVOKE_TWINCODE_OUTBOUND | INVOKE_TWINCODE_OUTBOUND_DONE;
            }
            if ((mState & UPDATE_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }
        }

        if (mTwincodeInbound != null) {

            //
            // Step 2: update the inbound twincode unless the contact was invalidated.
            //
            if ((mState & UPDATE_TWINCODE_INBOUND) == 0) {
                mState |= UPDATE_TWINCODE_INBOUND;

                List<AttributeNameValue> attributes = new ArrayList<>();
                PairProtocol.setTwincodeAttributePairTwincodeId(attributes, mPeerTwincodeOutbound.getId());

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeInboundService.updateTwincode: twincodeInbound=" + mTwincodeInbound + " attributes=" + attributes);
                }
                mTwinmeContextImpl.getTwincodeInboundService().updateTwincode(mTwincodeInbound, attributes, null,
                        this::onUpdateTwincodeInbound);
                return;
            }

            //
            // Step 3: save the object with the private peer twincode.
            //
            if ((mState & UPDATE_OBJECT) == 0) {
                mState |= UPDATE_OBJECT;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwinmeContext.updateObject: contact=" + mContact);
                }

                // Update the contact's peer twincode (beware that we may receive a refresh or unbind in parallel!).
                mModified = mContact.updatePeerTwincodeOutbound(mPeerTwincodeOutbound);
                mTwinmeContextImpl.getRepositoryService().updateObject(mContact, this::onUpdateObject);
                return;
            }
            if ((mState & UPDATE_TWINCODE_INBOUND_DONE) == 0) {
                return;
            }
            if ((mState & UPDATE_OBJECT_DONE) == 0) {
                return;
            }

            //
            // Step 4: invoke a refresh on the peer twincode if we have updated our identity.
            //
            if ((mState & INVOKE_TWINCODE_OUTBOUND) == 0) {
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
            // Step 5: force a synchronize conversation in case we have some pending messages
            // but we don't want to create the conversation instance if it does not exist.
            //
            mTwinmeContextImpl.getConversationService().updateConversation(mContact, mPeerTwincodeOutbound);
        }

        // Acknowledge the invocation as soon as we have finished to setup the contact and
        // before releasing the previous peer and get the image.  As soon as we drop the previous
        // peer twincode, the same `pair::bind` will not be handled because we don't know the public key.
        // (it is fine because the contact is now finalized).  If we are interrupted immediately after
        // the acknowledge, we won't cleanup the peer (it's ok) or we won't pre-fetch the avatar (it's ok too).
        if (mInvocation != null) {
            mTwinmeContextImpl.acknowledgeInvocation(mInvocation.getId(), ErrorCode.SUCCESS);
            mInvocation = null;
        }

        // Drop the previous peer twincode which comes from the profile that was scanned.
        if (mPreviousPeerTwincodeOutbound != null && mPreviousPeerTwincodeOutbound != mPeerTwincodeOutbound) {
            mTwinmeContextImpl.getTwincodeOutboundService().evictTwincodeOutbound(mPreviousPeerTwincodeOutbound);
        }

        //
        // Step 6: get the contact image so that we have it in the cache when we are done.
        //
        if (mTwincodeInbound != null && mAvatarId != null) {

            if ((mState & GET_PEER_TWINCODE_IMAGE) == 0) {
                mState |= GET_PEER_TWINCODE_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.getImage: imageId=" + mAvatarId);
                }
                mTwinmeContextImpl.getImageService().getImageFromServer(mAvatarId, ImageService.Kind.THUMBNAIL, (ErrorCode errorCode, Bitmap avatar) -> {
                    mState |= GET_PEER_TWINCODE_IMAGE_DONE;
                    onOperation();
                });
                return;
            }
            if ((mState & GET_PEER_TWINCODE_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        if (mModified) {
            if (!mContact.checkInvariants()) {
                mTwinmeContextImpl.assertion(ExecutorAssertPoint.CONTACT_INVARIANT, AssertPoint.create(mContact));
            }

            mTwinmeContextImpl.onUpdateContact(mRequestId, mContact);
        }

        stop();
    }

    private void onUpdateTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateTwincodeOutbound: twincodeOutbound=" + twincodeOutbound);
        }
        if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {

            onOperationError(UPDATE_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeOutbound, mTwincodeOutbound);

        mState |= UPDATE_TWINCODE_OUTBOUND_DONE;
        mContact.setTwincodeOutbound(mTwincodeOutbound);
        onOperation();
    }

    private void onUpdateTwincodeInbound(@NonNull ErrorCode errorCode, @Nullable TwincodeInbound twincodeInbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateTwincodeInbound errorCode=" + errorCode + " twincodeInbound=" + twincodeInbound);
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
            Log.d(LOG_TAG, "onUpdateObject: errorCode=" + errorCode + " object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || object == null) {

            onOperationError(UPDATE_OBJECT, errorCode, null);
            return;
        }

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

    protected void onOperationError(int operationId, BaseService.ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // Wait for reconnection
        if (errorCode == BaseService.ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;

            return;
        }

        if (operationId == INVOKE_TWINCODE_OUTBOUND && errorCode == ErrorCode.ITEM_NOT_FOUND) {
            mTwinmeContextImpl.unbindContact(mRequestId, mInvocation == null ? null : mInvocation.getId(), mContact);
            mInvocation = null;
        }

        stop();
    }

    protected void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        if (mInvocation != null) {
            mTwinmeContextImpl.acknowledgeInvocation(mInvocation.getId(), ErrorCode.SUCCESS);
        }
        super.stop();
    }
}
