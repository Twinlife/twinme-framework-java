/*
 *  Copyright (c) 2019 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameStringValue;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Engine card representation.
 *
 * The EngineCard describes the twinme engine configuration with the channels/groups and invitations that the engine
 * must create when it is running.  The EngineCard contains:
 *
 * o a list of channels/groups that are managed by the engine.
 *   Each channel/group is composed of:
 *   - a group twincode id
 *   - an engine twincode id
 *   - the engine permissions within the group
 * o a list of invitations that must be created to accept members in the managed groups.
 *   Each invitation contains:
 *   - the invitation twincode inbound,
 *   - the invitation twincode outbound,
 *   - the group twincode id,
 *   - the member's permission when the invitation is accepted.
 */
public class EngineCard {

    public static final UUID SCHEMA_ID = UUID.fromString("1cd83ca4-9909-4cb0-8748-ad96955e567a");
    @SuppressWarnings("unused")
    public static final int SCHEMA_VERSION = 1;

    @NonNull
    private UUID mId;
    @NonNull
    private final UUID mSchemaId;
    private final int mSchemaVersion;
    @NonNull
    private final String mSerializer;
    private final boolean mImmutable;
    @NonNull
    private final List<ChannelInvitation> mInvitations;
    @NonNull
    private final List<GroupProvisioning> mGroups;
    @NonNull
    private final List<SpaceCard> mSpaceCards;

    // Information to create a channel invitation.
    public final static class ChannelInvitation {
        public final UUID invitationTwincodeInId;
        public final UUID invitationTwincodeOutId;
        public final UUID groupTwincodeId;
        public final long permissions;

        public ChannelInvitation(@NonNull UUID groupTwincodeId, @NonNull UUID invitationTwincodeInId,
                                 @NonNull UUID invitationTwincodeOutId, long permissions) {
            this.groupTwincodeId = groupTwincodeId;
            this.invitationTwincodeInId = invitationTwincodeInId;
            this.invitationTwincodeOutId = invitationTwincodeOutId;
            this.permissions = permissions;
        }

        static ChannelInvitation create(String info) {

            String[] items = info.split(":");
            if (items.length != 4) {
                return null;
            }
            UUID uuid1 = Utils.UUIDFromString(items[0]);
            UUID uuid2 = Utils.UUIDFromString(items[1]);
            UUID uuid3 = Utils.UUIDFromString(items[2]);
            if (uuid1 == null || uuid2 == null || uuid3 == null) {
                return null;
            }
            try {
                long permissions = Long.valueOf(items[3]);
                return new ChannelInvitation(uuid1, uuid2, uuid3, permissions);

            } catch (NumberFormatException ex) {
                return null;
            }
        }

        @NonNull
        @Override
        public String toString() {
            return groupTwincodeId.toString() + ":"
                    + invitationTwincodeInId.toString() + ":"
                    + invitationTwincodeOutId.toString() + ":" + permissions;
        }
    }

    // Information to create a group.
    public final static class GroupProvisioning {
        public final UUID adminMemberTwincodeOutId;
        public final UUID adminMemberTwincodeInId;
        public final UUID groupTwincodeOutId;
        public final UUID groupTwincodeInId;
        public final long permissions;

        public GroupProvisioning(@NonNull UUID groupTwincodeInId, @NonNull UUID groupTwincodeOutId,
                                 @NonNull UUID adminMemberTwincodeInId,
                                 @NonNull UUID adminMemberTwincodeOutId, long permissions) {
            this.groupTwincodeInId = groupTwincodeInId;
            this.groupTwincodeOutId = groupTwincodeOutId;
            this.adminMemberTwincodeInId = adminMemberTwincodeInId;
            this.adminMemberTwincodeOutId = adminMemberTwincodeOutId;
            this.permissions = permissions;
        }

        static GroupProvisioning create(String info) {

            String[] items = info.split(":");
            if (items.length != 5) {
                return null;
            }
            UUID uuid1 = Utils.UUIDFromString(items[0]);
            UUID uuid2 = Utils.UUIDFromString(items[1]);
            UUID uuid3 = Utils.UUIDFromString(items[2]);
            UUID uuid4 = Utils.UUIDFromString(items[3]);
            if (uuid1 == null || uuid2 == null || uuid3 == null || uuid4 == null) {
                return null;
            }
            try {
                long permissions = Long.valueOf(items[4]);
                return new GroupProvisioning(uuid1, uuid2, uuid3, uuid4, permissions);

            } catch (NumberFormatException ex) {
                return null;
            }
        }

        @NonNull
        @Override
        public String toString() {
            return groupTwincodeInId.toString() + ":"
                    + groupTwincodeOutId.toString() + ":"
                    + adminMemberTwincodeInId.toString() + ":"
                    + adminMemberTwincodeOutId.toString() + ":" + permissions;
        }
    }

