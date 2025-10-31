/*
 *  Copyright (c) 2020-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import android.util.Log;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
 * <pre>
 *
 * Schema version 1
 *
 * {
 *  "type":"record",
 *  "name":"RoomCommand",
 *  "namespace":"org.twinlife.twinme.schemas",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"requestId", "type":"long"}
 *   {"name":"action", "type":"enum"}
 *   {"name":"text", [null, "type":"string"]}
 *   {"name":"image", [null, "type":"bitmap"]}
 *   {"name":"descriptorId", [null, {
 *     {"name":"id", "type":"uuid"},
 *     {"name":"sequenceId", "type":"int""}
 *   },
 *   {"name":"twincodeOutboundId", [null, "type":"UUID"]}
 *  ]
 * }
 *
 * </pre>
 */

/**
 * Command sent to a Twinroom.
 *
 */
public class RoomCommand {
    private static final String LOG_TAG = "RoomCommand";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("110cb974-1abc-4928-a6e6-dccdca0f3ab4");
    private static final int SCHEMA_VERSION = 1;

    private static final int CONVERSATION_SERVICE_MIN_MAJOR_VERSION = 2;
    private static final int CONVERSATION_SERVICE_MIN_MINOR_VERSION = 11;

    public static class RoomCommandSerializer extends Serializer {

        public RoomCommandSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, RoomCommand.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            RoomCommand command = (RoomCommand) object;
            encoder.writeLong(command.mRequestId);
            switch (command.mAction) {
                case ROOM_SET_NAME:
                    encoder.writeEnum(0);
                    break;

                case ROOM_SET_IMAGE:
                    encoder.writeEnum(1);
                    break;

                case ROOM_SET_WELCOME:
                    encoder.writeEnum(2);
                    break;

                case ROOM_DELETE_MESSAGE:
                    encoder.writeEnum(3);
                    break;

                case ROOM_FORWARD_MESSAGE:
                    encoder.writeEnum(4);
                    break;

                case ROOM_BLOCK_SENDER:
                    encoder.writeEnum(5);
                    break;

                case ROOM_DELETE_MEMBER:
                    encoder.writeEnum(6);
                    break;

                case ROOM_SET_ADMINISTRATOR:
                    encoder.writeEnum(7);
                    break;

                case ROOM_SET_CONFIG:
                    encoder.writeEnum(8);
                    break;

                case ROOM_LIST_MEMBERS:
                    encoder.writeEnum(9);
                    break;

                case ROOM_SET_ROLES:
                    encoder.writeEnum(10);
                    break;

                case ROOM_RENEW_TWINCODE:
                    encoder.writeEnum(12);
                    break;

                case ROOM_GET_CONFIG:
                    encoder.writeEnum(13);
                    break;

                case ROOM_SIGNAL_MEMBER:
                    encoder.writeEnum(14);
                    break;
            }
            if (command.mText == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeString(command.mText);
            }
            if (command.mMessageId == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(command.mMessageId.twincodeOutboundId);
                encoder.writeLong(command.mMessageId.sequenceId);
            }
            if (command.mRawImage == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeData(command.mRawImage);
            }
            if (command.mTwincodeOutboundId == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(command.mTwincodeOutboundId);
            }
            if (command.mAction == Action.ROOM_SET_ROLES) {
                if (command.mList == null) {
                    encoder.writeLong(0);
                } else {
                    encoder.writeLong(command.mList.size());
                    for (UUID member : command.mList) {
                        encoder.writeUUID(member);
                    }
                }
            }
            if (command.mAction == Action.ROOM_SET_CONFIG) {
                if (command.mConfig == null) {
                    encoder.writeEnum(0);
                } else {
                    encoder.writeEnum(1);
                    RoomConfig.Serializer.serialize(encoder, command.mConfig);
                }
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            Action action;
            long requestId = decoder.readLong();
            switch (decoder.readEnum()) {
                case 0:
                    action = Action.ROOM_SET_NAME;
                    break;

                case 1:
                    action = Action.ROOM_SET_IMAGE;
                    break;

                case 2:
                    action = Action.ROOM_SET_WELCOME;
                    break;

                case 3:
                    action = Action.ROOM_DELETE_MESSAGE;
                    break;

                case 4:
                    action = Action.ROOM_FORWARD_MESSAGE;
                    break;

                case 5:
                    action = Action.ROOM_BLOCK_SENDER;
                    break;

                case 6:
                    action = Action.ROOM_DELETE_MEMBER;
                    break;

                case 7:
                    action = Action.ROOM_SET_ADMINISTRATOR;
                    break;

                case 8:
                    action = Action.ROOM_SET_CONFIG;
                    break;

                case 9:
                    action = Action.ROOM_LIST_MEMBERS;
                    break;

                case 10:
                    action = Action.ROOM_SET_ROLES;
                    break;

                case 12:
                    action = Action.ROOM_RENEW_TWINCODE;
                    break;

                case 13:
                    action = Action.ROOM_GET_CONFIG;
                    break;

                case 14:
                    action = Action.ROOM_SIGNAL_MEMBER;
                    break;

                default:
                    throw new SerializerException("RoomCommand action not recognized");
            }

            String text;
            if (decoder.readEnum() == 1) {
                text = decoder.readString();
            } else {
                text = null;
            }
            DescriptorId descriptorId;
            if (decoder.readEnum() == 1) {
                UUID twincodeOutboundId = decoder.readUUID();
                long sequenceId = decoder.readLong();
                descriptorId = new DescriptorId(0, twincodeOutboundId, sequenceId);
            } else {
                descriptorId = null;
            }

            // Note: the image is deserialized and returned as raw bytes because the Twinroom engine
            // must access the image raw content.
            byte[] image;
            if (decoder.readEnum() == 1) {
                ByteBuffer data = decoder.readBytes(null);
                image = data.array();
            } else {
                image = null;
            }
            UUID twincodeOutboundId;
            if (decoder.readEnum() == 1) {
                twincodeOutboundId = decoder.readUUID();
            } else {
                twincodeOutboundId = null;
            }

            // Get the list of members only for the ROOM_SET_ROLES command.
            List<UUID> list = null;
            if (action == Action.ROOM_SET_ROLES) {
                long count = decoder.readLong();

                if (count > 0) {
                    list = new ArrayList<>();
                    while (count > 0) {
                        count--;
                        list.add(decoder.readUUID());
                    }
                }
            }
            RoomConfig config = null;
            if (action == Action.ROOM_SET_CONFIG) {
                if (decoder.readEnum() != 0) {
                    config = RoomConfig.Serializer.deserialize(decoder);
                }
            }
            return new RoomCommand(requestId, action, text, image, descriptorId, twincodeOutboundId, list, config);
        }

