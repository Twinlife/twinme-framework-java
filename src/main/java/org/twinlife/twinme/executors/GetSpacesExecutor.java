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
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.ContactFactory;
import org.twinlife.twinme.models.GroupFactory;
import org.twinlife.twinme.models.InvitationFactory;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.ProfileFactory;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.models.SpaceFactory;
import org.twinlife.twinme.models.SpaceSettings;
import org.twinlife.twinme.models.SpaceSettingsFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

//
// All observers are running in the SingleThreadExecutor provided by the twinlife library
// All observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.3
//

public class GetSpacesExecutor extends Executor {
    private static final String LOG_TAG = "GetSpacesExecutor";
    private static final boolean DEBUG = false;

    private static final int GET_SPACE_SETTINGS = 1;
    private static final int GET_SPACE_SETTINGS_DONE = 1 << 1;
    private static final int GET_SPACES = 1 << 2;
    private static final int GET_SPACES_DONE = 1 << 3;
    private static final int GET_PROFILES = 1 << 4;
    private static final int GET_PROFILES_DONE = 1 << 5;
    private static final int CREATE_SPACE = 1 << 6;
    private static final int CREATE_SPACE_DONE = 1 << 7;
    private static final int UPDATE_PROFILE = 1 << 9;
    private static final int UPDATE_PROFILE_DONE = 1 << 10;

    private class TwinmeContextObserver extends Executor.TwinmeContextObserver {

        @Override
        public void onCreateSpace(long requestId, @NonNull Space space) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwinmeContextObserver.onCreateSpace: requestId=" + requestId + " space=" + space);
            }

