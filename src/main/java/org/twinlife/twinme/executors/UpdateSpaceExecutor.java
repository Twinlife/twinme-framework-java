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
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.models.SpaceSettings;
import org.twinlife.twinme.models.SpaceSettingsFactory;

import java.io.File;
import java.util.UUID;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.2
//

public class UpdateSpaceExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "UpdateSpaceExecutor";
    private static final boolean DEBUG = false;

    private static final int CREATE_SPACE_IMAGE = 1;
    private static final int CREATE_SPACE_IMAGE_DONE = 1 << 1;
    private static final int CREATE_SETTINGS_OBJECT = 1 << 2;
    private static final int CREATE_SETTINGS_OBJECT_DONE = 1 << 3;
    private static final int UPDATE_SETTINGS_OBJECT = 1 << 4;
    private static final int UPDATE_SETTINGS_OBJECT_DONE = 1 << 5;
    private static final int UPDATE_SPACE = 1 << 6;
    private static final int UPDATE_SPACE_DONE = 1 << 7;
    private static final int DELETE_SPACE_IMAGE = 1 << 8;
    private static final int DELETE_SPACE_IMAGE_DONE = 1 << 9;

    @NonNull
    private final Space mSpace;
    @NonNull
    private final SpaceSettings mCurrentSettings;
    @Nullable
    private SpaceSettings mSpaceSettings;
    @Nullable
    private final Profile mProfile;
    @Nullable
    private final Bitmap mSpaceAvatar;
    @Nullable
    private final File mSpaceAvatarFile;
    @Nullable
    private ExportedImageId mSpaceAvatarId;
    @Nullable
    private UUID mOldSpaceAvatarId;
    private final boolean mCreateSettings;
    private final boolean mUpdateSpace;

    public UpdateSpaceExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Space space,
                               @Nullable SpaceSettings spaceSettings, @Nullable Bitmap spaceAvatar, @Nullable File spaceAvatarFile,
                               @Nullable Profile profile) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateSpaceExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " space=" + space);
        }

        mSpace = space;
        mSpaceAvatar = spaceAvatar;
        mSpaceAvatarFile = spaceAvatarFile;

        // If the spaceSettings parameter has a different ID, build a new instance with our space settings ID.
        mCurrentSettings = space.getSpaceSettings();
        if (spaceSettings != null && mCurrentSettings.getId() != null && !mCurrentSettings.getId().equals(spaceSettings.getId())) {
            spaceSettings = new SpaceSettings(mCurrentSettings, spaceSettings);
        }
        mCreateSettings = spaceSettings != null && spaceSettings.getId() == null;
        mSpaceSettings = spaceSettings;
        mProfile = profile;
        mUpdateSpace = mCreateSettings || profile != null;

        // The space settings and image are local only, we can start immediately.
        mNeedOnline = true;
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;

            if ((mState & CREATE_SPACE_IMAGE) != 0 && (mState & CREATE_SPACE_IMAGE_DONE) == 0) {
                mState &= ~CREATE_SPACE_IMAGE;
            }
            if ((mState & DELETE_SPACE_IMAGE) != 0 && (mState & DELETE_SPACE_IMAGE_DONE) == 0) {
                mState &= ~DELETE_SPACE_IMAGE;
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
        // Step 1: create the space image id for the settings.
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
        // Step 2: update the space object in the repository.
        //

        if (mSpaceSettings != null) {
            if (mCreateSettings) {

                if ((mState & CREATE_SETTINGS_OBJECT) == 0) {
                    mState |= CREATE_SETTINGS_OBJECT;

                    mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mSpaceSettings, 168);

                    if (DEBUG) {
                        Log.d(LOG_TAG, "RepositoryService.createObject: spaceSettings=" + mSpaceSettings);
                    }
                    mTwinmeContextImpl.getRepositoryService().createObject(SpaceSettingsFactory.INSTANCE,
                            RepositoryService.AccessRights.PRIVATE, (RepositoryObject object) -> {
                                SpaceSettings spaceSettings = (SpaceSettings) object;
                                if (mSpaceAvatarId != null) {
                                    spaceSettings.setAvatarId(mSpaceAvatarId.getExportedId());
                                }
                                spaceSettings.copy(mSpaceSettings);
                            }, this::onCreateObject);
                    return;
                }
                if ((mState & CREATE_SETTINGS_OBJECT_DONE) == 0) {
                    return;
                }

            } else {

                if ((mState & UPDATE_SETTINGS_OBJECT) == 0) {
                    mState |= UPDATE_SETTINGS_OBJECT;

                    mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mSpace, 192);

                    if (mSpaceAvatarId != null) {
                        mOldSpaceAvatarId = mSpaceSettings.getAvatarId();
                        mSpaceSettings.setAvatarId(mSpaceAvatarId.getExportedId());
                    }

                    if (DEBUG) {
                        Log.d(LOG_TAG, "RepositoryService.updateObject: object=" + mSpaceSettings);
                    }
                    mTwinmeContextImpl.getRepositoryService().updateObject(mSpaceSettings, this::onUpdateSettingsObject);
                    return;
                }
                if ((mState & UPDATE_SETTINGS_OBJECT_DONE) == 0) {
                    return;
                }
            }
        }

        //
        // Step 3: update the space object in the repository.
        //
        if (mUpdateSpace) {

            if ((mState & UPDATE_SPACE) == 0) {
                mState |= UPDATE_SPACE;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mSpace, 219);

                // Setup the new profile and update the object.
                if (mProfile != null) {
                    mSpace.setProfile(mProfile);
                }
                if (DEBUG) {
                    Log.d(LOG_TAG, "RepositoryService.updateObject: object=" + mSpace);
                }
                mTwinmeContextImpl.getRepositoryService().updateObject(mSpace, this::onUpdateObject);
                return;
            }
            if ((mState & UPDATE_SPACE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 4: delete the old space image when it was replaced by a new one.
        //
        if (mOldSpaceAvatarId != null) {

            if ((mState & DELETE_SPACE_IMAGE) == 0) {
                mState |= DELETE_SPACE_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.deleteImage: imageId=" + mOldSpaceAvatarId);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                ExportedImageId imageId = imageService.getImageId(mOldSpaceAvatarId);
                if (imageId != null) {
                    imageService.deleteImage(imageId, (ErrorCode status, ImageId unused) -> {
                        mState |= DELETE_SPACE_IMAGE_DONE;
                        onOperation();
                    });
                    return;
                }
                mState |= DELETE_SPACE_IMAGE_DONE;
                return;
            }
            if ((mState & DELETE_SPACE_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mSpace, 268);

        mTwinmeContextImpl.onUpdateSpace(mRequestId, mSpace);

        stop();
    }

    private void onCreateImage(@NonNull ErrorCode errorCode, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateImage errorCode=" + errorCode + " imageId=" + imageId);
        }

        if (errorCode != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(CREATE_SPACE_IMAGE, errorCode, null);
            return;
        }

        mState |= CREATE_SPACE_IMAGE_DONE;
        mSpaceAvatarId = imageId;
        onOperation();
    }

    private void onCreateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateObject: errorCode=" + errorCode);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof SpaceSettings)) {
            onOperationError(CREATE_SPACE_IMAGE, errorCode, null);
            return;
        }

        mState |= CREATE_SETTINGS_OBJECT_DONE;

        mSpaceSettings = (SpaceSettings) object;
        mSpace.setSpaceSettings(mSpaceSettings);
        onOperation();
    }

    private void onUpdateSettingsObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateSettingsObject: errorCode=" + errorCode + " object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || object == null) {
            onOperationError(UPDATE_SETTINGS_OBJECT, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, object, mSpaceSettings);
        mState |= UPDATE_SETTINGS_OBJECT_DONE;
        mSpace.setSpaceSettings(mSpaceSettings);
        onOperation();
    }

    private void onUpdateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateObject: object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || object == null) {
            onOperationError(UPDATE_SPACE, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, object, mSpace);

        mState |= UPDATE_SPACE_DONE;
        onOperation();
    }
}
