/*
 *  Copyright (c) 2018-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService.GroupMemberConversation;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.Originator;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.PairRefreshInvocation;

import java.util.List;
import java.util.UUID;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.7
//
// Called as background operation: must be connected and no timeout.

public class RefreshObjectExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "RefreshObjectExecutor";
    private static final boolean DEBUG = false;

    private static final int REFRESH_PEER_TWINCODE_OUTBOUND = 1;
    private static final int REFRESH_PEER_TWINCODE_OUTBOUND_DONE = 1 << 1;
    private static final int GET_PEER_IMAGE = 1 << 2;
    private static final int GET_PEER_IMAGE_DONE = 1 << 3;
    private static final int UPDATE_OBJECT = 1 << 4;
    private static final int UPDATE_OBJECT_DONE = 1 << 5;

    @Nullable
    private final TwincodeOutbound mPeerTwincodeOutbound;
    @NonNull
    private final Originator mSubject;
    @Nullable
    private final Contact mContact;
    @Nullable
    private final Group mGroup;
    @Nullable
    private final ImageId mOldAvatarId;
    @Nullable
    private final GroupMemberConversation mGroupMember;
    @Nullable
    private PairRefreshInvocation mInvocation;
    private ImageId mAvatarId;
    private final String mOldName;
    private List<AttributeNameValue> mPreviousAttributes;

    public RefreshObjectExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, @NonNull PairRefreshInvocation invocation,
                                 @NonNull Originator subject) {
        super(twinmeContextImpl, BaseService.DEFAULT_REQUEST_ID, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "RefreshObjectExecutor: twinmeContextImpl=" + twinmeContextImpl +
                    " invocation=" + invocation + " subject=" + subject);
        }

        TwincodeOutbound peerTwincodeOutbound = subject.getPeerTwincodeOutbound();
        GroupMemberConversation member = null;
        mSubject = subject;
        if (subject instanceof Contact) {
            mContact = (Contact) subject;
            mGroup = null;
        } else if (subject instanceof Group) {
            mGroup = (Group) subject;
            mContact = null;

            if (invocation.invocationAttributes != null) {
                UUID peerTwincodeOutboundId = AttributeNameValue.getUUIDAttribute(invocation.invocationAttributes, PairProtocol.PARAM_TWINCODE_OUTBOUND_ID);
                UUID groupTwincodeId = mGroup.getGroupTwincodeOutboundId();

                // If the twincode outbound attribute in the invocation does not match the group twincode
                // the updated twincode is a group member and we have to get it from the group conversation.
                if (peerTwincodeOutboundId != null && !peerTwincodeOutboundId.equals(groupTwincodeId) && groupTwincodeId != null) {
                    member = twinmeContextImpl.getConversationService().getGroupMemberConversation(groupTwincodeId, peerTwincodeOutboundId);

                    if (member != null) {
                        peerTwincodeOutbound = member.getPeerTwincodeOutbound();
                    }
                }
            }
        } else {
            mGroup = null;
            mContact = null;
        }

        mPeerTwincodeOutbound = peerTwincodeOutbound;
        mGroupMember = member;
        mOldName = mPeerTwincodeOutbound != null ? mPeerTwincodeOutbound.getName() : null;
        mOldAvatarId = mPeerTwincodeOutbound != null ? mPeerTwincodeOutbound.getAvatarId() : null;
        mInvocation = invocation;
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
            if ((mState & GET_PEER_IMAGE) != 0 && (mState & GET_PEER_IMAGE_DONE) == 0) {
                mState &= ~GET_PEER_IMAGE;
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
        // Step 1: refresh the peer twincode.
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
        // Step 2: get the peer thumbnail image so that we have it in our local cache before displaying the notification.
        //

        if (mAvatarId != null) {

            if ((mState & GET_PEER_IMAGE) == 0) {
                mState |= GET_PEER_IMAGE;

                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.getImageFromServer(mAvatarId, ImageService.Kind.THUMBNAIL, (ErrorCode errorCode, Bitmap avatar) -> {
                    mState |= GET_PEER_IMAGE_DONE;
                    onOperation();
                });

                // Delete the old avatar id (this is a local delete, ignore the result).
                if (mOldAvatarId != null) {
                    imageService.deleteImage(mOldAvatarId, (ErrorCode lStatus, ImageId imageId) -> {
                    });
                }
                return;
            }
            if ((mState & GET_PEER_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3: if the peer's name was changed and was not modified locally, update it.
        //

        if ((mState & UPDATE_OBJECT) == 0) {
            mState |= UPDATE_OBJECT;

            mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mSubject, 202);

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.updateObject: subject=" + mSubject);
            }
            mTwinmeContextImpl.getRepositoryService().updateObject(mSubject, this::onUpdateObject);
            return;
        }
        if ((mState & UPDATE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //

        // Post a notification when the subject's attributes was changed (except if it was a group member).
        if (mTwinmeContextImpl.isVisible(mSubject) && mPreviousAttributes != null && mGroupMember == null) {
            mTwinmeContextImpl.getNotificationCenter().onUpdateContact(mSubject, mPreviousAttributes);
        }

        // Trigger the onUpdateContact or onUpdateGroup to give a chance to take into account the name update.
        if (mContact != null) {
            mTwinmeContextImpl.onUpdateContact(mRequestId, mContact);
        } else if (mGroup != null && mGroupMember == null) {
            mTwinmeContextImpl.onUpdateGroup(mRequestId, mGroup);
        }

        stop();
    }

    private void onRefreshTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable List<AttributeNameValue> previousAttributes) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRefreshTwincodeOutbound: errorCode=" + errorCode + " previousAttributes=" + previousAttributes);
        }

        if (errorCode != ErrorCode.SUCCESS || previousAttributes == null) {

            onOperationError(REFRESH_PEER_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mState |= REFRESH_PEER_TWINCODE_OUTBOUND_DONE;

        mPreviousAttributes = previousAttributes;
        mSubject.setPeerTwincodeOutbound(mPeerTwincodeOutbound);

        // Check if we have a new avatarId for this contact.
        if (mOldAvatarId == null || !mOldAvatarId.equals(mSubject.getAvatarId())) {
            mAvatarId = mSubject.getAvatarId();
        }

        // Update the contact's name if it was not modified locally.
        if (mContact != null && !mContact.updatePeerName(mPeerTwincodeOutbound, mOldName)) {
            mState |= UPDATE_OBJECT | UPDATE_OBJECT_DONE;

            // Likewise for the group name if it was the group twincode.
        } else if (mGroupMember == null && mGroup != null && !mGroup.updatePeerName(mPeerTwincodeOutbound, mOldName)) {
            mState |= UPDATE_OBJECT | UPDATE_OBJECT_DONE;

            // If it was a group member, no need to update the object.
        } else if (mGroupMember != null) {
            mState |= UPDATE_OBJECT | UPDATE_OBJECT_DONE;
        }
        onOperation();
    }

    private void onUpdateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateObject: object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || object == null) {

            onOperationError(UPDATE_OBJECT, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, object, mSubject);

        mState |= UPDATE_OBJECT_DONE;
        onOperation();
    }

    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // If the peer twincode does not exist anymore, proceed with an unbind: this contact is dead now.
        if (errorCode == ErrorCode.ITEM_NOT_FOUND && operationId == REFRESH_PEER_TWINCODE_OUTBOUND) {
            mState |= REFRESH_PEER_TWINCODE_OUTBOUND_DONE | UPDATE_OBJECT | UPDATE_OBJECT_DONE;
            if (mInvocation != null && mContact != null) {
                mTwinmeContextImpl.unbindContact(mRequestId, mInvocation.getId(), mContact);
                mInvocation = null;
            }
            stop();
            return;
        }

        super.onOperationError(operationId, errorCode, errorParameter);
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
