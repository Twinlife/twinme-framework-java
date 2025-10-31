/*
 *  Copyright (c) 2019-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeURI;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupMember;
import org.twinlife.twinme.models.Invitation;
import org.twinlife.twinme.models.InvitationFactory;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.11
//
// User foreground operation: must be connected with a timeout if connection does not work.

public class CreateInvitationExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "CreateInvitationExec";
    private static final boolean DEBUG = false;

    private static final int COPY_IMAGE = 1;
    private static final int COPY_IMAGE_DONE = 1 << 1;
    private static final int CREATE_INVITATION_TWINCODE = 1 << 2;
    private static final int CREATE_INVITATION_TWINCODE_DONE = 1 << 3;
    private static final int GET_TWINCODE_OUTBOUND = 1 << 4;
    private static final int GET_TWINCODE_OUTBOUND_DONE = 1 << 5;
    private static final int GET_INVITATION_TWINCODE = 1 << 6;
    private static final int GET_INVITATION_TWINCODE_DONE = 1 << 7;
    private static final int BIND_INVITATION_TWINCODE = 1 << 8;
    private static final int BIND_INVITATION_TWINCODE_DONE = 1 << 9;
    private static final int CREATE_INVITATION_OBJECT = 1 << 10;
    private static final int CREATE_INVITATION_OBJECT_DONE = 1 << 11;
    private static final int PUSH_INVITATION = 1 << 12;
    private static final int PUSH_INVITATION_DONE = 1 << 13;
    private static final int UPDATE_INVITATION_OBJECT = 1 << 14;
    private static final int UPDATE_INVITATION_OBJECT_DONE = 1 << 15;

    private class ConversationServiceObserver extends AbstractTimeoutTwinmeExecutor.ConversationServiceObserver {

        @Override
        public void onPushDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onPushDescriptor: requestId=" + requestId
                        + " conversationId=" + conversation.getId() + " descriptor=" + descriptor);
            }

            if (getOperation(requestId) > 0) {
                CreateInvitationExecutor.this.onPushDescriptor(descriptor);
                onOperation();
            }
        }
    }

    private final String mIdentityName;
    private final ImageId mIdentityAvatarId;
    private ExportedImageId mCopiedIdentityAvatarId;
    private Group mGroup;
    @Nullable
    private final Contact mContact;
    @Nullable
    private final UUID mGroupId;
    @Nullable
    private final UUID mSendTo;
    @Nullable
    private final UUID mGroupMemberTwincodeId;
    @Nullable
    private final UUID mTwincodeInboundId;
    @Nullable
    private TwincodeInbound mTwincodeInbound;
    @Nullable
    private TwincodeOutbound mIdentityTwincodeOutbound;
    @Nullable
    private final UUID mTwincodeOutboundId;
    @Nullable
    private TwincodeFactory mInvitationTwincode;
    private TwincodeOutbound mTwincodeOutbound;
    private Invitation mInvitation;
    private TwincodeURI mTwincodeURI;
    @NonNull
    private final Space mSpace;
    private long mPermissions;

    private final ConversationServiceObserver mConversationServiceObserver;

    public CreateInvitationExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                    @NonNull Space space,
                                    @Nullable GroupMember contactGroupMember) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);

        mConversationServiceObserver = new ConversationServiceObserver();
        mTwincodeInboundId = null;
        mTwincodeOutboundId = null;
        mContact = null;
        mSpace = space;
        if (contactGroupMember != null) {
            mGroup = (Group) contactGroupMember.getGroup();
            mGroupId = mGroup.getId();
            mGroupMemberTwincodeId = contactGroupMember.getPeerTwincodeOutboundId();
        } else {
            mGroupId = null;
            mGroupMemberTwincodeId = null;
        }
        mSendTo = mGroupMemberTwincodeId;
        Profile profile = mSpace.getProfile();

        if (profile != null) {
            mIdentityName = profile.getName();
            mIdentityAvatarId = profile.getAvatarId();
            mIdentityTwincodeOutbound = profile.getTwincodeOutbound();
        } else {
            mIdentityName = null;
            mIdentityAvatarId = null;
            mIdentityTwincodeOutbound = null;
            mStopped = true;
        }
    }

    public CreateInvitationExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                    @NonNull Space space,
                                    @NonNull Group group, long permissions) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);

        mConversationServiceObserver = new ConversationServiceObserver();
        mTwincodeInboundId = null;
        mTwincodeOutboundId = null;
        mSendTo = null;
        mContact = null;
        mGroup = group;
        mGroupId = group.getId();
        mPermissions = permissions;
        mSpace = space;
        mGroupMemberTwincodeId = null;
        mIdentityName = group.getName();
        mIdentityAvatarId = group.getAvatarId();
        mIdentityTwincodeOutbound = null;
    }

    public CreateInvitationExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                    @NonNull Space space,
                                    @NonNull Group group, long permissions, @NonNull UUID twincodeIn, @NonNull UUID twincodeOut) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);

        mConversationServiceObserver = new ConversationServiceObserver();
        mContact = null;
        mGroup = group;
        mGroupId = group.getId();
        mPermissions = permissions;
        mSpace = space;
        mGroupMemberTwincodeId = null;
        mSendTo = null;
        mIdentityName = group.getName();
        mIdentityAvatarId = group.getAvatarId();
        mTwincodeInboundId = twincodeIn;
        mTwincodeOutboundId = twincodeOut;
    }

    public CreateInvitationExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                    @NonNull Space space, @NonNull Contact contact, @NonNull UUID sendTo) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);

        mConversationServiceObserver = new ConversationServiceObserver();
        mTwincodeInboundId = null;
        mTwincodeOutboundId = null;
        mGroupId = null;
        mContact = contact;
        mSpace = space;
        mSendTo = sendTo;
        mGroupMemberTwincodeId = null;
        Profile profile = mSpace.getProfile();

        if (profile != null) {
            mIdentityName = profile.getName();
            mIdentityAvatarId = profile.getAvatarId();
            mIdentityTwincodeOutbound = profile.getTwincodeOutbound();
        } else {
            mIdentityName = null;
            mIdentityAvatarId = null;
            mIdentityTwincodeOutbound = null;
            mStopped = true;
        }
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
            if ((mState & COPY_IMAGE) != 0 && (mState & COPY_IMAGE_DONE) == 0) {
                mState &= ~COPY_IMAGE;
            }
            if ((mState & CREATE_INVITATION_TWINCODE) != 0 && (mState & CREATE_INVITATION_TWINCODE_DONE) == 0) {
                mState &= ~CREATE_INVITATION_TWINCODE;
            }
            if ((mState & GET_INVITATION_TWINCODE) != 0 && (mState & GET_INVITATION_TWINCODE_DONE) == 0) {
                mState &= ~GET_INVITATION_TWINCODE;
            }
            if ((mState & BIND_INVITATION_TWINCODE) != 0 && (mState & BIND_INVITATION_TWINCODE_DONE) == 0) {
                mState &= ~BIND_INVITATION_TWINCODE;
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

        if (mTwincodeInboundId == null) {

            //
            // Step 1a: create a copy of the identity image if there is one (privacy constraint).
            //
            if (mIdentityAvatarId != null) {

                if ((mState & COPY_IMAGE) == 0) {
                    mState |= COPY_IMAGE;

                    if (DEBUG) {
                        Log.d(LOG_TAG, "ImageService.copyImage: imageId=" + mIdentityAvatarId);
                    }
                    ImageService imageService = mTwinmeContextImpl.getImageService();
                    imageService.copyImage(mIdentityAvatarId, this::onCopyImage);
                    return;
                }
                if ((mState & COPY_IMAGE_DONE) == 0) {
                    return;
                }
            }

            //
            // Step 1b: create the invitation twincode.
            //
            if ((mState & CREATE_INVITATION_TWINCODE) == 0) {
                mState |= CREATE_INVITATION_TWINCODE;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mIdentityName, 297);

                List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
                PairProtocol.setTwincodeAttributePair(twincodeFactoryAttributes);

                List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
                TwinmeAttributes.setTwincodeAttributeName(twincodeOutboundAttributes, mIdentityName);
                if (mCopiedIdentityAvatarId != null) {
                    TwinmeAttributes.setTwincodeAttributeAvatarId(twincodeOutboundAttributes, mCopiedIdentityAvatarId);
                }
                if (mGroupMemberTwincodeId != null) {
                    TwinmeAttributes.setTwincodeAttributeInvitationKind(twincodeOutboundAttributes, Invitation.CONTACT);
                } else if (mGroup != null) {
                    TwinmeAttributes.setTwincodeAttributeInvitationKind(twincodeOutboundAttributes, Invitation.CHANNEL);
                    TwinmeAttributes.setTwincodeAttributeChannel(twincodeOutboundAttributes, mGroup.getGroupTwincodeOutboundId());
                }

                // Copy a number of twincode attributes from the profile identity.
                if (mIdentityTwincodeOutbound != null) {
                    mTwinmeContextImpl.copySharedTwincodeAttributes(twincodeOutboundAttributes, mIdentityTwincodeOutbound);
                }

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: twincodeFactoryAttributes=" + twincodeFactoryAttributes
                            + "twincodeInboundAttributes=null" + "twincodeOutboundAttributes=" + twincodeOutboundAttributes);
                }
                mTwinmeContextImpl.getTwincodeFactoryService().createTwincode(twincodeFactoryAttributes, null,
                        twincodeOutboundAttributes, null,
                        Invitation.SCHEMA_ID, this::onCreateInvitationTwincode);
                return;
            }
            if ((mState & CREATE_INVITATION_TWINCODE_DONE) == 0) {
                return;
            }

        } else {
            //
            // Step 1b: the invitation twincode was created by the external factory, bind the invitation twincode.
            //

            if ((mState & GET_TWINCODE_OUTBOUND) == 0) {
                mState |= GET_TWINCODE_OUTBOUND;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mTwincodeOutboundId, 340);

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.getTwincode: twincodeOutboundId=" + mTwincodeOutboundId);
                }
                mTwinmeContextImpl.getTwincodeOutboundService().getTwincode(mTwincodeOutboundId, TwincodeOutboundService.REFRESH_PERIOD,
                        this::onGetTwincodeOutbound);
                return;
            }
            if ((mState & GET_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }

            if ((mState & GET_INVITATION_TWINCODE) == 0) {
                mState |= GET_INVITATION_TWINCODE;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mTwincodeInboundId, 356);

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeInboundService.getTwincode: twincodeInboundId=" + mTwincodeInboundId);
                }
                mTwinmeContextImpl.getTwincodeInboundService().getTwincode(mTwincodeInboundId, mTwincodeOutbound, this::onGetTwincode);
                return;
            }
            if ((mState & GET_INVITATION_TWINCODE_DONE) == 0) {
                return;
            }
            //
            // Step 1b: the invitation twincode was created by the external factory, bind the invitation twincode.
            //

            if ((mState & BIND_INVITATION_TWINCODE) == 0) {
                mState |= BIND_INVITATION_TWINCODE;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mTwincodeInbound, 374);

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeInboundService.bindTwincode: twincodeInboundId=" + mTwincodeInboundId);
                }
                mTwinmeContextImpl.getTwincodeInboundService().bindTwincode(mTwincodeInbound, this::onBindTwincode);
                return;
            }
            if ((mState & BIND_INVITATION_TWINCODE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3: create the invitation object and get the invitation link (necessary for the public key).
        //

        if ((mState & CREATE_INVITATION_OBJECT) == 0) {
            mState |= CREATE_INVITATION_OBJECT;

            mTwinmeContextImpl.getTwincodeOutboundService().createURI(TwincodeURI.Kind.Invitation, mTwincodeOutbound, (ErrorCode errorCode, TwincodeURI twincodeURI) -> mTwincodeURI = twincodeURI);
            mTwinmeContextImpl.getRepositoryService().createObject(InvitationFactory.INSTANCE,
                    RepositoryService.AccessRights.PRIVATE, (RepositoryObject object) -> {
                        Invitation invitation = (Invitation) object;
                        invitation.setSpace(mSpace);
                        invitation.setTwincodeFactory(mInvitationTwincode);
                        invitation.setTwincodeOutbound(mTwincodeOutbound);
                    }, this::onCreateObject);
            return;
        }
        if ((mState & CREATE_INVITATION_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Step 4: send the invitation twincode.
        //
        if (mSendTo != null && mTwincodeURI != null && mTwincodeURI.twincodeId != null && (mContact != null || mGroup != null)) {

            if ((mState & PUSH_INVITATION) == 0) {
                mState |= PUSH_INVITATION;

                long requestId = newOperation(PUSH_INVITATION);
                ConversationService conversationService = mTwinmeContextImpl.getConversationService();
                Conversation conversation;
                if (mContact != null) {
                    conversation = conversationService.getOrCreateConversation(mContact);
                } else {
                    conversation = conversationService.getConversation(mGroup);
                }
                if (conversation != null) {
                    conversationService.pushTwincode(requestId, conversation, mSendTo,
                            null, mTwincodeURI.twincodeId, Invitation.SCHEMA_ID, mTwincodeURI.pubKey, false, 0);
                }
                return;
            }
            if ((mState & PUSH_INVITATION_DONE) == 0) {
                return;
            }

            if ((mState & UPDATE_INVITATION_OBJECT) == 0) {
                mState |= UPDATE_INVITATION_OBJECT;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mInvitation, 437);

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwinmeContext.updateObject: invitation=" + mInvitation);
                }
                mTwinmeContextImpl.getRepositoryService().updateObject(mInvitation, this::onUpdateObject);
                return;
            }
            if ((mState & UPDATE_INVITATION_OBJECT_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mInvitation, 453);
        mTwinmeContextImpl.onCreateInvitation(mRequestId, mInvitation);

        stop();
    }

    private void onCopyImage(ErrorCode status, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCopyImage: status=" + status + " imageId=" + imageId);
        }

        if (status != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(COPY_IMAGE, status, mIdentityAvatarId.toString());
            return;
        }

        mState |= COPY_IMAGE_DONE;
        mCopiedIdentityAvatarId = imageId;
        onOperation();
    }

    private void onGetTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetTwincodeOutbound: errorCode=" + errorCode + " twincodeOutbound=" + twincodeOutbound);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {

            onOperationError(GET_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mState |= GET_TWINCODE_OUTBOUND_DONE;
        mTwincodeOutbound = twincodeOutbound;
        onOperation();
    }

    private void onGetTwincode(@NonNull ErrorCode errorCode, @Nullable TwincodeInbound twincodeInbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetTwincode: errorCode=" + errorCode + " twincodeInbound=" + twincodeInbound);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeInbound == null) {

            onOperationError(GET_INVITATION_TWINCODE, errorCode, null);
            return;
        }

        mState |= GET_INVITATION_TWINCODE_DONE;
        mTwincodeInbound = twincodeInbound;
        onOperation();
    }

    private void onBindTwincode(@NonNull ErrorCode errorCode, @Nullable UUID twincodeInboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBindTwincode: twincodeInboundId=" + twincodeInboundId);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeInboundId == null) {

            onOperationError(BIND_INVITATION_TWINCODE, errorCode, null);
            return;
        }

        mState |= BIND_INVITATION_TWINCODE_DONE;
        onOperation();
    }

    private void onCreateInvitationTwincode(@NonNull ErrorCode errorCode, @Nullable TwincodeFactory twincodeFactory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateInvitationTwincode: factory=" + twincodeFactory);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeFactory == null) {

            onOperationError(CREATE_INVITATION_TWINCODE, errorCode, null);
            return;
        }

        mState |= CREATE_INVITATION_TWINCODE_DONE;
        mInvitationTwincode = twincodeFactory;
        mTwincodeOutbound = twincodeFactory.getTwincodeOutbound();
        onOperation();
    }

    private void onCreateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateObject: errorCode=" + errorCode + " object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof Invitation)) {

            onOperationError(CREATE_INVITATION_OBJECT, errorCode, null);
            return;
        }

        mState |= CREATE_INVITATION_OBJECT_DONE;
        mInvitation = (Invitation) object;
        onOperation();
    }

    private void onPushDescriptor(@NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPushDescriptor: descriptor=" + descriptor);
        }

        mState |= PUSH_INVITATION_DONE;

        mInvitation.setDescriptorId(descriptor.getDescriptorId());
    }

    private void onUpdateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateObject: object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || object == null) {

            onOperationError(UPDATE_INVITATION_OBJECT, errorCode, null);
            return;
        }

        mState |= UPDATE_INVITATION_OBJECT_DONE;
        onOperation();
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