        @Override
        public boolean isSupported(int majorVersion, int minorVersion) {

            return majorVersion == CONVERSATION_SERVICE_MIN_MAJOR_VERSION && minorVersion >= CONVERSATION_SERVICE_MIN_MINOR_VERSION;
        }
    }

    private final long mRequestId;
    @NonNull
    private final Action mAction;
    @Nullable
    private final String mText;
    @Nullable
    private final byte[] mRawImage;
    @Nullable
    private final DescriptorId mMessageId;
    @Nullable
    private final UUID mTwincodeOutboundId;
    @Nullable
    private final List<UUID> mList;
    @Nullable
    private final RoomConfig mConfig;

    public enum Action {
        ROOM_SET_NAME,
        ROOM_SET_IMAGE,
        ROOM_SET_WELCOME,
        ROOM_DELETE_MESSAGE,
        ROOM_FORWARD_MESSAGE,
        ROOM_BLOCK_SENDER,
        ROOM_DELETE_MEMBER,
        ROOM_SET_ADMINISTRATOR,
        ROOM_SET_CONFIG,
        ROOM_SET_ROLES,
        ROOM_LIST_MEMBERS,
        ROOM_RENEW_TWINCODE,
        ROOM_GET_CONFIG,
        ROOM_SIGNAL_MEMBER
    }

    // Filters for the ROOM_LIST_MEMBERS command.
    public static final String LIST_ROLE_ADMINISTRATOR = "admin";
    public static final String LIST_ROLE_MEMBER = "member"; // members and moderators
    public static final String LIST_ROLE_MODERATOR = "moderator";
    public static final String LIST_ROLE_MEMBER_ONLY = "member-only";
    public static final String LIST_ALL = "all";

    public static final String ROLE_ADMINISTRATOR = "admin";
    public static final String ROLE_MEMBER = "member";
    public static final String ROLE_MODERATOR = "moderator";

    public RoomCommand(long requestId, @NonNull Action action) {
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomCommand: requestId=" + requestId + " action=" + action);
        }

