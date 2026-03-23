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
import org.twinlife.twinlife.TwinlifeContext;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContext;

import java.util.List;
import java.util.UUID;

/**
 * Factory used by the RepositoryService to create Contact object.
 */
public class CallReceiverFactory extends TwinmeObjectFactory implements RepositoryObjectFactory<CallReceiver> {
    private static final boolean DEBUG = false;
    private static final String LOG_TAG = "CallReceiverFactory";

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

    @Override
    public void syncObject(@NonNull TwinlifeContext twinlifeContext, @NonNull RepositoryObject callReceiver, @NonNull Consumer<RepositoryObject> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "syncObject: twinlifeContext=" + twinlifeContext + " callReceiver=" + callReceiver + " consumer=" + consumer);
        }

        if (!(callReceiver instanceof CallReceiver)) {
            Log.e(LOG_TAG, "object is not a call receiver: " + callReceiver);
            consumer.onGet(BaseService.ErrorCode.BAD_REQUEST, callReceiver);
            return;
        }

        if (!(twinlifeContext instanceof TwinmeContext)) {
            Log.e(LOG_TAG, "twinlifeContext is not a TwinmeContext: "+twinlifeContext);
            consumer.onGet(BaseService.ErrorCode.LIBRARY_ERROR, callReceiver);
            return;
        }

        TwinmeContext twinmeContext = (TwinmeContext) twinlifeContext;

        if (callReceiver.getTwincodeOutbound() == null) {
            Log.e(LOG_TAG, "CallReceiver has no twincodeOutbound, creating new one: " + callReceiver);
            twinmeContext.changeCallReceiverTwincode(BaseService.DEFAULT_REQUEST_ID, (CallReceiver) callReceiver, (errorCode, updatedCallReceiver) -> consumer.onGet(errorCode, callReceiver));
            return;
        }

        twinmeContext.getTwincodeOutboundService().updateTwincode(callReceiver.getTwincodeOutbound(), callReceiver.getTwincodeOutbound().getAttributes(), null, (errorCode, twincode) -> {
            if (errorCode == BaseService.ErrorCode.ITEM_NOT_FOUND || errorCode == BaseService.ErrorCode.EXPIRED) {
                twinmeContext.changeCallReceiverTwincode(BaseService.DEFAULT_REQUEST_ID, (CallReceiver) callReceiver, (err, updatedCallReceiver) -> consumer.onGet(err, callReceiver));
                return;
            } else if (errorCode != BaseService.ErrorCode.SUCCESS) {
                Log.e(LOG_TAG, "Error invoking twincode: " + errorCode + " for call receiver " + callReceiver);
            } else if (DEBUG) {
                Log.d(LOG_TAG, "Successfully updated twincode for call receiver " + callReceiver);
            }
            consumer.onGet(errorCode, callReceiver);
        });
    }

    @Override
    public void deleteObject(@NonNull TwinlifeContext twinlifeContext, @NonNull RepositoryObject callReceiver, @NonNull Consumer<RepositoryObject> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteObject: twinlifeContext=" + twinlifeContext + " callReceiver=" + callReceiver + " consumer=" + consumer);
        }

        if (!(callReceiver instanceof CallReceiver)) {
            Log.e(LOG_TAG, "object is not a call receiver: " + callReceiver);
            consumer.onGet(BaseService.ErrorCode.BAD_REQUEST, callReceiver);
            return;
        }

        if (!(twinlifeContext instanceof TwinmeContext)) {
            consumer.onGet(BaseService.ErrorCode.LIBRARY_ERROR, null);
        } else {
            ((TwinmeContext) twinlifeContext).deleteCallReceiver(1L, (CallReceiver) callReceiver, (errorCode, uuid) -> consumer.onGet(errorCode, callReceiver));
        }
    }

    private CallReceiverFactory() {
        super(CallReceiver.SCHEMA_ID, CallReceiver.SCHEMA_VERSION,
                RepositoryObjectFactory.USE_INBOUND | RepositoryObjectFactory.USE_OUTBOUND,
                SpaceFactory.INSTANCE);
    }
}
