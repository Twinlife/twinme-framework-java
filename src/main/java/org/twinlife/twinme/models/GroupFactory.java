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
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.ErrorCode;
import org.twinlife.twinlife.Permission;
import org.twinlife.twinlife.RepositoryImportService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwinlifeContext;
import org.twinlife.twinlife.conversation.GroupProtocol;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.executors.RefreshRosterExecutor;

import java.util.List;
import java.util.UUID;

/**
 * Factory used by the RepositoryService to create Group object.
 */
public class GroupFactory extends TwinmeObjectFactory implements RepositoryObjectFactory<Group> {
    private static final String LOG_TAG = "GroupFactory";
    private static final boolean DEBUG = false;

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
        UUID twincodeInboundId = key, twincodeOutboundId = null, groupTwincodeOutboundId = null, spaceId = null, twincodeFactoryId = null;
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

                case "twincodeInboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        twincodeInboundId = Utils.UUIDFromString((String) attribute.value);
                    }
                    break;

            }
        }

        // 6 attributes: name, description, twincodeInboundId, twincodeFactoryId, twincodeOutboundId, groupTwincodeOutboundId
        // spaceId are mapped to repository columns and they are dropped.
        final Group group = new Group(identifier, uuid, creationDate, name, description, attributes, creationDate);
        upgradeService.importObject(group, twincodeFactoryId, twincodeInboundId, twincodeOutboundId, groupTwincodeOutboundId, spaceId);
        return group;
    }

    @Override
    public void syncObject(@NonNull TwinlifeContext twinlifeContext, @NonNull RepositoryObject object, @NonNull Consumer<RepositoryObject> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "syncObject: twinlifeContext=" + twinlifeContext + " object=" + object + " consumer=" + consumer);
        }

        if (!(object instanceof Group)) {
            Log.e(LOG_TAG, "object: " + object + " is not a Group, this should not happen");
            consumer.onGet(ErrorCode.BAD_REQUEST, object);
            return;
        }

        if (!(twinlifeContext instanceof TwinmeContextImpl)) {
            Log.e(LOG_TAG, "twinlifeContext: " + twinlifeContext + " is not a TwinmeContextImpl, this should not happen");
            consumer.onGet(ErrorCode.BAD_REQUEST, object);
            return;
        }

        Group group = (Group) object;
        TwinmeContextImpl twinmeContext = (TwinmeContextImpl) twinlifeContext;

        TwincodeOutbound groupTwincode = group.getGroupTwincodeOutbound();

        if (groupTwincode == null) {
            Log.e(LOG_TAG, "Group has no twincodeOutbound: " + group);
            consumer.onGet(ErrorCode.BAD_REQUEST, group);
            return;
        }

        Permission joinPermission = GroupProtocol.getJoinPermissions(groupTwincode);
        Permission memberPermission = groupTwincode.isOwner() ? Permission.ALL_PERMISSIONS : joinPermission;
        // Create the conversation, required for the group to work properly (unlike contacts).
        ConversationService.GroupConversation groupConversation = twinmeContext.getConversationService().createGroup(group, groupTwincode.isOwner(), memberPermission, joinPermission);

        if (groupConversation == null) {
            Log.e(LOG_TAG, "Conversation could not be created for group: " + group);
            consumer.onGet(ErrorCode.LIBRARY_ERROR, group);
            return;
        }

        Consumer<TwincodeOutbound> refreshGroupAfterTwincodeUpdate = (status, updatedTwincode) -> {
            if (status == ErrorCode.ITEM_NOT_FOUND || status == ErrorCode.EXPIRED) {
                Log.e(LOG_TAG, "Group's Twincode not found, deleting from local database : " + group);

                twinmeContext.deleteGroup(-1L, group);
                consumer.onGet(ErrorCode.ITEM_NOT_FOUND, group);
                return;
            } else if (status != ErrorCode.SUCCESS) {
                Log.e(LOG_TAG, "Error updating twincode: " + status + " for group " + group);
                consumer.onGet(status, group);
                return;
            }

            RefreshRosterExecutor refreshRosterExecutor = new RefreshRosterExecutor(twinmeContext, group, (refreshStatus, ignored) -> {
                if (DEBUG) {
                    Log.d(LOG_TAG, "Refresh roster result for group" + group + ": " + refreshStatus);
                }
                consumer.onGet(refreshStatus, group);
            });

            twinmeContext.execute(refreshRosterExecutor::start);
        };

        if (groupTwincode.isOwner()) {
            twinmeContext.getTwincodeOutboundService().updateTwincode(groupTwincode, groupTwincode.getAttributes(), null, refreshGroupAfterTwincodeUpdate);
        } else {
            twinmeContext.getTwincodeOutboundService().refreshTwincode(groupTwincode, (status, attributes) -> refreshGroupAfterTwincodeUpdate.onGet(status, groupTwincode));
        }
    }

    @Override
    public void deleteObject(@NonNull TwinlifeContext twinlifeContext, @NonNull RepositoryObject object, @NonNull Consumer<RepositoryObject> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteObject: twinlifeContext=" + twinlifeContext + " object=" + object + " consumer=" + consumer);
        }

        if (!(object instanceof Group)) {
            Log.e(LOG_TAG, "object: " + object + " is not a Group, this should not happen");
            return;
        }

        if (!(twinlifeContext instanceof TwinmeContextImpl)) {
            Log.e(LOG_TAG, "twinlifeContext: " + twinlifeContext + " is not a TwinmeContextImpl, this should not happen");
            return;
        }

        Group group = (Group) object;
        ((TwinmeContextImpl) twinlifeContext).deleteGroup(-1L, group);
    }

    private GroupFactory() {
        super(Group.SCHEMA_ID, Group.SCHEMA_VERSION,
                RepositoryObjectFactory.USE_INBOUND | RepositoryObjectFactory.USE_OUTBOUND | RepositoryObjectFactory.USE_PEER_OUTBOUND,
                SpaceFactory.INSTANCE);
    }
}
