/*
 *  Copyright (c) 2019-2020 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.util;

import org.twinlife.twinlife.ConfigIdentifier;
import org.twinlife.twinlife.ConfigurationService;
import org.twinlife.twinlife.ConversationService.GeolocationDescriptor;
import org.twinlife.twinlife.Twinlife;

import static org.twinlife.twinme.TwinmeContext.ENABLE_REPORT_LOCATION;

public class LocationReport {

    private static final ConfigIdentifier LAST_TIMESTAMP = new ConfigIdentifier("location", "timestamp", "B9897346-AD61-4B1F-96F1-7C04C97C58D3", Long.class);
    private static final ConfigIdentifier LONGITUDE = new ConfigIdentifier("location", "longitude", "7A9A1269-D0BC-415E-A902-22A4250B2D6C", Float.class);
    private static final ConfigIdentifier LATITUDE = new ConfigIdentifier("location", "latitude", "BC837810-1882-4199-ACF9-DB1177B3EC6F", Float.class);
    private static final ConfigIdentifier ALTITUDE = new ConfigIdentifier("location", "altitude", "3EC08BE2-32C4-48BC-8343-5FEDB55EEF3F", Float.class);

    public static void recordGeolocation(Twinlife twinlife, GeolocationDescriptor geolocationDescriptor) {
        if (ENABLE_REPORT_LOCATION) {
            ConfigurationService configurationService = twinlife.getConfigurationService();
            ConfigurationService.Configuration savedConfiguration = configurationService.getConfiguration(LAST_TIMESTAMP);

            savedConfiguration.setLongConfig(LAST_TIMESTAMP, geolocationDescriptor.getCreatedTimestamp());
            savedConfiguration.setFloatConfig(LONGITUDE, (float) geolocationDescriptor.getLongitude());
            savedConfiguration.setFloatConfig(LATITUDE, (float) geolocationDescriptor.getLatitude());
            savedConfiguration.setFloatConfig(ALTITUDE, (float) geolocationDescriptor.getAltitude());
            savedConfiguration.save();
        }
    }

    public static String getReport(Twinlife twinlife) {
        if (ENABLE_REPORT_LOCATION) {
            ConfigurationService configurationService = twinlife.getConfigurationService();
            ConfigurationService.Configuration savedConfiguration = configurationService.getConfiguration(LAST_TIMESTAMP);

            long timestamp = savedConfiguration.getLongConfig(LAST_TIMESTAMP, 0);
            if (timestamp > 0) {
                float longitude = savedConfiguration.getFloatConfig(LONGITUDE, (float) 0.0);
                float latitude = savedConfiguration.getFloatConfig(LATITUDE, (float) 0.0);
                float altitude = savedConfiguration.getFloatConfig(ALTITUDE, (float) 0.0);

                return timestamp + ":" + longitude + ":" + latitude + ":" + altitude;
            }
        }
        return null;
    }
}