    public static EngineCard deserialize(@NonNull RepositoryService repositoryService,
                                         @NonNull RepositoryService.Object object) {

        UUID id = object.getId();
        UUID schemaId = object.getSchemaId();
        int schemaVersion = object.getSchemaVersion();
        String serializer = object.getSerializer();
        boolean immutable = object.getImmutable();
        List<ChannelInvitation> invitations = new ArrayList<>();
        List<GroupProvisioning> groups = new ArrayList<>();

        List<AttributeNameValue> attributes;
        try {
            attributes = repositoryService.deserialize(RepositoryService.XML_SERIALIZER, object.getContent());
        } catch (Exception exception) {
            attributes = null;
        }

        if (attributes != null) {
            for (AttributeNameValue attribute : attributes) {
                if (attribute.name == null) {
                    continue;
                }

                switch (attribute.name) {
                    case "invitations":
                        if (attribute instanceof BaseService.AttributeNameListValue) {
                            List<?> lUUIDInvitations = (List<?>) ((BaseService.AttributeNameListValue) attribute).value;
                            for (java.lang.Object lUUID : lUUIDInvitations) {
                                if (lUUID instanceof AttributeNameStringValue) {
                                    String value = (String) ((AttributeNameStringValue) lUUID).value;
                                    ChannelInvitation invitation = ChannelInvitation.create(value);
                                    if (invitation != null) {
                                        invitations.add(invitation);
                                    }
                                }
                            }
                        }
                        break;

                    case "groups":
                        if (attribute instanceof BaseService.AttributeNameListValue) {
                            List<?> lUUIDInvitations = (List<?>) ((BaseService.AttributeNameListValue) attribute).value;
                            for (java.lang.Object lUUID : lUUIDInvitations) {
                                if (lUUID instanceof AttributeNameStringValue) {
                                    String value = (String) ((AttributeNameStringValue) lUUID).value;
                                    GroupProvisioning group = GroupProvisioning.create(value);
                                    if (group != null) {
                                        groups.add(group);
                                    }
                                }
                            }
                        }
                        break;

                }
            }

            if (SCHEMA_ID.equals(schemaId) && schemaVersion == SCHEMA_VERSION
                    && RepositoryService.XML_SERIALIZER.equals(serializer)) {

                return new EngineCard(id, immutable, groups, invitations);
            }
        }

        return null;
    }

    @SuppressWarnings("unused")
    public UUID getId() {

        return mId;
    }

    @SuppressWarnings("unused")
    public UUID getSchemaId() {

        return mSchemaId;
    }

    @SuppressWarnings("unused")
    public int getSchemaVersion() {

        return mSchemaVersion;
    }

    @SuppressWarnings("unused")
    public String getSerializer() {

        return mSerializer;
    }

    @SuppressWarnings("unused")
    public boolean isImmutable() {

        return mImmutable;
    }

    @NonNull
    public List<GroupProvisioning> getGroups() {

        return mGroups;
    }

    @NonNull
    public List<ChannelInvitation> getInvitations() {

        return mInvitations;
    }

    @NonNull
    public List<SpaceCard> getSpaceCards() {

        return mSpaceCards;
    }

    @Override
    public String toString() {

        return "EngineCard:\n" +
                " id=" + mId + "\n" +
                " schemaId=" + mSchemaId + "\n" +
                " schemaVersion=" + mSchemaVersion + "\n" +
                " immutable=" + mImmutable + "\n" +
                " invitations=" + mInvitations + "\n";
    }

    @NonNull
    public String serialize(RepositoryService repositoryService) {

        final List<AttributeNameValue> groups = new ArrayList<>();
        final List<BaseService.AttributeNameValue> invitations = new ArrayList<>();
        synchronized (this) {
            for (GroupProvisioning group : mGroups) {
                groups.add(new AttributeNameStringValue(null, group.toString()));
            }
            for (ChannelInvitation invitation : mInvitations) {
                invitations.add(new AttributeNameStringValue(null, invitation.toString()));
            }
        }

        final List<BaseService.AttributeNameValue> attributes = new ArrayList<>();
        attributes.add(new BaseService.AttributeNameListValue("groups", groups));
        attributes.add(new BaseService.AttributeNameListValue("invitations", invitations));
        return repositoryService.serialize(RepositoryService.XML_SERIALIZER, attributes);
    }

    public void updateFrom(EngineCard engineCard) {

        if (engineCard != null && engineCard.getId() != null) {
            mId = engineCard.getId();
        }
    }

    public EngineCard() {

        mId = UUID.randomUUID();
        mSchemaId = SCHEMA_ID;
        mSchemaVersion = SCHEMA_VERSION;
        mSerializer = RepositoryService.XML_SERIALIZER;
        mImmutable = false;
        mGroups = new ArrayList<>();
        mInvitations = new ArrayList<>();
        mSpaceCards = new ArrayList<>();
    }

    //
    // Private Methods
    //

    private EngineCard(@NonNull UUID id, boolean immutable, @NonNull List<GroupProvisioning> groups,
                       @NonNull List<ChannelInvitation> invitations) {

        mId = id;
        mSchemaId = SCHEMA_ID;
        mSchemaVersion = SCHEMA_VERSION;
        mSerializer = RepositoryService.XML_SERIALIZER;
        mImmutable = immutable;
        mGroups = groups;
        mInvitations = invitations;
        mSpaceCards = new ArrayList<>();
    }
}
