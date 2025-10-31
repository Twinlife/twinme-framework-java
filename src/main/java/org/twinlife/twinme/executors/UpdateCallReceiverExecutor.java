/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.executors;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.CallReceiver;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.2
//

public class UpdateCallReceiverExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "UpdateCallReceiverExec";
    private static final boolean DEBUG = false;

    private static final int CREATE_IMAGE = 1;
    private static final int CREATE_IMAGE_DONE = 1 << 1;
    private static final int UPDATE_TWINCODE_OUTBOUND = 1 << 2;
    private static final int UPDATE_TWINCODE_OUTBOUND_DONE = 1 << 3;
    private static final int UPDATE_OBJECT = 1 << 4;
    private static final int UPDATE_OBJECT_DONE = 1 << 5;
    private static final int DELETE_OLD_IMAGE = 1 << 6;
    private static final int DELETE_OLD_IMAGE_DONE = 1 << 7;

    @NonNull
    private final CallReceiver mCallReceiver;
    @NonNull
    private final String mName;

    @Nullable
    private final String mDescription;
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
    private final boolean mUpdateTwincode;
    private final boolean mUpdateObject;
    private final TwincodeOutbound mTwincodeOutbound;
    private final boolean mCreateImage;

    @Nullable
    private final String mCapabilities;

    public UpdateCallReceiverExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull CallReceiver callReceiver,
                                      @NonNull String name, @Nullable String description, @NonNull String identityName, @Nullable String identityDescription, @Nullable Bitmap avatar, @Nullable File avatarFile,
                                      @Nullable Capabilities capabilities) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateCallReceiverExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " callReceiver=" + callReceiver + " name=" + name + "identityName=" + identityName + " avatar=" + avatar + " description=" + description + " capabilities=" + capabilities);
        }

        mCallReceiver = callReceiver;
        mName = name;
        mDescription = description;
        mIdentityName = identityName;
        mIdentityDescription = identityDescription;
        mAvatar = avatar;
        mAvatarFile = avatarFile;
        mOldAvatarId = callReceiver.getAvatarId();
        mCapabilities = capabilities == null ? null : capabilities.toAttributeValue();
        mCreateImage = avatarFile != null;

        // Check if one of the twincode attributes is modified.
        boolean updateIdentityDescription = !Utils.equals(mIdentityDescription, mCallReceiver.getIdentityDescription());
        boolean updateCapabilities = !Utils.equals(mCapabilities, mCallReceiver.getCapabilities().toAttributeValue());
        boolean updateIdentityName = !Utils.equals(mIdentityName, mCallReceiver.getIdentityName());

        mUpdateTwincode = updateIdentityName || mCreateImage || updateIdentityDescription || updateCapabilities;

        mUpdateObject = !Utils.equals(mName, mCallReceiver.getName()) || !Utils.equals(mDescription, mCallReceiver.getDescription());

        mTwincodeOutbound = mCallReceiver.getTwincodeOutbound();
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
            if ((mState & UPDATE_TWINCODE_OUTBOUND) != 0 && (mState & UPDATE_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~UPDATE_TWINCODE_OUTBOUND;
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
        // Step 2: update the twincode outbound if needed.
        //
        if (mUpdateTwincode && mTwincodeOutbound != null) {

            if ((mState & UPDATE_TWINCODE_OUTBOUND) == 0) {
                mState |= UPDATE_TWINCODE_OUTBOUND;

                List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
                if (!mIdentityName.equals(mCallReceiver.getIdentityName())) {
                    TwinmeAttributes.setTwincodeAttributeName(attributes, mIdentityName);
                }
                if (mAvatarId != null) {
                    TwinmeAttributes.setTwincodeAttributeAvatarId(attributes, mAvatarId);
                }
                if (mIdentityDescription != null) {
                    TwinmeAttributes.setTwincodeAttributeDescription(attributes, mIdentityDescription);
                }
                if (mCapabilities != null) {
                    TwinmeAttributes.setCapabilities(attributes, mCapabilities);
                }

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.updateTwincode: twincodeOutbound=" + mTwincodeOutbound);
                }
                mTwinmeContextImpl.getTwincodeOutboundService().updateTwincode(mTwincodeOutbound, attributes, null,
                        this::onUpdateTwincodeOutbound);
                return;
            }
            if ((mState & UPDATE_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3: update object if the name changed
        //
        if (mUpdateObject) {
            if ((mState & UPDATE_OBJECT) == 0) {
                mState |= UPDATE_OBJECT;

                mCallReceiver.setName(mName);
                mCallReceiver.setDescription(mDescription);

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

    private void onUpdateTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateTwincodeOutbound: errorCode=" + errorCode + " twincodeOutbound=" + twincodeOutbound);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {
            onOperationError(UPDATE_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeOutbound, mTwincodeOutbound);

        mState |= UPDATE_TWINCODE_OUTBOUND_DONE;

        mCallReceiver.setTwincodeOutbound(twincodeOutbound);
        onOperation();
    }

    private void onDeleteImage(@NonNull ErrorCode errorCode, @Nullable ImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteImage: errorCode=" + errorCode + " imageId=" + imageId);
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
