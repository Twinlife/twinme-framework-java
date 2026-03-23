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
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryImportService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwinlifeContext;
import org.twinlife.twinlife.conversation.ConversationProtocol;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Factory used by the RepositoryService to create Contact object.
 */
public class ContactFactory extends TwinmeObjectFactory implements RepositoryObjectFactory<Contact> {
    private static final boolean DEBUG = false;
    private static final String LOG_TAG = "ContactFactory";

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
                        privatePeerTwincodeOutboundId = Utils.UUIDFromString((String) attribute.value);
                    }
                    break;

                case "publicPeerTwincodeOutboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        publicPeerTwincodeOutboundId = Utils.UUIDFromString((String) attribute.value);
                    }
                    break;

                case "spaceId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        spaceId = Utils.UUIDFromString((String) attribute.value);
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
                        twincodeFactoryId = Utils.UUIDFromString((String) attribute.value);
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

    @Override
    public void syncObject(@NonNull TwinlifeContext twinlifeContext, @NonNull RepositoryObject contact, @NonNull Consumer<RepositoryObject> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "syncObject: twinlifeContext=" + twinlifeContext + " contact=" + contact + " consumer=" + consumer);
        }

        if (!(contact instanceof Contact)) {
            Log.e(LOG_TAG, "object is not a contact: " + contact);
            consumer.onGet(BaseService.ErrorCode.BAD_REQUEST, contact);
            return;
        }

        if (contact.getTwincodeOutbound() == null) {
            Log.e(LOG_TAG, "Contact has no twincodeOutbound: " + contact);
            consumer.onGet(BaseService.ErrorCode.BAD_REQUEST, contact);
            return;
        }

        if (contact.getPeerTwincodeOutbound() == null) {
            Log.w(LOG_TAG, "Contact has no peer twincodeOutbound: " + contact + ", either creation phase 2 wasn't performed before backup or the relation was revoked by the peer");
            // Return SUCCESS as this is most likely a false positive.
            consumer.onGet(BaseService.ErrorCode.SUCCESS, contact);
            return;
        }

        twinlifeContext.getTwincodeOutboundService().updateTwincode(contact.getTwincodeOutbound(), contact.getTwincodeOutbound().getAttributes(), null, (status, twincode) -> {
            if (status == BaseService.ErrorCode.ITEM_NOT_FOUND || status == BaseService.ErrorCode.EXPIRED) {
                Log.e(LOG_TAG, "Contact's Twincode not found, deleting from local database : " + contact);

                if (twinlifeContext instanceof TwinmeContext) {
                    ((TwinmeContext) twinlifeContext).deleteContact(1L, ((Contact) contact));
                    consumer.onGet(BaseService.ErrorCode.ITEM_NOT_FOUND, contact);
                }
                return;
            } else if (status != BaseService.ErrorCode.SUCCESS) {
                Log.e(LOG_TAG, "Error updating twincode: " + status + " for contact " + contact);
                consumer.onGet(status, contact);
                return;
            }

            if (contact.getTwincodeOutbound().isEncrypted()) {
                twinlifeContext.getTwincodeOutboundService().invokeTwincode(contact.getPeerTwincodeOutbound(), TwincodeOutboundService.INVOKE_WAKEUP,
                        ConversationProtocol.ACTION_CONVERSATION_NEED_SECRET, new ArrayList<>(), (errorCode, invocationId) -> {
                            if (errorCode != BaseService.ErrorCode.SUCCESS) {
                                Log.e(LOG_TAG, "Error invoking twincode: " + errorCode + " for contact " + contact);
                            } else if (DEBUG) {
                                Log.d(LOG_TAG, "Successfully invoked twincode for contact " + contact);
                            }
                            consumer.onGet(errorCode, contact);
                        });
                return;
            }

            consumer.onGet(BaseService.ErrorCode.SUCCESS, contact);
        });
    }

    @Override
    public void deleteObject(@NonNull TwinlifeContext twinlifeContext, @NonNull RepositoryObject contact, @NonNull Consumer<RepositoryObject> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteObject: twinlifeContext=" + twinlifeContext + " contact=" + contact + " consumer=" + consumer);
        }

        if (!(contact instanceof Contact)) {
            Log.e(LOG_TAG, "object is not a contact: " + contact);
            consumer.onGet(BaseService.ErrorCode.BAD_REQUEST, contact);
            return;
        }

        if (twinlifeContext instanceof TwinmeContext) {
            ((TwinmeContext) twinlifeContext).deleteContact(-1L, (Contact) contact);
        }

        consumer.onGet(BaseService.ErrorCode.SUCCESS, contact);
    }

    private ContactFactory() {
        super(Contact.SCHEMA_ID, Contact.SCHEMA_VERSION,
                RepositoryObjectFactory.USE_INBOUND | RepositoryObjectFactory.USE_OUTBOUND | RepositoryObjectFactory.USE_PEER_OUTBOUND,
                SpaceFactory.INSTANCE);
    }
}
