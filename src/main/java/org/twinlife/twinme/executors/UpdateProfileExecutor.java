/*
 *  Copyright (c) 2015-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.util.TwinmeAttributes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.11
//
// User foreground operation: must be connected with a timeout if connection does not work.

public class UpdateProfileExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "UpdateProfileExecutor";
    private static final boolean DEBUG = false;

    private static final int CREATE_IMAGE = 1;
    private static final int CREATE_IMAGE_DONE = 1 << 1;
    private static final int UPDATE_TWINCODE_OUTBOUND = 1 << 2;
    private static final int UPDATE_TWINCODE_OUTBOUND_DONE = 1 << 3;
    private static final int GET_CONTACTS = 1 << 4;
    private static final int GET_CONTACTS_DONE = 1 << 5;
    private static final int GET_GROUPS = 1 << 6;
    private static final int GET_GROUPS_DONE = 1 << 7;
    private static final int UPDATE_CONTACT = 1 << 8;
    private static final int UPDATE_CONTACT_DONE = 1 << 9;
    private static final int UPDATE_GROUP = 1 << 10;
    private static final int UPDATE_GROUP_DONE = 1 << 11;
    private static final int DELETE_OLD_IMAGE = 1 << 12;
    private static final int DELETE_OLD_IMAGE_DONE = 1 << 13;

    @NonNull
    private final Profile mProfile;
    private final Space mSpace;
    @NonNull
    private final String mName;
    @NonNull
    private final String mOldName;
    @Nullable
    private final String mOldDescription;
    @Nullable
    private final Bitmap mAvatar;
    @Nullable
    private final File mAvatarFile;
    @Nullable
    private ExportedImageId mAvatarId;
    @Nullable
    private final ImageId mOldAvatarId;
    private final boolean mUpdateIdentity;
    private final boolean mUpdateDescription;
    private final boolean mUpdateName;
    private final TwincodeOutbound mTwincodeOutbound;
    private final boolean mCreateImage;
    @Nullable
    private final String mDescription;
    @Nullable
    private final String mCapabilities;
    @Nullable
    private List<Contact> mContacts;
    @Nullable
    private List<Group> mGroups;
    @NonNull
    private final Profile.UpdateMode mUpdateMode;
    @Nullable
    private Map<ImageId, ImageId> mImageMap;

    public UpdateProfileExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Profile profile,
                                 @NonNull Profile.UpdateMode updateMode,
                                 @NonNull String name, @Nullable Bitmap avatar, @Nullable File avatarFile,
                                 @Nullable String description, @Nullable Capabilities capabilities) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateProfileExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId +
                    " profile=" + profile + " name=" + name + " avatar=" + avatar + " description=" + description + " capabilities=" + capabilities);
        }

        mProfile = profile;
        mSpace = profile.getSpace();
        mOldName = profile.getName();
        mOldDescription = (profile.getDescription() == null) ? "" : profile.getDescription();
        mName = name;
        mAvatar = avatar;
        mAvatarFile = avatarFile;
        mOldAvatarId = profile.getAvatarId();
        mDescription = description;
        mCapabilities = capabilities == null ? null : capabilities.toAttributeValue();
        mUpdateMode = updateMode;
        if (mUpdateMode == Profile.UpdateMode.NONE) {
            mState |= GET_CONTACTS | GET_CONTACTS_DONE | GET_GROUPS | GET_GROUPS_DONE
                    | UPDATE_CONTACT | UPDATE_CONTACT_DONE | UPDATE_GROUP | UPDATE_GROUP_DONE;
        }

        mCreateImage = avatarFile != null;

        // Check if one of the twincode attributes is modified.
        mUpdateDescription = !Utils.equals(description, mProfile.getDescription());
        boolean updateCapabilities = !Utils.equals(mCapabilities, mProfile.getIdentitiyCapabilities().toAttributeValue());
        mUpdateName = !Utils.equals(mName, mProfile.getName());
        mUpdateIdentity = mUpdateName || mCreateImage || mUpdateDescription | updateCapabilities;
        mTwincodeOutbound = mProfile.getTwincodeOutbound();
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

    @Override
    public void onUpdateContact(long requestId, @NonNull Contact contact) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateContact requestId=" + requestId + " contact=" + contact);
        }

        if (getOperation(requestId) > 0) {
            mState |= UPDATE_CONTACT_DONE;
            if (mContacts != null) {
                mContacts.remove(contact);
                if (!mContacts.isEmpty()) {
                    mState &= ~(UPDATE_CONTACT | UPDATE_CONTACT_DONE);
                }
            }
            onOperation();
        }
    }

    @Override
    public void onUpdateGroup(long requestId, @NonNull Group group) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateGroup requestId=" + requestId + " group=" + group);
        }

        if (getOperation(requestId) > 0) {
            mState |= UPDATE_GROUP_DONE;
            if (mGroups != null) {
                mGroups.remove(group);
                if (!mGroups.isEmpty()) {
                    mState &= ~(UPDATE_GROUP | UPDATE_GROUP_DONE);
                }
            }
            onOperation();
        }
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
        // Step 2: update the name and avatar.
        //
        if (mUpdateIdentity && mTwincodeOutbound != null) {

            if ((mState & UPDATE_TWINCODE_OUTBOUND) == 0) {
                mState |= UPDATE_TWINCODE_OUTBOUND;

                List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
                if (!mName.equals(mProfile.getName())) {
                    TwinmeAttributes.setTwincodeAttributeName(attributes, mName);
                }
                if (mAvatarId != null) {
                    TwinmeAttributes.setTwincodeAttributeAvatarId(attributes, mAvatarId);
                }
                if (mDescription != null) {
                    TwinmeAttributes.setTwincodeAttributeDescription(attributes, mDescription);
                }
                if (mCapabilities != null) {
                    TwinmeAttributes.setCapabilities(attributes, mCapabilities);
                }

                if (DEBUG) {
                    Log.d(LOG_TAG, "TwincodeOutboundService.updateTwincode: mTwincodeOutbound=" + mTwincodeOutbound);
                }
                mTwinmeContextImpl.getTwincodeOutboundService().updateTwincode(mTwincodeOutbound, attributes, null, this::onUpdateTwincodeOutbound);
                return;
            }
            if ((mState & UPDATE_TWINCODE_OUTBOUND_DONE) == 0) {
                return;
            }

            // Get the list of contacts for which we must propagate the identity change.
            if ((mState & GET_CONTACTS) == 0) {
                mState |= GET_CONTACTS;

                mImageMap = mTwinmeContextImpl.getImageService().listCopiedImages();
                Filter<RepositoryObject> filter = new Filter<RepositoryObject>(mSpace) {
                    @Override
                    public boolean accept(@NonNull RepositoryObject object) {

                        if (!(object instanceof Contact)) {
                            return false;
                        }

                        final Contact contact = (Contact) object;

                        // Don't update revoked contacts.
                        if (!contact.hasPeer()) {
                            return false;
                        }

                        if (mUpdateMode == Profile.UpdateMode.ALL) {
                            return true;
                        }

                        // Don't update if name or description are different.
                        if (mUpdateName && !Utils.equals(mOldName, contact.getIdentityName())) {
                            return false;
                        }
                        if (mUpdateDescription && !Utils.equals(mOldDescription, contact.getIdentityDescription())) {
                            return false;
                        }

                        ImageId contactIdentityAvatarId = contact.getIdentityAvatarId();
                        if (mAvatarId == null || contactIdentityAvatarId == null || mImageMap == null) {
                            // Profile image is not changed but name and description must be propagated.
                            return true;
                        }

                        contactIdentityAvatarId = mImageMap.get(contactIdentityAvatarId);
                        return Utils.equals(mAvatarId, contactIdentityAvatarId) || (mOldAvatarId != null && Utils.equals(mOldAvatarId, contactIdentityAvatarId));
                    }

                };
                mTwinmeContextImpl.findContacts(filter, this::onListContacts);
                // Continue to get the groups.
            }
            if ((mState & GET_GROUPS) == 0) {
                mState |= GET_GROUPS;
                Filter<RepositoryObject> filter = new Filter<RepositoryObject>(mSpace) {
                    public boolean accept(@NonNull RepositoryObject object) {

                        if (!(object instanceof Group)) {
                            return false;
                        }

                        final Group group = (Group) object;

                        // Don't update a group if we are leaving or it is being deleted.
                        if (group.isLeaving() || group.isDeleted()) {
                            return false;
                        }
                        if (mUpdateMode == Profile.UpdateMode.ALL) {
                            return true;
                        }

                        if (mUpdateName && !Utils.equals(mOldName, group.getIdentityName())) {
                            return false;
                        }

                        ImageId groupIdentityAvatarId = group.getIdentityAvatarId();
                        if (mAvatarId == null || groupIdentityAvatarId == null || mImageMap == null) {
                            return true;
                        }

                        groupIdentityAvatarId = mImageMap.get(groupIdentityAvatarId);
                        return Utils.equals(mAvatarId, groupIdentityAvatarId) || (mOldAvatarId != null && Utils.equals(mOldAvatarId, groupIdentityAvatarId));
                    }
                };
                mTwinmeContextImpl.findGroups(filter, this::onListGroups);
                return;
            }
            if ((mState & GET_CONTACTS_DONE) == 0) {
                return;
            }
            if ((mState & GET_GROUPS_DONE) == 0) {
                return;
            }

            // Propagate the name, description and image if it was created on the contact's identity.
            if (mContacts != null) {
                if ((mState & UPDATE_CONTACT) == 0) {
                    mState |= UPDATE_CONTACT;

                    Contact c = mContacts.get(0);
                    ImageId contactIdentityAvatarId = c.getIdentityAvatarId();
                    ImageId updateAvatarId;
                    // If the contact's image does not match the profile, update it from the profile avatar id.
                    if (contactIdentityAvatarId == null || mImageMap == null
                            || (mAvatarId != null && !Utils.equals(mAvatarId, mImageMap.get(contactIdentityAvatarId)))) {
                        updateAvatarId = mAvatarId;
                    } else {
                        updateAvatarId = null;
                    }
                    long requestId = newOperation(UPDATE_CONTACT);
                    new UpdateContactAndIdentityExecutor(mTwinmeContextImpl, requestId,
                            c, mUpdateName ? mName : c.getIdentityName(), updateAvatarId,
                            mUpdateDescription ? mDescription : c.getIdentityDescription(),
                            c.getIdentityCapabilities(), null, 0).start();
                    return;
                }
                if ((mState & UPDATE_CONTACT_DONE) == 0) {
                    return;
                }
            }

            // Propagate the name, description and image if it was created on the group's identity.
            if (mGroups != null) {
                if ((mState & UPDATE_GROUP) == 0) {
                    mState |= UPDATE_GROUP;

                    Group g = mGroups.get(0);
                    ImageId groupIdentityAvatarId = g.getIdentityAvatarId();
                    ImageId updateAvatarId;
                    // If the group's image does not match the profile, update it from the profile avatar id.
                    if (groupIdentityAvatarId == null || mImageMap == null
                            || (mAvatarId != null && !Utils.equals(mAvatarId, mImageMap.get(groupIdentityAvatarId)))) {
                        updateAvatarId = mAvatarId;
                    } else {
                        updateAvatarId = null;
                    }
                    long requestId = newOperation(UPDATE_GROUP);
                    new UpdateGroupExecutor(mTwinmeContextImpl, requestId, g, mUpdateName ? mName : g.getIdentityName(),
                            updateAvatarId, mUpdateDescription ? mDescription : g.getIdentityDescription(),
                            g.getIdentityCapabilities(), 0).start();
                    return;
                }
                if ((mState & UPDATE_GROUP_DONE) == 0) {
                    return;
                }
            }
        }

        //
        // Step 3: delete the old avatar image.
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

        mTwinmeContextImpl.assertNotNull(ExecutorAssertPoint.NULL_RESULT, mProfile, 427);

        mTwinmeContextImpl.onUpdateProfile(mRequestId, mProfile);

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

        mProfile.setTwincodeOutbound(twincodeOutbound);
        onOperation();
    }

    private void onListContacts(List<Contact> list) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListContacts: list=" + list);
        }

        mState |= GET_CONTACTS_DONE;
        if (!list.isEmpty()) {
            mContacts = list;
        } else {
            mState |= UPDATE_CONTACT | UPDATE_CONTACT_DONE;
        }
        onOperation();
    }

    private void onListGroups(List<Group> list) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListGroups: list=" + list);
        }

        mState |= GET_GROUPS_DONE;
        if (!list.isEmpty()) {
            mGroups = list;
        } else {
            mState |= UPDATE_GROUP | UPDATE_GROUP_DONE;
        }
        onOperation();
    }

    private void onDeleteImage(ErrorCode status, @Nullable ImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteImage: status=" + status + " imageId=" + imageId);
        }

        // Ignore the error and proceed!!!
        mState |= DELETE_OLD_IMAGE_DONE;
        onOperation();
    }

    @Override
    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperationError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        if (operationId == UPDATE_CONTACT) {
            mState |= UPDATE_CONTACT_DONE;
            if (mContacts != null) {
                mContacts.remove(0);
                if (!mContacts.isEmpty()) {
                    mState &= ~(UPDATE_CONTACT | UPDATE_CONTACT_DONE);
                }
            }
            return;
        }

        if (operationId == UPDATE_GROUP) {
            mState |= UPDATE_GROUP_DONE;
            if (mGroups != null) {
                mGroups.remove(0);
                if (!mGroups.isEmpty()) {
                    mState &= ~(UPDATE_GROUP | UPDATE_GROUP_DONE);
                }
            }
            return;
        }

        super.onOperationError(operationId, errorCode, errorParameter);
    }
}
