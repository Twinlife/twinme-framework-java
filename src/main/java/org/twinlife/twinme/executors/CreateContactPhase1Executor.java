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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.ContactFactory;
import org.twinlife.twinme.models.GroupMember;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.20
//
// User foreground operation: must be connected with a timeout if connection does not work.

public class CreateContactPhase1Executor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "CreateContactPhase1E...";
    private static final boolean DEBUG = false;

    private static final int CHECK_TWINCODE = 1;
    private static final int CHECK_TWINCODE_DONE = 1 << 1;
    private static final int COPY_IMAGE = 1 << 2;
    private static final int COPY_IMAGE_DONE = 1 << 3;
    private static final int CREATE_TWINCODE = 1 << 4;
    private static final int CREATE_TWINCODE_DONE = 1 << 5;
    private static final int CREATE_OBJECT = 1 << 6;
    private static final int CREATE_OBJECT_DONE = 1 << 7;
    private static final int INVOKE_TWINCODE_OUTBOUND = 1 << 8;
    private static final int INVOKE_TWINCODE_OUTBOUND_DONE = 1 << 9;

    @NonNull
    private final TwincodeOutbound mPeerTwincodeOutbound;
    @Nullable
    private final TwincodeInbound mIdentityTwincodeInbound;
    @NonNull
    private final String mIdentityName;
    @Nullable
    private final ImageId mIdentityAvatarId;
    private ExportedImageId mCopiedIdentityAvatarId;
    @Nullable
    private final UUID mGroupTwincodeId;
    @Nullable
    private final UUID mGroupMemberTwincodeId;
    @NonNull
    private final Space mSpace;

    private Contact mContact;
    private TwincodeOutbound mIdentityTwincodeOutbound;
    private TwincodeFactory mTwincodeFactory;

    public CreateContactPhase1Executor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                       @NonNull TwincodeOutbound peerTwincodeOutbound,
                                       @NonNull Space space,
                                       @NonNull Profile profile,
                                       @Nullable GroupMember contactGroupMember) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "CreateContactPhase1Executor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " peerTwincodeOutbound=" + peerTwincodeOutbound + " profile=" + profile);
        }

        mSpace = space;
        mPeerTwincodeOutbound = peerTwincodeOutbound;
        mIdentityName = profile.getName();
        mIdentityAvatarId = profile.getAvatarId();
        mIdentityTwincodeOutbound = profile.getTwincodeOutbound();
        mIdentityTwincodeInbound = profile.getTwincodeInbound();
        if (contactGroupMember != null) {
            mGroupTwincodeId = contactGroupMember.getGroup().getTwincodeOutboundId();
            mGroupMemberTwincodeId = contactGroupMember.getPeerTwincodeOutboundId();
        } else {
            mGroupTwincodeId = null;
            mGroupMemberTwincodeId = null;
        }
    }

    public CreateContactPhase1Executor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId,
                                       @NonNull TwincodeOutbound peerTwincodeOutbound,
                                       @NonNull Space space,
                                       @NonNull String identityName, @Nullable ImageId identityAvatarId,
                                       @Nullable GroupMember contactGroupMember) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "CreateContactPhase1Executor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " peerTwincodeOutbound=" + peerTwincodeOutbound + " identityName=" + identityName + " identityAvatarId=" + identityAvatarId);
        }

        mSpace = space;
        mPeerTwincodeOutbound = peerTwincodeOutbound;
        mIdentityName = identityName;
        mIdentityAvatarId = identityAvatarId;
        mIdentityTwincodeInbound = null;
        if (contactGroupMember != null) {
            mGroupTwincodeId = contactGroupMember.getGroup().getTwincodeOutboundId();
            mGroupMemberTwincodeId = contactGroupMember.getPeerTwincodeOutboundId();
        } else {
            mGroupTwincodeId = null;
            mGroupMemberTwincodeId = null;
        }
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & CHECK_TWINCODE) != 0 && (mState & CHECK_TWINCODE_DONE) == 0) {
                mState &= ~CHECK_TWINCODE;
            }
            if ((mState & COPY_IMAGE) != 0 && (mState & COPY_IMAGE_DONE) == 0) {
                mState &= ~COPY_IMAGE;
            }
            if ((mState & CREATE_TWINCODE) != 0 && (mState & CREATE_TWINCODE_DONE) == 0) {
                mState &= ~CREATE_TWINCODE;
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
        // Step 0: verify that the peer twincode is not one of our profile twincode, raises the BAD_REQUEST error if this occurs.
        // At the same time, verify that our identity twincode has a public/private key.
        // By updating the attributes, the TwincodeOutboundService will create the public/private key and sign the attributes.
        //
        if ((mState & CHECK_TWINCODE) == 0) {
            mState |= CHECK_TWINCODE;

            if (mTwinmeContextImpl.isProfileTwincode(mPeerTwincodeOutbound.getId())) {
                onOperationError(CHECK_TWINCODE, ErrorCode.BAD_REQUEST, mPeerTwincodeOutbound.getId().toString());
                return;
            }

            if (!mIdentityTwincodeOutbound.isTrusted() && mIdentityTwincodeInbound != null) {
                mTwinmeContextImpl.getTwincodeOutboundService().createPrivateKey(mIdentityTwincodeInbound,
                        this::onUpdateProfileTwincode);
                return;
            } else {
                mState |= CHECK_TWINCODE_DONE;
            }
        }
        if ((mState & CHECK_TWINCODE_DONE) == 0) {
            return;
        }

        //
        // Step 1a: create a copy of the identity image if there is one (privacy constraint).
        //
        if (mIdentityAvatarId != null) {

            if ((mState & COPY_IMAGE) == 0) {
                mState |= COPY_IMAGE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "ImageService.copyImage: imageId=" + mIdentityAvatarId);
                }
                ImageService imageService = mTwinmeContextImpl.getImageService();
                imageService.copyImage(mIdentityAvatarId, this::onCopyImage);
                return;
            }
            if ((mState & COPY_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 1b: create the private identity twincode.
        //

        if ((mState & CREATE_TWINCODE) == 0) {
            mState |= CREATE_TWINCODE;

            List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
            PairProtocol.setTwincodeAttributePair(twincodeFactoryAttributes);

            List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();
            TwinmeAttributes.setTwincodeAttributeName(twincodeOutboundAttributes, mIdentityName);
            if (mCopiedIdentityAvatarId != null) {
                TwinmeAttributes.setTwincodeAttributeAvatarId(twincodeOutboundAttributes, mCopiedIdentityAvatarId);
            }

            // Copy a number of twincode attributes from the profile identity.
            if (mIdentityTwincodeOutbound != null) {
                mTwinmeContextImpl.copySharedTwincodeAttributes(twincodeOutboundAttributes, mIdentityTwincodeOutbound);
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: twincodeFactoryAttributes=" + twincodeFactoryAttributes
                        + " twincodeOutboundAttributes=" + twincodeOutboundAttributes);
            }
            mTwinmeContextImpl.getTwincodeFactoryService().createTwincode(twincodeFactoryAttributes, null, twincodeOutboundAttributes,
                    null, Contact.SCHEMA_ID, this::onCreateTwincodeFactory);
            return;
        }
        if ((mState & CREATE_TWINCODE_DONE) == 0) {
            return;
        }

        //
        // Step 3: create the Contact object.
        //

        if ((mState & CREATE_OBJECT) == 0) {
            mState |= CREATE_OBJECT;

            mTwinmeContextImpl.getRepositoryService().createObject(ContactFactory.INSTANCE,
                    RepositoryService.AccessRights.PRIVATE, (RepositoryObject object) -> {
                        String name = mPeerTwincodeOutbound.getName();
                        if (name == null) {
                            mTwinmeContextImpl.assertion(ExecutorAssertPoint.CREATE_CONTACT1_NAME, AssertPoint.create(mPeerTwincodeOutbound));

                            name = mTwinmeContextImpl.getAnonymousName();
                        }
                        Contact contact = (Contact) object;
                        contact.setName(name);
                        contact.setTwincodeFactory(mTwincodeFactory);
                        contact.setPublicPeerTwincodeOutbound(mPeerTwincodeOutbound);
                        contact.setSpace(mSpace);
                        if (mGroupTwincodeId != null && mGroupMemberTwincodeId != null) {
                            contact.setGroupInformation(mGroupTwincodeId, mGroupMemberTwincodeId);
                        }
                    }, this::onCreateObject);
            return;
        }
        if ((mState & CREATE_OBJECT_DONE) == 0) {
            return;
        }

        //
        // Step 4: invoke the peer twincode to create the contact on the other side (CreateContactPhase2 on the peer device).
        //

        if ((mState & INVOKE_TWINCODE_OUTBOUND) == 0) {
            mState |= INVOKE_TWINCODE_OUTBOUND;

            mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mTwincodeFactory, 291);

            final TwincodeOutbound twincodeOutbound = mTwincodeFactory.getTwincodeOutbound();
            List<BaseService.AttributeNameValue> attributes;
            attributes = new ArrayList<>();
            PairProtocol.setInvokeTwincodeActionPairInviteAttributeTwincodeId(attributes, twincodeOutbound.getId());

            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeOutboundService.invokeTwincode: peerTwincodeOutboundId=" + mPeerTwincodeOutbound.getId() +
                        " attributes=" + attributes);
            }
            if (mPeerTwincodeOutbound.isSigned()) {
                mTwinmeContextImpl.getTwincodeOutboundService().secureInvokeTwincode(twincodeOutbound,
                        twincodeOutbound, mPeerTwincodeOutbound,
                        TwincodeOutboundService.INVOKE_URGENT | TwincodeOutboundService.CREATE_SECRET, PairProtocol.ACTION_PAIR_INVITE,
                        attributes, this::onInvokeTwincode);
            } else {
                mTwinmeContextImpl.getTwincodeOutboundService().invokeTwincode(mPeerTwincodeOutbound,
                        TwincodeOutboundService.INVOKE_URGENT, PairProtocol.ACTION_PAIR_INVITE,
                        attributes, this::onInvokeTwincode);
            }
            return;
        }
        if ((mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mContact, 322);
        if (!mContact.checkInvariants()) {
            mTwinmeContextImpl.assertion(ExecutorAssertPoint.CONTACT_INVARIANT, AssertPoint.create(mContact));
        }

        mTwinmeContextImpl.onCreateContact(mRequestId, mContact);

        stop();
    }

    private void onUpdateProfileTwincode(@NonNull ErrorCode errorCode, @Nullable TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateProfileTwincode: errorCode=" + errorCode + " twincodeOutbound=" + twincodeOutbound);
        }

        if (errorCode != ErrorCode.SUCCESS) {

            onOperationError(CHECK_TWINCODE, errorCode, null);
            return;
        }

        mState |= CHECK_TWINCODE_DONE;
        onOperation();
    }

    private void onCopyImage(ErrorCode status, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCopyImage: status=" + status + " imageId=" + imageId);
        }

        if (status != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(COPY_IMAGE, status, mIdentityAvatarId != null ? mIdentityAvatarId.toString() : null);
            return;
        }

        mState |= COPY_IMAGE_DONE;
        mCopiedIdentityAvatarId = imageId;
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

    private void onInvokeTwincode(@NonNull ErrorCode errorCode, @Nullable UUID invocationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvokeTwincode: invocationId=" + invocationId);
        }

        if (errorCode != ErrorCode.SUCCESS || invocationId == null) {

            onOperationError(INVOKE_TWINCODE_OUTBOUND, errorCode, mPeerTwincodeOutbound.toString());
            return;
        }

        mState |= INVOKE_TWINCODE_OUTBOUND_DONE;
        onOperation();
    }

    private void onCreateObject(@NonNull BaseService.ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateObject: errorCode=" + errorCode + " object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof Contact)) {

            onOperationError(CREATE_OBJECT, errorCode, mPeerTwincodeOutbound.toString());
            return;
        }

        mState |= CREATE_OBJECT_DONE;
        mContact = (Contact) object;
        onOperation();
    }

    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        if (operationId == INVOKE_TWINCODE_OUTBOUND || operationId == CREATE_OBJECT) {
            if (errorCode == ErrorCode.ITEM_NOT_FOUND || errorCode == ErrorCode.NO_PRIVATE_KEY
                    || errorCode == ErrorCode.INVALID_PUBLIC_KEY || errorCode == ErrorCode.INVALID_PRIVATE_KEY) {
                // The peer twincode is invalid, delete the contact without waiting (do not timeout on this delete).
                new DeleteContactExecutor(mTwinmeContextImpl, mTwinmeContextImpl.newRequestId(),
                        mContact, null, 0).start();
                // And return the ITEM_NOT_FOUND error: there is nothing we can do.
            }
        }

        super.onOperationError(operationId, errorCode, errorParameter);
    }
}
