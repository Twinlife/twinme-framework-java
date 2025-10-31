/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryImportService;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.util.Utils;

import java.util.List;
import java.util.UUID;

/**
 * Factory used by the RepositoryService to create Group object.
 */
public class GroupFactory extends TwinmeObjectFactory implements RepositoryObjectFactory<Group> {

    public static final GroupFactory INSTANCE = new GroupFactory();

    @Override
    @NonNull
    public Group createObject(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                                long creationDate, @Nullable String name, @Nullable String description,
                                @Nullable List<AttributeNameValue> attributes,
                                long modificationDate) {

        return new Group(identifier, uuid, creationDate, name, description, attributes, modificationDate);
    }

    @Override
    public void loadObject(@NonNull Group object, String name, String description,
                           @Nullable List<AttributeNameValue> attributes, long modificationDate) {

        object.update(name, description, attributes, modificationDate);
    }

    @Override
    @Nullable
    public Group importObject(@NonNull RepositoryImportService upgradeService,
                              @NonNull DatabaseIdentifier identifier, @NonNull UUID uuid, @Nullable UUID key,
                              long creationDate, @NonNull List<AttributeNameValue> attributes) {
        String name = null, description = null;
        UUID twincodeOutboundId = null, groupTwincodeOutboundId = null, spaceId = null, twincodeFactoryId = null;
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

                case "spaceId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        spaceId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

                case "memberTwincodeOutboundId":
                case "twincodeOutboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        twincodeOutboundId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

                case "memberTwincodeFactoryId":
                case "twincodeFactoryId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        twincodeFactoryId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

                case "groupTwincodeOutboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        groupTwincodeOutboundId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

            }
        }

        // 6 attributes: name, description, twincodeInboundId, twincodeFactoryId, twincodeOutboundId, groupTwincodeOutboundId
        // spaceId are mapped to repository columns and they are dropped.
        final Group group = new Group(identifier, uuid, creationDate, name, description, attributes, creationDate);
        upgradeService.importObject(group, twincodeFactoryId, key, twincodeOutboundId, groupTwincodeOutboundId, spaceId);
        return group;
    }

    private GroupFactory() {
        super(Group.SCHEMA_ID, Group.SCHEMA_VERSION,
                RepositoryObjectFactory.USE_INBOUND | RepositoryObjectFactory.USE_OUTBOUND | RepositoryObjectFactory.USE_PEER_OUTBOUND,
                SpaceFactory.INSTANCE);
    }
}
