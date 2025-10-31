/*
 *  Copyright (c) 2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.actions;

import android.graphics.Bitmap;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContext;

import java.util.UUID;

/**
 * A Twinme action to get a twincode name and avatar (guarded by a timeout).
 */
public class GetTwincodeAction extends TwinmeAction {
    private static final String LOG_TAG = "GetTwincodeAction";
    private static final boolean DEBUG = false;

    private static final int GET_TWINCODE = 1;
    private static final int GET_TWINCODE_DONE = 1 << 1;
    private static final int GET_TWINCODE_IMAGE = 1 << 4;
    private static final int GET_TWINCODE_IMAGE_DONE = 1 << 5;

    private static final int TIMEOUT = 10000; // 10s

    private final long mRequestId;
    @NonNull
    private final UUID mTwincodeOutboundId;
    @Nullable
    private ImageId mTwincodeAvatarId;
    private String mTwincodeName;
    private int mState = 0;
    private Consumer mConsumer;

    public interface Consumer {
        void onGetTwincodeAction(@NonNull ErrorCode errorCode, @Nullable String name, @Nullable Bitmap avatar);
    }

    public GetTwincodeAction(@NonNull TwinmeContext twinmeContext, @NonNull UUID twincodeOutboundId) {
        super(twinmeContext, TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "GetTwincodeAction twincodeOutboundId=" + twincodeOutboundId);
        }

        mRequestId = newRequestId();
        mTwincodeOutboundId = twincodeOutboundId;
    }

    public GetTwincodeAction onResult(final Consumer consumer) {

        mConsumer = consumer;
        return this;
    }

    @Override
    public void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        onOperation();
    }

    @Override
    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        // Get the twincode information.
        if ((mState & GET_TWINCODE) == 0) {
            mState |= GET_TWINCODE;

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeOutboundService.getTwincode: requestId=" + mRequestId + " twincodeOutboundId=" + mTwincodeOutboundId);
            }
            mTwinmeContext.getTwincodeOutboundService().getTwincode(mTwincodeOutboundId,
                    TwincodeOutboundService.REFRESH_PERIOD, this::onGetTwincodeOutbound);
        }
        if ((mState & GET_TWINCODE_DONE) == 0) {
            return;
        }

        // We must get the twincode avatar id.
        if (mTwincodeAvatarId != null) {
            if ((mState & GET_TWINCODE_IMAGE) == 0) {
                mState |= GET_TWINCODE_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.getImage: imageId=" + mTwincodeAvatarId);
                }
                Bitmap image = mTwinmeContext.getImageService().getImage(mTwincodeAvatarId, ImageService.Kind.THUMBNAIL);
                onGetImage(image);
            }
            if ((mState & GET_TWINCODE_IMAGE_DONE) == 0) {
                return;
            }
        }

        onFinish();
    }

    protected void onGetTwincodeOutbound(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetTwincodeOutbound errorCode=" + errorCode + " twincodeOutbound=" + twincodeOutbound);
        }

        mState |= GET_TWINCODE_DONE;

        if (errorCode != ErrorCode.SUCCESS || twincodeOutbound == null) {
            fireError(errorCode);
            return;
        }

        mTwincodeName = twincodeOutbound.getName();
        mTwincodeAvatarId = twincodeOutbound.getAvatarId();
        if (mConsumer != null && mTwincodeAvatarId == null) {
            mConsumer.onGetTwincodeAction(errorCode, mTwincodeName, null);
        }

        onOperation();
    }

    protected void onGetImage(Bitmap image) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetImage");
        }

        mState |= GET_TWINCODE_IMAGE_DONE;

        if (mConsumer != null) {
            // This is a success even if we don't get the image: the twincode is valid and we have a name!
            mConsumer.onGetTwincodeAction(ErrorCode.SUCCESS, mTwincodeName, image);
        }
        onFinish();
    }

    @Override
    protected void fireError(@NonNull ErrorCode errorCode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "fireError errorCode=" + errorCode);
        }

        if (mConsumer != null) {
            mConsumer.onGetTwincodeAction(errorCode, null, null);
        }

        onFinish();
    }
}
