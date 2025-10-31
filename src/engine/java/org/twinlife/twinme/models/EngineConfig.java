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

/**
 * Configuration of the Twinroom engine to describe the engine capabilities:
 *
 * - the engine class,
 * - the max number of room members,
 * - the max number of members for an audio or video call,
 * - the max size used on filesystem,
 *
 * To disable audio or video, the max number of audio or video members must be set to 0.
 */
public class EngineConfig {

    public enum Status {
        // Engine is disabled.
        DISABLED,

        // Engine is enabled but is stopped.
        STOPPED,

        // Engine is enabled and running.
        RUNNING

        // Other status: DELETED, MIGRATING, MAINTENANCE
    }

    public static int fromStatus(@Nullable Status status) {
        if (status == null) {
            return 0;
        }
        switch (status) {
            case DISABLED:
                return 1;

            case STOPPED:
                return 2;

            case RUNNING:
                return 3;
        }
        return 0;
    }

    @Nullable
    public static Status toStatus(int value) throws SerializerException {
        switch (value) {
            case 0:
                return null;

            case 1:
                return Status.DISABLED;

            case 2:
                return Status.STOPPED;

            case 3:
                return Status.RUNNING;

            default:
                throw new SerializerException();
        }
    }
    public static class Serializer {

        public static void serialize(@NonNull Encoder encoder, @NonNull EngineConfig config) throws SerializerException {

            encoder.writeEnum(fromStatus(config.mStatus));
            encoder.writeString(config.mEngineClass);
            encoder.writeInt(config.mMaxMembers);
            encoder.writeInt(config.mMaxAudioMembers);
            encoder.writeInt(config.mMaxVideoMembers);

            // Finish with a 0 so that we can more easily extend the EngineConfig object.
            encoder.writeEnum(0);
        }

        @NonNull
        public static EngineConfig deserialize(@NonNull Decoder decoder) throws SerializerException {

            final Status status = toStatus(decoder.readEnum());
            final String engineClass = decoder.readString();
            int maxMembers = decoder.readInt();
            int maxAudioMembers = decoder.readInt();
            int maxVideoMembers = decoder.readInt();

            int unused = decoder.readEnum();
            // If we add information in RoomConfig, we can extract it with.  It is ignored otherwise.
            // if (decoder.readEnum() != 0) {
            //
            // }

            return new EngineConfig(status, engineClass, maxMembers, maxAudioMembers, maxVideoMembers);
        }
    }

    private final String mEngineClass;
    private final Status mStatus;
    private final int mMaxMembers;
    private final int mMaxAudioMembers;
    private final int mMaxVideoMembers;

    public EngineConfig(@NonNull EngineConfig config, @NonNull Status status) {
        this.mStatus = status;
        this.mMaxMembers = config.mMaxMembers;
        this.mMaxAudioMembers = config.mMaxAudioMembers;
        this.mMaxVideoMembers = config.mMaxVideoMembers;
        this.mEngineClass = config.mEngineClass;
    }

    public EngineConfig(@NonNull Status status, @NonNull String engineClass,
                        int maxMembers, int maxAudioMembers, int maxVideoMembers) {
        this.mStatus = status;
        this.mEngineClass = engineClass;
        this.mMaxMembers = maxMembers;
        this.mMaxAudioMembers = maxAudioMembers;
        this.mMaxVideoMembers = maxVideoMembers;
    }

    public Status getStatus() {

        return mStatus;
    }

    public String getEngineClass() {

        return mEngineClass;
    }

    public int getMaxMembers() {

        return mMaxMembers;
    }

    public int getMaxAudioMembers() {

        return mMaxAudioMembers;
    }

    public int getMaxVideoMembers() {

        return mMaxVideoMembers;
    }
}