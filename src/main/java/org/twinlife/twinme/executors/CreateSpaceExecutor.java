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

import android.graphics.Bitmap;
import android.util.Log;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.models.SpaceFactory;
import org.twinlife.twinme.models.SpaceSettings;
import org.twinlife.twinme.models.SpaceSettingsFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.6
//

/**
 * Executor to create a new Space either local to use user or imported from a SpaceCard:
 *
 * - get the space twincode which gives us the name/avatar/permissions (optional),
 *   if this fails, the space is not created and onError is called with ITEM_NOT_FOUND
 * - create a profile associated with the space,
 * - create the space settings object that holds user's local configuration (saved locally in the repository),
 * - create the space object,
 * - get the invitations defined by the SpaceCard (optional),
 *   if this fails, failed invitations are ignored and silently dropped,
 * - create the groups defined by the SpaceCard (optional).
 *   if this fails, failed groups are ignored and silently dropped
 */
public class CreateSpaceExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "CreateSpaceExecutor";
    private static final boolean DEBUG = false;

    private static final int GET_SPACE_TWINCODE = 1;
    private static final int GET_SPACE_TWINCODE_DONE = 1 << 1;
    private static final int CREATE_PROFILE = 1 << 2;
    private static final int CREATE_PROFILE_DONE = 1 << 3;
    private static final int CREATE_SPACE_IMAGE = 1 << 4;
    private static final int CREATE_SPACE_IMAGE_DONE = 1 << 5;
    private static final int CREATE_SETTINGS_OBJECT = 1 << 6;
    private static final int CREATE_SETTINGS_OBJECT_DONE = 1 << 7;
    private static final int CREATE_OBJECT = 1 << 8;
    private static final int CREATE_OBJECT_DONE = 1 << 9;
    private static final int GET_INVITATION_TWINCODE = 1 << 10;
    private static final int GET_INVITATION_TWINCODE_DONE = 1 << 11;
    private static final int CREATE_GROUP = 1 << 12;
    private static final int CREATE_GROUP_DONE = 1 << 13;

    @NonNull
    private final String mName;
    @Nullable
    private final String mIdentityName;
    @Nullable
    private final Bitmap mIdentityAvatar;
    @Nullable
    private final File mIdentityAvatarFile;
    @Nullable
    private Profile mProfile;
    @Nullable
    private final Bitmap mSpaceAvatar;
    @Nullable
    private final File mSpaceAvatarFile;
    @Nullable
    private ExportedImageId mSpaceAvatarId;
    private final boolean mIsDefault;
    private final List<UUID> mInvitations;
    private final UUID mSpaceTwincodeId;
    private Space mSpace;
    private SpaceSettings mSpaceSettings;
    private UUID mInvitationTwincodeOutboundId;
    private TwincodeOutbound mSpaceTwincodeOutbound;
    private TwincodeOutbound mInvitationTwincodeOutbound;

    public CreateSpaceExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull SpaceSettings settings,
                               @Nullable Bitmap spaceAvatar, @Nullable File spaceAvatarFile,
                               @Nullable String name, @Nullable Bitmap avatar, @Nullable File avatarFile, boolean isDefault) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "CreateSpaceExecutor: twinmeContextImpl=" + twinmeContextImpl
                    + " requestId=" + requestId + " settings=" + settings + " avatarFile=" + avatarFile);
        }

        mName = settings.getName();
        mIdentityName = name;
        mIdentityAvatar = avatar;
        mIdentityAvatarFile = avatarFile;
        mIsDefault = isDefault;
        mInvitations = new ArrayList<>();
        mSpaceSettings = settings;
        mSpaceTwincodeId = null;
        mSpaceAvatar = spaceAvatar;
        mSpaceAvatarFile = spaceAvatarFile;
    }

    public CreateSpaceExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull SpaceSettings settings,
                               @Nullable Bitmap spaceAvatar, @Nullable File spaceAvatarFile,
                               @Nullable Profile profile, boolean isDefault) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "CreateSpaceExecutor: twinmeContextImpl=" + twinmeContextImpl
                    + " requestId=" + requestId + " settings=" + settings);
        }

        mName = settings.getName();
        mProfile = profile;
        mIdentityAvatar = null;
        mIdentityAvatarFile = null;
        if (profile != null) {
            mIdentityName = profile.getName();
        } else {
            mIdentityName = null;
        }
        mIsDefault = isDefault;
        mInvitations = new ArrayList<>();
        mSpaceTwincodeId = null;
        mSpaceSettings = settings;
        mSpaceAvatar = spaceAvatar;
        mSpaceAvatarFile = spaceAvatarFile;
    }

    @Override
    public void onCreateGroup(long requestId, @NonNull Group group, @NonNull ConversationService.GroupConversation conversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateGroup: requestId=" + requestId + " group=" + group);
        }

        int operationId = getOperation(requestId);
        if (operationId > 0) {
            onCreateGroup(group);
        }
    }

    @Override
    public void onCreateProfile(long requestId, @NonNull Profile profile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwinmeContextObserver.onCreateProfile: requestId=" + requestId + " profile=" + profile);
        }

        int operationId = getOperation(requestId);
        if (operationId > 0) {
            onCreateProfile(profile);
        }
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & GET_SPACE_TWINCODE) != 0 && (mState & GET_SPACE_TWINCODE_DONE) == 0) {
                mState &= ~GET_SPACE_TWINCODE;
            }

            if ((mState & CREATE_SPACE_IMAGE) != 0 && (mState & CREATE_SPACE_IMAGE_DONE) == 0) {
                mState &= ~CREATE_SPACE_IMAGE;
            }

            // Restart the get invitation twincode only when there is a pending invitation to get.
            // Don't restart the group creation.
            if (mInvitationTwincodeOutboundId != null && (mState & GET_INVITATION_TWINCODE) != 0 && (mState & GET_INVITATION_TWINCODE_DONE) == 0) {
                mState &= ~GET_INVITATION_TWINCODE;
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
        // Step 1: get the space twincode.
        //
        if (mSpaceTwincodeId != null) {

            if ((mState & GET_SPACE_TWINCODE) == 0) {
                mState |= GET_SPACE_TWINCODE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.getTwincode: twincodeId=" + mSpaceTwincodeId);
                }
                mTwinmeContextImpl.getTwincodeOutboundService().getTwincode(mSpaceTwincodeId, TwincodeOutboundService.REFRESH_PERIOD,
                        this::onGetSpaceTwincodeOutbound);
                return;
            }
            if ((mState & GET_SPACE_TWINCODE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2: create the Profile object.
        //
        if (mProfile == null && mIdentityName != null && mIdentityAvatar != null) {

            if ((mState & CREATE_PROFILE) == 0) {
                mState |= CREATE_PROFILE;

                long requestId = newOperation(CREATE_PROFILE);

                if (DEBUG) {
                    Log.d(LOG_TAG, "RepositoryService.createProfile: requestId=" + requestId + " name=" + mIdentityName);
                }
                new CreateProfileExecutor(mTwinmeContextImpl, requestId, mIdentityName, mIdentityAvatar,
                        mIdentityAvatarFile, null, null, 0).start();
                return;
            }
            if ((mState & CREATE_PROFILE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3a: create the space image id for the settings.
        //
        if (mSpaceAvatar != null) {

            if ((mState & CREATE_SPACE_IMAGE) == 0) {
                mState |= CREATE_SPACE_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.createImage: " + mSpaceAvatar);
                }

                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.createLocalImage(mSpaceAvatarFile, mSpaceAvatar, this::onCreateImage);
                return;
            }
            if ((mState & CREATE_SPACE_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3b: allocate a UUID for the space settings and create its instance locally in the repository.
        //

        if ((mState & CREATE_SETTINGS_OBJECT) == 0) {
            mState |= CREATE_SETTINGS_OBJECT;

            mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mSpaceSettings, 285);

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.createObject: spaceSettings=" + mSpaceSettings);
            }
            mTwinmeContextImpl.getRepositoryService().createObject(SpaceSettingsFactory.INSTANCE,
                    RepositoryService.AccessRights.PRIVATE, (RepositoryObject object) -> {
                        SpaceSettings spaceSettings = (SpaceSettings) object;
                        spaceSettings.copy(mSpaceSettings);
                        if (mSpaceAvatarId != null) {
                            spaceSettings.setAvatarId(mSpaceAvatarId.getExportedId());
                        }
                    }, this::onCreateSettingsObject);
            return;
        }
        if ((mState & CREATE_SETTINGS_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Step 4: create the Space object.
        //

        if ((mState & CREATE_OBJECT) == 0) {
            mState |= CREATE_OBJECT;

            mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mSpaceSettings, 311);

            mTwinmeContextImpl.getRepositoryService().createObject(SpaceFactory.INSTANCE,
                    RepositoryService.AccessRights.PRIVATE, (RepositoryObject object) -> {
                        Space space = (Space) object;
                        space.setProfile(mProfile);
                        space.setSpaceSettings(mSpaceSettings);
                        space.setTwincodeOutbound(mSpaceTwincodeOutbound);
                    }, this::onCreateSpace);
            return;
        }
        if ((mState & CREATE_OBJECT_DONE) == 0) {
            return;
        }

        if (!mInvitations.isEmpty()) {

            //
            // Step 5: get a pending invitation twincode.
            //

            if ((mState & GET_INVITATION_TWINCODE) == 0) {
                mState |= GET_INVITATION_TWINCODE;

                mInvitationTwincodeOutboundId = mInvitations.get(0);
                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mInvitationTwincodeOutboundId, 336);

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.getTwincode: twincodeId=" + mInvitationTwincodeOutboundId);
                }
                mTwinmeContextImpl.getTwincodeOutboundService().getTwincode(mInvitationTwincodeOutboundId, TwincodeOutboundService.REFRESH_PERIOD,
                        this::onGetInvitationTwincodeOutbound);
                return;
            }
            if ((mState & GET_INVITATION_TWINCODE_DONE) == 0) {
                return;
            }

            //
            // Step 6: create the group with the pending invitation twincode.
            //

            if ((mState & CREATE_GROUP) == 0) {
                mState |= CREATE_GROUP;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mInvitationTwincodeOutboundId, 356);

                long requestId = newOperation(CREATE_GROUP);
                if (DEBUG) {
                    Log.d(LOG_TAG, "TwinmeContext.createGroup: requestId=" + requestId
                            + " twincode=" + mInvitationTwincodeOutboundId);
                }
                new CreateGroupExecutor(mTwinmeContextImpl, requestId, mSpace, mInvitationTwincodeOutbound, 0).start();
                return;
            }
            if ((mState & CREATE_GROUP_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mSpace, 375);

        if (mIsDefault) {
            mTwinmeContextImpl.setDefaultSpace(mSpace);
        }
        mTwinmeContextImpl.onCreateSpace(mRequestId, mSpace);

        stop();
    }

    private void onCreateProfile(@NonNull Profile profile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateProfile: profile=" + profile);
        }

        mState |= CREATE_PROFILE_DONE;

        mProfile = profile;
        onOperation();
    }

    private void onCreateImage(@NonNull ErrorCode status, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateImage status=" + status + " imageId=" + imageId);
        }

        if (status != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(CREATE_SPACE_IMAGE, status, null);
            return;
        }

        mState |= CREATE_SPACE_IMAGE_DONE;
        mSpaceAvatarId = imageId;

        // Delete the image file when the creation succeeded.
        if (mSpaceAvatarFile != null) {
            Utils.deleteFile("image", mSpaceAvatarFile);
        }
        onOperation();
    }

    private void onCreateSettingsObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateSettingsObject: errorCode=" + errorCode);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof SpaceSettings)) {
            onOperationError(CREATE_SETTINGS_OBJECT, errorCode, null);
            return;
        }

        mState |= CREATE_SETTINGS_OBJECT_DONE;

        mSpaceSettings = (SpaceSettings) object;
        onOperation();
    }

    private void onCreateSpace(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateSpace: errorCode=" + errorCode + " object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof Space)) {
            onOperationError(CREATE_OBJECT, errorCode, null);
            return;
        }

        mState |= CREATE_OBJECT_DONE;
        mSpace = (Space) object;

        if (mProfile != null) {
            mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.CREATE_SPACE, mSpace.getProfileId(), mProfile.getId());

            mSpace.setProfile(mProfile);
        }
        onOperation();
    }

    private void onGetSpaceTwincodeOutbound(@NonNull ErrorCode status, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetSpaceTwincodeOutbound: twincodeOutbound=" + twincodeOutbound);
        }

        if (status != ErrorCode.SUCCESS || twincodeOutbound == null) {

            onOperationError(GET_SPACE_TWINCODE, status, mSpaceTwincodeId.toString());
            return;
        }

        mState |= GET_SPACE_TWINCODE_DONE;

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeOutbound.getId(), mSpaceTwincodeId);

        mSpaceTwincodeOutbound = twincodeOutbound;
        onOperation();
    }

    private void onGetInvitationTwincodeOutbound(@NonNull ErrorCode status, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetInvitationTwincodeOutbound: twincodeOutbound=" + twincodeOutbound);
        }

        if (status != ErrorCode.SUCCESS || twincodeOutbound == null) {

            onOperationError(GET_INVITATION_TWINCODE, status, mInvitationTwincodeOutboundId.toString());
            return;
        }

        mState |= GET_INVITATION_TWINCODE_DONE;

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeOutbound.getId(), mInvitationTwincodeOutboundId);

        mInvitationTwincodeOutboundId = null;
        mInvitationTwincodeOutbound = twincodeOutbound;
        onOperation();
    }

    private void onCreateGroup(@NonNull Group group) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateGroup: group=" + group);
        }

        mState |= CREATE_GROUP_DONE;

        mInvitationTwincodeOutbound = null;
        mInvitations.remove(0);
        if (!mInvitations.isEmpty()) {
            mState &= ~(GET_INVITATION_TWINCODE | GET_INVITATION_TWINCODE_DONE | CREATE_GROUP | CREATE_GROUP_DONE);
        }
        onOperation();
    }

    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        if (operationId == GET_INVITATION_TWINCODE && errorCode == ErrorCode.ITEM_NOT_FOUND) {
            mInvitations.remove(0);
            mState &= ~GET_INVITATION_TWINCODE;
            mInvitationTwincodeOutboundId = null;
            onOperation();
            return;
        }
        if (operationId == CREATE_GROUP && errorCode == ErrorCode.ITEM_NOT_FOUND) {
            mInvitations.remove(0);
            mState &= ~(CREATE_GROUP | GET_INVITATION_TWINCODE | GET_INVITATION_TWINCODE_DONE);
            mInvitationTwincodeOutbound = null;
            onOperation();
            return;
        }

        super.onOperationError(operationId, errorCode, errorParameter);
    }
}
