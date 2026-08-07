/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.executors;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.InvitationDescriptor;
import org.twinlife.twinlife.CryptoService;
import org.twinlife.twinlife.ErrorCode;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Invitation;
import org.twinlife.twinme.models.Space;

import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.16
//
// Called as background operation: must be connected and no timeout.

public class AnswerContactShareExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "AnswerContactShareExecutor";
    private static final boolean DEBUG = false;

    private static final int CREATE_INVITATION = 1;
    private static final int CREATE_INVITATION_DONE = 1 << 1;
    private static final int ANSWER_CONTACT_SHARE = 1 << 2;
    private static final int ANSWER_CONTACT_SHARE_DONE = 1 << 3;

    @NonNull
    ConversationService.Conversation mConversation;
    @NonNull
    ConversationService.ContactShareDescriptor mContactShareDescriptor;
    @Nullable
    Space mSpace;
    @NonNull
    InvitationDescriptor.Status mAnswer;
    boolean mAutoAnswer;

    @Nullable
    private Invitation mInvitation;
    @Nullable
    private CryptoService.PublicKeyData mInvitationTwincodePubkey;

    public AnswerContactShareExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, @NonNull ConversationService.Conversation conversation, @NonNull ConversationService.ContactShareDescriptor contactShareDescriptor, @Nullable Space space, @NonNull InvitationDescriptor.Status answer, boolean autoAnswer) {
        super(twinmeContextImpl, BaseService.DEFAULT_REQUEST_ID, LOG_TAG);

        mConversation = conversation;
        mContactShareDescriptor = contactShareDescriptor;
        mSpace = space;
        mAnswer = answer;
        mAutoAnswer = autoAnswer;
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
        // Step 1: createInvitation if needed
        //
        if (mAnswer == InvitationDescriptor.Status.ACCEPTED) {
            if ((mState & CREATE_INVITATION) == 0) {
                mState |= CREATE_INVITATION;

                long requestId = newOperation(CREATE_INVITATION);
                mTwinmeContextImpl.createInvitation(requestId, mSpace, mContactShareDescriptor.getDescriptorId());
                return;
            }

            if ((mState & CREATE_INVITATION_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2: update Descriptor and send IQ
        //

        if ((mState & ANSWER_CONTACT_SHARE) == 0) {
            mState |= ANSWER_CONTACT_SHARE;

            UUID twincodeOutboundId = mInvitation == null ? null : mInvitation.getTwincodeOutboundId();

            mTwinmeContextImpl.getConversationService().answerContactShare(mConversation, mContactShareDescriptor,
                    mAnswer, mAutoAnswer, twincodeOutboundId, mInvitationTwincodePubkey, this::onAnswerContactShare);
        }

        if ((mState & ANSWER_CONTACT_SHARE_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //

        // No observer call, ConversationService.answerContactShare() will call ServiceObserver.onUpdateDescriptor().

        stop();
    }

    public void onCreateInvitation(long requestId, @NonNull Invitation invitation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateInvitation: requestId=" + requestId + " invitation=" + invitation);
        }

        if (getOperation(requestId) > 0) {
            mState |= CREATE_INVITATION_DONE;
            mInvitation = invitation;

            if (mInvitation.getTwincodeOutbound() != null) {
                String publicKey = mTwinmeContextImpl.getCryptoService().getPublicKey(mInvitation.getTwincodeOutbound());
                mInvitationTwincodePubkey = CryptoService.PublicKeyData.create(publicKey);
            }

            onOperation();
        }
    }

    public void onAnswerContactShare(@NonNull ErrorCode errorCode, @Nullable ConversationService.ContactShareDescriptor contactShareDescriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onAnswerContactShare: errorCode=" + errorCode + " contactShareDescriptor=" + contactShareDescriptor);
        }

        mState |= ANSWER_CONTACT_SHARE_DONE;

        if (errorCode != ErrorCode.SUCCESS) {
            onOperationError(ANSWER_CONTACT_SHARE, errorCode, null);
        }

        onOperation();
    }
}
