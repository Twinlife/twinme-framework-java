/*
 *  Copyright (c) 2020-2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;

import android.util.Log;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

/*
 * <pre>
 *
 * Schema version 1
 *
 * {
 *  "type":"record",
 *  "name":"EngineCommand",
 *  "namespace":"org.twinlife.twinme.schemas",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"requestId", "type":"long"}
 *   {"name":"action", "type":"enum"}
 *   {"name":"text", [null, "type":"string"]}
 *   {"name":"engineId", [null, "type":"uuid"]}
 *   {"name":"image", [null, "type":"bitmap"]}
 *   {"name":"config", [null, "type":"EngineConfig"]}
 * }
 *
 * </pre>
 */

/**
 * Engine management command sent to a Twinroom manager.
 *
 */
public class EngineCommand {
    private static final String LOG_TAG = "EngineCommand";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("d176a379-ba67-419e-9b5f-f0227db3b6c4");
    private static final int SCHEMA_VERSION = 1;

    private static final int CONVERSATION_SERVICE_MIN_MAJOR_VERSION = 2;
    private static final int CONVERSATION_SERVICE_MIN_MINOR_VERSION = 11;

    public static class EngineCommandSerializer extends Serializer {

        public EngineCommandSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, EngineCommand.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            EngineCommand command = (EngineCommand) object;
            encoder.writeLong(command.mRequestId);
            switch (command.mAction) {
                case ENGINE_CREATE:
                    encoder.writeEnum(0);
                    break;

                case ENGINE_START:
                    encoder.writeEnum(1);
                    break;

                case ENGINE_STOP:
                    encoder.writeEnum(2);
                    break;

                case ENGINE_STATUS:
                    encoder.writeEnum(3);
                    break;

                case ENGINE_CONFIGURE:
                    encoder.writeEnum(4);
                    break;

                case ENGINE_DESTROY:
                    encoder.writeEnum(5);
                    break;

                case ENGINE_LIST:
                    encoder.writeEnum(6);
                    break;

                case ENGINE_STAT:
                    encoder.writeEnum(7);
                    break;

                case ENGINE_SERVER_INFO:
                    encoder.writeEnum(8);
                    break;
            }
            encoder.writeOptionalString(command.mText);
            encoder.writeOptionalUUID(command.mEngineId);
            encoder.writeOptionalBytes(command.mRawImage);

            if (command.mConfig == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                EngineConfig.Serializer.serialize(encoder, command.mConfig);
            }

            if (command.mRoomConfig == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                RoomConfig.Serializer.serialize(encoder, command.mRoomConfig);
            }

            // Finish with a 0 so that we can more easily extend the EngineConfig object.
            encoder.writeEnum(0);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            Action action;
            long requestId = decoder.readLong();
            switch (decoder.readEnum()) {
                case 0:
                    action = Action.ENGINE_CREATE;
                    break;

                case 1:
                    action = Action.ENGINE_START;
                    break;

                case 2:
                    action = Action.ENGINE_STOP;
                    break;

                case 3:
                    action = Action.ENGINE_STATUS;
                    break;

                case 4:
                    action = Action.ENGINE_CONFIGURE;
                    break;

                case 5:
                    action = Action.ENGINE_DESTROY;
                    break;

                case 6:
                    action = Action.ENGINE_LIST;
                    break;

                case 7:
                    action = Action.ENGINE_STAT;
                    break;

                case 8:
                    action = Action.ENGINE_SERVER_INFO;
                    break;

                default:
                    throw new SerializerException("EngineCommand action not recognized");
            }

            String text = decoder.readOptionalString();
            UUID engineId = decoder.readOptionalUUID();
            byte[] rawImage = decoder.readOptionalBytes(null);

            EngineConfig config = null;
            if (decoder.readEnum() != 0) {
                config = EngineConfig.Serializer.deserialize(decoder);
            }

            RoomConfig roomConfig = null;
            if (decoder.readEnum() != 0) {
                roomConfig = RoomConfig.Serializer.deserialize(decoder);
            }

            // If we add information in EngineCommand, we can extract it with.  It is ignored otherwise.
            // if (decoder.readEnum() != 0) {
            //
            // }

            return new EngineCommand(requestId, action, text, engineId, rawImage, config, roomConfig);
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
    private final UUID mEngineId;
    @Nullable
    private final byte[] mRawImage;
    @Nullable
    private final EngineConfig mConfig;
    @Nullable
    private final RoomConfig mRoomConfig;

    public enum Action {
        ENGINE_CREATE,
        ENGINE_START,
        ENGINE_STOP,
        ENGINE_STATUS,
        ENGINE_CONFIGURE,
        ENGINE_DESTROY,
        ENGINE_LIST,
        ENGINE_STAT,
        ENGINE_SERVER_INFO
    }

