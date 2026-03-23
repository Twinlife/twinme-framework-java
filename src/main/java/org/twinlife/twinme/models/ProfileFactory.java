/*
 *  Copyright (c) 2023-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme.models;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryImportService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.TwinlifeContext;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContext;

import java.util.List;
import java.util.UUID;

/**
 * Factory used by the RepositoryService to create Profile object.
 */
public class ProfileFactory extends TwinmeObjectFactory implements RepositoryObjectFactory<Profile> {
    private static final boolean DEBUG = false;
    private static final String LOG_TAG = "ProfileFactory";

    public static final ProfileFactory INSTANCE = new ProfileFactory();

    @Override
    @NonNull
    public Profile createObject(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                                long creationDate, @Nullable String name, @Nullable String description,
                                @Nullable List<BaseService.AttributeNameValue> attributes,
                                long modificationDate) {

        return new Profile(identifier, uuid, creationDate, name, description, attributes, modificationDate);
    }

    @Override
    public void loadObject(@NonNull Profile object, String name, String description,
                           @Nullable List<BaseService.AttributeNameValue> attributes, long modificationDate) {

        object.update(name, description, attributes, modificationDate);
    }

    @Override
    @Nullable
    public Profile importObject(@NonNull RepositoryImportService upgradeService,
                                @NonNull DatabaseIdentifier identifier, @NonNull UUID uuid, @Nullable UUID key,
                                long creationDate, @NonNull List<BaseService.AttributeNameValue> attributes) {
        String name = null, description = null;
        UUID twincodeInboundId = key, twincodeOutboundId = null, twincodeFactoryId = null, spaceId = null;
        for (BaseService.AttributeNameValue attribute : attributes) {
            switch (attribute.name) {
                case "name":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        name = (String) attribute.value;
                    }
                    break;

                case "description":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        description = (String) attribute.value;
                    }
                    break;

                case "twincodeInboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        twincodeInboundId = Utils.UUIDFromString((String) attribute.value);
                    }
                    break;

                case "twincodeOutboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        twincodeOutboundId = Utils.UUIDFromString((String) attribute.value);
                    }
                    break;

                case "twincodeFactoryId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        twincodeFactoryId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

                case "spaceId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        spaceId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;
            }
        }

        // When we migrate to V20 (or import from the server), the Profile repository object is not linked to the Space because
        // we don't have the spaceId.
        // 5 attributes: name, description, twincodeInboundId, twincodeFactoryId, twincodeOutboundId, spaceId are mapped to repository
        // columns and they are dropped.  The Profile object will be updated by GetSpacesExecutor if necessary.
        final Profile profile = new Profile(identifier, uuid, creationDate, name, description, attributes, creationDate);
        upgradeService.importObject(profile, twincodeFactoryId, twincodeInboundId, twincodeOutboundId, null, spaceId);
        return profile;
    }

    @Override
    public void syncObject(@NonNull TwinlifeContext twinlifeContext, @NonNull RepositoryObject profile, @NonNull Consumer<RepositoryObject> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "syncObject: twinlifeContext=" + twinlifeContext + " profile=" + profile + " consumer=" + consumer);
        }

        if (!(profile instanceof Profile)) {
            Log.e(LOG_TAG, "object is not a profile: " + profile);
            consumer.onGet(BaseService.ErrorCode.BAD_REQUEST, profile);
            return;
        }

        if (profile.getTwincodeOutbound() == null) {
            Log.e(LOG_TAG, "Profile has no twincodeOutbound: " + profile);
            if (twinlifeContext instanceof TwinmeContext) {
                ((TwinmeContext) twinlifeContext).changeProfileTwincode((Profile) profile, (status, updatedProfile) -> {
                    if (status != BaseService.ErrorCode.SUCCESS) {
                        Log.e(LOG_TAG, "Error changing profile twincode: " + status);
                    } else if (DEBUG) {
                        Log.d(LOG_TAG, "Successfully changed twincode for profile " + profile);
                    }
                    consumer.onGet(status, updatedProfile);
                });
            }
            return;
        }

        twinlifeContext.getTwincodeOutboundService().updateTwincode(profile.getTwincodeOutbound(), profile.getTwincodeOutbound().getAttributes(), null, (errorCode, twincode) -> {
            if (errorCode == BaseService.ErrorCode.ITEM_NOT_FOUND || errorCode == BaseService.ErrorCode.EXPIRED) {
                if (twinlifeContext instanceof TwinmeContext) {
                    ((TwinmeContext) twinlifeContext).changeProfileTwincode((Profile) profile, (status, updatedProfile) -> {
                        if (status != BaseService.ErrorCode.SUCCESS) {
                            Log.e(LOG_TAG, "Error changing profile twincode: " + status);
                        } else if (DEBUG) {
                            Log.d(LOG_TAG, "Successfully changed twincode for profile " + profile);
                        }
                        consumer.onGet(status, updatedProfile);
                    });
                }
                return;
            } else if (errorCode != BaseService.ErrorCode.SUCCESS) {
                Log.e(LOG_TAG, "Error updating twincode: " + errorCode + " for profile: " + profile);
            } else if (DEBUG) {
                Log.d(LOG_TAG, "Successfully updated twincode for profile " + profile);
            }
            consumer.onGet(errorCode, profile);
        });
    }

    @Override
    public void deleteObject(@NonNull TwinlifeContext twinlifeContext, @NonNull RepositoryObject profile, @NonNull Consumer<RepositoryObject> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteObject: twinlifeContext=" + twinlifeContext + " profile=" + profile + " consumer=" + consumer);
        }

        if (!(profile instanceof Profile)) {
            Log.e(LOG_TAG, "object is not a profile: " + profile);
            consumer.onGet(BaseService.ErrorCode.BAD_REQUEST, profile);
            return;
        }

        if (twinlifeContext instanceof TwinmeContext) {
            ((TwinmeContext) twinlifeContext).deleteProfile(-1L, (Profile) profile);
        }

        consumer.onGet(BaseService.ErrorCode.SUCCESS, profile);
    }

    private ProfileFactory() {
        super(Profile.SCHEMA_ID, Profile.SCHEMA_VERSION,
                RepositoryObjectFactory.USE_INBOUND | RepositoryObjectFactory.USE_OUTBOUND,
                SpaceFactory.INSTANCE);
    }
}
