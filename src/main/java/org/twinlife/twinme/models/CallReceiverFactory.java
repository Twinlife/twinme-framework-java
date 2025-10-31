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
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryImportService;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.util.Utils;

import java.util.List;
import java.util.UUID;

/**
 * Factory used by the RepositoryService to create Contact object.
 */
public class CallReceiverFactory extends TwinmeObjectFactory implements RepositoryObjectFactory<CallReceiver> {

    public static final CallReceiverFactory INSTANCE = new CallReceiverFactory();

    @Override
    @NonNull
    public CallReceiver createObject(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                                long creationDate, @Nullable String name, @Nullable String description,
                                @Nullable List<AttributeNameValue> attributes,
                                long modificationDate) {

        return new CallReceiver(identifier, uuid, creationDate, name, description, attributes, modificationDate);
    }

    @Override
    public void loadObject(@NonNull CallReceiver object, String name, String description,
                           @Nullable List<BaseService.AttributeNameValue> attributes, long modificationDate) {

        object.update(name, description, attributes, modificationDate);
    }

    @Override
    @Nullable
    public CallReceiver importObject(@NonNull RepositoryImportService upgradeService,
                                     @NonNull DatabaseIdentifier identifier, @NonNull UUID uuid, @Nullable UUID key,
                                     long creationDate, @NonNull List<AttributeNameValue> attributes) {
        String name = null, description = null;
        UUID twincodeInboundId = key, twincodeOutboundId = null, privatePeerTwincodeOutboundId = null, spaceId = null, twincodeFactoryId = null;
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

                case "privatePeerTwincodeOutboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        privatePeerTwincodeOutboundId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

                case "spaceId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        spaceId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

                case "twincodeInboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        twincodeInboundId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

                case "twincodeOutboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        twincodeOutboundId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

                case "twincodeFactoryId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        twincodeFactoryId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;
            }
        }

        // 7 attributes: name, description, twincodeInboundId, twincodeFactoryId, twincodeOutboundId, privatePeerTwincodeOutboundId
        // spaceId are mapped to repository columns and they are dropped.
        final CallReceiver contact = new CallReceiver(identifier, uuid, creationDate, name, description, attributes, creationDate);
        upgradeService.importObject(contact, twincodeFactoryId, twincodeInboundId, twincodeOutboundId, privatePeerTwincodeOutboundId, spaceId);
        return contact;
    }

    private CallReceiverFactory() {
        super(CallReceiver.SCHEMA_ID, CallReceiver.SCHEMA_VERSION,
                RepositoryObjectFactory.USE_INBOUND | RepositoryObjectFactory.USE_OUTBOUND,
                SpaceFactory.INSTANCE);
    }
}