        mRequestId = requestId;
        mAction = action;
        mText = null;
        mRawImage = null;
        mMessageId = null;
        mTwincodeOutboundId = null;
        mList = null;
        mConfig = null;
    }

    public RoomCommand(long requestId, @NonNull Action action, @NonNull String text) {
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomCommand: requestId=" + requestId + " action=" + action
                    + " text=" + text);
        }

        mRequestId = requestId;
        mAction = action;
        mText = text;
        mRawImage = null;
        mMessageId = null;
        mTwincodeOutboundId = null;
        mList = null;
        mConfig = null;
    }

    public RoomCommand(long requestId, @NonNull Action action, @NonNull RoomConfig config) {
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomCommand: requestId=" + requestId + " action=" + action
                    + " config=" + config);
        }

        mRequestId = requestId;
        mAction = action;
        mText = null;
        mRawImage = null;
        mMessageId = null;
        mTwincodeOutboundId = null;
        mList = null;
        mConfig = config;
    }

    public RoomCommand(long requestId, @NonNull Action action, @NonNull UUID twincodeOutboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomCommand: requestId=" + requestId + " action=" + action
                    + " twincodeOutboundId=" + twincodeOutboundId);
        }

        mRequestId = requestId;
        mAction = action;
        mText = null;
        mRawImage = null;
        mMessageId = null;
        mTwincodeOutboundId = twincodeOutboundId;
        mList = null;
        mConfig = null;
    }

    public RoomCommand(long requestId, @NonNull Action action, @NonNull DescriptorId messageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomCommand: requestId=" + requestId + " action=" + action
                    + " messageId=" + messageId);
        }

        mRequestId = requestId;
        mAction = action;
        mText = null;
        mRawImage = null;
        mMessageId = messageId;
        mTwincodeOutboundId = null;
        mList = null;
        mConfig = null;
    }

    public RoomCommand(long requestId, @NonNull Action action, @NonNull byte[] rawImage) {
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomCommand: requestId=" + requestId + " action=" + action
                    + " image=" + rawImage);
        }

        mRequestId = requestId;
        mAction = action;
        mText = null;
        mRawImage = rawImage;
        mMessageId = null;
        mTwincodeOutboundId = null;
        mList = null;
        mConfig = null;
    }

    public RoomCommand(long requestId, @NonNull Action action, @NonNull String text, @NonNull List<UUID> members) {
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomCommand: requestId=" + requestId + " action=" + action
                    + " text=" + text + " members=" + members);
        }

        mRequestId = requestId;
        mAction = action;
        mText = text;
        mRawImage = null;
        mMessageId = null;
        mTwincodeOutboundId = null;
        mList = members;
        mConfig = null;
    }

    private RoomCommand(long requestId, @NonNull Action action, @Nullable String text,
                        @Nullable byte[] rawImage, @Nullable DescriptorId descriptorId,
                        @Nullable UUID twincodeOutboundId, @Nullable List<UUID> list,
                        @Nullable RoomConfig roomConfig) {
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomCommand: requestId=" + requestId + " action=" + action
                    + " image=" + rawImage + " descriptorId=" + descriptorId + " list=" + list);
        }

        mRequestId = requestId;
        mAction = action;
        mText = text;
        mRawImage = rawImage;
        mMessageId = descriptorId;
        mTwincodeOutboundId = twincodeOutboundId;
        mList = list;
        mConfig = roomConfig;
    }

    public long getRequestId() {

        return mRequestId;
    }

    @NonNull
    public Action getAction() {

        return mAction;
    }

    @Nullable
    public String getText() {

        return mText;
    }

    @Nullable
    public byte[] getRawImage() {

        return mRawImage;
    }

    @Nullable
    public DescriptorId getDescriptorId() {

        return mMessageId;
    }

    @Nullable
    public UUID getTwincodeOutboundId() {

        return mTwincodeOutboundId;
    }

    @Nullable
    public List<UUID> getList() {

        return mList;
    }

    @Nullable
    public RoomConfig getConfig() {

        return mConfig;
    }

    @Override
    @NonNull
    public String toString() {

        return "RoomCommand: requestId=" + mRequestId + " action=" + mAction + "\n"
                + (BuildConfig.ENABLE_DUMP ? " text=" + mText : "") + "\n"
                + " messageId=" + mMessageId + "\n"
                + " twincodeOutboundId=" + mTwincodeOutboundId + "\n";
    }
}
