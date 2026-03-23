/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.CallReceiver;

import java.io.File;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.1
//

public class UpdateOrganizerExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "UpdateOrganizerExec";
    private static final boolean DEBUG = false;

    private static final int CREATE_IMAGE = 1;
    private static final int CREATE_IMAGE_DONE = 1 << 1;
    private static final int UPDATE_OBJECT = 1 << 4;
    private static final int UPDATE_OBJECT_DONE = 1 << 5;
    private static final int DELETE_OLD_IMAGE = 1 << 6;
    private static final int DELETE_OLD_IMAGE_DONE = 1 << 7;

    @NonNull
    private final CallReceiver mCallReceiver;
    @NonNull
    private final String mIdentityName;

    @Nullable
    private final String mIdentityDescription;
    @Nullable
    private final Bitmap mAvatar;
    @Nullable
    private final File mAvatarFile;
    private ExportedImageId mAvatarId;
    @Nullable
    private final ImageId mOldAvatarId;
    private final boolean mUpdateObject;
    private final boolean mCreateImage;

    public UpdateOrganizerExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull CallReceiver callReceiver,
                                   @NonNull String identityName, @Nullable String identityDescription, @Nullable Bitmap avatar, @Nullable File avatarFile) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateOrganizerExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " callReceiver=" + callReceiver + " identityName=" + identityName + "identityName=" + identityName + " avatar=" + avatar
                    + " identityDescription=" + identityDescription);
        }

        // Important note :
        // the term `identityName` and `identityDescription` refers to the user's identity for a relation.
        // For a Profile, Contact, Group, it is stored in the twincode associated with the object.
        // BUT, for a click-to-call, this identity must be saved elsewhere and only in the database.
        // Such identity is saved in the database object and we use the term `name` and `description` for that.
        mCallReceiver = callReceiver;
        mIdentityName = identityName;
        mIdentityDescription = identityDescription;
        mAvatar = avatar;
        mAvatarFile = avatarFile;
        mOldAvatarId = callReceiver.getIdentityAvatarId();
        mCreateImage = avatarFile != null;

        mUpdateObject = !Utils.equals(mIdentityName, mCallReceiver.getIdentityName())
                || !Utils.equals(mIdentityDescription, mCallReceiver.getIdentityDescription())
                || mCreateImage;
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
            if ((mState & DELETE_OLD_IMAGE) != 0 && (mState & DELETE_OLD_IMAGE_DONE) == 0) {
                mState &= ~DELETE_OLD_IMAGE;
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
        // Step 1: a new image must be setup, create it.
        //
        if (mCreateImage && mAvatar != null) {

            if ((mState & CREATE_IMAGE) == 0) {
                mState |= CREATE_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.createImage: mAvatarFile=" + mAvatarFile);
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
        // Step 3: update object if the name changed
        //
        if (mUpdateObject) {
            if ((mState & UPDATE_OBJECT) == 0) {
                mState |= UPDATE_OBJECT;

                mCallReceiver.setOrganizerName(mIdentityName);
                mCallReceiver.setOrganizerDescription(mIdentityDescription);
                if (mCreateImage) {
                    mCallReceiver.setOrganizerAvatarId(mAvatarId);
                }

                mTwinmeContextImpl.getRepositoryService().updateObject(mCallReceiver, this::onUpdateObject);
                return;
            }

            if ((mState & UPDATE_OBJECT_DONE) == 0) {
                return;
            }
        }

        //
        // Step 4: delete the old avatar image.
        //
        if (mOldAvatarId != null && mCreateImage) {

            if ((mState & DELETE_OLD_IMAGE) == 0) {
                mState |= DELETE_OLD_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.deleteImage: imageId=" + mOldAvatarId);
                }
                mTwinmeContextImpl.getImageService().deleteImage(mOldAvatarId, this::onDeleteImage);
                return;
            }
            if ((mState & DELETE_OLD_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mCallReceiver, 245);

        mTwinmeContextImpl.onUpdateCallReceiver(mRequestId, mCallReceiver);

        stop();
    }

    private void onCreateImage(@NonNull ErrorCode errorCode, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateImage: errorCode=" + errorCode + " imageId=" + imageId);
        }

        if (errorCode != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(CREATE_IMAGE, errorCode, imageId != null ? imageId.toString() : null);
            return;
        }

        mState |= CREATE_IMAGE_DONE;
        mAvatarId = imageId;
        onOperation();
    }

    private void onDeleteImage(@NonNull ErrorCode errorCode, @Nullable ImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteImage: errorCode=" + errorCode + " imageId=" + imageId);
        }

        if (errorCode != ErrorCode.SUCCESS && errorCode != ErrorCode.ITEM_NOT_FOUND) {
            onOperationError(DELETE_OLD_IMAGE, errorCode, null);
            return;
        }

        // Ignore the error and proceed!!!
        mState |= DELETE_OLD_IMAGE_DONE;
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

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, object, mCallReceiver);

        mState |= UPDATE_OBJECT_DONE;
        onOperation();
    }
}
