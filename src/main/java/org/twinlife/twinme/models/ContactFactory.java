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
 * Factory used by the RepositoryService to create Contact object.
 */
public class ContactFactory extends TwinmeObjectFactory implements RepositoryObjectFactory<Contact> {

    public static final ContactFactory INSTANCE = new ContactFactory();

    @Override
    @NonNull
    public Contact createObject(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                                long creationDate, @Nullable String name, @Nullable String description,
                                @Nullable List<AttributeNameValue> attributes,
                                long modificationDate) {

        return new Contact(identifier, uuid, creationDate, name, description, attributes, modificationDate);
    }

    @Override
    public void loadObject(@NonNull Contact object, String name, String description,
                           @Nullable List<BaseService.AttributeNameValue> attributes, long modificationDate) {

        object.update(name, description, attributes, modificationDate);
    }

    @Override
    @Nullable
    public Contact importObject(@NonNull RepositoryImportService upgradeService,
                                @NonNull DatabaseIdentifier identifier, @NonNull UUID uuid, @Nullable UUID key,
                                long creationDate, @NonNull List<AttributeNameValue> attributes) {
        String name = null, description = null;
        UUID twincodeInboundId = key, twincodeOutboundId = null, privatePeerTwincodeOutboundId = null, spaceId = null, twincodeFactoryId = null;
        UUID publicPeerTwincodeOutboundId = null;
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

                case "publicPeerTwincodeOutboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        publicPeerTwincodeOutboundId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
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

        // Use the peerTwincodeOutbound to keep track of the public peer twincode
        if (privatePeerTwincodeOutboundId == null && publicPeerTwincodeOutboundId != null) {
            privatePeerTwincodeOutboundId = publicPeerTwincodeOutboundId;
            attributes.add(new BaseService.AttributeNameBooleanValue("noPrivatePeer", true));
        }

        // 7 attributes: name, description, twincodeInboundId, twincodeFactoryId, twincodeOutboundId, privatePeerTwincodeOutboundId
        // spaceId are mapped to repository columns and they are dropped.
        final Contact contact = new Contact(identifier, uuid, creationDate, name, description, attributes, creationDate);
        upgradeService.importObject(contact, twincodeFactoryId, twincodeInboundId, twincodeOutboundId, privatePeerTwincodeOutboundId, spaceId);
        return contact;
    }

    private ContactFactory() {
        super(Contact.SCHEMA_ID, Contact.SCHEMA_VERSION,
                RepositoryObjectFactory.USE_INBOUND | RepositoryObjectFactory.USE_OUTBOUND | RepositoryObjectFactory.USE_PEER_OUTBOUND,
                SpaceFactory.INSTANCE);
    }
}
