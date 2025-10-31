/*
 *  Copyright (c) 2014-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.executors;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.models.Contact;
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
// version: 1.19
//
// User foreground operation: must be connected with a timeout if connection does not work.

public class UpdateContactAndIdentityExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "UpdateContactAndIden...";
    private static final boolean DEBUG = false;

    private static final int CREATE_IMAGE = 1;
    private static final int CREATE_IMAGE_DONE = 1 << 1;
    private static final int COPY_IMAGE = 1 << 2;
    private static final int COPY_IMAGE_DONE = 1 << 3;
    private static final int UPDATE_OBJECT = 1 << 4;
    private static final int UPDATE_OBJECT_DONE = 1 << 5;
    private static final int UPDATE_TWINCODE_OUTBOUND = 1 << 6;
    private static final int UPDATE_TWINCODE_OUTBOUND_DONE = 1 << 7;
    private static final int INVOKE_TWINCODE_OUTBOUND = 1 << 8;
    private static final int INVOKE_TWINCODE_OUTBOUND_DONE = 1 << 9;
    private static final int DELETE_OLD_IMAGE = 1 << 10;
    private static final int DELETE_OLD_IMAGE_DONE = 1 << 11;

    private static final int UPDATE_TWINCODE_INBOUND = 1 << 12;
    private static final int UPDATE_TWINCODE_INBOUND_DONE = 1 << 13;

    @NonNull
    private final Contact mContact;
    @Nullable
    private final String mContactName;
    @Nullable
    private final String mIdentityName;
    @Nullable
    private final Bitmap mIdentityAvatar;
    @Nullable
    private final File mIdentityAvatarFile;
    @Nullable
    private final TwincodeOutbound mTwincodeOutbound;
    @Nullable
    private final TwincodeInbound mTwincodeInbound;
    private final ImageId mCopyImageId;
    private final boolean mUpdateTwincodeOutbound;
    private final boolean mUpdateTwincodeInbound;
    private final boolean mUpdateContact;
    private final Space mSpace;
    private final Space mOldSpace;
    private final boolean mCreateImage;
    @Nullable
    private final String mDescription;
    @Nullable
    private final String mCapabilities;
    @Nullable
    private final String mPrivateCapabilities;
    @Nullable
    private ExportedImageId mIdentityAvatarId;
    @Nullable
    private ImageId mOldIdentityAvatarId;

    public UpdateContactAndIdentityExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                            @NonNull Contact contact, @NonNull String contactName,
                                            @Nullable String description) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateContactAndIdentityExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " contact=" + contact + contactName + " description=" + description);
        }

        mContact = contact;
        mContactName = contactName;
        mDescription = description;
        mSpace = contact.getSpace();
        mOldSpace = mSpace;
        mIdentityName = null;
        mIdentityAvatar = null;
        mIdentityAvatarFile = null;
        mCopyImageId = null;
        mCapabilities = null;
        mPrivateCapabilities = null;
        mIdentityAvatarId = null;
        mTwincodeOutbound = contact.getIdentityTwincodeOutbound();
        mTwincodeInbound = null;
        mCreateImage = false;
        mUpdateTwincodeOutbound = false;
        mUpdateTwincodeInbound = false;
        mUpdateContact = !Utils.equals(mContactName, contact.getName()) || !Utils.equals(mDescription, contact.getDescription());

        // Contact identity is not modified, we can start immediately.
        mNeedOnline = false;
    }

    public UpdateContactAndIdentityExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                            @NonNull Contact contact, @NonNull String identityName, @Nullable Bitmap identityAvatar,
                                            @Nullable File identityAvatarFile, @Nullable String description,
                                            @Nullable Capabilities capabilities, @Nullable Capabilities privateCapabilities) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateContactAndIdentityExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " contact=" + contact + " identityName=" + identityName + " identityAvatar=" + identityAvatar
                    + " description=" + description + " capabilities=" + capabilities);
        }

        mContact = contact;
        mContactName = null;
        mCopyImageId = null;
        mSpace = contact.getSpace();
        mOldSpace = mSpace;
        mIdentityName = identityName;
        mIdentityAvatar = identityAvatar;
        mIdentityAvatarFile = identityAvatarFile;
        mOldIdentityAvatarId = contact.getIdentityAvatarId();
        mTwincodeOutbound = contact.getIdentityTwincodeOutbound();
        mDescription = description;
        mCapabilities = capabilities == null ? null : capabilities.toAttributeValue();
        mPrivateCapabilities = privateCapabilities == null ? null : privateCapabilities.toAttributeValue();
        mCreateImage = identityAvatarFile != null;

        // Check if one of the twincode attributes is modified.
        boolean updateDescription = !Utils.equals(description, contact.getPeerDescription());
        boolean updateCapabilities = !Utils.equals(mCapabilities, contact.getIdentityCapabilities().toAttributeValue());
        boolean updateName = !Utils.equals(identityName, mContact.getIdentityName());
        mUpdateTwincodeOutbound = updateName || updateDescription || updateCapabilities || mCreateImage;
        mUpdateTwincodeInbound = !Utils.equals(mPrivateCapabilities, contact.getPrivateCapabilities().toAttributeValue());
        mTwincodeInbound = contact.getTwincodeInbound();
        mUpdateContact = false;
    }

    public UpdateContactAndIdentityExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                            @NonNull Contact contact, @NonNull String identityName,
                                            @Nullable ImageId identityAvatarId, @Nullable String description,
                                            @Nullable Capabilities capabilities, @Nullable Capabilities privateCapabilities,
                                            long timeout) {
        super(twinmeContextImpl, requestId, LOG_TAG, timeout);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateContactAndIdentityExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " contact=" + contact + " identityName=" + identityName + " identityAvatarId=" + identityAvatarId);
        }

        mContact = contact;
        mContactName = null;
        mSpace = contact.getSpace();
        mOldSpace = mSpace;
        mIdentityName = identityName;
        mIdentityAvatar = null;
        mIdentityAvatarFile = null;
        mOldIdentityAvatarId = contact.getIdentityAvatarId();
        mTwincodeOutbound = contact.getIdentityTwincodeOutbound();
        mTwincodeInbound = contact.getTwincodeInbound();
        mDescription = description;
        mCapabilities = capabilities == null ? null : capabilities.toAttributeValue();
        mPrivateCapabilities = privateCapabilities == null ? null : privateCapabilities.toAttributeValue();
        if (identityAvatarId != null && !identityAvatarId.equals(mOldIdentityAvatarId)) {
            mCopyImageId = identityAvatarId;
        } else {
            mCopyImageId = null;
        }
        mCreateImage = false;
        mUpdateTwincodeOutbound = true;
        mUpdateTwincodeInbound = !Utils.equals(mPrivateCapabilities, contact.getPrivateCapabilities().toAttributeValue());
        mUpdateContact = false;
    }

    public UpdateContactAndIdentityExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                            @NonNull Contact contact, @NonNull Space space) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateContactAndIdentityExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " contact=" + contact + " space=" + space);
        }

        mContact = contact;
        mSpace = space;
        mOldSpace = contact.getSpace();
        mContactName = contact.getName();
        mIdentityName = contact.getIdentityName();
        mIdentityAvatarId = null;
        mDescription = contact.getDescription();
        mIdentityAvatar = null;
        mIdentityAvatarFile = null;
        mTwincodeOutbound = null;
        mTwincodeInbound = null;
        mCapabilities = null;
        mPrivateCapabilities = null;
        mCopyImageId = null;
        mCreateImage = false;
        mUpdateTwincodeOutbound = false;
        mUpdateTwincodeInbound = false;
        mUpdateContact = true;

        // We are moving contact to another space, we can start immediately.
        mNeedOnline = false;
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
            if ((mState & COPY_IMAGE) != 0 && (mState & COPY_IMAGE_DONE) == 0) {
                mState &= ~COPY_IMAGE;
            }
            if ((mState & UPDATE_TWINCODE_OUTBOUND) != 0 && (mState & UPDATE_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~UPDATE_TWINCODE_OUTBOUND;
            }
            if ((mState & INVOKE_TWINCODE_OUTBOUND) != 0 && (mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~INVOKE_TWINCODE_OUTBOUND;
            }
            if ((mState & UPDATE_TWINCODE_INBOUND) != 0 && (mState & UPDATE_TWINCODE_INBOUND_DONE) == 0) {
                mState &= ~UPDATE_TWINCODE_INBOUND;
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
        // Step 1a: a new image must be setup for the private identity, create it.
        //
        if (mIdentityAvatarFile != null && mIdentityAvatar != null && mCreateImage) {

            if ((mState & CREATE_IMAGE) == 0) {
                mState |= CREATE_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.createImage: mAvatarFile=" + mIdentityAvatarFile);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.createImage(mIdentityAvatarFile, mIdentityAvatar, this::onCreateImage);
                return;
            }
            if ((mState & CREATE_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 1b: a new image must be setup and copied from an existing image, do the copy.
        //
        if (mCopyImageId != null) {

            if ((mState & COPY_IMAGE) == 0) {
                mState |= COPY_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.copyImage: mCopyImageId=" + mCopyImageId);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.copyImage(mCopyImageId, this::onCopyImage);
                return;
            }
            if ((mState & COPY_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 2: update the contact object when the space or contact's name is modified.
        //

        if (mUpdateContact && mContactName != null) {

            if ((mState & UPDATE_OBJECT) == 0) {
                mState |= UPDATE_OBJECT;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mContact, 332);
                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mContactName, 333);

                // AvatarId
                mContact.setName(mContactName);
                mContact.setSpace(mSpace);
                mContact.setDescription(mDescription);

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwinmeContext.updateObject: contact=" + mContact);
                }
                mTwinmeContextImpl.getRepositoryService().updateObject(mContact, this::onUpdateObject);
                return;
            }
            if ((mState & UPDATE_OBJECT_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3: update the private identity name and avatar.
        //

        if (mUpdateTwincodeOutbound && mTwincodeOutbound != null) {

            if ((mState & UPDATE_TWINCODE_OUTBOUND) == 0) {
                mState |= UPDATE_TWINCODE_OUTBOUND;

                List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
                if (mIdentityName != null && !mIdentityName.equals(mContact.getIdentityName())) {
                    TwinmeAttributes.setTwincodeAttributeName(attributes, mIdentityName);
                }
                if (mIdentityAvatarId != null) {
                    TwinmeAttributes.setTwincodeAttributeAvatarId(attributes, mIdentityAvatarId);
                }
                if (mDescription != null && !mDescription.equals(mContact.getDescription())) {
                    TwinmeAttributes.setTwincodeAttributeDescription(attributes, mDescription);
                }
                if (mCapabilities != null) {
                    TwinmeAttributes.setCapabilities(attributes, mCapabilities);
                }

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.updateTwincode: twincodeOutboundId="
                            + mTwincodeOutbound);
                }
                mTwinmeContextImpl.getTwincodeOutboundService().updateTwincode(mTwincodeOutbound, attributes, null,
                        this::onUpdateTwincodeOutbound);
                return;
            }
            if ((mState & UPDATE_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }

            //
            // Step 3
            //

            if (mContact.getPeerTwincodeOutbound() != null) {

                if ((mState & INVOKE_TWINCODE_OUTBOUND) == 0) {
                    mState |= INVOKE_TWINCODE_OUTBOUND;

                    if (DEBUG) {
                        Log.d(LOG_TAG, "TwincodeOutboundService.invokeTwincode: peerTwincodeOutbound="
                                + mContact.getPeerTwincodeOutbound());
                    }
                    mTwinmeContextImpl.getTwincodeOutboundService().invokeTwincode(mContact.getPeerTwincodeOutbound(),
                            TwincodeOutboundService.INVOKE_WAKEUP, PairProtocol.ACTION_PAIR_REFRESH,
                            null, this::onInvokeTwincode);
                    return;
                }
                if ((mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                    return;
                }
            }
        }

        if (mUpdateTwincodeInbound && mTwincodeInbound != null) {

            if ((mState & UPDATE_TWINCODE_INBOUND) == 0) {
                mState |= UPDATE_TWINCODE_INBOUND;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mTwincodeInbound, 414);

                List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
                List<String> deleteAttributeNames = new ArrayList<>();

                if(mPrivateCapabilities != null) {
                    TwinmeAttributes.setCapabilities(attributes, mPrivateCapabilities);
                }else{
                    deleteAttributeNames.add(Twincode.CAPABILITIES);
                }

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeInboundService.updateTwincode: twincodeInboundId="
                            + mTwincodeInbound);
                }
                mTwinmeContextImpl.getTwincodeInboundService().updateTwincode(mTwincodeInbound, attributes, deleteAttributeNames,
                        this::onUpdateTwincodeInbound);
                return;
            }
            if ((mState & UPDATE_TWINCODE_INBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 4: delete the old avatar image.
        //
        if (mOldIdentityAvatarId != null && (mCreateImage || mCopyImageId != null)) {

            if ((mState & DELETE_OLD_IMAGE) == 0) {
                mState |= DELETE_OLD_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.deleteImage: imageId=" + mOldIdentityAvatarId);
                }
                mTwinmeContextImpl.getImageService().deleteImage(mOldIdentityAvatarId, this::onDeleteImage);
                return;
            }
            if ((mState & DELETE_OLD_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //
        //

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mContact, 416);
        if (!mContact.checkInvariants()) {
            mTwinmeContextImpl.assertion(ExecutorAssertPoint.CONTACT_INVARIANT, AssertPoint.create(mContact));
        }

        if (mOldSpace != mSpace) {
            mTwinmeContextImpl.onMoveToSpace(mRequestId, mContact, mOldSpace);
        } else {
            mTwinmeContextImpl.onUpdateContact(mRequestId, mContact);
        }

        stop();
    }

    private void onCreateImage(@NonNull ErrorCode errorCode, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateImage: errorCode=" + errorCode + " imageId=" + imageId);
        }

        if (errorCode != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(CREATE_IMAGE, errorCode, null);
            return;
        }

        mState |= CREATE_IMAGE_DONE;
        mIdentityAvatarId = imageId;
        onOperation();
    }

    private void onCopyImage(@NonNull ErrorCode errorCode, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCopyImage: errorCode=" + errorCode + " imageId=" + imageId);
        }

        if (errorCode != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(COPY_IMAGE, errorCode, null);
            return;
        }

        mState |= COPY_IMAGE_DONE;
        mIdentityAvatarId = imageId;
        onOperation();
    }

    private void onUpdateTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateTwincodeOutbound: twincodeOutbound=" + twincodeOutbound);
        }
        if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {

            onOperationError(UPDATE_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeOutbound, mTwincodeOutbound);

        mState |= UPDATE_TWINCODE_OUTBOUND_DONE;
        mContact.setTwincodeOutbound(mTwincodeOutbound);
        onOperation();
    }

    private void onUpdateTwincodeInbound(@NonNull ErrorCode errorCode, @Nullable TwincodeInbound twincodeInbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateTwincodeInbound: twincodeInbound=" + twincodeInbound);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeInbound == null) {

            onOperationError(UPDATE_TWINCODE_INBOUND, errorCode, null);
            return;
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_TWINCODE, twincodeInbound.getId(), mUpdateTwincodeInbound);

        mState |= UPDATE_TWINCODE_INBOUND_DONE;

        mContact.setTwincodeInbound(twincodeInbound);
        onOperation();
    }

    private void onInvokeTwincode(@NonNull ErrorCode errorCode, @Nullable UUID invocationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvokeTwincode: invocationId=" + invocationId);
        }

        if (errorCode != ErrorCode.SUCCESS || invocationId == null) {

            onOperationError(INVOKE_TWINCODE_OUTBOUND, errorCode, null);
            return;
        }

        mState |= INVOKE_TWINCODE_OUTBOUND_DONE;
        onOperation();
    }

    private void onUpdateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateObject: errorCode=" + errorCode + " object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || object == null) {

            onOperationError(UPDATE_OBJECT, errorCode, null);
            return;
        }

        mState |= UPDATE_OBJECT_DONE;
        onOperation();
    }

    private void onDeleteImage(ErrorCode errorCode, @Nullable ImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteImage: errorCode=" + errorCode + " imageId=" + imageId);
        }

        // Ignore the error and proceed!!!
        mState |= DELETE_OLD_IMAGE_DONE;
        onOperation();
    }
}
