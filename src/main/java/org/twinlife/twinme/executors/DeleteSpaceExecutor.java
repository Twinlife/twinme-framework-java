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
import android.util.Log;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.CallReceiver;
import org.twinlife.twinme.models.CallReceiverFactory;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.ContactFactory;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupFactory;
import org.twinlife.twinme.models.Invitation;
import org.twinlife.twinme.models.InvitationFactory;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.Space;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.3
//
// User foreground operation: must be connected with a timeout if connection does not work.

public class DeleteSpaceExecutor extends AbstractTimeoutTwinmeExecutor {
    private static final String LOG_TAG = "DeleteSpaceExecutor";
    private static final boolean DEBUG = false;

    private static final int GET_CONTACTS = 1;
    private static final int GET_CONTACTS_DONE = 1 << 1;
    private static final int DELETE_CONTACT = 1 << 3;
    private static final int DELETE_CONTACTS_DONE = 1 << 4;
    private static final int GET_GROUPS = 1 << 5;
    private static final int GET_GROUPS_DONE = 1 << 6;
    private static final int DELETE_GROUP = 1 << 8;
    private static final int DELETE_GROUPS_DONE = 1 << 9;
    private static final int GET_INVITATIONS = 1 << 10;
    private static final int GET_INVITATIONS_DONE = 1 << 11;
    private static final int DELETE_INVITATION = 1 << 13;
    private static final int DELETE_INVITATIONS_DONE = 1 << 14;
    private static final int GET_CALL_RECEIVERS = 1 << 15;
    private static final int GET_CALL_RECEIVERS_DONE = 1 << 16;
    private static final int DELETE_CALL_RECEIVER = 1 << 17;
    private static final int DELETE_CALL_RECEIVERS_DONE = 1 << 18;
    private static final int DELETE_PROFILE = 1 << 19;
    private static final int DELETE_PROFILE_DONE = 1 << 20;
    private static final int DELETE_SPACE = 1 << 21;
    private static final int DELETE_SPACE_DONE = 1 << 22;

    private final List<Contact> mContacts = new ArrayList<>();
    private final List<Group> mGroups = new ArrayList<>();
    private final List<Invitation> mInvitations = new ArrayList<>();
    private final List<CallReceiver> mCallReceivers = new ArrayList<>();
    private final Set<UUID> mToDeleteContacts = new HashSet<>();
    private final Set<UUID> mToDeleteGroups = new HashSet<>();
    private final Set<UUID> mToDeleteInvitations = new HashSet<>();
    private final Set<UUID> mToDeleteCallReceivers = new HashSet<>();
    private final Space mSpace;
    private final Filter<RepositoryObject> mFilter;

