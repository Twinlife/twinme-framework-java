/*
 *  Copyright (c) 2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;

import java.util.UUID;

/**
 * Twinroom engine information.
 */
public class EngineInfo {

    public static class Serializer {

        public static void serialize(@NonNull Encoder encoder, @NonNull EngineInfo info) throws SerializerException {

            encoder.writeInt(info.getLogicalId());
            encoder.writeString(info.getLogicalName());
            encoder.writeUUID(info.getEngineId());
            encoder.writeLong(info.getCreationDate());
            EngineConfig.Serializer.serialize(encoder, info.getEngineConfig());
            RoomConfig roomConfig = info.getRoomConfig();
            if (roomConfig == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                RoomConfig.Serializer.serialize(encoder, roomConfig);
            }
            encoder.writeInt(info.getMemberCount());
        }

        @NonNull
        public static EngineInfo deserialize(@NonNull Decoder decoder) throws SerializerException {
            int logicalId = decoder.readInt();
            String logicalName = decoder.readString();
            UUID engineId = decoder.readUUID();
            long creationDate = decoder.readLong();
            EngineConfig engineConfig = EngineConfig.Serializer.deserialize(decoder);
            RoomConfig roomConfig;
            if (decoder.readEnum() == 0) {
                roomConfig = null;
            } else {
                roomConfig = RoomConfig.Serializer.deserialize(decoder);
            }
            int memberCount = decoder.readInt();
            return new EngineInfo(logicalId, logicalName, engineId, engineConfig, creationDate, roomConfig, memberCount);
        }
    }

    @NonNull
    private final String mLogicalName;
    private final int mLogicalId;
    @NonNull
    private final UUID mEngineId;
    @NonNull
    private final EngineConfig mEngineConfig;
    @Nullable
    private final RoomConfig mRoomConfig;
    private final int mMemberCount;
    private final long mCreationDate;

    public EngineInfo(final @NonNull EngineInfo engine, @NonNull EngineConfig.Status status) {

        this.mLogicalId = engine.mLogicalId;
        this.mLogicalName = engine.mLogicalName;
        this.mEngineId = engine.mEngineId;
        this.mEngineConfig = new EngineConfig(engine.mEngineConfig, status);
        this.mRoomConfig = engine.getRoomConfig();
        this.mMemberCount = engine.getMemberCount();
        this.mCreationDate = engine.mCreationDate;
    }

    public EngineInfo(final @NonNull EngineInfo engine, @NonNull RoomConfig room, int memberCount) {

        this.mLogicalId = engine.mLogicalId;
        this.mLogicalName = engine.mLogicalName;
        this.mEngineId = engine.mEngineId;
        this.mEngineConfig = engine.mEngineConfig;
        this.mRoomConfig = room;
        this.mMemberCount = memberCount;
        this.mCreationDate = engine.mCreationDate;
    }

    public EngineInfo(final int logicalId, @NonNull final String logicalName, @NonNull final UUID engineId,
                      @NonNull final EngineConfig engineConfig, final long creationDate) {

        this.mLogicalId = logicalId;
        this.mLogicalName = logicalName;
        this.mEngineId = engineId;
        this.mEngineConfig = engineConfig;
        this.mRoomConfig = null;
        this.mMemberCount = 0;
        this.mCreationDate = creationDate;
    }

    public EngineInfo(int logicalId, @NonNull final String logicalName, @NonNull final UUID engineId,
                      @NonNull final EngineConfig engineConfig, final long creationDate,
                      @Nullable final RoomConfig config, int memberCount) {

        this.mLogicalId = logicalId;
        this.mLogicalName = logicalName;
        this.mEngineId = engineId;
        this.mEngineConfig = engineConfig;
        this.mRoomConfig = config;
        this.mMemberCount = memberCount;
        this.mCreationDate = creationDate;
    }

    public int getLogicalId() {

        return mLogicalId;
    }

    @NonNull
    public String getLogicalName() {

        return mLogicalName;
    }

    @NonNull
    public UUID getEngineId() {

        return mEngineId;
    }

    @Nullable
    public RoomConfig getRoomConfig() {

        return mRoomConfig;
    }

    @NonNull
    public EngineConfig getEngineConfig() {

        return mEngineConfig;
    }

    @NonNull
    public EngineConfig.Status getStatus() {

        return mEngineConfig.getStatus();
    }

    @NonNull
    public String getEngineClass() {

        return mEngineConfig.getEngineClass();
    }

    public long getCreationDate() {

        return mCreationDate;
    }

    public int getMemberCount() {

        return mMemberCount;
    }
}