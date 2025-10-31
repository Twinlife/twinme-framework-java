/*
 *  Copyright (c) 2020 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

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
 *   {"name":"status", "type":"enum"}
 *  ]
 * }
 *
 * </pre>
 */

/**
 * Result of a command sent to a Twinroom.
 *
 */
public class RoomCommandResult {
    private static final String LOG_TAG = "RoomResult";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("c1124181-8360-49a0-8180-0f4802d1dc04");
    private static final int SCHEMA_VERSION = 1;

    private static final int CONVERSATION_SERVICE_MIN_MAJOR_VERSION = 2;
    private static final int CONVERSATION_SERVICE_MIN_MINOR_VERSION = 10;

    public static class RoomResultSerializer extends Serializer {

        public RoomResultSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, RoomCommandResult.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            RoomCommandResult result = (RoomCommandResult) object;
            encoder.writeLong(result.mRequestId);
            switch (result.mStatus) {
                case SUCCESS:
                    encoder.writeEnum(0);
                    break;

                case ERROR:
                    encoder.writeEnum(1);
                    break;

                case BAD_COMMAND:
                    encoder.writeEnum(2);
                    break;

                case PERMISSION_DENIED:
                    encoder.writeEnum(3);
                    break;

                case ITEM_NOT_FOUND:
                    encoder.writeEnum(4);
                    break;
            }

            if (result.mMembers == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(result.mMembers.size());
                for (UUID memberId : result.mMembers) {
                    encoder.writeUUID(memberId);
                }
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            Status status;
            long requestId = decoder.readLong();
            switch (decoder.readEnum()) {
                case 0:
                    status = Status.SUCCESS;
                    break;

                case 1:
                    status = Status.ERROR;
                    break;

                case 2:
                    status = Status.BAD_COMMAND;
                    break;

                case 3:
                    status = Status.PERMISSION_DENIED;
                    break;

                case 4:
                    status = Status.ITEM_NOT_FOUND;
                    break;

                default:
                    throw new SerializerException("RoomCommand action not recognized");
            }

            int count = decoder.readInt();
            List<UUID> members = null;
            if (count > 0) {
                members = new ArrayList<>();
                while (--count >= 0) {
                    members.add(decoder.readUUID());
                }
            }

            return new RoomCommandResult(requestId, status, members);
        }

        @Override
        public boolean isSupported(int majorVersion, int minorVersion) {

            return majorVersion == CONVERSATION_SERVICE_MIN_MAJOR_VERSION && minorVersion >= CONVERSATION_SERVICE_MIN_MINOR_VERSION;
        }
    }

    private final long mRequestId;
    @NonNull
    private final Status mStatus;
    @Nullable
    private final List<UUID> mMembers;

    public enum Status {
        SUCCESS,
        ERROR,
        BAD_COMMAND,
        PERMISSION_DENIED,
        ITEM_NOT_FOUND
    }

    public RoomCommandResult(@NonNull RoomCommand command, @NonNull Status status) {
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomCommandResult: command=" + command + " status=" + status);
        }

        mRequestId = command.getRequestId();
        mStatus = status;
        mMembers = null;
    }

    public RoomCommandResult(@NonNull RoomCommand command, @NonNull List<UUID> members) {
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomCommandResult: command=" + command + " members=" + members);
        }

        mRequestId = command.getRequestId();
        mStatus = Status.SUCCESS;
        mMembers = members;
    }

    public RoomCommandResult(long requestId, @NonNull Status status, @Nullable List<UUID> members) {
        if (DEBUG) {
            Log.d(LOG_TAG, "RoomResult: requestId=" + requestId + " status=" + status);
        }

        mRequestId = requestId;
        mStatus = status;
        mMembers = members;
    }

    public long getRequestId() {

        return mRequestId;
    }

    @NonNull
    public Status getStatus() {

        return mStatus;
    }

    @Nullable
    public List<UUID> getMembers() {

        return mMembers;
    }

    @Override
    @NonNull
    public String toString() {

        return "RoomResult: requestId=" + mRequestId + " status=" + mStatus + "\n";
    }
}
