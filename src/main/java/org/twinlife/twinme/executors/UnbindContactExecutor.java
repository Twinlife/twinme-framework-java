/*
 *  Copyright (c) 2015-2024 twinlife SA.
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

import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Contact;

import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.10
//
// Called as background operation: must be connected and no timeout.

public class UnbindContactExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "UnbindContactExecutor";
    private static final boolean DEBUG = false;

    private static final int DELETE_PEER_IMAGE = 1;
    private static final int DELETE_PEER_IMAGE_DONE = 1 << 1;
    private static final int UPDATE_OBJECT = 1 << 2;
    private static final int UPDATE_OBJECT_DONE = 1 << 3;
    private static final int DELETE_CONVERSATION = 1 << 4;

    @Nullable
    private final UUID mInvocationId;
    @NonNull
    private final Contact mContact;
    @Nullable
    private final UUID mTwincodeOutboundId;

    public UnbindContactExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @Nullable UUID invocationId, @NonNull Contact contact) {
        super(twinmeContextImpl, requestId, LOG_TAG);

        mInvocationId = invocationId;
        mContact = contact;
        mTwincodeOutboundId = contact.getTwincodeOutboundId();

        // mInvocationId == null - unbindContact calls on error
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
        // Step 1: before dropping the peer twincode, remove the image from the local cache.
        //

        if (mContact.getAvatarId() != null) {

            if ((mState & DELETE_PEER_IMAGE) == 0) {
                mState |= DELETE_PEER_IMAGE;

                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.evictImage(mContact.getAvatarId());
                mState |= DELETE_PEER_IMAGE_DONE;
            }
        }

        //
        // Step 2: drop the peer twincode and update the object.
        //

        if ((mState & UPDATE_OBJECT) == 0) {
            mState |= UPDATE_OBJECT;

            mContact.setPeerTwincodeOutbound(null);
            mContact.setPublicPeerTwincodeOutbound(null);

            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContext.updateObject: contact=" + mContact);
            }
            mTwinmeContextImpl.getRepositoryService().updateObject(mContact, this::onUpdateObject);
            return;
        }
        if ((mState & UPDATE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Step 3: delete the conversation.
        //
        if (mTwincodeOutboundId != null) {

            if ((mState & DELETE_CONVERSATION) == 0) {
                mState |= DELETE_CONVERSATION;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwinmeContext.deleteConversation: twincodeOutboundId=" + mTwincodeOutboundId);
                }
                mTwinmeContextImpl.getConversationService().deleteConversation(mContact);
            }
        }

        // Post a notification when we unbind the contact.
        if (mTwinmeContextImpl.isVisible(mContact)) {
            mTwinmeContextImpl.getNotificationCenter().onUnbindContact(mContact);
        }

        //
        // Last Step
        //

        if (!mContact.checkInvariants()) {
            mTwinmeContextImpl.assertion(ExecutorAssertPoint.CONTACT_INVARIANT, AssertPoint.create(mContact));
        }

        mTwinmeContextImpl.onUpdateContact(mRequestId, mContact);

        stop();
    }

    private void onUpdateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateObject: object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || object == null) {
            onOperationError(UPDATE_OBJECT, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, object, mContact);

        mState |= UPDATE_OBJECT_DONE;
        onOperation();
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
