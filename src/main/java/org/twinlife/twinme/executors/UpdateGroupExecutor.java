/*
 *  Copyright (c) 2015-2024 twinlife SA.
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
import org.twinlife.twinlife.BaseService.AttributeNameStringValue;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.GroupConversation;
import org.twinlife.twinlife.ConversationService.GroupMemberConversation;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.14
//
// User foreground operation: must be connected with a timeout if connection does not work.

public class UpdateGroupExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "UpdateGroupExecutor";
    private static final boolean DEBUG = false;

    private static final int CREATE_GROUP_IMAGE = 1;
    private static final int CREATE_GROUP_IMAGE_DONE = 1 << 1;
    private static final int CREATE_MEMBER_IMAGE = 1 << 2;
    private static final int CREATE_MEMBER_IMAGE_DONE = 1 << 3;
    private static final int COPY_MEMBER_IMAGE = 1 << 4;
    private static final int COPY_MEMBER_IMAGE_DONE = 1 << 5;
    private static final int UPDATE_GROUP_TWINCODE_OUTBOUND = 1 << 6;
    private static final int UPDATE_GROUP_TWINCODE_OUTBOUND_DONE = 1 << 7;
    private static final int UPDATE_MEMBER_TWINCODE_OUTBOUND = 1 << 8;
    private static final int UPDATE_MEMBER_TWINCODE_OUTBOUND_DONE = 1 << 9;
    private static final int UPDATE_OBJECT = 1 << 10;
    private static final int UPDATE_OBJECT_DONE = 1 << 11;
    private static final int DELETE_OLD_GROUP_IMAGE = 1 << 12;
    private static final int DELETE_OLD_GROUP_IMAGE_DONE = 1 << 13;
    private static final int DELETE_OLD_MEMBER_IMAGE = 1 << 14;
    private static final int DELETE_OLD_MEMBER_IMAGE_DONE = 1 << 15;
    private static final int INVOKE_TWINCODE_OUTBOUND = 1 << 16;
    private static final int INVOKE_TWINCODE_OUTBOUND_DONE = 1 << 17;

    @NonNull
    private final Group mGroup;
    @Nullable
    private final String mGroupName;
    @Nullable
    private final String mGroupDescription;
    @Nullable
    private final Bitmap mGroupAvatar;
    @Nullable
    private final File mGroupAvatarFile;
    private final boolean mCreateGroupImage;
    private final ImageId mCopyImageId;
    @Nullable
    private ExportedImageId mGroupAvatarId;
    @Nullable
    private final ImageId mOldGroupAvatarId;
    @Nullable
    private final String mGroupCapabilities;
    @Nullable
    private final String mProfileName;
    @Nullable
    private final Bitmap mProfileAvatar;
    @Nullable
    private final File mProfileAvatarFile;
    private final boolean mCreateMemberImage;
    @Nullable
    private ExportedImageId mProfileAvatarId;
    @Nullable
    private final ImageId mOldProfileAvatarId;
    private final Space mSpace;
    private final Space mOldSpace;
    @Nullable
    private final TwincodeOutbound mGroupTwincodeOutbound;
    @Nullable
    private final TwincodeOutbound mMemberTwincodeOutbound;

    private final boolean mUpdateGroupTwincode;
    @Nullable
    private List<TwincodeOutbound> mRefreshMembers;
    @Nullable
    private List<BaseService.AttributeNameValue> mRefreshAttributes;

    public UpdateGroupExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Group group,
                               @Nullable String name, @Nullable String groupDescription, @Nullable Bitmap avatar, @Nullable File avatarFile,
                               @Nullable String profileName, @Nullable Bitmap profileAvatar, @Nullable File profileAvatarFile, @Nullable Capabilities groupCapabilities) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateGroupExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " group=" + group + " name=" + name + " avatarFile=" + avatarFile + " profileName=" + profileName +
                    " profileAvatarFile=" + profileAvatarFile);
        }

        mGroup = group;
        mSpace = group.getSpace();
        mOldSpace = mSpace;
        mGroupName = name;
        mCopyImageId = null;
        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.PARAMETER, mGroup, 133);
        mCreateGroupImage = avatar != null;
        mCreateMemberImage = profileAvatar != null;

        boolean updateGroupName = mGroup.isOwner() && name != null && !Utils.equals(name, mGroup.getName());
        boolean updateGroupDescription = !Utils.equals(groupDescription, mGroup.getDescription());
        boolean updateGroupCapabilities = (groupCapabilities != null && !Utils.equals(groupCapabilities, mGroup.getCapabilities()));

        mGroupTwincodeOutbound = group.getGroupTwincodeOutbound();
        if (group.isOwner() && name != null && mGroupTwincodeOutbound != null) {
            mGroupAvatar = avatar;
            mGroupAvatarFile = avatarFile;
            mOldGroupAvatarId = avatar != null ? group.getAvatarId() : null;
            mUpdateGroupTwincode = mCreateGroupImage || updateGroupName || updateGroupCapabilities || updateGroupDescription;
        } else {
            mGroupAvatarId = null;
            mGroupAvatar = null;
            mGroupAvatarFile = null;
            mOldGroupAvatarId = null;
            mUpdateGroupTwincode = false;

            if (name != null) {
                mGroup.setName(name);
            }
        }

        // Update the user's profile within the group.
        mMemberTwincodeOutbound = group.getMemberTwincodeOutbound();
        if (profileName != null) {
            mProfileName = profileName;
            mProfileAvatar = profileAvatar;
            mProfileAvatarFile = profileAvatarFile;
            mOldProfileAvatarId = profileAvatar != null ? group.getIdentityAvatarId() : null;
        } else {
            mProfileName = null;
            mProfileAvatar = null;
            mProfileAvatarFile = null;
            mProfileAvatarId = null;
            mOldProfileAvatarId = null;
        }

        mGroupCapabilities = groupCapabilities == null ? null : groupCapabilities.toAttributeValue();
        mGroupDescription = groupDescription;
        mGroup.setDescription(groupDescription);
    }

    public UpdateGroupExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Group group,
                               @Nullable String name, @Nullable ImageId identityAvatarId, @Nullable String description,
                               @Nullable Capabilities groupCapabilities, long timeout) {
        super(twinmeContextImpl, requestId, LOG_TAG, timeout);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateGroupExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " group=" + group + " name=" + name + " identityAvatarId=" + identityAvatarId + " description=" + description +
                    " groupCapabilities=" + groupCapabilities);
        }

        mGroup = group;
        mSpace = group.getSpace();
        mOldSpace = mSpace;
        mGroupName = null;
        mCreateGroupImage = false;
        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.PARAMETER, mGroup, 195);

        mGroupTwincodeOutbound = group.getGroupTwincodeOutbound();
        mGroupAvatarId = null;
        mGroupAvatar = null;
        mGroupAvatarFile = null;
        mOldGroupAvatarId = null;
        mCopyImageId = identityAvatarId;

        mCreateMemberImage = identityAvatarId != null;

        // Update the user's profile within the group.
        mMemberTwincodeOutbound = group.getMemberTwincodeOutbound();
        mProfileName = name;
        mProfileAvatar = null;
        mProfileAvatarFile = null;
        mOldProfileAvatarId = identityAvatarId == null ? null : group.getIdentityAvatarId();

        mGroupCapabilities = groupCapabilities == null ? null : groupCapabilities.toAttributeValue();
        mGroupDescription = description;

        mUpdateGroupTwincode = false;
    }

    public UpdateGroupExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Group group, @NonNull Space space) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateGroupExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " group=" + group);
        }

        mGroup = group;
        mSpace = space;
        mOldSpace = group.getSpace();
        mGroup.setSpace(space);
        mGroupName = group.getName();
        mGroupAvatarId = null;
        mGroupAvatar = null;
        mGroupAvatarFile = null;
        mOldGroupAvatarId = null;
        mCreateGroupImage = false;
        mProfileName = null;
        mProfileAvatarId = null;
        mProfileAvatar = null;
        mProfileAvatarFile = null;
        mOldProfileAvatarId = null;
        mCreateMemberImage = false;
        mMemberTwincodeOutbound = null;
        mGroupTwincodeOutbound = null;
        mCopyImageId = null;
        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.PARAMETER, mGroup, 244);

        // We are moving group to another space, we can start immediately.
        mNeedOnline = false;

        mGroupCapabilities = null;
        mGroupDescription = null;
        mUpdateGroupTwincode = false;
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & CREATE_GROUP_IMAGE) != 0 && (mState & CREATE_GROUP_IMAGE_DONE) == 0) {
                mState &= ~CREATE_GROUP_IMAGE;
            }
            if ((mState & CREATE_MEMBER_IMAGE) != 0 && (mState & CREATE_MEMBER_IMAGE_DONE) == 0) {
                mState &= ~CREATE_MEMBER_IMAGE;
            }
            if ((mState & COPY_MEMBER_IMAGE) != 0 && (mState & COPY_MEMBER_IMAGE_DONE) == 0) {
                mState &= ~COPY_MEMBER_IMAGE;
            }
            if ((mState & UPDATE_GROUP_TWINCODE_OUTBOUND) != 0 && (mState & UPDATE_GROUP_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~UPDATE_GROUP_TWINCODE_OUTBOUND;
            }
            if ((mState & UPDATE_MEMBER_TWINCODE_OUTBOUND) != 0 && (mState & UPDATE_MEMBER_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~UPDATE_MEMBER_TWINCODE_OUTBOUND;
            }
            if ((mState & DELETE_OLD_GROUP_IMAGE) != 0 && (mState & DELETE_OLD_GROUP_IMAGE_DONE) == 0) {
                mState &= ~DELETE_OLD_GROUP_IMAGE;
            }
            if ((mState & DELETE_OLD_MEMBER_IMAGE) != 0 && (mState & DELETE_OLD_MEMBER_IMAGE_DONE) == 0) {
                mState &= ~DELETE_OLD_MEMBER_IMAGE;
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
        // Step 1a: a new image must be setup for the group, create it.
        //
        if (mCreateGroupImage && mGroupAvatar != null) {

            if ((mState & CREATE_GROUP_IMAGE) == 0) {
                mState |= CREATE_GROUP_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.createImage: mGroupAvatarFile=" + mGroupAvatarFile);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.createImage(mGroupAvatarFile, mGroupAvatar, this::onCreateGroupImage);
                return;
            }
            if ((mState & CREATE_GROUP_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 1a: a new image must be setup for the private identity, create it.
        //
        if (mCreateMemberImage && mProfileAvatar != null) {

            if ((mState & CREATE_MEMBER_IMAGE) == 0) {
                mState |= CREATE_MEMBER_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.createImage: mProfileAvatar=" + mProfileAvatar);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.createImage(mProfileAvatarFile, mProfileAvatar, this::onCreateProfileImage);
                return;
            }
            if ((mState & CREATE_MEMBER_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 1b: copy the image which is used by the profile to get a copy for our identity within the group.
        //
        if (mCreateMemberImage && mCopyImageId != null) {

            if ((mState & COPY_MEMBER_IMAGE) == 0) {
                mState |= COPY_MEMBER_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.copyImage: mCopyImageId=" + mCopyImageId);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.copyImage(mCopyImageId, this::onCopyImage);
                return;
            }
            if ((mState & COPY_MEMBER_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 1: update the group name and avatar if we are the group owner.
        //

        if (mGroupTwincodeOutbound != null && mUpdateGroupTwincode) {

            if ((mState & UPDATE_GROUP_TWINCODE_OUTBOUND) == 0) {
                mState |= UPDATE_GROUP_TWINCODE_OUTBOUND;

                List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
                List<String> deleteAttributes = new ArrayList<>();
                if (mGroupName != null && !mGroupName.equals(mGroup.getName())) {
                    TwinmeAttributes.setTwincodeAttributeName(attributes, mGroupName);
                }
                if (mGroupAvatarId != null) {
                    TwinmeAttributes.setTwincodeAttributeAvatarId(attributes, mGroupAvatarId);
                }
                if (!Utils.equals(mGroupDescription, mGroup.getDescription())) {
                    if(mGroupDescription != null) {
                        TwinmeAttributes.setTwincodeAttributeDescription(attributes, mGroupDescription);
                    } else {
                        deleteAttributes.add(Twincode.DESCRIPTION);
                    }
                }
                if (mGroupCapabilities != null && !Utils.equals(mGroupCapabilities, mGroup.getCapabilities().toAttributeValue())) {
                    TwinmeAttributes.setCapabilities(attributes, mGroupCapabilities);
                }
                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.updateTwincode: twincodeOutbound=" + mGroupTwincodeOutbound);
                }
                mTwinmeContextImpl.getTwincodeOutboundService().updateTwincode(mGroupTwincodeOutbound, attributes, deleteAttributes,
                        this::onUpdateGroupTwincodeOutbound);
                return;
            }
            if ((mState & UPDATE_GROUP_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2: update the member's profile name and avatar.
        //

        if (mMemberTwincodeOutbound != null && mProfileName != null) {

            if ((mState & UPDATE_MEMBER_TWINCODE_OUTBOUND) == 0) {
                mState |= UPDATE_MEMBER_TWINCODE_OUTBOUND;

                List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
                if (!mProfileName.equals(mGroup.getIdentityName())) {
                    TwinmeAttributes.setTwincodeAttributeName(attributes, mProfileName);
                }
                if (mProfileAvatarId != null) {
                    TwinmeAttributes.setTwincodeAttributeAvatarId(attributes, mProfileAvatarId);
                }
                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.updateTwincode: twincodeOutboundId=" + mMemberTwincodeOutbound);
                }
                mTwinmeContextImpl.getTwincodeOutboundService().updateTwincode(mMemberTwincodeOutbound, attributes, null,
                        this::onUpdateMemberTwincodeOutbound);
                return;
            }
            if ((mState & UPDATE_MEMBER_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3: update the group object in the repository.
        //

        if ((mState & UPDATE_OBJECT) == 0) {
            mState |= UPDATE_OBJECT;

            mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_SUBJECT, mGroup, 438);

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.updateObject: object=" + mGroup);
            }
            mTwinmeContextImpl.getRepositoryService().updateObject(mGroup, this::onUpdateObject);
            return;
        }
        if ((mState & UPDATE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Step 1b: delete the old group image.
        //
        if (mOldGroupAvatarId != null && mCreateGroupImage) {

            if ((mState & DELETE_OLD_GROUP_IMAGE) == 0) {
                mState |= DELETE_OLD_GROUP_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.deleteImage: imageId=" + mOldGroupAvatarId);
                }
                mTwinmeContextImpl.getImageService().deleteImage(mOldGroupAvatarId, this::onDeleteGroupImage);
                return;
            }
            if ((mState & DELETE_OLD_GROUP_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 1b: delete the old identity image.
        //
        if (mOldProfileAvatarId != null && mCreateMemberImage) {

            if ((mState & DELETE_OLD_MEMBER_IMAGE) == 0) {
                mState |= DELETE_OLD_MEMBER_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.deleteImage: imageId=" + mOldProfileAvatarId);
                }
                mTwinmeContextImpl.getImageService().deleteImage(mOldProfileAvatarId, this::onDeleteProfileImage);
                return;
            }
            if ((mState & DELETE_OLD_MEMBER_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Invoke the group members to notify them about the change.
        //
        if (mRefreshMembers != null && mMemberTwincodeOutbound != null && !mRefreshMembers.isEmpty() && mRefreshAttributes != null) {
            if ((mState & INVOKE_TWINCODE_OUTBOUND) == 0) {
                mState |= INVOKE_TWINCODE_OUTBOUND;

                TwincodeOutbound peerTwincodeOutbound = mRefreshMembers.get(0);
                mTwinmeContextImpl.getTwincodeOutboundService().secureInvokeTwincode(mMemberTwincodeOutbound, mMemberTwincodeOutbound,
                        peerTwincodeOutbound, TwincodeOutboundService.INVOKE_WAKEUP, PairProtocol.ACTION_PAIR_REFRESH, mRefreshAttributes, this::onInvokeTwincode);
                return;
            }
            if ((mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mGroup, 509);

        if (mSpace != mOldSpace) {
            mTwinmeContextImpl.onMoveToSpace(mRequestId, mGroup, mOldSpace);
        } else {
            mTwinmeContextImpl.onUpdateGroup(mRequestId, mGroup);
        }

        stop();
    }

    private void onCreateGroupImage(@NonNull ErrorCode errorCode, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateGroupImage: errorCode=" + errorCode + " imageId=" + imageId);
        }

        if (errorCode != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(CREATE_GROUP_IMAGE, errorCode, null);
            return;
        }

        mState |= CREATE_GROUP_IMAGE_DONE;
        mGroupAvatarId = imageId;
        onOperation();
    }

    private void onCreateProfileImage(@NonNull ErrorCode errorCode, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateProfileImage: errorCode=" + errorCode + " imageId=" + imageId);
        }

        if (errorCode != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(CREATE_MEMBER_IMAGE, errorCode, null);
            return;
        }

        mState |= CREATE_MEMBER_IMAGE_DONE;
        mProfileAvatarId = imageId;
        onOperation();
    }

    private void onCopyImage(ErrorCode errorCode, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCopyImage: errorCode=" + errorCode + " imageId=" + imageId);
        }

        if (errorCode != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(COPY_MEMBER_IMAGE, errorCode, null);
            return;
        }

        mState |= COPY_MEMBER_IMAGE_DONE;
        mProfileAvatarId = imageId;
        onOperation();
    }

    private void onUpdateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateObject: object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof Group)) {
            onOperationError(UPDATE_OBJECT, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, object, mGroup);

        mState |= UPDATE_OBJECT_DONE;
        onOperation();
    }

    private void onUpdateGroupTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateGroupTwincodeOutbound: errorCode=" + errorCode + " twincodeOutbound=" + twincodeOutbound);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {
            onOperationError(UPDATE_GROUP_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeOutbound, mGroupTwincodeOutbound);

        mState |= UPDATE_GROUP_TWINCODE_OUTBOUND_DONE;

        mGroup.setGroupTwincodeOutbound(twincodeOutbound);
        if (mGroupName != null) {
            mGroup.setName(mGroupName);
        }

        refreshMembers(twincodeOutbound);
        onOperation();
    }

    private void refreshMembers(@NonNull TwincodeOutbound updatedTwincode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "refreshMembers: updatedTwincode=" + updatedTwincode);
        }

        if (mGroupTwincodeOutbound == null) {
            return;
        }

        final GroupConversation groupConversation = mTwinmeContextImpl.getConversationService().getGroupConversationWithGroupTwincodeId(mGroupTwincodeOutbound.getId());
        if (groupConversation == null) {
            return;
        }

        if (mRefreshMembers == null) {
            mRefreshMembers = new ArrayList<>();
        }
        if (mRefreshAttributes == null) {
            mRefreshAttributes = new ArrayList<>();
        }

        mRefreshAttributes.add(new AttributeNameStringValue(PairProtocol.PARAM_TWINCODE_OUTBOUND_ID, updatedTwincode.getId().toString()));
        final List<GroupMemberConversation> members = groupConversation.getGroupMembers(ConversationService.MemberFilter.JOINED_MEMBERS);
        for (GroupMemberConversation member : members) {
            final TwincodeOutbound peerTwincodeOutbound = member.getPeerTwincodeOutbound();
            if (peerTwincodeOutbound != null && peerTwincodeOutbound.isSigned()) {
                mRefreshMembers.add(peerTwincodeOutbound);
            }
        }
    }

    private void onUpdateMemberTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateMemberTwincodeOutbound: twincodeOutbound=" + twincodeOutbound);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {
            onOperationError(UPDATE_MEMBER_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeOutbound, mMemberTwincodeOutbound);

        mState |= UPDATE_MEMBER_TWINCODE_OUTBOUND_DONE;

        mGroup.setTwincodeOutbound(twincodeOutbound);
        refreshMembers(twincodeOutbound);
        onOperation();
    }

    private void onInvokeTwincode(@NonNull ErrorCode errorCode, @Nullable UUID invocationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvokeTwincode: errorCode=" + errorCode + " invocationId=" + invocationId);
        }

        if (errorCode != ErrorCode.SUCCESS || invocationId == null) {
            onOperationError(INVOKE_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mState &= ~(INVOKE_TWINCODE_OUTBOUND | INVOKE_TWINCODE_OUTBOUND_DONE);
        if (mRefreshMembers != null) {
            mRefreshMembers.remove(0);
        }
        onOperation();
    }

    private void onDeleteGroupImage(@NonNull ErrorCode errorCode, @Nullable ImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteGroupImage: errorCode=" + errorCode + " imageId=" + imageId);
        }

        // Ignore the error and proceed!!!
        mState |= DELETE_OLD_GROUP_IMAGE_DONE;
        onOperation();
    }

    private void onDeleteProfileImage(ErrorCode status, @Nullable ImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteProfileImage: status=" + status + " imageId=" + imageId);
        }

        // Ignore the error and proceed!!!
        mState |= DELETE_OLD_MEMBER_IMAGE_DONE;
        onOperation();
    }

    @Override
    protected void onOperationError(int operationId, BaseService.ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        if (errorCode == ErrorCode.ITEM_NOT_FOUND && operationId == INVOKE_TWINCODE_OUTBOUND) {
            if (mRefreshMembers != null && !mRefreshMembers.isEmpty()) {
                mRefreshMembers.remove(0);
            }
            mState &= ~(INVOKE_TWINCODE_OUTBOUND | INVOKE_TWINCODE_OUTBOUND_DONE);
            onOperation();
            return;
        }

        super.onOperationError(operationId, errorCode, errorParameter);
    }
}
