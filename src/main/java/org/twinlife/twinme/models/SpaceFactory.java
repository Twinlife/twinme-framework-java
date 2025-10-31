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
 * Factory used by the RepositoryService to create Space object.
 */
public class SpaceFactory extends TwinmeObjectFactory implements RepositoryObjectFactory<Space> {

    public static final SpaceFactory INSTANCE = new SpaceFactory();

    @Override
    @NonNull
    public Space createObject(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                              long creationDate, @Nullable String name, @Nullable String description,
                              @Nullable List<BaseService.AttributeNameValue> attributes,
                              long modificationDate) {

        return new Space(identifier, uuid, creationDate, name, description, attributes, modificationDate);
    }

    @Override
    public void loadObject(@NonNull Space object, String name, String description,
                           @Nullable List<BaseService.AttributeNameValue> attributes, long modificationDate) {

        object.update(name, description, attributes, modificationDate);
    }

    @Override
    @Nullable
    public Space importObject(@NonNull RepositoryImportService upgradeService,
                              @NonNull DatabaseIdentifier identifier, @NonNull UUID uuid, @Nullable UUID key,
                              long creationDate, @NonNull List<BaseService.AttributeNameValue> attributes) {
        String name = null, description = null;
        UUID settingsId = null, spaceTwincodeOutboundId = null;
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

                case "spaceTwincodeId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        spaceTwincodeOutboundId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

                case "settingsId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        settingsId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;
            }
        }

        // 4 attributes: name, description, spaceTwincodeId, settingsId are mapped to repository columns and they are dropped.
        final Space space = new Space(identifier, uuid, creationDate, name, description, attributes, creationDate);
        upgradeService.importObject(space, null, key,null, spaceTwincodeOutboundId, settingsId);
        return space;
    }

    private SpaceFactory() {
        super(Space.SCHEMA_ID, Space.SCHEMA_VERSION, RepositoryObjectFactory.USE_PEER_OUTBOUND, SpaceSettingsFactory.INSTANCE);
    }
}