    public DeleteSpaceExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, Space space) {
        super(twinmeContextImpl, requestId, LOG_TAG, DEFAULT_TIMEOUT);
        if (DEBUG) {
            Log.d(LOG_TAG, "DeleteAccountExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId);
        }

        mSpace = space;
        mFilter = new Filter<>(mSpace);
    }

    @Override
    public void onDeleteContact(long requestId, @NonNull UUID contactId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteContact: requestId=" + requestId + " contactId=" + contactId);
        }

        if (getOperation(requestId) > 0) {
            DeleteSpaceExecutor.this.onDeleteContact(contactId);
        }
    }

    @Override
    public void onDeleteGroup(long requestId, @NonNull UUID groupId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteGroup: requestId=" + requestId + " groupId=" + groupId);
        }

        if (getOperation(requestId) > 0) {
            onDeleteGroup(groupId);
        }
    }

    @Override
    public void onDeleteInvitation(long requestId, @NonNull UUID invitationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteInvitation: requestId=" + requestId + " invitationId=" + invitationId);
        }

        if (getOperation(requestId) > 0) {
            onDeleteInvitation(invitationId);
        }
    }

    @Override
    public void onDeleteCallReceiver(long requestId, @NonNull UUID callReceiverId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteCallReceiver: requestId=" + requestId + " callReceiverId=" + callReceiverId);
        }

        if (getOperation(requestId) > 0) {
            onDeleteCallReceiver(callReceiverId);
            onOperation();
        }
    }

    @Override
    public void onDeleteProfile(long requestId, @NonNull UUID profileId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteProfile: requestId=" + requestId + " profileId=" + profileId);
        }

        if (getOperation(requestId) > 0) {
            onDeleteProfile(profileId);
        }
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;

            // Restart everything!
            mState = 0;
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
        // Step 1: get all contact ids from the repository (we don't want TwinmeContext to filter on the space).
        //

        if ((mState & GET_CONTACTS) == 0) {
            mState |= GET_CONTACTS;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.getObjectIds: schemaId=" + Contact.SCHEMA_ID);
            }
            mTwinmeContextImpl.getRepositoryService().listObjects(ContactFactory.INSTANCE, mFilter, this::onListContacts);
            return;
        }
        if ((mState & GET_CONTACTS_DONE) == 0) {
            return;
        }

        //
        // Step 2: get all group ids from the repository (we don't want TwinmeContext filter on the level).
        //

        if ((mState & GET_GROUPS) == 0) {
            mState |= GET_GROUPS;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.getObjectIds: schemaId=" + Group.SCHEMA_ID);
            }
            mTwinmeContextImpl.getRepositoryService().listObjects(GroupFactory.INSTANCE, mFilter, this::onListGroups);
            return;
        }
        if ((mState & GET_GROUPS_DONE) == 0) {
            return;
        }

        //
        // Step 3: get all invitation ids from the repository (we don't want TwinmeContext filter on the level).
        //

        if ((mState & GET_INVITATIONS) == 0) {
            mState |= GET_INVITATIONS;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.getObjectIds: schemaId=" + Invitation.SCHEMA_ID);
            }
            mTwinmeContextImpl.getRepositoryService().listObjects(InvitationFactory.INSTANCE, mFilter, this::onListInvitations);
            return;
        }
        if ((mState & GET_INVITATIONS_DONE) == 0) {
            return;
        }

        //
        // Step 4: get all call receiver ids from the repository (we don't want TwinmeContext filter on the level).
        //

        if ((mState & GET_CALL_RECEIVERS) == 0) {
            mState |= GET_CALL_RECEIVERS;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.getObjectIds: schemaId=" + CallReceiver.SCHEMA_ID);
            }
            mTwinmeContextImpl.getRepositoryService().listObjects(CallReceiverFactory.INSTANCE, mFilter, this::onGetCallReceiverIds);
            return;
        }
        if ((mState & GET_CALL_RECEIVERS_DONE) == 0) {
            return;
        }

        //
        // Step 5: get and delete every contact.  Because deleting a contact is very slow, run all requests in parallel.
        //

        // The mState is not used but instead we accumulate in mToDeleteContacts a list of contacts we want to be deleted.
        while (!mContacts.isEmpty()) {

            // Pick the last contact id.
            Contact contact = mContacts.remove(mContacts.size() - 1);
            long requestId = newOperation(DELETE_CONTACT);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContext.deleteContact: requestId=" + requestId + " group=" + contact);
            }
            mToDeleteContacts.add(contact.getId());
            new DeleteContactExecutor(mTwinmeContextImpl, requestId, contact, null, 0).start();
        }

        //
        // Step 6: get and delete every group.
        //

        // The mState is not used but instead we accumulate in mToDeleteGroups a list of groups we want to be deleted.
        while (!mGroups.isEmpty()) {

            // Pick the last group.
            Group group = mGroups.remove(mGroups.size() - 1);
            mToDeleteGroups.add(group.getId());
            long requestId = newOperation(DELETE_GROUP);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContext.deleteGroup: requestId=" + requestId + " group=" + group);
            }

            new DeleteGroupExecutor(mTwinmeContextImpl, requestId, group, 0).start();
        }

        //
        // Step 7: get and delete every group.
        //

        // The mState is not used but instead we accumulate in mInvitations a list of groups we want to be deleted.
        while (!mInvitations.isEmpty()) {

            // Pick the last invitation id.
            Invitation invitation = mInvitations.remove(mInvitations.size() - 1);
            long requestId = newOperation(DELETE_INVITATION);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContext.deleteInvitation: requestId=" + requestId + " invitation=" + invitation);
            }

            mToDeleteInvitations.add(invitation.getId());
            new DeleteInvitationExecutor(mTwinmeContextImpl, requestId, invitation, 0).start();
        }

        //
        // Step 8: get and delete every call receiver.
        //

        // The mState is not used but instead we accumulate in mCallReceivers a list of call receivers we want to be deleted.
        while (!mCallReceivers.isEmpty()) {

            // Pick the last group id.
            CallReceiver callReceiver = mCallReceivers.remove(mCallReceivers.size() - 1);
            long requestId = newOperation(DELETE_CALL_RECEIVER);
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContext.deleteCallReceiver: requestId=" + requestId + " callReceiver=" + callReceiver);
            }

            mToDeleteCallReceivers.add(callReceiver.getId());
            mTwinmeContextImpl.deleteCallReceiver(requestId, callReceiver);
        }

        //
        // Step 9: wait fo all contacts and groups to be deleted.
        //
        if (!mToDeleteContacts.isEmpty()) {
            return;
        }
        if (!mToDeleteGroups.isEmpty()) {
            return;
        }
        if (!mToDeleteInvitations.isEmpty()) {
            return;
        }
        if (!mToDeleteCallReceivers.isEmpty()) {
            return;
        }

        //
        // Step 10: delete the profile
        //

        Profile profile = mSpace.getProfile();
        if (profile != null) {

            if ((mState & DELETE_PROFILE) == 0) {
                mState |= DELETE_PROFILE;

                long requestId = newOperation(DELETE_PROFILE);
                if (DEBUG) {
                    Log.d(LOG_TAG, "TwinmeContext.deleteProfile: requestId=" + requestId + " profile=" + profile);
                }
                new DeleteProfileExecutor(mTwinmeContextImpl, requestId, profile, 0).start();
                return;
            }
            if ((mState & DELETE_PROFILE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 11: delete the space object
        //

        if ((mState & DELETE_SPACE) == 0) {
            mState |= DELETE_SPACE;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.deleteObject: objectId=" + mSpace.getId());
            }
            mTwinmeContextImpl.getRepositoryService().deleteObject(mSpace, this::onDeleteObject);
            return;
        }
        if ((mState & DELETE_SPACE_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //
        mTwinmeContextImpl.onDeleteSpace(mRequestId, mSpace.getId());

        stop();
    }

    private void onListContacts(ErrorCode status, @Nullable List<RepositoryObject> objects) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListContacts: status=" + status + " objects=" + objects);
        }

        if (status != ErrorCode.SUCCESS || objects == null) {

            onOperationError(GET_CONTACTS, status, null);
            return;
        }

        mState |= GET_CONTACTS_DONE;

        // Keep and delete only the contacts of the space to delete.
        for (RepositoryObject object : objects) {
            Contact c = (Contact) object;
            if (mSpace.isOwner(c)) {
                mContacts.add(c);
            }
        }
        onOperation();
    }

    private void onDeleteContact(@NonNull UUID contactId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteContact: contact=" + contactId);
        }

        mToDeleteContacts.remove(contactId);
        if (mToDeleteContacts.isEmpty()) {
            mState |= DELETE_CONTACTS_DONE;
        }
        onOperation();
    }

    private void onListGroups(@NonNull ErrorCode errorCode, @Nullable List<RepositoryObject> objects) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListGroups: errorCode=" + errorCode + " objects=" + objects);
        }

        if (errorCode != ErrorCode.SUCCESS || objects == null) {

            onOperationError(GET_GROUPS, errorCode, null);
            return;
        }

        mState |= GET_GROUPS_DONE;

        // Keep and delete only the groups of the space to delete.
        for (RepositoryObject object : objects) {
            Group c = (Group) object;
            if (mSpace.isOwner(c)) {
                mGroups.add(c);
            }
        }
        onOperation();
    }

    private void onDeleteGroup(@NonNull UUID groupId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteGroup: groupId=" + groupId);
        }

        mToDeleteGroups.remove(groupId);
        if (mToDeleteGroups.isEmpty()) {
            mState |= DELETE_GROUPS_DONE;
        }
        onOperation();
    }

    private void onListInvitations(@NonNull ErrorCode errorCode, @Nullable List<RepositoryObject> objects) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListInvitations: errorCode=" + errorCode + " objects=" + objects);
        }

        if (errorCode != ErrorCode.SUCCESS || objects == null) {

            onOperationError(GET_GROUPS, errorCode, null);
            return;
        }

        mState |= GET_INVITATIONS_DONE;

        // Keep and delete only the invitations of the space to delete.
        for (RepositoryObject object : objects) {
            Group c = (Group) object;
            if (mSpace.isOwner(c)) {
                mGroups.add(c);
            }
        }
        onOperation();
    }

    private void onGetCallReceiverIds(@NonNull ErrorCode errorCode, @Nullable List<RepositoryObject> objects) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetCallReceiverIds: errorCode=" + errorCode + " objects=" + objects);
        }

        if (errorCode != ErrorCode.SUCCESS || objects == null) {

            onOperationError(GET_CALL_RECEIVERS, errorCode, null);
            return;
        }

        mState |= GET_CALL_RECEIVERS_DONE;

        // Keep and delete only the invitations of the space to delete.
        for (RepositoryObject object : objects) {
            CallReceiver c = (CallReceiver) object;
            if (mSpace.isOwner(c)) {
                mCallReceivers.add(c);
            }
        }
        onOperation();
    }

    private void onDeleteInvitation(@NonNull UUID invitationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteInvitation: invitationId=" + invitationId);
        }

        mToDeleteInvitations.remove(invitationId);
        if (mToDeleteInvitations.isEmpty()) {
            mState |= DELETE_INVITATIONS_DONE;
        }
        onOperation();
    }

    private void onDeleteCallReceiver(@NonNull UUID callReceiverId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteInvitation: callReceiverId=" + callReceiverId);
        }

        mToDeleteCallReceivers.remove(callReceiverId);
        if (mToDeleteCallReceivers.isEmpty()) {
            mState |= DELETE_CALL_RECEIVERS_DONE;
        }
    }

    private void onDeleteProfile(@NonNull UUID profileId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteProfile: profileId=" + profileId);
        }

        mState |= DELETE_PROFILE_DONE;
        mSpace.setProfile(null);

        mTwinmeContext.setDynamicShortcuts();

        onOperation();
    }

    private void onDeleteObject(@NonNull ErrorCode errorCode, @Nullable UUID objectId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteObject: objectId=" + objectId);
        }

        mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, objectId, mSpace.getId());

        mState |= DELETE_SPACE_DONE;
        onOperation();
    }

    @Override
    protected void onOperationError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // The delete operation succeeds if we get an item not found error.
        if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
            UUID id = Utils.UUIDFromString(errorParameter);
            switch (operationId) {
                case DELETE_CONTACT:
                    if (id != null) {
                        onDeleteContact(id);
                    }
                    return;

                case DELETE_GROUP:
                    if (id != null) {
                        onDeleteGroup(id);
                    }
                    return;

                case DELETE_INVITATION:
                    if (id != null) {
                        onDeleteInvitation(id);
                    }
                    return;

                case DELETE_CALL_RECEIVER:
                    if (id != null) {
                        onDeleteCallReceiver(id);
                    }
                    return;

                case DELETE_PROFILE:
                    if (id != null) {
                        onDeleteProfile(id);
                    }
                    return;

                case DELETE_SPACE:
                    if (id != null) {
                        onDeleteObject(errorCode, id);
                    }
                    return;

                default:
                    break;
            }
        }

        super.onOperationError(operationId, errorCode, errorParameter);
    }
}
