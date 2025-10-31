/*
 *  Copyright (c) 2015-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.ProfileFactory;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.16
//
// User foreground operation: must be connected with a timeout if connection does not work.

public class CreateProfileExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "CreateProfileExecutor";
    private static final boolean DEBUG = false;

    private static final int CREATE_IMAGE = 1;
    private static final int CREATE_IMAGE_DONE = 1 << 1;
    private static final int CREATE_TWINCODE = 1 << 2;
    private static final int CREATE_TWINCODE_DONE = 1 << 3;
    private static final int CREATE_OBJECT = 1 << 6;
    private static final int CREATE_OBJECT_DONE = 1 << 7;
    private static final int UPDATE_SPACE = 1 << 8;
    private static final int UPDATE_SPACE_DONE = 1 << 9;

    @NonNull
    private final String mName;
    @Nullable
    private final Bitmap mAvatar;
    @Nullable
    private final File mAvatarFile;
    @Nullable
    private ExportedImageId mAvatarId;
    @Nullable
    private final Space mSpace;
    @Nullable
    private final String mDescription;
    @Nullable
    private final String mCapabilities;
    private TwincodeFactory mTwincodeFactory;
    private Profile mProfile;

    public CreateProfileExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull String name,
                                 @Nullable Bitmap avatar, @Nullable File avatarFile, @Nullable String description,
                                 @Nullable Capabilities capabilities, long timeout) {
        super(twinmeContextImpl, requestId, LOG_TAG, timeout);
        if (DEBUG) {
            Log.d(LOG_TAG, "CreateProfileExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId
                    + " name=" + name + " avatarUri=" + avatarFile);
        }

        mName = name;
        mAvatar = avatar;
        mAvatarFile = avatarFile;
        mDescription = description;
        mCapabilities = capabilities == null ? null : capabilities.toAttributeValue();
        mSpace = null;

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.CREATE_PROFILE_NAME, mName);
    }

    public CreateProfileExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull String name,
                                 @Nullable Bitmap avatar, @Nullable File avatarFile,
                                 @Nullable String description, @Nullable Capabilities capabilities, @NonNull Space space) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "CreateProfileExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId
                    + " name=" + name + " avatarUri=" + avatarFile + " space=" + space);
        }

        mName = name;
        mAvatar = avatar;
        mAvatarFile = avatarFile;
        mDescription = description;
        mCapabilities = capabilities == null ? null : capabilities.toAttributeValue();
        mSpace = space;

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.CREATE_PROFILE_NAME, mName);
    }

    @Override
    public void onUpdateSpace(long requestId, @NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateSpace: requestId=" + requestId + " space=" + space);
        }

        if (getOperation(requestId) > 0) {
            CreateProfileExecutor.this.onUpdateSpace(space);
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
            if ((mState & CREATE_TWINCODE) != 0 && (mState & CREATE_TWINCODE_DONE) == 0) {
                mState &= ~CREATE_TWINCODE;
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
        // Step 1a: create the image id.
        //
        if (mAvatar != null) {

            if ((mState & CREATE_IMAGE) == 0) {
                mState |= CREATE_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.createImage: " + mAvatarFile);
                }

                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.createImage(mAvatarFile, mAvatar, this::onCreateImage);
                return;
            }
            if ((mState & CREATE_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 1b: create the public profile twincode.
        //

        if ((mState & CREATE_TWINCODE) == 0) {
            mState |= CREATE_TWINCODE;

            List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
            PairProtocol.setTwincodeAttributeMetaPair(twincodeFactoryAttributes);

            List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
            TwinmeAttributes.setTwincodeAttributeName(twincodeOutboundAttributes, mName);
            if (mAvatarId != null) {
                TwinmeAttributes.setTwincodeAttributeAvatarId(twincodeOutboundAttributes, mAvatarId);
            }
            if (mDescription != null && !mDescription.isEmpty()) {
                TwinmeAttributes.setTwincodeAttributeDescription(twincodeOutboundAttributes, mDescription);
            }
            if (mCapabilities != null && !mCapabilities.isEmpty()) {
                TwinmeAttributes.setCapabilities(twincodeOutboundAttributes, mCapabilities);
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: twincodeFactoryAttributes=" + twincodeFactoryAttributes
                        + " twincodeOutboundAttributes=" + twincodeOutboundAttributes);
            }
            mTwinmeContextImpl.getTwincodeFactoryService().createTwincode(twincodeFactoryAttributes, null,
                    twincodeOutboundAttributes, null, Profile.SCHEMA_ID, this::onCreateTwincodeFactory);
            return;
        }
        if ((mState & CREATE_TWINCODE_DONE) == 0) {
            return;
        }

        //
        // Step 2: create the profile.
        //

        if ((mState & CREATE_OBJECT) == 0) {
            mState |= CREATE_OBJECT;

            mTwinmeContextImpl.getRepositoryService().createObject(ProfileFactory.INSTANCE,
                    RepositoryService.AccessRights.PRIVATE, (RepositoryObject object) -> {
                        Profile profile = (Profile) object;
                        profile.setTwincodeFactory(mTwincodeFactory);
                        if (mSpace != null) {
                            profile.setSpace(mSpace);
                        }
                    }, this::onCreateObject);
            return;
        }
        if ((mState & CREATE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Step 4: associate the profile with the space.
        //

        if (mSpace != null) {

            if ((mState & UPDATE_SPACE) == 0) {
                mState |= UPDATE_SPACE;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.CREATE_PROFILE, mProfile);

                long requestId = newOperation(UPDATE_SPACE);
                if (DEBUG) {
                    Log.d(LOG_TAG, "TwinmeContext.updateSpace: requestId=" + requestId + " space=" + mSpace
                            + " profile=" + mProfile);
                }
                mTwinmeContextImpl.updateSpace(requestId, mSpace, mProfile);
                return;
            }
            if ((mState & UPDATE_SPACE_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.CREATE_PROFILE, mProfile);

        mTwinmeContextImpl.onCreateProfile(mRequestId, mProfile);

        stop();
    }

    private void onCreateImage(@NonNull ErrorCode status, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateImage status=" + status + " imageId=" + imageId);
        }

        if (status != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(CREATE_IMAGE, status, null);
            return;
        }

        mState |= CREATE_IMAGE_DONE;
        mAvatarId = imageId;
        onOperation();
    }

    private void onCreateTwincodeFactory(@NonNull ErrorCode errorCode, @Nullable TwincodeFactory twincodeFactory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateTwincodeFactory: twincodeFactory=" + twincodeFactory);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeFactory == null) {

            onOperationError(CREATE_TWINCODE, errorCode, null);
            return;
        }

        mState |= CREATE_TWINCODE_DONE;

        mTwincodeFactory = twincodeFactory;
        onOperation();
    }

    private void onCreateObject(BaseService.ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateObject: errorCode=" + errorCode + " object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof Profile)) {
            onOperationError(CREATE_OBJECT, errorCode, null);
            return;
        }

        mState |= CREATE_OBJECT_DONE;
        mProfile = (Profile) object;
        onOperation();
    }

    private void onUpdateSpace(@NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateSpace: space=" + space);
        }

        mState |= UPDATE_SPACE_DONE;
        onOperation();
    }
}
