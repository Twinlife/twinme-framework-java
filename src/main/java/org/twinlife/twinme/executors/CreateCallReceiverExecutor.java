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
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContext.Consumer;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.CallReceiver;
import org.twinlife.twinme.models.CallReceiverFactory;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.models.TwincodeKind;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.1
//

public class CreateCallReceiverExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "CreateCallReceiverExec";
    private static final boolean DEBUG = false;

    private static final int CREATE_IMAGE = 1;
    private static final int CREATE_IMAGE_DONE = 1 << 1;
    private static final int CREATE_TWINCODE = 1 << 2;
    private static final int CREATE_TWINCODE_DONE = 1 << 3;
    private static final int CREATE_OBJECT = 1 << 6;
    private static final int CREATE_OBJECT_DONE = 1 << 7;

    @NonNull
    private final String mIdentityName;
    @Nullable
    private final ImageId mIdentityAvatarId;

    @Nullable
    private final String mName;
    @Nullable
    private final String mDescription;
    @NonNull
    private final Space mSpace;

    @Nullable
    private final Bitmap mAvatar;
    @Nullable
    private final File mAvatarFile;
    @Nullable
    private final String mIdentityDescription;
    @Nullable
    private final String mCapabilities;

    private ExportedImageId mCopiedIdentityAvatarId;
    @Nullable
    private TwincodeFactory mTwincodeFactory;
    private TwincodeOutbound mTwincodeOutbound;

    private CallReceiver mCallReceiver;
    private final Consumer<CallReceiver> mConsumer;

    public CreateCallReceiverExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                      @NonNull Space space, @Nullable String name, @Nullable String description, @Nullable String identityName, @Nullable String identityDescription, @Nullable Bitmap avatar, @Nullable File avatarFile,
                                      @Nullable Capabilities capabilities, Consumer<CallReceiver> consumer) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);

        mConsumer = consumer;

        mName = name;
        mDescription = description;
        mSpace = space;

        // Init twincodeOutbound attributes : use the custom attributes if defined, otherwise copy them from the profile.
        Profile profile = mSpace.getProfile();

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.PARAMETER, profile, 103);

        mIdentityName = identityName != null ? identityName : profile.getName();
        mIdentityDescription = identityDescription != null ? identityDescription : profile.getDescription();

        String capsAttrValue = capabilities != null ? capabilities.toAttributeValue() : profile.getIdentitiyCapabilities().toAttributeValue();
        Capabilities caps = new Capabilities(capsAttrValue);
        caps.setKind(TwincodeKind.CALL_RECEIVER);
        mCapabilities = caps.toAttributeValue();

        if (avatar != null) {
            mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.PARAMETER, avatarFile, 114);

            mIdentityAvatarId = null;
            mAvatar = avatar;
            mAvatarFile = avatarFile;
        } else {
            mIdentityAvatarId = profile.getAvatarId();
            mAvatar = null;
            mAvatarFile = null;
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
        // Step 1: create a new image if specified, otherwise create a copy of the identity image if there is one (privacy constraint).
        //
        if ((mState & CREATE_IMAGE) == 0) {
            mState |= CREATE_IMAGE;

            if (mAvatar != null) {
                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.createImage: avatarFile=" + mAvatarFile);
                }

                mTwinmeContextImpl.getImageService().createImage(mAvatarFile, mAvatar, this::onCreateImage);
            } else if (mIdentityAvatarId != null) {
                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.copyImage: imageId=" + mIdentityAvatarId);
                }

                mTwinmeContextImpl.getImageService().copyImage(mIdentityAvatarId, this::onCreateImage);
            }
            return;
        }

        if ((mState & CREATE_IMAGE_DONE) == 0) {
            return;
        }

        //
        // Step 2: create the call receiver twincode.
        //

        if ((mState & CREATE_TWINCODE) == 0) {
            mState |= CREATE_TWINCODE;

            List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
            PairProtocol.setTwincodeAttributePair(twincodeFactoryAttributes);

            List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
            TwinmeAttributes.setTwincodeAttributeName(twincodeOutboundAttributes, mIdentityName);
            if(mIdentityDescription != null) {
                TwinmeAttributes.setTwincodeAttributeDescription(twincodeOutboundAttributes, mIdentityDescription);
            }
            if (mCapabilities != null) {
                TwinmeAttributes.setCapabilities(twincodeOutboundAttributes, mCapabilities);
            }

            if (mCopiedIdentityAvatarId != null) {
                TwinmeAttributes.setTwincodeAttributeAvatarId(twincodeOutboundAttributes, mCopiedIdentityAvatarId);
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: twincodeFactoryAttributes=" + twincodeFactoryAttributes
                        + "twincodeInboundAttributes=null" + "twincodeOutboundAttributes=" + twincodeOutboundAttributes);
            }
            mTwinmeContextImpl.getTwincodeFactoryService().createTwincode(twincodeFactoryAttributes, null,
                    twincodeOutboundAttributes, null,
                    CallReceiver.SCHEMA_ID, this::onCreateTwincodeFactory);
            return;
        }
        if ((mState & CREATE_TWINCODE_DONE) == 0) {
            return;
        }

        //
        // Step 4: create the CallReceiver object.
        //

        if ((mState & CREATE_OBJECT) == 0) {
            mState |= CREATE_OBJECT;

            mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mTwincodeOutbound, 226);

            mTwinmeContextImpl.getRepositoryService().createObject(CallReceiverFactory.INSTANCE,
                    RepositoryService.AccessRights.PRIVATE, (RepositoryObject object) -> {
                        CallReceiver callReceiver = (CallReceiver) object;
                        callReceiver.setName(mName);
                        callReceiver.setDescription(mDescription);
                        callReceiver.setTwincodeFactory(mTwincodeFactory);
                        callReceiver.setTwincodeOutbound(mTwincodeOutbound);
                        callReceiver.setSpace(mSpace);
                        }, this::onCreateObject);
            return;
        }
        if ((mState & CREATE_OBJECT_DONE) == 0) {
            return;
        }

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mCallReceiver, 242);
        mTwinmeContextImpl.onCreateCallReceiver(mRequestId, mCallReceiver, mConsumer);

        stop();
    }

    private void onCreateImage(ErrorCode status, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateImage: status=" + status + " imageId=" + imageId);
        }

        if (status != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(CREATE_IMAGE, status, mIdentityAvatarId != null ? mIdentityAvatarId.toString() : "[NO AVATAR ID]");
            return;
        }

        mState |= CREATE_IMAGE_DONE;
        mCopiedIdentityAvatarId = imageId;
        onOperation();
    }

    private void onCreateTwincodeFactory(@NonNull ErrorCode errorCode, @Nullable TwincodeFactory twincodeFactory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateCallReceiverTwincode: factory=" + twincodeFactory);
        }

        if (errorCode != ErrorCode.SUCCESS || twincodeFactory == null) {

            onOperationError(CREATE_TWINCODE, errorCode, null);
            return;
        }

        mState |= CREATE_TWINCODE_DONE;
        mTwincodeFactory = twincodeFactory;
        mTwincodeOutbound = mTwincodeFactory.getTwincodeOutbound();
        onOperation();
    }

    private void onCreateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateObject: errorCode=" + errorCode + " object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof CallReceiver)) {
            onOperationError(CREATE_OBJECT, errorCode, null);
            return;
        }

        mState |= CREATE_OBJECT_DONE;
        mCallReceiver = (CallReceiver) object;
        onOperation();
    }
}
