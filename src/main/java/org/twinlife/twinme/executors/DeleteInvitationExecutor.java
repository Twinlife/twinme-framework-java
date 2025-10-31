/*
 *  Copyright (c) 2019-2024 twinlife SA.
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
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Invitation;

import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.6
//
// User foreground operation: must be connected with a timeout if connection does not work.
// BUT, when it is called from DeleteSpace, the timeout is set to infinity.

/**
 * Executor to delete the group object with the group member.
 */
public class DeleteInvitationExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "DeleteInvitationExec";
    private static final boolean DEBUG = false;

    private static final int UNBIND_INVITATION_TWINCODE_INBOUND = 1;
    private static final int UNBIND_INVITATION_TWINCODE_INBOUND_DONE = 1 << 1;
    private static final int DELETE_INVITATION_TWINCODE = 1 << 2;
    private static final int DELETE_INVITATION_TWINCODE_DONE = 1 << 3;
    private static final int DELETE_IDENTITY_IMAGE = 1 << 4;
    private static final int DELETE_IDENTITY_IMAGE_DONE = 1 << 5;
    private static final int DELETE_INVITATION_DESCRIPTOR = 1 << 6;
    private static final int DELETE_INVITATION_DESCRIPTOR_DONE = 1 << 7;
    private static final int DELETE_OBJECT = 1 << 8;
    private static final int DELETE_OBJECT_DONE = 1 << 9;

    private class ConversationServiceObserver extends AbstractTimeoutTwinmeExecutor.ConversationServiceObserver {

        @Override
        public void onMarkDescriptorDeleted(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onMarkDescriptorDeleted: requestId="
                        + requestId + " descriptor=" + descriptor);
            }

            if (getOperation(requestId) > 0) {
                DeleteInvitationExecutor.this.onMarkDescriptorDeleted(descriptor);
            }
        }
    }

    @NonNull
    private final Invitation mInvitation;
    @Nullable
    private final TwincodeInbound mTwincodeInbound;
    @Nullable
    private final ImageId mInvitationAvatarId;
    private final UUID mTwincodeFactoryId;
    @Nullable
    private final DescriptorId mDescriptorId;

    private final ConversationServiceObserver mConversationServiceObserver;

    public DeleteInvitationExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                    @NonNull Invitation invitation, long timeout) {
        super(twinmeContextImpl, requestId, LOG_TAG, timeout);
        if (DEBUG) {
            Log.d(LOG_TAG, "DeleteGroupExecutor: twinmeContextImpl=" + twinmeContextImpl
                    + " requestId=" + requestId + " invitation=" + invitation);
        }

        mInvitation = invitation;
        mTwincodeInbound = invitation.getTwincodeInbound();
        mTwincodeFactoryId = invitation.getTwincodeFactoryId();
        mInvitationAvatarId = invitation.getAvatarId();
        mDescriptorId = invitation.getDescriptorId();

        mConversationServiceObserver = new ConversationServiceObserver();
    }

    @Override
    public void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        mTwinmeContextImpl.getConversationService().addServiceObserver(mConversationServiceObserver);
        super.onTwinlifeReady();
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & UNBIND_INVITATION_TWINCODE_INBOUND) != 0 && (mState & UNBIND_INVITATION_TWINCODE_INBOUND_DONE) == 0) {
                mState &= ~UNBIND_INVITATION_TWINCODE_INBOUND;
            }
            if ((mState & DELETE_INVITATION_TWINCODE) != 0 && (mState & DELETE_INVITATION_TWINCODE_DONE) == 0) {
                mState &= ~DELETE_INVITATION_TWINCODE;
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
        // Step 1: unbind the invitation inbound twincode.
        //
        if (mTwincodeInbound != null) {

            if ((mState & UNBIND_INVITATION_TWINCODE_INBOUND) == 0) {
                mState |= UNBIND_INVITATION_TWINCODE_INBOUND;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeInboundService.unbindTwincode: twincodeInboundId=" + mTwincodeInbound);
                }
                mTwinmeContextImpl.getTwincodeInboundService().unbindTwincode(mTwincodeInbound, this::onUnbindTwincodeInbound);
                return;
            }
            if ((mState & UNBIND_INVITATION_TWINCODE_INBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2a: delete the invitation twincode.
        //
        if (mTwincodeFactoryId != null) {
            if ((mState & DELETE_INVITATION_TWINCODE) == 0) {
                mState |= DELETE_INVITATION_TWINCODE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeFactoryService.deleteTwincode: twincodeFactoryId=" + mTwincodeFactoryId);
                }
                mTwinmeContextImpl.getTwincodeFactoryService().deleteTwincode(mTwincodeFactoryId, this::onDeleteTwincode);
                return;
            }
            if ((mState & DELETE_INVITATION_TWINCODE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2b: delete the twincode avatarId.
        //
        if (mInvitationAvatarId != null) {

            if ((mState & DELETE_IDENTITY_IMAGE) == 0) {
                mState |= DELETE_IDENTITY_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.deleteImage: imageId=" + mInvitationAvatarId);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.deleteImage(mInvitationAvatarId, (ErrorCode status, ImageId imageId) -> {
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
        // Step 3: delete the invitation descriptor.
        //

        if (mDescriptorId != null) {

            if ((mState & DELETE_INVITATION_DESCRIPTOR) == 0) {
                mState |= DELETE_INVITATION_DESCRIPTOR;

                long requestId = newOperation(DELETE_INVITATION_DESCRIPTOR);
                if (DEBUG) {
                    Log.d(LOG_TAG, "ConversationService.markDescriptorDeleted: requestId=" + requestId
                                + " descriptorId=" + mDescriptorId);
                }
                mTwinmeContextImpl.getConversationService().markDescriptorDeleted(requestId, mDescriptorId);
                return;
            }
            if ((mState & DELETE_INVITATION_DESCRIPTOR_DONE) == 0) {
                return;
            }
        }

        //
        // Step 4: delete the invitation object.
        //

        if ((mState & DELETE_OBJECT) == 0) {
            mState |= DELETE_OBJECT;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.deleteObject: objectId=" + mInvitation.getId());
            }
            mTwinmeContextImpl.getRepositoryService().deleteObject(mInvitation, this::onDeleteObject);
            return;
        }
        if ((mState & DELETE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.onDeleteInvitation(mRequestId, mInvitation.getId());

        stop();
    }

    private void onUnbindTwincodeInbound(@NonNull ErrorCode errorCode, @Nullable UUID twincodeInboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUnbindTwincodeInbound: twincodeInboundId=" + twincodeInboundId);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeInboundId == null) {

            onOperationError(UNBIND_INVITATION_TWINCODE_INBOUND, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeInboundId, mInvitation.getTwincodeInboundId());

        mState |= UNBIND_INVITATION_TWINCODE_INBOUND_DONE;
        onOperation();
    }

    private void onDeleteTwincode(@NonNull ErrorCode errorCode, @Nullable UUID twincodeFactoryId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteMemberTwincode: twincodeFactoryId=" + twincodeFactoryId);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeFactoryId == null) {

            onOperationError(DELETE_INVITATION_TWINCODE, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeFactoryId, mInvitation.getTwincodeFactoryId());

        mState |= DELETE_INVITATION_TWINCODE_DONE;
        onOperation();
    }

    private void onMarkDescriptorDeleted(@NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onMarkDescriptorDeleted: descriptor=" + descriptor);
        }

        mState |= DELETE_INVITATION_DESCRIPTOR_DONE;
        onOperation();
    }

    private void onDeleteObject(@NonNull ErrorCode errorCode, @Nullable UUID objectId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteObject: objectId=" + objectId);
        }

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

                case UNBIND_INVITATION_TWINCODE_INBOUND:
                    mState |= UNBIND_INVITATION_TWINCODE_INBOUND_DONE;
                    onOperation();
                    return;

                case DELETE_INVITATION_TWINCODE:
                    mState |= DELETE_INVITATION_TWINCODE_DONE;
                    onOperation();
                    return;

                case DELETE_INVITATION_DESCRIPTOR:
                    mState |= DELETE_INVITATION_DESCRIPTOR_DONE;
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

        mStopped = true;

        mTwinmeContextImpl.getConversationService().removeServiceObserver(mConversationServiceObserver);

        super.stop();
    }
}
