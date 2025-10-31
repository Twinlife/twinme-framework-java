/*
 *  Copyright (c) 2019-2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.services;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.twinlife.twinlife.BaseService.ErrorCode;

import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.Space;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple service to get the contacts and spaces.
 */
public class ContactsTwinmeService extends AbstractTwinmeService {
    private static final String LOG_TAG = "ContactsTwinmeService";
    private static final boolean DEBUG = false;

    protected static final int GET_CONTACTS = 1;
    protected static final int GET_CONTACTS_DONE = 1 << 1;
    protected static final int GET_SPACES = 1 << 2;
    protected static final int GET_SPACES_DONE = 1 << 3;

    @Nullable
    protected Observer mObserver;
    @NonNull
    protected List<Contact> mContacts;
    @NonNull
    protected List<Space> mSpaces;

    public interface Observer {
        void onGetContacts(@NonNull List<Contact> list);

        void onGetSpaces(@NonNull List<Space> list);
    }

    public ContactsTwinmeService(@NonNull TwinmeContext twinmeContext, @Nullable Observer observer) {
        super(twinmeContext, LOG_TAG);

        mContacts = new ArrayList<>();
        mSpaces = new ArrayList<>();
        mObserver = observer;

        start();
    }

    @NonNull
    public List<Contact> getContacts() {

        return mContacts;
    }

    @NonNull
    public List<Space> getSpaces() {

        return mSpaces;
    }

    @NonNull
    public List<Profile> getProfiles() {

        List<Profile> result = new ArrayList<>();
        for (Space space : mSpaces) {
            Profile profile = space.getProfile();
            if (profile != null) {
                result.add(profile);
            }
        }
        return result;
    }

    @Override
    public void onCreateSpace(long requestId, @NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateSpace requestId=" + requestId + " space=" + space);
        }

        mSpaces.add(space);
    }

    @Override
    public void onError(long requestId, @NonNull ErrorCode errorCode, String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

    }

    @Override
    public void onCreateContact(long requestId, @NonNull Contact contact) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateContact");
        }

        mContacts.add(contact);
    }

    @Override
    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        if (!mIsReady) {
            return;
        }

        if ((mState & GET_SPACES) == 0) {
            mState |= GET_SPACES;

            long requestId = newOperation(GET_SPACES);
            mTwinmeContext.findSpaces(requestId, (Space space) -> { return true; }, this::onGetSpaces);
        }
        if ((mState & GET_SPACES_DONE) == 0) {
            return;
        }

        if ((mState & GET_CONTACTS) == 0) {
            mState |= GET_CONTACTS;

            long requestId = newOperation(GET_CONTACTS);
            mTwinmeContext.findContacts(requestId, (Contact contact) -> { return true; }, this::onGetContacts);
        }
        if ((mState & GET_CONTACTS_DONE) == 0) {
            return;
        }

        // No more action, we are done now and we can terminate.
    }

    private void onGetContacts(@NonNull List<Contact> contacts) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetContacts");
        }

        mState |= GET_CONTACTS_DONE;
        mContacts = contacts;

        if (mObserver != null) {
            mObserver.onGetContacts(contacts);
        }
        onOperation();
    }

    private void onGetSpaces(@NonNull List<Space> spaces) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetSpaces");
        }

        mState |= GET_SPACES_DONE;
        mSpaces = spaces;

        if (mObserver != null) {
            mObserver.onGetSpaces(spaces);
        }
        onOperation();
    }
}
