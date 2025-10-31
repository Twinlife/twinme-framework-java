/*
 *  Copyright (c) 2014-2025 twinlife SA.
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
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.ContactFactory;
import org.twinlife.twinme.models.Invitation;
import org.twinlife.twinme.models.PairInviteInvocation;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.twinlife.twinlife.BaseService.DEFAULT_REQUEST_ID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// Called as background operation: must be connected and no timeout.

public class CreateContactPhase2Executor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "CreateContactPhase2E...";
    private static final boolean DEBUG = false;

    private static final int FIND_CONTACT = 1 << 2;
    private static final int FIND_CONTACT_DONE = 1 << 3;
    private static final int COPY_IMAGE = 1 << 4;
    private static final int COPY_IMAGE_DONE = 1 << 5;
    private static final int CREATE_TWINCODE = 1 << 6;
    private static final int CREATE_TWINCODE_DONE = 1 << 7;
    private static final int CREATE_CONTACT_OBJECT = 1 << 10;
    private static final int CREATE_CONTACT_OBJECT_DONE = 1 << 11;
    private static final int INVOKE_TWINCODE_OUTBOUND = 1 << 12;
    private static final int INVOKE_TWINCODE_OUTBOUND_DONE = 1 << 13;
    private static final int DELETE_INVITATION = 1 << 14;
    private static final int DELETE_INVITATION_DONE = 1 << 15;
    private static final int GET_PEER_IMAGE = 1 << 16;
    private static final int GET_PEER_IMAGE_DONE = 1 << 17;
    private static final int ON_ERROR_INVOKE_TWINCODE_OUTBOUND = 1 << 18;
    private static final int ON_ERROR_INVOKE_TWINCODE_OUTBOUND_DONE = 1 << 19;

    @NonNull
    private final PairInviteInvocation mInvocation;
    private final String mIdentityName;
    private final ImageId mIdentityAvatarId;
    @NonNull
    private final TwincodeOutbound mPeerTwincodeOutbound;
    private ExportedImageId mCopiedIdentityAvatarId;
    @Nullable
    private final Space mSpace;
    private final TwincodeOutbound mIdentityTwincodeOutbound;
    private TwincodeFactory mTwincodeFactory;
    @Nullable
    private TwincodeOutbound mContactTwincodeOutbound;
    private final UUID mGroupTwincodeId;
    private final UUID mGroupMemberTwincodeId;
    private Contact mContact;
    private Invitation mInvitation;
    private boolean mUnbindContact = false;
    private boolean mCreationError = false;

    public CreateContactPhase2Executor(@NonNull TwinmeContextImpl twinmeContextImpl,
                                       @NonNull PairInviteInvocation invocation,
                                       @NonNull Profile profile) {
        super(twinmeContextImpl, DEFAULT_REQUEST_ID, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "CreateContactPhase2Executor: twinmeContextImpl=" + twinmeContextImpl + " invocation=" + invocation +
                    " profile=" + profile);
        }

        mInvocation = invocation;
        mSpace = profile.getSpace();
        mPeerTwincodeOutbound = invocation.getTwincodeOutbound();
        mGroupTwincodeId = null;
        mGroupMemberTwincodeId = null;
        mIdentityName = profile.getName();
        mIdentityAvatarId = profile.getAvatarId();
        mIdentityTwincodeOutbound = profile.getTwincodeOutbound();
        if (mSpace == null) {
            mState |= FIND_CONTACT | FIND_CONTACT_DONE;
            mCreationError = true;
        }
    }

    public CreateContactPhase2Executor(@NonNull TwinmeContextImpl twinmeContextImpl,
                                       @NonNull PairInviteInvocation invocation,
                                       @NonNull Invitation invitation) {
        super(twinmeContextImpl, DEFAULT_REQUEST_ID, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "CreateContactPhase2Executor: twinmeContextImpl=" + twinmeContextImpl
                    + " invocation=" + invocation + " invitation=" + invitation);
        }

        mInvocation = invocation;
        mInvitation = invitation;
        mSpace = invitation.getSpace();
        mPeerTwincodeOutbound = invocation.getTwincodeOutbound();
        mGroupTwincodeId = invitation.getGroupId();
        mGroupMemberTwincodeId = invitation.getGroupMemberTwincodeId();
        mIdentityName = invitation.getName();
        mIdentityAvatarId = invitation.getAvatarId();
        mIdentityTwincodeOutbound = invitation.getTwincodeOutbound();
        if (mSpace == null) {
            mState |= FIND_CONTACT | FIND_CONTACT_DONE;
            mCreationError = true;
        }
    }

    @Override
    public void onDeleteInvitation(long requestId, @NonNull UUID invitationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwinmeContextObserver.onDeleteInvitation: requestId=" + requestId + " invitationId=" + invitationId);
        }

        if (getOperation(requestId) > 0) {
            onDeleteInvitation(invitationId);
        }
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            if ((mState & COPY_IMAGE) != 0 && (mState & COPY_IMAGE_DONE) == 0) {
                mState &= ~COPY_IMAGE;
            }
            if ((mState & CREATE_TWINCODE) != 0 && (mState & CREATE_TWINCODE_DONE) == 0) {
                mState &= ~CREATE_TWINCODE;
            }
            if ((mState & INVOKE_TWINCODE_OUTBOUND) != 0 && (mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~INVOKE_TWINCODE_OUTBOUND;
            }
            if ((mState & GET_PEER_IMAGE) != 0 && (mState & GET_PEER_IMAGE_DONE) == 0) {
                mState &= ~GET_PEER_IMAGE;
            }
            if ((mState & ON_ERROR_INVOKE_TWINCODE_OUTBOUND) != 0 && (mState & ON_ERROR_INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                mState &= ~ON_ERROR_INVOKE_TWINCODE_OUTBOUND;
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
        // Step 2: check if a contact has not been created previously with the same peerTwincodeOutboundId
        //
        if ((mState & FIND_CONTACT) == 0) {
            mState |= FIND_CONTACT;

            long requestId = newOperation(FIND_CONTACT);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeOutboundService.findContacts: requestId=" + requestId + " contact.peerTwincodeOutbound==" + mPeerTwincodeOutbound);
            }

            final Filter<RepositoryObject> filter = new Filter<RepositoryObject>(null) {
                public boolean accept(@NonNull RepositoryObject object) {

                    if (!(object instanceof Contact)) {
                        return false;
                    }

                    final Contact contact = (Contact) object;
                    return mPeerTwincodeOutbound.getId().equals(contact.getPeerTwincodeOutboundId());
                }
            };
            mTwinmeContextImpl.findContacts(filter, (List<Contact> contacts) -> {
                if (contacts != null && !contacts.isEmpty()) {
                    //
                    // A contact with the same peerTwincodeOutboundId has been created in a previous call to CreateContactPhase2Executor
                    //  that has been stopped prematurely, reuse it in order to avoid to have two different contacts bounded to the same peer
                    //  contact
                    //
                    mContact = contacts.get(0);
                    mContact.setSpace(mSpace);
                    mContactTwincodeOutbound = mContact.getTwincodeOutbound();
                }

                mState |= FIND_CONTACT_DONE;
                onOperation();
            });
            return;
        }

        if ((mState & FIND_CONTACT_DONE) == 0) {
            return;
        }

        //
        // Step 2: invoke peer to unbind the contact on its side.
        //
        if (mCreationError) {

            if ((mState & ON_ERROR_INVOKE_TWINCODE_OUTBOUND) == 0) {
                mState |= ON_ERROR_INVOKE_TWINCODE_OUTBOUND;

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.invokeTwincode: peerTwincodeOutboundId=" + mPeerTwincodeOutbound);
                }
                // No need to sign and encrypt that invocation: there are no attributes (the server must also be able to make that invocation).
                mTwinmeContextImpl.getTwincodeOutboundService().invokeTwincode(mPeerTwincodeOutbound,
                        TwincodeOutboundService.INVOKE_URGENT, PairProtocol.ACTION_PAIR_UNBIND, null, this::onInvokeTwincode);
                return;
            }
            if ((mState & ON_ERROR_INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }

            stop();
            return;
        }

        if (mContact == null) {
            //
            // Step 3: create a copy of the identity image if there is one (privacy constraint).
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
            // Step 4: create the private identity twincode.
            //

            if ((mState & CREATE_TWINCODE) == 0) {
                mState |= CREATE_TWINCODE;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mPeerTwincodeOutbound, 292);
                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mIdentityName, 293);

                List<AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
                PairProtocol.setTwincodeAttributePair(twincodeFactoryAttributes);

                List<AttributeNameValue> twincodeInboundAttributes = new ArrayList<>();
                PairProtocol.setTwincodeAttributePairTwincodeId(twincodeInboundAttributes, mPeerTwincodeOutbound.getId());

                List<AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();

                // Copy a number of twincode attributes from the profile identity.
                if (mIdentityTwincodeOutbound != null) {
                    mTwinmeContextImpl.copySharedTwincodeAttributes(twincodeOutboundAttributes, mIdentityTwincodeOutbound);
                }
                TwinmeAttributes.setTwincodeAttributeName(twincodeOutboundAttributes, mIdentityName);
                if (mCopiedIdentityAvatarId != null) {
                    TwinmeAttributes.setTwincodeAttributeAvatarId(twincodeOutboundAttributes, mCopiedIdentityAvatarId);
                }

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeFactoryService.createTwincode: twincodeFactoryAttributes=" + twincodeFactoryAttributes +
                            "twincodeInboundAttributes=" + twincodeInboundAttributes + "twincodeOutboundAttributes=" + twincodeOutboundAttributes);
                }
                mTwinmeContextImpl.getTwincodeFactoryService().createTwincode(twincodeFactoryAttributes,
                        twincodeInboundAttributes, twincodeOutboundAttributes, null,
                        Contact.SCHEMA_ID, this::onCreateTwincodeFactory);
                return;
            }
            if ((mState & CREATE_TWINCODE_DONE) == 0) {
                return;
            }

            //
            // Step 6: create the contact object.
            //

            if ((mState & CREATE_CONTACT_OBJECT) == 0) {
                mState |= CREATE_CONTACT_OBJECT;

                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mPeerTwincodeOutbound, 332);
                mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_OBJECT, mTwincodeFactory, 333);

                mTwinmeContextImpl.getRepositoryService().createObject(ContactFactory.INSTANCE,
                        RepositoryService.AccessRights.PRIVATE, (RepositoryObject object) -> {
                            String name = mPeerTwincodeOutbound.getName();
                            if (name == null) {
                                mTwinmeContextImpl.assertion(ExecutorAssertPoint.CREATE_CONTACT2_NAME, AssertPoint.create(mPeerTwincodeOutbound));

                                name = mTwinmeContextImpl.getAnonymousName();
                            }
                            Contact contact = (Contact) object;
                            contact.setName(name);
                            contact.setTwincodeFactory(mTwincodeFactory);
                            contact.setPeerTwincodeOutbound(mPeerTwincodeOutbound);
                            contact.setSpace(mSpace);
                            if (mGroupTwincodeId != null && mGroupMemberTwincodeId != null) {
                                contact.setGroupInformation(mGroupTwincodeId, mGroupMemberTwincodeId);
                            }
                        }, this::onCreateObject);
                return;
            }
            if ((mState & CREATE_CONTACT_OBJECT_DONE) == 0) {
                return;
            }
        }

        //
        // Step 7: invoke the peer twincode to send our private identity twincode.
        //

        if (mIdentityTwincodeOutbound != null && mContactTwincodeOutbound != null) {
            if ((mState & INVOKE_TWINCODE_OUTBOUND) == 0) {
                mState |= INVOKE_TWINCODE_OUTBOUND;

                List<AttributeNameValue> attributes;
                attributes = new ArrayList<>();
                PairProtocol.setInvokeTwincodeActionPairBindAttributeTwincodeId(attributes, mContactTwincodeOutbound.getId());
                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.invokeTwincode: peerTwincodeOutboundId=" + mPeerTwincodeOutbound.getId() +
                            " attributes=" + attributes);
                }
                if (mPeerTwincodeOutbound.isSigned()) {
                    // The cipher twincode must be our public identity because we must authenticate and encrypt with a public
                    // key that is trusted by the receiver (that public key is trusted in the process by CreateContactPhase1).
                    // But, we must send information about our contact twincode to give our public key.
                    mTwinmeContextImpl.getTwincodeOutboundService().secureInvokeTwincode(mIdentityTwincodeOutbound,
                            mContactTwincodeOutbound, mPeerTwincodeOutbound,
                            TwincodeOutboundService.INVOKE_URGENT | TwincodeOutboundService.CREATE_SECRET, PairProtocol.ACTION_PAIR_BIND,
                            attributes, this::onInvokeTwincode);
                } else {
                    mTwinmeContextImpl.getTwincodeOutboundService().invokeTwincode(mPeerTwincodeOutbound,
                            TwincodeOutboundService.INVOKE_URGENT, PairProtocol.ACTION_PAIR_BIND,
                            attributes, this::onInvokeTwincode);
                }
                return;
            }
            if ((mState & INVOKE_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }
        }

        //
        // Step 8: delete the invitation object because we don't need it anymore.
        //

        if (mInvitation != null) {
            if ((mState & DELETE_INVITATION) == 0) {
                mState |= DELETE_INVITATION;

                long requestId = newOperation(DELETE_INVITATION);

                mTwinmeContextImpl.deleteInvitation(requestId, mInvitation);
                return;
            }
            if ((mState & DELETE_INVITATION_DONE) == 0) {
                return;
            }
        }

        //
        // Step 8: get the peer thumbnail image so that we have it in our local cache before displaying the notification.
        //

        if (mContact != null && mContact.getAvatarId() != null && !mUnbindContact) {
            if ((mState & GET_PEER_IMAGE) == 0) {
                mState |= GET_PEER_IMAGE;

                mTwinmeContextImpl.getImageService().getImageFromServer(mContact.getAvatarId(), ImageService.Kind.THUMBNAIL, (ErrorCode errorCode, Bitmap avatar) -> {
                    mState |= GET_PEER_IMAGE_DONE;
                    onOperation();
                });
                return;
            }
            if ((mState & GET_PEER_IMAGE_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mContact, 434);
        if (!mContact.checkInvariants()) {
            mTwinmeContextImpl.assertion(ExecutorAssertPoint.CONTACT_INVARIANT, AssertPoint.create(mContact));
        }

        // Post a notification for the new contact (contactPhase2 received asynchronously).
        if (mTwinmeContextImpl.isVisible(mContact)) {
            mTwinmeContextImpl.getNotificationCenter().onNewContact(mContact);
        }

        mTwinmeContextImpl.onCreateContact(mRequestId, mContact);

        if (mUnbindContact) {
            mTwinmeContextImpl.unbindContact(DEFAULT_REQUEST_ID, null, mContact);
        }

        stop();
    }

    private void onCopyImage(ErrorCode status, @Nullable ExportedImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCopyImage: status=" + status + " imageId=" + imageId);
        }

        if (status != ErrorCode.SUCCESS || imageId == null) {
            onOperationError(COPY_IMAGE, status, mIdentityAvatarId.toString());
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
        mContactTwincodeOutbound = twincodeFactory.getTwincodeOutbound();
        onOperation();
    }

    private void onCreateObject(BaseService.ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateObject: object=" + object);
        }

        if (errorCode != ErrorCode.SUCCESS || !(object instanceof Contact)) {

            onOperationError(CREATE_CONTACT_OBJECT, errorCode, null);
            return;
        }

        mState |= CREATE_CONTACT_OBJECT_DONE;

        mContact = (Contact) object;
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

    private void onDeleteInvitation(@NonNull UUID invitationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteInvitation: invitationId=" + invitationId);
        }

        mState |= DELETE_INVITATION_DONE;
        onOperation();
    }

    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        if (operationId == INVOKE_TWINCODE_OUTBOUND) {
            if (errorCode == ErrorCode.ITEM_NOT_FOUND || errorCode == ErrorCode.NO_PRIVATE_KEY
                || errorCode == ErrorCode.INVALID_PUBLIC_KEY || errorCode == ErrorCode.INVALID_PRIVATE_KEY) {

                mState |= INVOKE_TWINCODE_OUTBOUND_DONE;

                mUnbindContact = true;
                onOperation();
                return;
            }
        } else if (operationId == DELETE_INVITATION && errorCode == ErrorCode.ITEM_NOT_FOUND) {
            mState |= DELETE_INVITATION_DONE;
            return;
        }

        super.onOperationError(operationId, errorCode, errorParameter);
    }

    protected void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        mStopped = true;

        mTwinmeContextImpl.acknowledgeInvocation(mInvocation.getId(), ErrorCode.SUCCESS);

        super.stop();
    }
}
