/*
 *  Copyright (c) 2018-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.BaseService.ServiceStats;
import org.twinlife.twinlife.ConfigIdentifier;
import org.twinlife.twinlife.ConfigurationService;
import org.twinlife.twinlife.DeviceInfo;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.RepositoryService.StatType;
import org.twinlife.twinlife.Twinlife;
import org.twinlife.twinme.TwinmeContext;
import org.twinlife.twinme.TwinmeContextImpl;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.util.LocationReport;

import java.util.HashMap;
import java.util.Map;

//
// Executor and observers are running in the SingleThreadExecutor provided by the twinlife library
// Observers are reachable (not eligible for garbage collection) between start() and stop() calls
//
// version: 1.4
//

public class ReportStatsExecutor extends AbstractConnectedTwinmeExecutor {
    private static final String LOG_TAG = "ReportStatsExecutor";
    private static final boolean DEBUG = false;

    private static final int REPORT_CONTACT_STATS = 1;
    private static final int REPORT_CONTACT_STATS_DONE = 1 << 1;
    private static final int REPORT_GROUP_STATS = 1 << 2;
    private static final int REPORT_GROUP_STATS_DONE = 1 << 3;
    private static final int REPORT_SEND = 1 << 4;
    private static final int REPORT_SEND_DONE = 1 << 6;

    private static final String REPOSITORY_REPORT = "repositoryReport";
    private static final String EVENT_ID_REPORT_STATS = "twinme::stats";
    private static final String LAST_REPORT_DATE = "lastReportDate";
    private static final String NEW_REPORT_DATE = "currentReportDate";
    private static final String DEVICE_REPORT = "androidDeviceReport";
    private static final String SERVICE_REPORT = "serviceReport";
    private static final String LOCATION_REPORT = "locationReport";
    private static final String REPOSITORY_REPORT_VERSION = "4:";
    private static final String DEVICE_REPORT_VERSION = "1:";
    private static final String SERVICE_REPORT_VERSION = "2:";
    private static final String LOCATION_REPORT_VERSION = "1:";

    private static final long MIN_REPORT_DELAY = 24 * 3600 * 1000;
    private static final ConfigIdentifier LAST_REPORT_DATE_PREFERENCE = new ConfigIdentifier("TwinmeStats", "lastReportDate", "9D8EB22F-14DE-4BC7-8C39-892F249724BE", Long.class);

    private long mLastReportDate = 0;
    private long mNewReportDate = 0;
    private final StringBuilder mReport = new StringBuilder(REPOSITORY_REPORT_VERSION);
    private final Twinlife mTwinlife;
    private final ConfigurationService.Configuration mSavedConfiguration;

    public ReportStatsExecutor(@NonNull TwinmeContextImpl twinmeContextImpl, long requestId, @NonNull Twinlife twinlife) {
        super(twinmeContextImpl, requestId, LOG_TAG);
        if (DEBUG) {
            Log.d(LOG_TAG, "UpdateScoresExecutor: twinmeContextImpl=" + twinmeContextImpl + " requestId=" + requestId);
        }

        mTwinlife = twinlife;

        final ConfigurationService mConfigurationService = mTwinlife.getConfigurationService();
        mSavedConfiguration = mConfigurationService.getConfiguration(LAST_REPORT_DATE_PREFERENCE);
    }

    public long getNextDelay() {
        if (DEBUG) {
            Log.d(LOG_TAG, "start");
        }

        mNewReportDate = System.currentTimeMillis();
        mLastReportDate = mSavedConfiguration.getLongConfig(LAST_REPORT_DATE_PREFERENCE, 0);
        final long mNextReportDate = mLastReportDate + MIN_REPORT_DELAY;
        return mNextReportDate - mNewReportDate;
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mRestarted) {
            mRestarted = false;

            if ((mState & REPORT_CONTACT_STATS) != 0 && (mState & REPORT_CONTACT_STATS_DONE) == 0) {
                mState &= ~REPORT_CONTACT_STATS;
            }

            if ((mState & REPORT_CONTACT_STATS) != 0 && (mState & REPORT_CONTACT_STATS_DONE) == 0) {
                mState &= ~REPORT_CONTACT_STATS;
            }
        }
        onOperation();
    }

    //
    // Private methods
    //

    protected void onOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOperation");
        }

        if (mStopped) {

            return;
        }

        //
        // Step 1: check if a report is needed.
        //
        if (mLastReportDate == 0) {
            final long delay = getNextDelay();
            if (delay > 0) {
                mTwinmeContextImpl.onReportStats(mRequestId, mLastReportDate + MIN_REPORT_DELAY - System.currentTimeMillis());
                stop();
                return;
            }
        }

        //
        // Step 2: report stats on contacts.
        //
        if ((mState & REPORT_CONTACT_STATS) == 0) {
            mState |= REPORT_CONTACT_STATS;

            onReportContactStats(mTwinmeContextImpl.getRepositoryService().reportStats(Contact.SCHEMA_ID));
        }

        //
        // Step 3: report stats on groups.
        //
        if ((mState & REPORT_GROUP_STATS) == 0) {
            mState |= REPORT_GROUP_STATS;

            onReportGroupStats(mTwinmeContextImpl.getRepositoryService().reportStats(Group.SCHEMA_ID));
        }

        //
        // Step 4: send the report
        //
        if ((mState & REPORT_SEND) == 0) {
            mState |= REPORT_SEND;

            final Map<String, String> attributes = new HashMap<>();
            attributes.put(LAST_REPORT_DATE, String.valueOf(mLastReportDate));
            attributes.put(NEW_REPORT_DATE, String.valueOf(mNewReportDate));
            attributes.put(REPOSITORY_REPORT, mReport.toString());

            final DeviceInfo deviceInfo = mTwinmeContextImpl.getJobService().getDeviceInfo(true);
            final StringBuilder deviceReport = new StringBuilder();
            deviceReport.append(DEVICE_REPORT_VERSION);
            deviceReport.append(":");
            deviceReport.append(deviceInfo.getAppStandbyBucket());
            deviceReport.append(deviceInfo.isBackgroundRestricted() ? ":1" : ":0");
            deviceReport.append(deviceInfo.isIgnoringBatteryOptimizations() ? ":1" : ":0");
            deviceReport.append(deviceInfo.isCharging() ? ":1" : ":0");
            deviceReport.append(":");
            deviceReport.append(deviceInfo.getBatteryLevel());
            deviceReport.append(":");
            deviceReport.append(deviceInfo.getBackgroundTime());
            deviceReport.append(":");
            deviceReport.append(deviceInfo.getForegroundTime());
            deviceReport.append(":");
            deviceReport.append(deviceInfo.getFCMCount());
            deviceReport.append(":");
            deviceReport.append(deviceInfo.getFCMDowngradeCount());
            deviceReport.append(":");
            deviceReport.append(deviceInfo.getFCMTotalDelay());
            attributes.put(DEVICE_REPORT, deviceReport.toString());

            final Map<String, ServiceStats> serviceStats = mTwinmeContextImpl.getServiceStats();
            final StringBuilder serviceReport = new StringBuilder();
            serviceReport.append(SERVICE_REPORT_VERSION);
            for (Map.Entry<String, ServiceStats> stat : serviceStats.entrySet()) {
                final ServiceStats info = stat.getValue();
                if (info.sendPacketCount > 0 || info.sendErrorCount > 0 || info.sendDisconnectedCount > 0 || info.sendTimeoutCount > 0) {
                    serviceReport.append(":");
                    serviceReport.append(stat.getKey());
                    serviceReport.append("=");
                    serviceReport.append(Long.valueOf(info.sendPacketCount));
                    serviceReport.append(":");
                    serviceReport.append(Long.valueOf(info.sendDisconnectedCount));
                    serviceReport.append(":");
                    serviceReport.append(Long.valueOf(info.sendErrorCount));
                    serviceReport.append(":");
                    serviceReport.append(Long.valueOf(info.sendTimeoutCount));
                }
            }
            attributes.put(SERVICE_REPORT, serviceReport.toString());

            // Send the last known location if the report is enabled and we know the position.
            if (TwinmeContext.ENABLE_REPORT_LOCATION) {
                final String report = LocationReport.getReport(mTwinlife);
                if (report != null) {
                    attributes.put(LOCATION_REPORT, LOCATION_REPORT_VERSION + report);
                }
            }

            mTwinmeContextImpl.getManagementService().logEvent(EVENT_ID_REPORT_STATS, attributes, true);

            //
            // Last Step: checkpoint for the next report.
            //
            mTwinmeContextImpl.getRepositoryService().checkpointStats();

            mSavedConfiguration.setLongConfig(LAST_REPORT_DATE_PREFERENCE, mNewReportDate);
            mSavedConfiguration.save();

            mState |= REPORT_SEND_DONE;
        }

        mTwinmeContextImpl.onReportStats(mRequestId, mNewReportDate + MIN_REPORT_DELAY - System.currentTimeMillis());

        stop();
    }

    // Tables that defines the values and their order to put in the report.
    private static final StatType[] contactSendReport = {
            StatType.NB_MESSAGE_SENT,
            StatType.NB_IMAGE_SENT,
            StatType.NB_VIDEO_SENT,
            StatType.NB_FILE_SENT,
            StatType.NB_AUDIO_SENT,
            StatType.NB_GEOLOCATION_SENT,
            StatType.NB_TWINCODE_SENT
    };
    private static final StatType[] contactReceiveReport = {
            StatType.NB_MESSAGE_RECEIVED,
            StatType.NB_IMAGE_RECEIVED,
            StatType.NB_VIDEO_RECEIVED,
            StatType.NB_FILE_RECEIVED,
            StatType.NB_AUDIO_RECEIVED,
            StatType.NB_GEOLOCATION_RECEIVED,
            StatType.NB_TWINCODE_RECEIVED
    };
    private static final StatType[] contactSendAudioReport = {
            StatType.NB_AUDIO_CALL_SENT
    };
    private static final StatType[] contactReceiveAudioReport = {
            StatType.NB_AUDIO_CALL_RECEIVED,
            StatType.NB_AUDIO_CALL_MISSED,
    };
    private static final StatType[] contactSendVideoReport = {
            StatType.NB_VIDEO_CALL_SENT
    };
    private static final StatType[] contactReceiveVideoReport = {
            StatType.NB_VIDEO_CALL_RECEIVED,
            StatType.NB_VIDEO_CALL_MISSED,
    };
    private static final StatType[] groupSendReport = {
            StatType.NB_MESSAGE_SENT,
            StatType.NB_IMAGE_SENT,
            StatType.NB_VIDEO_SENT,
            StatType.NB_FILE_SENT,
            StatType.NB_AUDIO_SENT,
            StatType.NB_GEOLOCATION_SENT,
            StatType.NB_TWINCODE_SENT
    };
    private static final StatType[] groupReceiveReport = {
            StatType.NB_MESSAGE_RECEIVED,
            StatType.NB_IMAGE_RECEIVED,
            StatType.NB_VIDEO_RECEIVED,
            StatType.NB_FILE_RECEIVED,
            StatType.NB_AUDIO_RECEIVED,
            StatType.NB_GEOLOCATION_RECEIVED,
            StatType.NB_TWINCODE_RECEIVED
    };

    private void reportStats(@NonNull String name, @NonNull RepositoryService.ObjectStatReport stat, StatType[] report) {
        if (DEBUG) {
            Log.d(LOG_TAG, "reportStats name=" + name + " stat=" + stat);
        }

        boolean empty = true;
        for (StatType kind : report) {
            if (stat.counters[kind.ordinal()] != 0) {
                empty = false;
                break;
            }
        }

        // Report the stats when there are some values.
        if (!empty) {
            mReport.append(name);
            for (StatType kind : report) {
                mReport.append(":");
                mReport.append(stat.counters[kind.ordinal()]);
            }
        }
    }

    private void onReportContactStats(@Nullable RepositoryService.StatReport stats) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onReportContactStats stats=" + stats);
        }

        mState |= REPORT_CONTACT_STATS_DONE;
        if (stats != null) {
            mReport.append(":contacts:");
            mReport.append(stats.objectCount);
            mReport.append(":");
            mReport.append(stats.certifiedCount);
            mReport.append(":");
            mReport.append(stats.invitationCodeCount);
            if (stats.stats.isEmpty()) {
                mReport.append(":");
            } else {
                for (RepositoryService.ObjectStatReport stat : stats.stats) {
                    reportStats(":csend", stat, contactSendReport);
                    reportStats(":crecv", stat, contactReceiveReport);
                    reportStats(":asend", stat, contactSendAudioReport);
                    reportStats(":arecv", stat, contactReceiveAudioReport);
                    reportStats(":vsend", stat, contactSendVideoReport);
                    reportStats(":vrecv", stat, contactReceiveVideoReport);
                    mReport.append(";");
                }
            }
        }
    }

    private void onReportGroupStats(@Nullable RepositoryService.StatReport stats) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onReportGroupStats stats=" + stats);
        }

        mState |= REPORT_GROUP_STATS_DONE;
        if (stats != null) {
            mReport.append(":groups:");
            mReport.append(stats.objectCount + stats.certifiedCount + stats.invitationCodeCount);
            if (stats.stats.isEmpty()) {
                mReport.append(":");
            } else {
                for (RepositoryService.ObjectStatReport stat : stats.stats) {
                    reportStats(":gsend", stat, groupSendReport);
                    reportStats(":grecv", stat, groupReceiveReport);
                    mReport.append(";");
                }
            }
        }
    }
}
