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

import android.util.Log;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
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
 *  "name":"EngineServerInfoResult",
 *  "namespace":"org.twinlife.twinme.schemas",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"requestId", "type":"long"}
 *   {"name":"status", "type":"enum"},
 *   {"name":"applicationName", [null, "type":"string"]},
 *   {"name":"applicationVersion", [null, "type":"string"]},
 *   {"name":"applicationId", [null, "type":"string"]},
 *   {"name":"serviceId", [null, "type":"string"]},
 *   {"name":"hostname", [null, "type":"string"]},
 *   {"name":"ipAddresses", [null, "type":"string"]},
 *   {"name":"engineCount", "type":"int"},
 *   {"name":"engineSlots", "type":"int"},
 *  ]
 * }
 *
 * </pre>
 */

/**
 * Result of the ENGINE_SERVER_INFO command to provide supervision information.
 */
public class EngineServerInfoResult extends EngineCommandResult {
    private static final String LOG_TAG = "EngineServerInfoResult";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("335295b7-6054-410f-befa-fcc6eb52a1c0");
    private static final int SCHEMA_VERSION = 1;

    public static class EngineServerInfoResultSerializer extends EngineCommandResult.EngineCommandResultSerializer {

        public EngineServerInfoResultSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, EngineServerInfoResult.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            EngineServerInfoResult result = (EngineServerInfoResult) object;
            serialize(encoder, result);

            encoder.writeOptionalString(result.mApplicationName);
            encoder.writeOptionalString(result.mApplicationVersion);
            encoder.writeOptionalString(result.mApplicationId);
            encoder.writeOptionalString(result.mServiceId);
            if (result.mEngineClasses == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(result.mEngineClasses.size());
                for (String name : result.mEngineClasses) {
                    encoder.writeString(name);
                }
            }
            encoder.writeOptionalString(result.mHostname);
            encoder.writeOptionalString(result.mIpAddresses);
            encoder.writeInt(result.mEngineCount);
            encoder.writeInt(result.mEngineSlots);

            // Finish with a 0 so that we can more easily extend the EngineConfig object.
            encoder.writeEnum(0);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            EngineCommandResult result = deserialize(decoder);
            String applicationName = decoder.readOptionalString();
            String applicationVersion = decoder.readOptionalString();
            String applicationId = decoder.readOptionalString();
            String serviceId = decoder.readOptionalString();
            int count = decoder.readInt();
            List<String> engineClasses;
            if (count == 0) {
                engineClasses = null;
            } else {
                engineClasses = new ArrayList<>();
                while (count > 0) {
                    engineClasses.add(decoder.readString());
                    count--;
                }
            }
            String hostname = decoder.readOptionalString();
            String ipAddresses = decoder.readOptionalString();
            int engineCount = decoder.readInt();
            int engineSlots = decoder.readInt();

            // If we add information in EngineCommand, we can extract it with.  It is ignored otherwise.
            // if (decoder.readEnum() != 0) {
            //
            // }

            return new EngineServerInfoResult(result, applicationName, applicationVersion, applicationId,
                    serviceId, engineClasses, hostname, ipAddresses, engineCount, engineSlots);
        }
    }

    // Information about the engine configuration
    @Nullable
    private final String mApplicationName;
    @Nullable
    private final String mApplicationVersion;
    @Nullable
    private final String mApplicationId;
    @Nullable
    private final String mServiceId;
    @Nullable
    private final List<String> mEngineClasses;

    // Information about the hostname.
    @Nullable
    private final String mHostname;
    @Nullable
    private final String mIpAddresses;

    // Information about the engines
    private final int mEngineCount;

    private final int mEngineSlots;

    public EngineServerInfoResult(@NonNull EngineCommand command, @NonNull Status status, @Nullable String applicationName,
                                  @Nullable String applicationVersion, @Nullable String applicationId,
                                  @Nullable String serviceId, @Nullable List<String> engineClasses,
                                  @Nullable String hostname,
                                  @Nullable String ipAddresses, int engineCount, int engineSlots) {
        super(command, status);
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineServerInfoResult: command=" + command + " status=" + status);
        }

        mApplicationName = applicationName;
        mApplicationId = applicationId;
        mApplicationVersion = applicationVersion;
        mServiceId = serviceId;
        mEngineClasses = engineClasses;
        mHostname = hostname;
        mIpAddresses = ipAddresses;
        mEngineCount = engineCount;
        mEngineSlots = engineSlots;
    }

    @Nullable
    public String getApplicationName() {

        return mApplicationName;
    }

    @Nullable
    public String getApplicationVersion() {

        return mApplicationVersion;
    }

    @Nullable
    public String getApplicationId() {

        return mApplicationId;
    }

    @Nullable
    public String getServiceId() {

        return mServiceId;
    }

    @Nullable
    public List<String> getEngineClasses() {

        return mEngineClasses;
    }

    @Nullable
    public String getHostname() {

        return mHostname;
    }

    @Nullable
    public String getIpAddresses() {

        return mIpAddresses;
    }

    public int getEngineCount() {

        return mEngineCount;
    }

    public int getEngineSlots() {

        return mEngineSlots;
    }

    private EngineServerInfoResult(@NonNull EngineCommandResult result, @Nullable String applicationName,
                                   @Nullable String applicationVersion, @Nullable String applicationId,
                                   @Nullable String serviceId, @Nullable List<String> engineClasses,
                                   @Nullable String hostname,
                                   @Nullable String ipAddresses, int engineCount, int engineSlots) {
        super(result.getRequestId(), result.getStatus());
        if (DEBUG) {
            Log.d(LOG_TAG, "EngineServerInfoResult: result=" + result);
        }

        mApplicationName = applicationName;
        mApplicationId = applicationId;
        mApplicationVersion = applicationVersion;
        mServiceId = serviceId;
        mEngineClasses = engineClasses;
        mHostname = hostname;
        mIpAddresses = ipAddresses;
        mEngineCount = engineCount;
        mEngineSlots = engineSlots;
    }

    @Override
    @NonNull
    public String toString() {

        return "EngineServerInfoResult: requestId=" + getRequestId() + " status=" + getStatus() + "\n";
    }
}