    @NonNull
    public static EngineCommand create(long requestId, @NonNull String name, @NonNull Bitmap avatar,
                                       @NonNull EngineConfig config, @NonNull RoomConfig roomConfig) {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        avatar.compress(Bitmap.CompressFormat.PNG, 0, byteArrayOutputStream);
        byte[] rawImage = byteArrayOutputStream.toByteArray();

        return new EngineCommand(requestId, Action.ENGINE_CREATE, name, rawImage, config, roomConfig);
    }

    @NonNull
    public static EngineCommand configure(long requestId, @NonNull String logicalName, @NonNull EngineConfig config) {

        return new EngineCommand(requestId, Action.ENGINE_CONFIGURE, logicalName, (UUID)null, config);
    }

    @NonNull
    public static EngineCommand configure(long requestId, @NonNull UUID engineId, @NonNull EngineConfig config) {

        return new EngineCommand(requestId, Action.ENGINE_CONFIGURE, null, engineId, config);
    }

    @NonNull
    public static EngineCommand start(long requestId, @NonNull String logicalName) {

        return new EngineCommand(requestId, Action.ENGINE_START, logicalName, (UUID)null, null);
    }

    @NonNull
    public static EngineCommand start(long requestId, @NonNull UUID engineId) {

        return new EngineCommand(requestId, Action.ENGINE_START, null, engineId, null);
    }

    @NonNull
    public static EngineCommand stop(long requestId, @NonNull String logicalName) {

        return new EngineCommand(requestId, Action.ENGINE_STOP, logicalName, (UUID)null, null);
    }

    @NonNull
    public static EngineCommand stop(long requestId, @NonNull UUID engineId) {

        return new EngineCommand(requestId, Action.ENGINE_STOP, null, engineId, null);
    }

    @NonNull
    public static EngineCommand destroy(long requestId, @NonNull String logicalName) {

        return new EngineCommand(requestId, Action.ENGINE_DESTROY, logicalName, (UUID)null, null);
    }

    @NonNull
    public static EngineCommand destroy(long requestId, @NonNull UUID engineId) {

        return new EngineCommand(requestId, Action.ENGINE_DESTROY, null, engineId, null);
    }

    @NonNull
    public static EngineCommand list(long requestId, @NonNull String filter) {

        return new EngineCommand(requestId, Action.ENGINE_LIST, filter);
    }

    @NonNull
    public static EngineCommand stats(long requestId, @NonNull String filter) {

        return new EngineCommand(requestId, Action.ENGINE_STAT, filter);
    }

    @NonNull
    public static EngineCommand serverInformation(long requestId) {

        return new EngineCommand(requestId, Action.ENGINE_SERVER_INFO, null);
    }

    private EngineCommand(long requestId, @NonNull Action action, @Nullable String text) {
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineCommand: requestId=" + requestId + " action=" + action
                    + " text=" + text);
        }

        mRequestId = requestId;
        mAction = action;
        mText = text;
        mEngineId = null;
        mRawImage = null;
        mConfig = null;
        mRoomConfig = null;
    }

    private EngineCommand(long requestId, @NonNull Action action, @NonNull String name,
                          @Nullable byte[] rawImage, @NonNull EngineConfig config, @NonNull RoomConfig roomConfig) {
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineCommand: requestId=" + requestId + " action=" + action
                    + " name=" + name + " config=" + config);
        }

        mRequestId = requestId;
        mAction = action;
        mText = name;
        mEngineId = null;
        mRawImage = rawImage;
        mConfig = config;
        mRoomConfig = roomConfig;
    }

    private EngineCommand(long requestId, @NonNull Action action, @Nullable String logicalName,
                          @Nullable UUID engineId, @Nullable EngineConfig config) {
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineCommand: requestId=" + requestId + " action=" + action
                    + " logicalName=" + logicalName + " engineId=" + engineId + " config=" + config);
        }

        mRequestId = requestId;
        mAction = action;
        mText = logicalName;
        mEngineId = engineId;
        mRawImage = null;
        mConfig = config;
        mRoomConfig = null;
    }

    private EngineCommand(long requestId, @NonNull Action action, @Nullable String text,
                          @Nullable UUID engineId, @Nullable byte[] rawImage, @Nullable EngineConfig engineConfig,
                          @NonNull RoomConfig roomConfig) {
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineCommand: requestId=" + requestId + " action=" + action
                    + " engineId=" + engineId + " engineConfig=" + engineConfig);
        }

        mRequestId = requestId;
        mAction = action;
        mText = text;
        mEngineId = engineId;
        mRawImage = rawImage;
        mConfig = engineConfig;
        mRoomConfig = roomConfig;
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
    public UUID getEngineId() {

        return mEngineId;
    }

    @Nullable
    public byte[] getRawImage() {

        return mRawImage;
    }

    @Nullable
    public EngineConfig getConfig() {

        return mConfig;
    }

    @Nullable
    public RoomConfig getRoomConfig() {

        return mRoomConfig;
    }

    @Override
    @NonNull
    public String toString() {

        return "EngineCommand: requestId=" + mRequestId + " action=" + mAction + "\n"
                + " text=" + mText + "\n";
    }
}
