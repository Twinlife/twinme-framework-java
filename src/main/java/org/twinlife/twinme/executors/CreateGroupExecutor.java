/*
 *  Copyright (c) 2018-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.graphics.Bitmap;
import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService.InvitationDescriptor;
import org.twinlife.twinlife.ConversationService.GroupConversation;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.conversation.GroupProtocol;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupFactory;
import org.twinlife.twinme.models.GroupMember;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.10
//
// User foreground operation: must be connected with a timeout if connection does not work.

/**
 * Executor for the creation of a group.
 * - create the group member twincode with the member name, avatar and inviter member id (optional),
 * - get the group member twincode outbound,
 * - create the group twincode with the group name, the group member twincode and group avatar (optional),
 * - get the group twincode outbound,
 * - create the group local object in the repository,
 * - create the group conversation object in the conversation service.
 * The step4 (create the group twincode) is optional and is not made when a user joins the group.
 * The inviter's member twincode outbound id is stored in the group member's twincode in the "invited-by" attribute.
 * The group creator's member twincode outbound id is stored in the group twincode in the "created-by" attribute.
 */
public class CreateGroupExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "CreateGroupExecutor";
    private static final boolean DEBUG = false;

    private static final int FIND_GROUP = 1;
    private static final int CREATE_IMAGE = 1 << 1;
    private static final int CREATE_IMAGE_DONE = 1 << 2;
    private static final int COPY_PROFILE_IMAGE = 1 << 3;
    private static final int COPY_PROFILE_IMAGE_DONE = 1 << 4;
    private static final int CREATE_MEMBER_TWINCODE = 1 << 5;
    private static final int CREATE_MEMBER_TWINCODE_DONE = 1 << 6;
    private static final int CREATE_GROUP_TWINCODE = 1 << 7;
    private static final int CREATE_GROUP_TWINCODE_DONE = 1 << 8;
    private static final int GET_GROUP_TWINCODE_OUTBOUND = 1 << 9;
    private static final int GET_GROUP_TWINCODE_OUTBOUND_DONE = 1 << 10;
    private static final int GET_GROUP_IMAGE = 1 << 11;
    private static final int GET_GROUP_IMAGE_DONE = 1 << 12;
    private static final int CREATE_GROUP_OBJECT = 1 << 13;
    private static final int CREATE_GROUP_OBJECT_DONE = 1 << 14;
    private static final int INVOKE_TWINCODE = 1 << 15;
    private static final int INVOKE_TWINCODE_DONE = 1 << 16;
    private static final int ACCEPT_INVITATION = 1 << 17;
    private static final int ACCEPT_INVITATION_DONE = 1 << 18;
    private static final int UPDATE_GROUP = 1 << 19;
    private static final int UPDATE_GROUP_DONE = 1 << 20;

    @Nullable
    private TwincodeFactory mMemberTwincode;
    private Group mGroup;
    private final String mName;
    @Nullable
    private final String mDescription;
    private String mIdentityName;
    private final boolean mIsOwner;
    private final UUID mInvitedByMemberTwincodeId;
    private UUID mGroupTwincodeId;
    @Nullable
    private TwincodeFactory mGroupTwincodeFactory;
    private ExportedImageId mAvatarId;
    private ImageId mGroupAvatarId;
    @Nullable
    private final Bitmap mAvatar;
    @Nullable
    private final File mAvatarFile;
    @NonNull
    private final Space mSpace;
    private ImageId mIdentityAvatarId;
    private ExportedImageId mCopiedIdentityAvatarId;
    private TwincodeOutbound mMemberTwincodeOutbound;
    private TwincodeOutbound mGroupTwincodeOutbound;
    @Nullable
    private final TwincodeOutbound mInvitationTwincode;
    @Nullable
    private final InvitationDescriptor mInvitation;
    private GroupConversation mConversation;

    public CreateGroupExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Space space,
                               @NonNull String name, @Nullable String description, @Nullable Bitmap avatar, @Nullable File avatarFile) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);

        mSpace = space;
        mName = name;
        mDescription = description;
        mAvatar = avatar;
        mAvatarFile = avatarFile;
        mGroupTwincodeId = null;
        mInvitedByMemberTwincodeId = null;
        mIsOwner = true;
        mInvitationTwincode = null;
        mConversation = null;
        mInvitation = null;
        mState |= FIND_GROUP;

        Profile profile = space.getProfile();
        if (profile != null) {
            mIdentityName = profile.getName();
            mIdentityAvatarId = profile.getAvatarId();
        }
    }

    public CreateGroupExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Space space,
                               @NonNull InvitationDescriptor invitation) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);

        mSpace = space;
        mName = invitation.getName();
        mDescription = null;
        mAvatar = null;
        mAvatarFile = null;
        mGroupTwincodeId = invitation.getGroupTwincodeId();
        mInvitedByMemberTwincodeId = invitation.getInviterTwincodeId();
        mIsOwner = false;
        mInvitationTwincode = null;
        mConversation = null;
        mInvitation = invitation;

        Profile profile = space.getProfile();
        if (profile != null) {
            mIdentityName = profile.getName();
            mIdentityAvatarId = profile.getAvatarId();
        }
    }

    public CreateGroupExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Space space,
                               @NonNull TwincodeOutbound invitationTwincode, long timeout) {
        super(twinmeContextImpl, requestId, LOG_TAG, timeout);

        mSpace = space;
        mName = invitationTwincode.getName();
        mDescription = invitationTwincode.getDescription();
        mAvatar = null;
        mAvatarFile = null;
        mGroupTwincodeId = TwinmeAttributes.getChannel(invitationTwincode);
        mConversation = null;
        mInvitation = null;
        if (mGroupTwincodeId == null) {
            mStopped = true;
        }
        mInvitedByMemberTwincodeId = null;
        mIsOwner = false;
        mInvitationTwincode = invitationTwincode;

        Profile profile = space.getProfile();
        if (profile != null) {
            mIdentityName = profile.getName();
            mIdentityAvatarId = profile.getAvatarId();
        }
    }

    public CreateGroupExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Space space,
                               @NonNull UUID groupTwincodeId) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);

        mSpace = space;
        mName = "Engine";
        mIdentityName = "Engine";
        mDescription = null;
        mAvatar = null;
        mAvatarFile = null;
        mAvatarId = null;
        mGroupTwincodeId = groupTwincodeId;
        mInvitedByMemberTwincodeId = null;
        mIsOwner = true;
        mInvitationTwincode = null;
        mConversation = null;
        mInvitation = null;

        Profile profile = space.getProfile();
        if (profile != null) {
            mIdentityName = profile.getName();
            mIdentityAvatarId = profile.getAvatarId();
        }
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & CREATE_IMAGE) != 0 && (mState & CREATE_IMAGE_DONE) == 0) {
                mState &= ~CREATE_IMAGE;
            }
            if ((mState & COPY_PROFILE_IMAGE) != 0 && (mState & COPY_PROFILE_IMAGE_DONE) == 0) {
                mState &= ~COPY_PROFILE_IMAGE;
            }
            if ((mState & CREATE_GROUP_TWINCODE) != 0 && (mState & CREATE_GROUP_TWINCODE_DONE) == 0) {
                mState &= ~CREATE_GROUP_TWINCODE;
            }
            if ((mState & CREATE_MEMBER_TWINCODE) != 0 && (mState & CREATE_MEMBER_TWINCODE_DONE) == 0) {
                mState &= ~CREATE_MEMBER_TWINCODE;
            }
            if ((mState & GET_GROUP_TWINCODE_OUTBOUND) != 0 && (mState & GET_GROUP_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~GET_GROUP_TWINCODE_OUTBOUND;
            }
            if ((mState & GET_GROUP_IMAGE) != 0 && (mState & GET_GROUP_IMAGE_DONE) == 0) {
                mState &= ~GET_GROUP_IMAGE;
            }
            if ((mState & INVOKE_TWINCODE) != 0 && (mState & INVOKE_TWINCODE_DONE) == 0) {
                mState &= ~INVOKE_TWINCODE;
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
        // Step 1a: look for an existing group conversation and retrieve the associated group and group member identity.
        //
        if (mGroupTwincodeId != null && (mState & FIND_GROUP) == 0) {
            mState |= FIND_GROUP;

            mConversation = mTwinmeContextImpl.getConversationService().getGroupConversationWithGroupTwincodeId(mGroupTwincodeId);
            if (mConversation != null) {
                mGroup = (Group) mConversation.getSubject();
                mMemberTwincodeOutbound = mGroup.getMemberTwincodeOutbound();
                if (mMemberTwincodeOutbound != null) {
                    mState |= CREATE_IMAGE | CREATE_IMAGE_DONE;
                    mState |= COPY_PROFILE_IMAGE | COPY_PROFILE_IMAGE_DONE;
                    mState |= CREATE_MEMBER_TWINCODE | CREATE_MEMBER_TWINCODE_DONE;
                    mState |= CREATE_GROUP_OBJECT | CREATE_GROUP_OBJECT_DONE;
                }
            }
        }

        //
        // Step 1a: create the group image if there is one.
        //
        if (mAvatar != null) {

            if ((mState & CREATE_IMAGE) == 0) {
                mState |= CREATE_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.createImage: imageFile=" + mAvatarFile);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.createImage(mAvatarFile, mAvatar, this::onCreateGroupImage);
                return;
            }
            if ((mState & CREATE_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 1a: create a copy of the identity image if there is one (privacy constraint).
        //
        if (mIdentityAvatarId != null) {

            if ((mState & COPY_PROFILE_IMAGE) == 0) {
                mState |= COPY_PROFILE_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.copyImage: imageId=" + mIdentityAvatarId);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.copyImage(mIdentityAvatarId, this::onCopyImage);
                return;
            }
            if ((mState & COPY_PROFILE_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 1: create the group member twincode.
        //

        if ((mState & CREATE_MEMBER_TWINCODE) == 0) {
            mState |= CREATE_MEMBER_TWINCODE;

            List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
            PairProtocol.setTwincodeAttributePair(twincodeFactoryAttributes);

            List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
            if (mIdentityName != null) {
                TwinmeAttributes.setTwincodeAttributeName(twincodeOutboundAttributes, mIdentityName);
                twincodeOutboundAttributes.add(new BaseService.AttributeNameStringValue("member", mIdentityName));
            }
            if (mCopiedIdentityAvatarId != null) {
                TwinmeAttributes.setTwincodeAttributeAvatarId(twincodeOutboundAttributes, mCopiedIdentityAvatarId);
            }
            if (mInvitedByMemberTwincodeId != null) {
                TwinmeAttributes.setTwincodeAttributeInvitedBy(twincodeOutboundAttributes, mInvitedByMemberTwincodeId);
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: twincodeFactoryAttributes=" + twincodeFactoryAttributes
                        + "twincodeInboundAttributes=null" + "twincodeOutboundAttributes=" + twincodeOutboundAttributes);
            }
            mTwinmeContextImpl.getTwincodeFactoryService().createTwincode(twincodeFactoryAttributes, null,
                    twincodeOutboundAttributes, null,
                    GroupMember.SCHEMA_ID, this::onCreateMemberTwincode);
            return;
        }
        if ((mState & CREATE_MEMBER_TWINCODE_DONE) == 0) {
            return;
        }

        //
        // Step 3: create the group twincode (unless we are joining a group).
        //

        if (mGroupTwincodeId == null) {

            if ((mState & CREATE_GROUP_TWINCODE) == 0) {
                mState |= CREATE_GROUP_TWINCODE;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mName, 371);

                List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
                PairProtocol.setTwincodeAttributePair(twincodeFactoryAttributes);

                List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
                TwinmeAttributes.setTwincodeAttributeName(twincodeOutboundAttributes, mName);
                twincodeOutboundAttributes.add(new BaseService.AttributeNameStringValue("group", mName));
                if (mAvatarId != null) {
                    TwinmeAttributes.setTwincodeAttributeAvatarId(twincodeOutboundAttributes, mAvatarId);
                }
                if (mDescription != null) {
                    TwinmeAttributes.setTwincodeAttributeDescription(twincodeOutboundAttributes, mDescription);
                }
                TwinmeAttributes.setTwincodeAttributeCreatedBy(twincodeOutboundAttributes, mMemberTwincodeOutbound.getId());

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: twincodeFactoryAttributes=" + twincodeFactoryAttributes
                            + "twincodeInboundAttributes=null" + "twincodeOutboundAttributes=" + twincodeOutboundAttributes);
                }
                mTwinmeContextImpl.getTwincodeFactoryService().createTwincode(twincodeFactoryAttributes, null,
                        twincodeOutboundAttributes, null, Group.SCHEMA_ID, this::onCreateGroupTwincode);
                return;
            }
            if ((mState & CREATE_GROUP_TWINCODE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 4a: get the group twincode outbound.
        //

        if ((mState & GET_GROUP_TWINCODE_OUTBOUND) == 0) {
            mState |= GET_GROUP_TWINCODE_OUTBOUND;

            mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mGroupTwincodeId, 407);

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeOutboundService.getTwincode: twincodeOutboundId=" + mGroupTwincodeId);
            }
            if (mInvitation != null && mInvitation.getPublicKey() != null) {
                mTwinmeContextImpl.getTwincodeOutboundService().getSignedTwincode(mGroupTwincodeId, mInvitation.getPublicKey(),
                        TrustMethod.PEER, this::onGetGroupTwincodeOutbound);
            } else {
                mTwinmeContextImpl.getTwincodeOutboundService().getTwincode(mGroupTwincodeId, TwincodeOutboundService.REFRESH_PERIOD,
                        this::onGetGroupTwincodeOutbound);
            }
            return;
        }
        if ((mState & GET_GROUP_TWINCODE_OUTBOUND_DONE) == 0) {
            return;
        }

        //
        // Step 4a: get the group image so that we have it in the cache when we are done.
        // (If we're the creator of the group the image will be found locally and no network request will be made).
        //
        if (mGroupAvatarId != null) {

            if ((mState & GET_GROUP_IMAGE) == 0) {
                mState |= GET_GROUP_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.getImage: imageId=" + mAvatarId);
                }
                mTwinmeContextImpl.getImageService().getImageFromServer(mGroupAvatarId, ImageService.Kind.THUMBNAIL, (ErrorCode errorCode, Bitmap avatar) -> {
                    mState |= GET_GROUP_IMAGE_DONE;
                    onOperation();
                });
                return;
            }
            if ((mState & GET_GROUP_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 5: create the group object that links the group ID and the member twincode.
        //

        if (mMemberTwincode != null && (mState & CREATE_GROUP_OBJECT) == 0) {
            mState |= CREATE_GROUP_OBJECT;

            mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mMemberTwincodeOutbound, 455);

            mTwinmeContextImpl.getRepositoryService().createObject(GroupFactory.INSTANCE,
                    RepositoryService.AccessRights.PRIVATE, (RepositoryObject object) -> {
                        Group group = (Group) object;
                        group.setName(mName);
                        group.setDescription(mDescription);
                        group.setTwincodeFactory(mMemberTwincode);
                        if (mGroupTwincodeFactory != null) {
                            group.setGroupTwincodeFactory(mGroupTwincodeFactory);
                        }
                        group.setGroupTwincodeOutbound(mGroupTwincodeOutbound);
                        group.setSpace(mSpace);
                    }, this::onCreateObject);
            return;
        }
        if ((mState & CREATE_GROUP_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Step 6: send the subscribe invocation on the invitation twincode.
        //
        if (mInvitationTwincode != null) {

            if ((mState & INVOKE_TWINCODE) == 0) {
                mState |= INVOKE_TWINCODE;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mMemberTwincodeOutbound, 483);
                List<BaseService.AttributeNameValue> attributes;
                attributes = new ArrayList<>();
                GroupProtocol.setInvokeTwincodeActionGroupSubscribeMemberTwincodeId(attributes, mMemberTwincodeOutbound.getId());

                long requestId = newOperation(INVOKE_TWINCODE);
                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.invokeTwincode: requestId=" + requestId
                            + " invitationTwincodeOutboundId=" + mInvitationTwincode.getId() +
                            " attributes=" + attributes);
                }
                mTwinmeContextImpl.getTwincodeOutboundService().invokeTwincode(mInvitationTwincode,
                        TwincodeOutboundService.INVOKE_URGENT, GroupProtocol.ACTION_GROUP_SUBSCRIBE, attributes, this::onInvokeTwincode);
                return;
            }
            if ((mState & INVOKE_TWINCODE_DONE) == 0) {
                return;
            }
        }

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mGroup, 503);

        // If we were leaving this group, update because we have now accepted a new invitation.
        if (mGroup != null) {
            if (mGroup.isLeaving() && (mState & UPDATE_GROUP) == 0) {
                mState |= UPDATE_GROUP;
                mGroup.clearLeaving();
                mTwinmeContextImpl.getRepositoryService().updateObject(mGroup, this::onUpdateGroup);
                return;
            }
            if ((mState & UPDATE_GROUP) != 0 && (mState & UPDATE_GROUP_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step: create the group conversation.  If this fails (database creation error),
        // cleanup the group that was created and report the error.
        //
        if (mConversation == null) {
            mConversation = mTwinmeContextImpl.getConversationService().createGroup(mGroup, mIsOwner);
            if (mConversation == null) {
                DeleteGroupExecutor deleteGroupExecutor = new DeleteGroupExecutor(mTwinmeContextImpl, mTwinmeContextImpl.newRequestId(), mGroup, 0);
                deleteGroupExecutor.start();
                onOperationError(CREATE_GROUP_OBJECT, ErrorCode.DATABASE_ERROR, null);
                return;
            }
        }

        // We must accept the group invitation and we have not done it yet.
        if (mGroup != null) {
            if (mInvitation != null) {
                if ((mState & ACCEPT_INVITATION) == 0) {
                    mState |= ACCEPT_INVITATION;
                    ErrorCode result = mTwinmeContextImpl.getConversationService().joinGroup(mRequestId, mInvitation.getDescriptorId(), mGroup);
                    if (result != ErrorCode.SUCCESS) {
                        onOperationError(ACCEPT_INVITATION, result, null);
                    } else {
                        mTwinmeContextImpl.onCreateGroup(mRequestId, mGroup, mConversation);
                    }
                }
                if ((mState & ACCEPT_INVITATION_DONE) == 0) {
                    return;
                }

            } else {
                mTwinmeContextImpl.onCreateGroup(mRequestId, mGroup, mConversation);
            }
        }
        stop();
    }

    private void onCreateGroupImage(ErrorCode status, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateGroupImageImage: status=" + status + " imageId=" + imageId);
        }

        if (status != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(CREATE_IMAGE, status, null);
            return;
        }

        mState |= CREATE_IMAGE_DONE;
        mAvatarId = imageId;
        onOperation();
    }

    private void onCopyImage(ErrorCode status, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCopyImage: status=" + status + " imageId=" + imageId);
        }

        if (status != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(COPY_PROFILE_IMAGE, status, mIdentityAvatarId.toString());
            return;
        }

        mState |= COPY_PROFILE_IMAGE_DONE;
        mCopiedIdentityAvatarId = imageId;
        onOperation();
    }

    private void onCreateGroupTwincode(@NonNull ErrorCode errorCode, @Nullable TwincodeFactory twincodeFactory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateGroupTwincode: factory=" + twincodeFactory);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeFactory == null) {

            onOperationError(CREATE_GROUP_TWINCODE, errorCode, null);
            return;
        }

        mState |= CREATE_GROUP_TWINCODE_DONE;
        mGroupTwincodeFactory = twincodeFactory;
        mGroupTwincodeId = twincodeFactory.getTwincodeOutbound().getId();
        onOperation();
    }

    private void onCreateMemberTwincode(@NonNull ErrorCode errorCode, @Nullable TwincodeFactory twincodeFactory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateMemberTwincode: factory=" + twincodeFactory);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeFactory == null) {

            onOperationError(CREATE_MEMBER_TWINCODE, errorCode, null);
            return;
        }

        mState |= CREATE_MEMBER_TWINCODE_DONE;
        mMemberTwincode = twincodeFactory;
        mMemberTwincodeOutbound = twincodeFactory.getTwincodeOutbound();
        onOperation();
    }

    private void onGetGroupTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetGroupTwincodeOutbound: errorCode=" + errorCode + " twincodeOutbound=" + twincodeOutbound);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {

            onOperationError(GET_GROUP_TWINCODE_OUTBOUND, errorCode, mGroupTwincodeId.toString());
            return;
        }

        mState |= GET_GROUP_TWINCODE_OUTBOUND_DONE;

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeOutbound.getId(), mGroupTwincodeId);

        mGroupTwincodeOutbound = twincodeOutbound;
        mGroupAvatarId = mGroupTwincodeOutbound.getAvatarId();
        onOperation();
    }

    private void onCreateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateObject: object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof Group)) {

            onOperationError(CREATE_GROUP_OBJECT, errorCode, null);
            return;
        }

        mState |= CREATE_GROUP_OBJECT_DONE;
        mGroup = (Group) object;
        onOperation();
    }

    private void onUpdateGroup(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateGroup: object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof Group)) {

            onOperationError(UPDATE_GROUP, errorCode, null);
            return;
        }

        mState |= UPDATE_GROUP_DONE;
        onOperation();
    }

    private void onInvokeTwincode(@NonNull ErrorCode errorCode, @Nullable UUID invocationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvokeTwincode: invocationId=" + invocationId);
        }

        if (errorCode != ErrorCode.SUCCESS || invocationId == null) {

            onOperationError(INVOKE_TWINCODE, errorCode, null);
            return;
        }

        mState |= INVOKE_TWINCODE_DONE;
        onOperation();
    }
}
