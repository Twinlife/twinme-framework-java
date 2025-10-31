/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryImportService;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.util.Utils;

import java.util.List;
import java.util.UUID;

/**
 * Factory used by the RepositoryService to create Profile object.
 */
public class ProfileFactory extends TwinmeObjectFactory implements RepositoryObjectFactory<Profile> {

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

    private ProfileFactory() {
        super(Profile.SCHEMA_ID, Profile.SCHEMA_VERSION,
                RepositoryObjectFactory.USE_INBOUND | RepositoryObjectFactory.USE_OUTBOUND,
                SpaceFactory.INSTANCE);
    }
}