            if (mRequestIds.remove(requestId) != null) {
                GetSpacesExecutor.this.onCreateSpace(space);
                onOperation();
            }
        }
    }

    private Space mDefaultSpace;
    private final List<Space> mSpaces = new ArrayList<>();
    private final List<Profile> mProfiles = new ArrayList<>();
    private final TwinmeContextObserver mTwinmeContextObserver;
    private final boolean mEnableSpaces;
    @Nullable
    private List<Profile> mUpdatedProfiles;

    public GetSpacesExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, boolean enableSpaces) {
        super(twinmeContextImpl, requestId, "getSpaces");
        if (DEBUG) {
            Log.d(LOG_TAG, "GetSpacesExecutor: twinmeContextImpl=" + twinmeContextImpl
                    + " requestId=" + requestId);
        }

        mTwinmeContextObserver = new TwinmeContextObserver();
        mEnableSpaces = enableSpaces;
    }

    @Override
    public void start() {
        if (DEBUG) {
            Log.d(LOG_TAG, "start");
        }

        mTwinmeContextImpl.setObserver(mTwinmeContextObserver);
    }

    //
    // Private methods
    //

    @Override
    protected void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;

            if ((mState & GET_SPACES) != 0 && (mState & GET_SPACES_DONE) == 0) {
                mState &= ~GET_SPACES;
            }
            if ((mState & GET_PROFILES) != 0 && (mState & GET_PROFILES_DONE) == 0) {
                mState &= ~GET_PROFILES;
            }
        }
    }

    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        if (mStopped) {

            return;
        }

        //
        // Step 1: load the space settings first.
        //
        if ((mState & GET_SPACE_SETTINGS) == 0) {
            mState |= GET_SPACE_SETTINGS;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.listObjects: schemaId=" + SpaceSettings.SCHEMA_ID);
            }
            mTwinmeContextImpl.getRepositoryService().listObjects(SpaceSettingsFactory.INSTANCE, null, this::onListSpaceSettings);
            return;
        }
        if ((mState & GET_SPACE_SETTINGS_DONE) == 0) {
            return;
        }

        //
        // Step 2: get the list of spaces.
        //
        if ((mState & GET_SPACES) == 0) {
            mState |= GET_SPACES;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.listObjects: schemaId=" + Space.SCHEMA_ID);
            }
            mTwinmeContextImpl.getRepositoryService().listObjects(SpaceFactory.INSTANCE, null, this::onListSpaces);
            return;
        }
        if ((mState & GET_SPACES_DONE) == 0) {
            return;
        }

        //
        // Step 3: get the list of profiles.
        //
        if ((mState & GET_PROFILES) == 0) {
            mState |= GET_PROFILES;

            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryService.listObjects: schemaId=" + Profile.SCHEMA_ID);
            }
            mTwinmeContextImpl.getRepositoryService().listObjects(ProfileFactory.INSTANCE, null, this::onListProfiles);
            return;
        }
        if ((mState & GET_PROFILES_DONE) == 0) {
            return;
        }

        //
        // Step 5: create the default space.
        //
        if (mDefaultSpace == null && !mProfiles.isEmpty()) {

            if ((mState & CREATE_SPACE) == 0) {
                Profile profile = getDefaultProfile();

                mState |= CREATE_SPACE;

                long requestId = newOperation(CREATE_SPACE);
                if (DEBUG) {
                    Log.d(LOG_TAG, "TwinmeContext.createSpace: requestId=" + requestId + " name=" + profile.getName());
                }

                // Create the space using the default settings.
                SpaceSettings settings = mTwinmeContextImpl.getDefaultSpaceSettings();
                mTwinmeContextImpl.createDefaultSpace(requestId, settings, profile);
                return;
            }
            if ((mState & CREATE_SPACE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 6: check that each profile has a space.
        //
        if (!mProfiles.isEmpty()) {

            if ((mState & CREATE_SPACE) == 0) {
                mState |= CREATE_SPACE;

                Profile profile = mProfiles.get(0);
                long requestId = newOperation(CREATE_SPACE);
                if (DEBUG) {
                    Log.d(LOG_TAG, "TwinmeContext.createSpace: requestId=" + requestId + " name=" + profile.getName());
                }

                // Create the space using the default settings.
                SpaceSettings settings = mTwinmeContextImpl.getDefaultSpaceSettings();

                settings.setName(profile.getName());
                mTwinmeContextImpl.createSpace(requestId, settings, profile);
                return;
            }
            if ((mState & CREATE_SPACE_DONE) == 0) {
                return;
            }
        }

        //
        // Step 3: get the list of profiles.
        //
        if (mUpdatedProfiles != null) {
            if ((mState & UPDATE_PROFILE) == 0) {
                mState |= UPDATE_PROFILE;

                if (DEBUG) {
                    Log.d(LOG_TAG, "RepositoryService.updateObject: schemaId=" + Profile.SCHEMA_ID);
                }
                mTwinmeContextImpl.getRepositoryService().updateObject(mUpdatedProfiles.get(0), this::onUpdateProfile);
                return;
            }
            if ((mState & UPDATE_PROFILE_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mTwinmeContextImpl.onGetSpaces(mSpaces);

        stop();
    }

    private void onListSpaceSettings(@NonNull ErrorCode errorCode, @Nullable List<RepositoryObject> objects) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListSpaceSettings: errorCode=" + errorCode + " objectIds=" + objects);
        }

        // Ignore errors as well as the list: the space settings are now loaded in the database service cache.
        mState |= GET_SPACE_SETTINGS_DONE;
        onOperation();
    }

    private void onListProfiles(ErrorCode errorCode, @Nullable List<RepositoryObject> objects) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListProfiles: errorCode=" + errorCode + " objectIds=" + objects);
        }

        if (errorCode != ErrorCode.SUCCESS || objects == null) {

            onError(GET_PROFILES, errorCode, null);
            return;
        }
        mState |= GET_PROFILES_DONE;

        // Profiles have been associated with spaces but there could remain some orphaned profiles.
        // For twinme, it was possible to use several profiles per space.
        final Map<UUID, Profile> profiles = new HashMap<>();
        for (RepositoryObject object : objects) {
            Profile profile = (Profile) object;
            profiles.put(profile.getId(), profile);
            if (profile.getSpace() == null) {
                // Find a space that could reference the profile.
                // If we find it, we must save the profile to keep the link to the space in the database.
                for (Space space : mSpaces) {
                    if (profile.getId().equals(space.getProfileId())) {
                        space.setProfile(profile);
                        profile.setSpace(space);
                        if (mUpdatedProfiles == null) {
                            mUpdatedProfiles = new ArrayList<>();
                        }
                        mUpdatedProfiles.add(profile);
                        break;
                    }
                }
                if (profile.getSpace() == null) {
                    mProfiles.add(profile);
                }
            } else if (profile.getId().equals(profile.getSpace().getProfileId())) {
                // The space must be linked to this profile (see Profile.setOwner).
                mTwinmeContextImpl.assertEqual(ExecutorAssertPoint.INVALID_SUBJECT, profile, profile.getSpace().getProfile());
            }
        }

        if (!mProfiles.isEmpty() && !mEnableSpaces && mDefaultSpace != null) {
            for (int i = mProfiles.size() - 1; i >= 0; i--) {
                Profile profile = mProfiles.remove(i);
                profile.setSpace(mDefaultSpace);

                if (mUpdatedProfiles == null) {
                    mUpdatedProfiles = new ArrayList<>();
                }
                mUpdatedProfiles.add(profile);
            }
        }

        // Same issue as on iOS (bug Twinme/twinme-framework-ios#28), a profile could be used by several spaces.
        // Identify the profiles which are shared between several spaces.
        // Because the profile now links to the space in the database, it could have been associated
        // with one of these duplicate space, and may be not the correct one after the migration.
        // - step1: find profiles without a space.
        // - step2: drop/ignore spaces
        if (!profiles.isEmpty() && mSpaces.size() > 1) {
            checkProfiles(profiles);
        }
        onOperation();
    }

    private void checkProfiles(@NonNull Map<UUID, Profile> profiles) {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkProfiles");
        }

        // Step 1: identify profiles used by the same space.
        Map<UUID, List<Space>> usedProfiles = new HashMap<>();
        Map<UUID, List<Space>> duplicateSpaces = null;
        for (Space space : mSpaces) {
            UUID profileId = space.getProfileId();
            if (profileId != null) {
                List<Space> list = usedProfiles.get(profileId);
                if (list == null) {
                    list = new ArrayList<>();
                    usedProfiles.put(profileId, list);
                } else {
                    if (duplicateSpaces == null) {
                        duplicateSpaces = new HashMap<>();
                    }
                    if (!duplicateSpaces.containsKey(profileId)) {
                        duplicateSpaces.put(profileId, list);
                    }
                }
                list.add(space);
            }
        }

        if (duplicateSpaces != null) {
            for (List<Space> list : duplicateSpaces.values()) {
                for (Space space : list) {
                    if (space == mDefaultSpace && space.getProfile() == null) {
                        Profile profile = profiles.get(space.getProfileId());
                        if (profile != null) {
                            if (profile.getSpace() != null) {
                                profile.getSpace().setProfile(null);
                            }
                            profile.setSpace(space);
                            space.setProfile(profile);
                            if (mUpdatedProfiles == null) {
                                mUpdatedProfiles = new ArrayList<>();
                            }
                            mUpdatedProfiles.add(profile);
                        }
                    }
                }
            }
        }
    }

    private void onListSpaces(ErrorCode errorCode, @Nullable List<RepositoryObject> objects) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListSpaces: errorCode=" + errorCode);
        }

        if (errorCode != ErrorCode.SUCCESS || objects == null) {

            onError(GET_SPACES, errorCode, null);
            return;
        }

        mState |= GET_SPACES_DONE;

        final UUID defaultSpaceId = mTwinmeContextImpl.getDefaultSpaceId();
        for (RepositoryObject object : objects) {
            final Space space = (Space) object;
            mSpaces.add(space);
            if (space.getId().equals(defaultSpaceId)) {
                mDefaultSpace = space;
            }
        }

        // Make sure we know a current space.
        if (mDefaultSpace == null && !mSpaces.isEmpty()) {
            mDefaultSpace = mSpaces.get(0);
        }

        onOperation();
    }

    private void onCreateSpace(@NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateSpace: space=" + space);
        }

        if ((mState & CREATE_SPACE_DONE) != 0) {

            return;
        }
        mState |= CREATE_SPACE_DONE;

        // Take into account every profile associated with this space.  There should be only one
        // except for the migration of some users which could have several profiles for the same space.
        for (int i = mProfiles.size(); i > 0; ) {
            i--;

            Profile profile = mProfiles.get(i);
            if (space.getProfile() == profile) {
                // When spaces are disabled, we can have multiple profiles per space:
                // remove them since they are assigned to this space.
                if (!mEnableSpaces) {
                    mProfiles.remove(i);
                }
                profile.setSpace(space);
                if (space.getProfile() == null || space.getProfile().getPriority() < profile.getPriority()) {
                    space.setProfile(profile);
                }
                if (mUpdatedProfiles == null) {
                    mUpdatedProfiles = new ArrayList<>();
                }
                mUpdatedProfiles.add(profile);
            } else if (!mEnableSpaces) {
                mProfiles.remove(i);
                profile.setSpace(space);
                if (mUpdatedProfiles == null) {
                    mUpdatedProfiles = new ArrayList<>();
                }
                mUpdatedProfiles.add(profile);
            }
        }

        if (mEnableSpaces) {
            Profile profile = space.getProfile();
            if (profile != null) {
                mProfiles.remove(profile);
            }
        }
        if (mTwinmeContextImpl.isDefaultSpace(space)) {
            mDefaultSpace = space;
            mTwinmeContextImpl.getRepositoryService().setOwner(ContactFactory.INSTANCE, space);
            mTwinmeContextImpl.getRepositoryService().setOwner(GroupFactory.INSTANCE, space);
            mTwinmeContextImpl.getRepositoryService().setOwner(InvitationFactory.INSTANCE, space);
        }
        mSpaces.add(space);
        if (!mProfiles.isEmpty()) {
            mState &= ~CREATE_SPACE;
            mState &= ~CREATE_SPACE_DONE;
        }
    }

    private void onUpdateProfile(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateProfile: errorCode=" + errorCode + " object=" + object);
        }

        mState |= UPDATE_PROFILE_DONE;
        if (mUpdatedProfiles != null) {
            mUpdatedProfiles.remove(0);
            if (!mUpdatedProfiles.isEmpty()) {
                mState &= ~(UPDATE_PROFILE | UPDATE_PROFILE_DONE);
            }
        }
        onOperation();
    }

    private Profile getDefaultProfile() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDefaultProfile");
        }

        Profile result = null;
        for (Profile profile : mProfiles) {
            if ((result == null || result.getPriority() < profile.getPriority())) {
                result = profile;
            }
        }
        return result;
    }

    protected void onError(int operationId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: operationId=" + operationId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        // Wait for reconnection
        if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
            if (!TwinmeContextImpl.GET_REMOTE_OBJECTS) {
                if (operationId == GET_PROFILES) {
                    // If we get the Offline error on the getObjectIds operation, the local database does not contain any profile.
                    // We MUST proceed with the space objects because a space may exist without a profile.
                    mState |= GET_PROFILES_DONE;

                    return;
                }
                if (operationId == GET_SPACES && mProfiles.isEmpty()) {
                    // If we get the Offline error on the getObjectIds operation, the local database does not contain any space.
                    mTwinmeContextImpl.onGetSpaces(mSpaces);

                    stop();
                    return;
                }
            }
            mRestarted = true;

            return;
        }

        super.onError(operationId, errorCode, errorParameter);
    }

    @Override
    protected void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        mTwinmeContextImpl.removeObserver(mTwinmeContextObserver);

        super.stop();
    }
}
