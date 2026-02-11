/*
 *  Copyright (c) 2014-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Denis Campredon (Denis.Campredon@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConfigIdentifier;
import org.twinlife.twinlife.ConfigurationService;
import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ConversationService.DescriptorAnnotation;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.ConversationService.GroupConversation;
import org.twinlife.twinlife.ConversationService.UpdateType;
import org.twinlife.twinlife.DisplayCallsMode;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.ImageTools;
import org.twinlife.twinlife.JobService;
import org.twinlife.twinlife.Notification;
import org.twinlife.twinlife.NotificationService;
import org.twinlife.twinlife.NotificationService.NotificationStat;
import org.twinlife.twinlife.NotificationService.NotificationType;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.Offer;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.RepositoryService.StatType;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeInboundService;
import org.twinlife.twinlife.TwincodeInvocation;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeURI;
import org.twinlife.twinlife.TwinlifeContext;
import org.twinlife.twinlife.TwinlifeContextImpl;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.conversation.GroupProtocol;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.actions.TwinmeAction;
import org.twinlife.twinme.executors.BindContactExecutor;
import org.twinlife.twinme.executors.BindAccountMigrationExecutor;
import org.twinlife.twinme.executors.ChangeCallReceiverTwincodeExecutor;
import org.twinlife.twinme.executors.ChangeProfileTwincodeExecutor;
import org.twinlife.twinme.executors.CreateCallReceiverExecutor;
import org.twinlife.twinme.executors.CreateContactPhase1Executor;
import org.twinlife.twinme.executors.CreateContactPhase2Executor;
import org.twinlife.twinme.executors.CreateAccountMigrationExecutor;
import org.twinlife.twinme.executors.CreateGroupExecutor;
import org.twinlife.twinme.executors.CreateInvitationCodeExecutor;
import org.twinlife.twinme.executors.CreateInvitationExecutor;
import org.twinlife.twinme.executors.CreateProfileExecutor;
import org.twinlife.twinme.executors.CreateSpaceExecutor;
import org.twinlife.twinme.executors.DeleteAccountExecutor;
import org.twinlife.twinme.executors.DeleteCallReceiverExecutor;
import org.twinlife.twinme.executors.DeleteContactExecutor;
import org.twinlife.twinme.executors.DeleteAccountMigrationExecutor;
import org.twinlife.twinme.executors.DeleteGroupExecutor;
import org.twinlife.twinme.executors.DeleteInvitationExecutor;
import org.twinlife.twinme.executors.DeleteLevelExecutor;
import org.twinlife.twinme.executors.DeleteProfileExecutor;
import org.twinlife.twinme.executors.DeleteSpaceExecutor;
import org.twinlife.twinme.executors.Executor;
import org.twinlife.twinme.executors.GetAccountMigrationExecutor;
import org.twinlife.twinme.executors.GetGroupMemberExecutor;
import org.twinlife.twinme.executors.GetInvitationCodeExecutor;
import org.twinlife.twinme.executors.GetSpacesExecutor;
import org.twinlife.twinme.executors.GroupRegisteredExecutor;
import org.twinlife.twinme.executors.GroupSubscribeExecutor;
import org.twinlife.twinme.executors.ListMembersExecutor;
import org.twinlife.twinme.executors.ProcessInvocationExecutor;
import org.twinlife.twinme.executors.RebindContactExecutor;
import org.twinlife.twinme.executors.RefreshObjectExecutor;
import org.twinlife.twinme.executors.ReportStatsExecutor;
import org.twinlife.twinme.executors.UnbindContactExecutor;
import org.twinlife.twinme.executors.UpdateCallReceiverExecutor;
import org.twinlife.twinme.executors.UpdateContactAndIdentityExecutor;
import org.twinlife.twinme.executors.UpdateGroupExecutor;
import org.twinlife.twinme.executors.UpdateNotificationExecutor;
import org.twinlife.twinme.executors.UpdateProfileExecutor;
import org.twinlife.twinme.executors.UpdateScoresExecutor;
import org.twinlife.twinme.executors.UpdateSettingsExecutor;
import org.twinlife.twinme.executors.UpdateSpaceExecutor;
import org.twinlife.twinme.executors.VerifyContactExecutor;
import org.twinlife.twinme.models.AccountMigrationFactory;
import org.twinlife.twinme.models.CallReceiver;
import org.twinlife.twinme.models.CallReceiverFactory;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.ContactFactory;
import org.twinlife.twinme.models.AccountMigration;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupFactory;
import org.twinlife.twinme.models.GroupMember;
import org.twinlife.twinme.models.GroupRegisteredInvocation;
import org.twinlife.twinme.models.GroupSubscribeInvocation;
import org.twinlife.twinme.models.Invitation;
import org.twinlife.twinme.models.InvitationFactory;
import org.twinlife.twinme.models.Invocation;
import org.twinlife.twinme.models.NotificationContent;
import org.twinlife.twinme.models.Originator;
import org.twinlife.twinme.models.PairBindInvocation;
import org.twinlife.twinme.models.PairInviteInvocation;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.PairRefreshInvocation;
import org.twinlife.twinme.models.PairUnbindInvocation;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.ProfileFactory;
import org.twinlife.twinme.models.RoomCommand;
import org.twinlife.twinme.models.RoomConfig;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.models.SpaceSettings;
import org.twinlife.twinme.models.SpaceSettingsFactory;
import org.twinlife.twinme.util.LocationReport;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class TwinmeContextImpl extends TwinlifeContextImpl implements TwinmeContext {
    private static final String LOG_TAG = "TwinmeContextImpl";
    private static final boolean DEBUG = false;
    private static final boolean INFO = org.twinlife.twinlife.BuildConfig.ENABLE_INFO_LOG;

    private static final boolean DELETE_CONTACT_ON_UNBIND_CONTACT = BuildConfig.DELETE_CONTACT_ON_UNBIND_CONTACT;
    private static final int NOTIFICATION_REFRESH_DELAY = 1000; // 1 second
    public static final boolean GET_REMOTE_OBJECTS = false;

    private static final String AES_MODE = "AES/CBC/PKCS7Padding";
    private static final int IV_LENGTH = 16;

    private class ConversationServiceObserver extends ConversationService.DefaultServiceObserver {

        @Override
        public void onPopDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onPopObject: requestId=" + requestId + " conversation=" + conversation + " descriptor=" + descriptor);
            }

            TwinmeContextImpl.this.onPopDescriptor(conversation, descriptor);
        }

        @Override
        public void onPushDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onPushDescriptor: requestId=" + requestId + " conversation=" + conversation + " descriptor=" + descriptor);
            }

            TwinmeContextImpl.this.onPushDescriptor(conversation, descriptor);
        }

        @Override
        public void onUpdateDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor, UpdateType updateType) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onUpdateDescriptor: requestId=" + requestId + " conversation=" + conversation + " descriptor=" + descriptor +
                        " updateType=" + updateType);
            }

            TwinmeContextImpl.this.onUpdateDescriptor(conversation, descriptor, updateType);
        }

        @Override
        public void onUpdateAnnotation(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor,
                                       @NonNull TwincodeOutbound annotatingUser) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onUpdateAnnotation: requestId=" + requestId
                        + " conversation=" + conversation + " descriptor=" + descriptor + " annotatingUser=" + annotatingUser);
            }

            TwinmeContextImpl.this.onUpdateAnnotation(conversation, descriptor, annotatingUser);
        }

        @Override
        public void onLeaveGroup(long requestId, @NonNull GroupConversation conversation, @NonNull UUID memberId) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onLeaveGroup: conversation=" + conversation + " memberId=" + memberId);
            }

            TwinmeContextImpl.this.onLeaveGroup(memberId, conversation);
        }

        @Override
        public void onRevoked(@NonNull Conversation conversation) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onRevoked: conversation=" + conversation);
            }

            TwinmeContextImpl.this.onRevoked(conversation);
        }

        @Override
        public void onSignatureInfo(@NonNull Conversation conversation, @NonNull TwincodeOutbound twincodeOutbound) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationServiceObserver.onSignatureInfo: twincodeOutbound=" + twincodeOutbound);
            }

            TwinmeContextImpl.this.onSignatureInfo(conversation, twincodeOutbound);

        }
    }

    private class TwincodeInboundServiceListener implements TwincodeInboundService.InvocationListener {

        @Override
        @Nullable
        public ErrorCode onInvokeTwincode(@NonNull TwincodeInvocation invocation) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeInboundServiceObserver.onInvokeTwincode: invocation=" + invocation);
            }

            return TwinmeContextImpl.this.onInvokeTwincodeInbound(invocation);
        }
    }

    private class TwincodeOutboundServiceObserver implements TwincodeOutboundService.ServiceObserver {

        @Override
        public void onRefreshTwincode(@NonNull TwincodeOutbound twincodeOutbound,
                                      @NonNull List<AttributeNameValue> previousAttributes) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeOutboundServiceObserver.onRefreshTwincode: twincodeOutbound=" + twincodeOutbound);
            }

            TwinmeContextImpl.this.onRefreshTwincodeOutbound(twincodeOutbound, previousAttributes);
        }
    }

    private class PeerConnectionServiceObserver extends PeerConnectionService.DefaultServiceObserver {

        @Override
        public void onIncomingPeerConnection(@NonNull UUID peerConnectionId, @NonNull String peerId, @NonNull Offer offer) {
            if (DEBUG) {
                Log.d(LOG_TAG, "TwincodeOutboundServiceObserver.onIncomingPeerConnection: peerConnectionId=" + peerConnectionId + " peerId=" + peerId);
            }

            TwinmeContextImpl.this.onIncomingPeerConnection(peerConnectionId, peerId, offer);
        }
    }

    private class RepositoryServiceObserver implements RepositoryService.ServiceObserver {

        @Override
        public void onInvalidObject(@NonNull RepositoryObject object) {
            if (DEBUG) {
                Log.d(LOG_TAG, "RepositoryServiceObserver.onInvalidObject: object=" + object);
            }

            TwinmeContextImpl.this.onInvalidObject(object);
        }
    }

    private class NotificationServiceObserver implements NotificationService.ServiceObserver {

        @Override
        public void onCanceledNotifications(@NonNull List<Long> notificationIds) {
            if (DEBUG) {
                Log.d(LOG_TAG, "NotificationServiceObserver.onCanceledNotifications: notificationIds=" + notificationIds);
            }

            for (long notificationId : notificationIds) {
                mNotificationCenter.cancel((int) notificationId);
            }
        }
    }

    private final TwinmeApplication mTwinmeApplication;
    private volatile Profile mCurrentProfile;
    private volatile Space mCurrentSpace;
    private final HashMap<UUID, GroupMember> mGroupMembers = new HashMap<>();
    private volatile boolean mGetSpacesDone = false;
    private volatile boolean mHasProfiles = false;
    private volatile boolean mHasSpaces = false;
    private final AtomicReference<UUID> mActiveConversationId = new AtomicReference<>(null);
    private final HashMap<UUID, Space> mSpaces = new HashMap<>();
    private final HashMap<Class<?>, Executor> mExecutors = new HashMap<>();
    private Map<String, String> mCopyTwincodeAttributes;
    @NonNull
    private SpaceSettings mDefaultSpaceSettings = new SpaceSettings("");
    @Nullable
    private String mOldDefaultLabel = null;
    private UUID mDefaultSpaceId;
    @Nullable
    private UUID mDefaultSettingsId;
    private final NotificationCenter mNotificationCenter;
    private JobService.Job mReportJob;

    private final ConversationServiceObserver mConversationServiceObserver;
    private final PeerConnectionServiceObserver mPeerConnectionServiceObserver;
    private final TwincodeInboundServiceListener mTwincodeInboundServiceObserver;
    private final TwincodeOutboundServiceObserver mTwincodeOutboundServiceObserver;
    private final RepositoryServiceObserver mRepositoryServiceObserver;
    private final NotificationServiceObserver mNotificationServiceObserver;
    private final boolean mEnableSpaces;
    private final TreeSet<TwinmeAction> mPendingActions;
    private TwinmeAction mFirstAction;
    private JobService.Job mActionTimeoutJob;
    private JobService.Job mNotificationRefreshJob;
    private NotificationStat mVisibleNotificationStat;
    private long mReportRequestId = BaseService.DEFAULT_REQUEST_ID;

    private static final ConfigIdentifier DEFAULT_SPACE_ID = new ConfigIdentifier("spaces", "defaultSpaceId", "D7E5E971-2813-4418-AD23-D9DE2E1D085F");
    private static final ConfigIdentifier DEFAULT_SETTINGS_ID = new ConfigIdentifier("spaces", "defaultSettingId", "f80f7791-15a7-4944-b743-99a84eba6fba");

    public TwinmeContextImpl(@NonNull TwinmeApplication twinmeApplication,
                             @NonNull TwinmeConfiguration twinmeConfiguration,
                             @NonNull JobService jobService,
                             @NonNull ConfigurationService configurationService) {
        // The JobService must be created very early because it installed the Android lifecycle callbacks.
        // It is also required by the foreground service that we start when a FCM message is received and
        // that foreground service can start quickly, before having the Twinlife service.
        super(twinmeConfiguration, jobService, configurationService);

        if (DEBUG) {
            Log.d(LOG_TAG, "TwinmeContextImpl: twinmeApplication=" + twinmeApplication + " twinmeConfiguration=" + twinmeConfiguration);
        }

        mTwinmeApplication = twinmeApplication;
        mEnableSpaces = twinmeConfiguration.enableSpaces;

        mNotificationCenter = mTwinmeApplication.newNotificationCenter(this);

        mConversationServiceObserver = new ConversationServiceObserver();
        mPeerConnectionServiceObserver = new PeerConnectionServiceObserver();
        mTwincodeInboundServiceObserver = new TwincodeInboundServiceListener();
        mTwincodeOutboundServiceObserver = new TwincodeOutboundServiceObserver();
        mRepositoryServiceObserver = new RepositoryServiceObserver();
        mNotificationServiceObserver = new NotificationServiceObserver();

        mPendingActions = new TreeSet<>();

        // Get default space UUID if there is one.
        ConfigurationService.Configuration spaceConfiguration = configurationService.getConfiguration(DEFAULT_SPACE_ID);
        mDefaultSpaceId = Utils.UUIDFromString(spaceConfiguration.getStringConfig(DEFAULT_SPACE_ID, null));
        mDefaultSettingsId = Utils.UUIDFromString(spaceConfiguration.getStringConfig(DEFAULT_SETTINGS_ID, null));
    }

    @Override
    public boolean isDatabaseUpgraded() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isDatabaseUpgraded");
        }

        // It can be called while Twinlife instance is not yet created.
        final TwinlifeImpl twinlife = mTwinlifeImpl;
        if (twinlife != null && twinlife.isDatabaseUpgraded()) {
            return true;
        }

        // These flags are updated from onTwinlifeReady() called from another thread.
        synchronized (this) {
            return mHasProfiles && !mHasSpaces;
        }
    }

    //
    // Twincode management
    //

    @Override
    public void registerSharedTwincodeAttribute(@NonNull String name, @NonNull String newName) {
        if (DEBUG) {
            Log.d(LOG_TAG, "registerSharedTwincodeAttribute: name=" + name + " newName=" + newName);
        }

        if (mCopyTwincodeAttributes == null) {
            mCopyTwincodeAttributes = new HashMap<>();
        }
        mCopyTwincodeAttributes.put(name, newName);
    }

    public void copySharedTwincodeAttributes(@NonNull List<AttributeNameValue> attributes,
                                             @NonNull TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "copySharedTwincodeAttributes: attributes=" + attributes
                    + " twincodeOutbound=" + twincodeOutbound);
        }

        if (mCopyTwincodeAttributes == null) {
            return;
        }

        // Copy from the twincode all the registered attributes to be copied.
        for (AttributeNameValue attribute : twincodeOutbound.getAttributes()) {
            String newName = mCopyTwincodeAttributes.get(attribute.name);
            if (newName == null) {
                continue;
            }

            if (attribute instanceof BaseService.AttributeNameBooleanValue) {
                attributes.add(new BaseService.AttributeNameBooleanValue(newName, (Boolean) attribute.value));

            } else if (attribute instanceof BaseService.AttributeNameStringValue) {
                attributes.add(new BaseService.AttributeNameStringValue(newName, (String) attribute.value));

            } else if (attribute instanceof BaseService.AttributeNameLongValue) {
                attributes.add(new BaseService.AttributeNameLongValue(newName, (Long) attribute.value));

            } else {
                Log.e(LOG_TAG, "Attribute not supported");
            }
        }
    }

    //
    // TwincodeInbound Management
    //

    @NonNull
    private RepositoryService.FindResult getReceiver(@NonNull UUID twincodeInboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getReceiver: twincodeInboundId=" + twincodeInboundId);
        }

        RepositoryObjectFactory<?>[] factories = {
                ProfileFactory.INSTANCE,
                ContactFactory.INSTANCE,
                GroupFactory.INSTANCE,
                InvitationFactory.INSTANCE,
                AccountMigrationFactory.INSTANCE,
                CallReceiverFactory.INSTANCE
        };

        return getRepositoryService().findObject(true, twincodeInboundId, factories);
    }

    //
    // Profile management
    //

    @Override
    public boolean isCurrentProfile(@NonNull Profile profile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "isCurrentProfile: profile=" + profile);
        }

        if (profile == mCurrentProfile) {

            return true;
        }

        //noinspection SimplifiableIfStatement
        if (mCurrentProfile == null) {

            return false;
        }

        return profile.getId().equals(mCurrentProfile.getId());
    }

    @Override
    public boolean isSpaceProfile(@NonNull Profile profile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "isSpaceProfile: profile=" + profile);
        }

        return profile.getSpace() == mCurrentSpace;
    }

    public void getProfile(@NonNull UUID profileId, @NonNull ConsumerWithError<Profile> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getProfile: profileId=" + profileId);
        }

        Profile profile = null;
        synchronized (mSpaces) {
            for (Space space : mSpaces.values()) {
                if (profileId.equals(space.getProfileId())) {
                    profile = space.getProfile();
                    break;
                }
            }
        }

        if (profile != null) {
            consumer.onGet(ErrorCode.SUCCESS, profile);

        } else if (mGetSpacesDone) {
            consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);

        } else {
            // We must load the spaces to known the space that is associated with the profile.
            // While loading the spaces, we also load all the profiles.
            findSpaces((Space space) -> false, (ErrorCode errorCode, List<Space> spaces) -> {
                Profile lProfile = null;
                synchronized (mSpaces) {
                    for (Space space : mSpaces.values()) {
                        if (profileId.equals(space.getProfileId())) {
                            lProfile = space.getProfile();
                            break;
                        }
                    }
                }
                if (lProfile != null) {
                    consumer.onGet(ErrorCode.SUCCESS, lProfile);
                } else {
                    consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                }
            });
        }
    }

    public void getProfiles(long requestId, @NonNull Consumer<List<Profile>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getProfiles: requestId=" + requestId);
        }

        mTwinlifeExecutor.execute(() -> {
            final RepositoryService repositoryService = getRepositoryService();

            final Filter<RepositoryObject> filter = createSpaceFilter();
            repositoryService.listObjects(ProfileFactory.INSTANCE, filter, (ErrorCode errorCode, List<RepositoryObject> list) -> {
                final List<Profile> result = new ArrayList<>();
                if (list != null) {
                    for (RepositoryObject profile : list) {
                        result.add((Profile) profile);
                    }
                }
                consumer.accept(result);
            });
        });
    }

    public boolean isProfileTwincode(@NonNull UUID twincodeId) {

        synchronized (mSpaces) {
            for (Space space : mSpaces.values()) {
                Profile profile = space.getProfile();
                if (profile != null && twincodeId.equals(profile.getTwincodeOutboundId())) {
                    return true;
                }
            }
        }

        return false;
    }

    public void createProfile(long requestId, @NonNull String name, @NonNull Bitmap avatar, @Nullable File avatarFile,
                              @Nullable String description, @Nullable Capabilities capabilities) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createProfile: requestId=" + requestId + " name=" + name + " avatarFile=" + avatarFile +
                    " description=" + description + " capabilities=" + capabilities);
        }

        CreateProfileExecutor createProfileExecutor = new CreateProfileExecutor(this, requestId, name, avatar, avatarFile,
                description, capabilities, DEFAULT_TIMEOUT);
        mTwinlifeExecutor.execute(createProfileExecutor::start);
    }

    @Override
    public void createProfile(long requestId, @NonNull String name, @NonNull Bitmap avatar, @Nullable File avatarFile,
                              @Nullable String description, @Nullable Capabilities capabilities, @NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createProfile: requestId=" + requestId + " name=" + name + " avatarFile=" + avatarFile
                    + " description=" + description + " capabilities=" + capabilities + " space=" + space);
        }

        CreateProfileExecutor createProfileExecutor = new CreateProfileExecutor(this, requestId, name, avatar, avatarFile, description, capabilities, space);
        mTwinlifeExecutor.execute(createProfileExecutor::start);
    }

    public void onCreateProfile(long requestId, @NonNull Profile profile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateProfile: requestId=" + requestId + " profile=" + profile);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onCreateProfile(requestId, profile));
            }
        }
    }

    @Override
    public void updateProfile(long requestId, @NonNull Profile profile, @NonNull Profile.UpdateMode updateMode,
                              @NonNull String identityName, @Nullable Bitmap identityAvatar, @Nullable File identityAvatarFile,
                              @Nullable String description, @Nullable Capabilities capabilities) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateProfile: requestId=" + requestId + " identityName=" + identityName
                    + " identityAvatarFile=" + identityAvatarFile + " description=" + description
                    + " capabilities=" + capabilities);
        }

        UpdateProfileExecutor updateProfileExecutor = new UpdateProfileExecutor(this, requestId, profile, updateMode,
                identityName, identityAvatar, identityAvatarFile, description, capabilities);
        mTwinlifeExecutor.execute(updateProfileExecutor::start);
    }

    public void onUpdateProfile(long requestId, @NonNull Profile profile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateProfile: requestId=" + requestId + " profile=" + profile);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onUpdateProfile(requestId, profile));
            }
        }
    }

    @Override
    public void changeProfileTwincode(@NonNull Profile profile, @NonNull ConsumerWithError<Profile> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "changeProfileTwincode: profile=" + profile);
        }

        ChangeProfileTwincodeExecutor updateProfileExecutor = new ChangeProfileTwincodeExecutor(this,
                profile, consumer);
        mTwinlifeExecutor.execute(updateProfileExecutor::start);
    }

    @Override
    public void deleteProfile(long requestId, @NonNull Profile profile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteProfile: requestId=" + requestId + " profile=" + profile);
        }

        DeleteProfileExecutor deleteProfileExecutor = new DeleteProfileExecutor(this, requestId, profile, DEFAULT_TIMEOUT);
        mTwinlifeExecutor.execute(deleteProfileExecutor::start);
    }

    public void onDeleteProfile(long requestId, @NonNull UUID profileId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteProfile: requestId=" + requestId + " profileId=" + profileId);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onDeleteProfile(requestId, profileId));
            }
        }
    }

    @Override
    public void deleteAccount(long requestId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteAccount: requestId=" + requestId);
        }

        DeleteAccountExecutor deleteAccountExecutor = new DeleteAccountExecutor(this, requestId);
        mTwinlifeExecutor.execute(deleteAccountExecutor::start);
    }

    public void onDeleteAccount(long requestId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteAccount: requestId=" + requestId);
        }


        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onDeleteAccount(requestId));
            }
        }
    }

    //
    // Identity management
    //

    @Override
    @NonNull
    public String getAnonymousName() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getAnonymousName");
        }

        return mTwinmeApplication.getAnonymousName();
    }

    @Override
    @NonNull
    public Bitmap getAnonymousAvatar() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getAnonymousAvatar");
        }

        return mTwinmeApplication.getAnonymousAvatar();
    }

    @Override
    @NonNull
    public Bitmap getDefaultAvatar() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDefaultAvatar");
        }

        return mTwinmeApplication.getDefaultAvatar();
    }

    @Override
    @NonNull
    public Bitmap getDefaultGroupAvatar() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDefaultGroupAvatar");
        }

        return mTwinmeApplication.getDefaultGroupAvatar();
    }

    //
    // Contact management
    //

    @Override
    public void getContact(@NonNull UUID contactId, @NonNull ConsumerWithError<Contact> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getContact: contactId=" + contactId);
        }

        mTwinlifeExecutor.execute(() -> getRepositoryService().getObject(contactId, ContactFactory.INSTANCE,
                (ErrorCode errorCode, RepositoryObject object) -> consumer.onGet(errorCode, (Contact) object)));
    }

    @Override
    public void findContacts(@NonNull Filter<RepositoryObject> filter, @NonNull Consumer<List<Contact>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findContacts: filter=" + filter + " consumer=" + consumer);
        }

        mTwinlifeExecutor.execute(() -> getRepositoryService().listObjects(ContactFactory.INSTANCE, filter, (ErrorCode errorCode, List<RepositoryObject> list) -> {
            final List<Contact> result = new ArrayList<>(list != null ? list.size() : 0);

            if (list != null) {
                for (RepositoryObject object : list) {
                    result.add((Contact) object);
                }
            }
            consumer.accept(result);
        }));
    }

    @Override
    public void createContactPhase1(long requestId, @NonNull TwincodeOutbound peerTwincodeOutbound,
                                    @Nullable Space space, @NonNull Profile profile,
                                    @Nullable GroupMember contactGroupMember) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createContactPhase1: requestId=" + requestId + " peerTwincodeOutbound=" + peerTwincodeOutbound +
                    " profile=" + profile);
        }

        if (space == null) {
            space = mCurrentSpace;
        }
        CreateContactPhase1Executor createContactPhase1Executor = new CreateContactPhase1Executor(this, requestId, peerTwincodeOutbound,
                space, profile, contactGroupMember);
        mTwinlifeExecutor.execute(createContactPhase1Executor::start);
    }

    @Override
    public void createContactPhase1(long requestId, @NonNull TwincodeOutbound peerTwincodeOutbound,
                                    @Nullable Space space, @NonNull String identityName, @NonNull ImageId avatarId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createContactPhase1: requestId=" + requestId + " peerTwincodeOutbound=" + peerTwincodeOutbound +
                    " identityName=" + identityName);
        }

        if (space == null) {
            space = mCurrentSpace;
        }
        CreateContactPhase1Executor createContactPhase1Executor = new CreateContactPhase1Executor(this, requestId, peerTwincodeOutbound,
                space, identityName, avatarId, null);
        mTwinlifeExecutor.execute(createContactPhase1Executor::start);
    }

    private void createContactPhase2(@NonNull PairInviteInvocation invocation, @NonNull Profile profile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createContactPhase2: invocation=" + invocation + " profile=" + profile);
        }

        CreateContactPhase2Executor createContactPhase2Executor = new CreateContactPhase2Executor(this, invocation, profile);
        mTwinlifeExecutor.execute(createContactPhase2Executor::start);
    }

    private void createContactPhase2(@NonNull PairInviteInvocation invocation, @NonNull Invitation invitation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createContactPhase2: invocation=" + invocation + " invitation=" + invitation);
        }

        CreateContactPhase2Executor createContactPhase2Executor = new CreateContactPhase2Executor(this, invocation, invitation);
        mTwinlifeExecutor.execute(createContactPhase2Executor::start);
    }

    public void onCreateContact(long requestId, @NonNull Contact contact) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateContact: requestId=" + requestId + " contact=" + contact);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onCreateContact(requestId, contact));
            }
        }
    }

    @Override
    public void updateContact(long requestId, @NonNull Contact contact, @NonNull String contactName, @Nullable String description) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateContact: requestId=" + requestId + " contact=" + contact
                    + " contactName=" + contactName + " description=" + description);
        }

        UpdateContactAndIdentityExecutor updateContactAndIdentityExecutor = new UpdateContactAndIdentityExecutor(this, requestId,
                contact, contactName, description);
        mTwinlifeExecutor.execute(updateContactAndIdentityExecutor::start);
    }

    @Override
    public void updateContactIdentity(long requestId, @NonNull Contact contact, @NonNull String identityName,
                                      @Nullable Bitmap identityAvatar, @Nullable File identityAvatarFile,
                                      @Nullable String description, @Nullable Capabilities capabilities, @Nullable Capabilities privateCapabilities) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateContactAndIdentity: requestId=" + requestId + " contact=" + contact
                    + " identityName=" + identityName + " identityAvatarFile=" + identityAvatarFile
                    + " description=" + description + " capabilities=" + capabilities);
        }

        UpdateContactAndIdentityExecutor updateContactAndIdentityExecutor = new UpdateContactAndIdentityExecutor(this, requestId,
                contact, identityName, identityAvatar, identityAvatarFile, description, capabilities, privateCapabilities);
        mTwinlifeExecutor.execute(updateContactAndIdentityExecutor::start);
    }

    @Override
    public void updateContactIdentity(long requestId, @NonNull Contact contact, @NonNull String identityName,
                                      @Nullable ImageId identityAvatarId, @Nullable String description,
                                      @Nullable Capabilities capabilities, @Nullable Capabilities privateCapabilities) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateContactAndIdentity: requestId=" + requestId + " contact=" + contact
                    + " identityName=" + identityName + " identityAvatarId=" + identityAvatarId
                    + " description=" + description + " capabilities=" + capabilities);
        }

        UpdateContactAndIdentityExecutor updateContactAndIdentityExecutor = new UpdateContactAndIdentityExecutor(this, requestId,
                contact, identityName, identityAvatarId, description, capabilities, privateCapabilities, DEFAULT_TIMEOUT);
        mTwinlifeExecutor.execute(updateContactAndIdentityExecutor::start);
    }

    private void bindContact(@NonNull PairBindInvocation invocation, @NonNull Contact contact) {
        if (DEBUG) {
            Log.d(LOG_TAG, "bindContact: invocation=" + invocation + " contact=" + contact);
        }

        BindContactExecutor bindContactExecutor = new BindContactExecutor(this, invocation, contact);
        mTwinlifeExecutor.execute(bindContactExecutor::start);
    }

    @Override
    public void unbindContact(long requestId, @Nullable UUID invocationId, @NonNull Contact contact) {
        if (DEBUG) {
            Log.d(LOG_TAG, "unbindContact: requestId=" + requestId + " invocationId=" + invocationId + " contact=" + contact);
        }

        if (DELETE_CONTACT_ON_UNBIND_CONTACT) {
            DeleteContactExecutor deleteContactExecutor = new DeleteContactExecutor(this, requestId, contact,
                    invocationId, 0);
            mTwinlifeExecutor.execute(deleteContactExecutor::start);
        } else {
            UnbindContactExecutor unbindContactExecutor = new UnbindContactExecutor(this, requestId, invocationId, contact);
            mTwinlifeExecutor.execute(unbindContactExecutor::start);
        }
    }

    @Override
    public void verifyContact(@NonNull TwincodeURI twincodeURI, @NonNull TrustMethod trustMethod,
                              @NonNull ConsumerWithError<Contact> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "verifyContact: twincodeURI=" + twincodeURI);
        }

        VerifyContactExecutor verifyContactExecutor = new VerifyContactExecutor(this, twincodeURI, trustMethod, complete);
        mTwinlifeExecutor.execute(verifyContactExecutor::start);
    }

    private void refreshRepositoryObject(@NonNull PairRefreshInvocation invocation, @NonNull Originator subject) {
        if (DEBUG) {
            Log.d(LOG_TAG, "refreshRepositoryObject: invocation=" + invocation + " subject=" + subject);
        }

        RefreshObjectExecutor refreshObjectExecutor = new RefreshObjectExecutor(this, invocation, subject);
        mTwinlifeExecutor.execute(refreshObjectExecutor::start);
    }

    public void onUpdateContact(long requestId, @NonNull Contact contact) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateContact: requestId=" + requestId + " contact=" + contact);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onUpdateContact(requestId, contact));
            }
        }
    }

    public void onMoveToSpace(long requestId, @NonNull Contact contact, @NonNull Space oldSpace) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onMoveToSpace: requestId=" + requestId + " contact=" + contact + " oldSpace=" + oldSpace);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onMoveToSpace(requestId, contact, oldSpace));
            }
        }
    }

    @Override
    public void deleteContact(long requestId, @NonNull Contact contact) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteContact: requestId=" + requestId + " contact=" + contact);
        }

        DeleteContactExecutor deleteContactExecutor = new DeleteContactExecutor(this, requestId, contact, null, DEFAULT_TIMEOUT);
        mTwinlifeExecutor.execute(deleteContactExecutor::start);
    }

    public void onDeleteContact(long requestId, @NonNull UUID contactId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteContact: requestId=" + requestId + " contactId=" + contactId);
        }

        scheduleRefreshNotifications();

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onDeleteContact(requestId, contactId));
            }
        }
    }

    @Override
    public void getOriginator(@NonNull UUID originatorId, @NonNull ConsumerWithError<Originator> consumer) {
        getOriginator(originatorId, null, consumer);
    }

    @Override
    public void getOriginator(@NonNull UUID originatorId, @Nullable UUID groupId, @NonNull ConsumerWithError<Originator> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getOriginator: originatorId=" + originatorId);
        }

        if (groupId != null) {
            //group != null => We're looking for a group member (incoming group call originator)
            getGroup(groupId, (ErrorCode errorCode, Group group) -> {
                if (group != null) {
                    getGroupMember(group, originatorId, consumer::onGet);
                } else {
                    consumer.onGet(errorCode, null);
                }
            });
            return;
        }

        // Check if we've already loaded the originator in the cache
        RepositoryObjectFactory<?>[] factories = {
                ContactFactory.INSTANCE,
                CallReceiverFactory.INSTANCE,
                GroupFactory.INSTANCE
        };
        mTwinlifeExecutor.execute(() -> {
            RepositoryService.FindResult result = getRepositoryService().findObject(
                    false,
                    originatorId,
                    factories);
            consumer.onGet(result.errorCode, (Originator) result.object);
        });
    }

    @Override
    public void createInvitation(long requestId, @Nullable GroupMember contactGroupMember) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createInvitation: requestId=" + requestId + " contactGroupMember=" + contactGroupMember);
        }

        CreateInvitationExecutor createInvitationExecutor = new CreateInvitationExecutor(this, requestId, mCurrentSpace, contactGroupMember);
        mTwinlifeExecutor.execute(createInvitationExecutor::start);
    }

    @Override
    public void createInvitation(long requestId, @NonNull Group group, long permissions) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createInvitation: requestId=" + requestId + " group=" + group + " permissions=" + permissions);
        }

        CreateInvitationExecutor createInvitationExecutor = new CreateInvitationExecutor(this, requestId, mCurrentSpace, group, permissions);
        mTwinlifeExecutor.execute(createInvitationExecutor::start);
    }

    @Override
    public void createInvitation(long requestId, @NonNull Contact contact, @NonNull UUID sendTo) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createInvitation: requestId=" + requestId + " contact=" + contact + " sendTo=" + sendTo);
        }

        CreateInvitationExecutor createInvitationExecutor = new CreateInvitationExecutor(this, requestId, mCurrentSpace, contact, sendTo);
        mTwinlifeExecutor.execute(createInvitationExecutor::start);
    }

    public void onCreateInvitation(long requestId, @NonNull Invitation invitation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateInvitation: requestId=" + requestId + " invitation=" + invitation);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onCreateInvitation(requestId, invitation));
            }
        }
    }

    @Override
    public void updateInvitation(@NonNull Invitation invitation, @NonNull ConsumerWithError<Invitation> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateInvitation: invitation=" + invitation + " consumer=" + consumer);
        }

        mTwinlifeExecutor.execute(() -> getRepositoryService().updateObject(invitation, (status, updatedInvitation) -> {
            if (status != ErrorCode.SUCCESS || updatedInvitation == null) {
                consumer.onGet(status, null);
            }

            consumer.onGet(ErrorCode.SUCCESS, (Invitation) updatedInvitation);
        }));
    }

    @Override
    public void getInvitation(@NonNull UUID invitationId, @NonNull ConsumerWithError<Invitation> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getInvitation: invitationId=" + invitationId);
        }

        mTwinlifeExecutor.execute(() -> getRepositoryService().getObject(invitationId, InvitationFactory.INSTANCE,
                (ErrorCode errorCode, RepositoryObject object) -> consumer.onGet(errorCode, (Invitation) object)));
    }

    @Override
    public void findInvitations(@NonNull Filter<RepositoryObject> filter, @NonNull Consumer<List<Invitation>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findInvitations: filter=" + filter + " consumer=" + consumer);
        }

        mTwinlifeExecutor.execute(() -> getRepositoryService().listObjects(InvitationFactory.INSTANCE, filter, (ErrorCode errorCode, List<RepositoryObject> list) -> {
            final List<Invitation> result = new ArrayList<>(list != null ? list.size() : 0);

            if (list != null) {
                for (RepositoryObject object : list) {
                    result.add((Invitation) object);
                }
            }
            consumer.accept(result);
        }));
    }

    @Override
    public void deleteInvitation(long requestId, @NonNull Invitation invitation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteInvitation: requestId=" + requestId + " invitation=" + invitation);
        }

        DeleteInvitationExecutor deleteInvitationExecutor = new DeleteInvitationExecutor(this, requestId, invitation, DEFAULT_TIMEOUT);
        mTwinlifeExecutor.execute(deleteInvitationExecutor::start);
    }

    public void onDeleteInvitation(long requestId, @NonNull UUID invitationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteInvitation: requestId=" + requestId + " invitationId=" + invitationId);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onDeleteInvitation(requestId, invitationId));
            }
        }
    }

    private void deleteInvitation(@NonNull ConversationService.TwincodeDescriptor twincodeDescriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteInvitation: twincodeDescriptor=" + twincodeDescriptor);
        }

        Filter<RepositoryObject> filter = new Filter<RepositoryObject>(null) {
            @Override
            public boolean accept(@NonNull RepositoryObject object) {
                if (!(object instanceof Invitation)) {
                    return false;
                }

                final Invitation invitation = (Invitation) object;
                return twincodeDescriptor.getTwincodeId().equals(invitation.getTwincodeOutboundId());
            }
        };

        // Kludge: trigger a findInvitations to check a matching invitation and deleted it because its descriptor is deleted.
        findInvitations(filter,
                (List<Invitation> invitations) -> {
                });
    }

    @Override
    public void createInvitationWithCode(long requestId, int validityPeriod) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createInvitationCode: requestId=" + requestId + " validityPeriod=" + validityPeriod);
        }

        CreateInvitationCodeExecutor executor = new CreateInvitationCodeExecutor(this, requestId, validityPeriod);
        mTwinlifeExecutor.execute(executor::start);
    }

    public void onCreateInvitationWithCode(long requestId, @NonNull Invitation invitation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateInvitationCode: requestId=" + requestId + " invitation=" + invitation);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onCreateInvitationWithCode(requestId, invitation));
            }
        }
    }

    @Override
    public void getInvitationCode(long requestId, @NonNull String code) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getInvitationCode: code=" + code);
        }

        GetInvitationCodeExecutor executor = new GetInvitationCodeExecutor(this, requestId, code);
        mTwinlifeExecutor.execute(executor::start);
    }

    public void onGetInvitationCode(long requestId, @NonNull TwincodeOutbound twincodeOutbound, @Nullable String publicKey) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetInvitationCode: requestId=" + requestId + " twincodeOutbound=" + twincodeOutbound + " publicKey=" + publicKey);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onGetInvitationCode(requestId, twincodeOutbound, publicKey));
            }
        }
    }

    @Override
    public void updateScores(long requestId, boolean updateScore) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateStats: requestId=" + requestId + " updateScore=" + updateScore);
        }

        UpdateScoresExecutor updateScoresExecutor = new UpdateScoresExecutor(this, requestId, updateScore);
        mTwinlifeExecutor.execute(updateScoresExecutor::start);
    }

    public void onUpdateScores(long requestId, @NonNull List<RepositoryObject> contacts, @NonNull List<RepositoryObject> groups) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateStats: requestId=" + requestId);
        }

        /* SCz List<Contact> updatedContacts = new ArrayList<>();
        synchronized (mContacts) {
            for (RepositoryObject object : contacts) {
                Contact contact = mContacts.get(object.getId());
                if (contact != null) {
                    if (contact.updateScore((Contact) object) && isCurrentSpace(contact)) {
                        updatedContacts.add(contact);
                    }
                }
            }
        }

        List<Group> updatedGroups = new ArrayList<>();
        synchronized (mGroups) {
            for (RepositoryObject object : groups) {
                Group group = mGroups.get(object.getId());
                if (group != null) {
                    if (group.updateScore(object) && isCurrentSpace(group)) {
                        updatedGroups.add(group);
                    }
                }
            }
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onUpdateStats(requestId, updatedContacts, updatedGroups));
            }
        } */
    }

    //
    // Groups
    //

    @Override
    public void findGroups(@NonNull Filter<RepositoryObject> filter, @NonNull Consumer<List<Group>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findGroups: filter=" + filter + " consumer=" + consumer);
        }

        mTwinlifeExecutor.execute(() -> getRepositoryService().listObjects(GroupFactory.INSTANCE, filter, (ErrorCode errorCode, List<RepositoryObject> list) -> {
            final List<Group> result = new ArrayList<>(list != null ? list.size() : 0);

            if (list != null) {
                for (RepositoryObject object : list) {
                    result.add((Group) object);
                }
            }
            consumer.accept(result);
        }));
    }

    @Override
    public void getGroup(@NonNull UUID groupId, @NonNull ConsumerWithError<Group> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getGroup: groupId=" + groupId);
        }

        mTwinlifeExecutor.execute(() -> getRepositoryService().getObject(groupId, GroupFactory.INSTANCE, (ErrorCode errorCode, RepositoryObject object) -> consumer.onGet(errorCode, (Group) object)));
    }

    public void onUpdateGroup(long requestId, @NonNull Group group) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateGroup: requestId=" + requestId + " group=" + group);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onUpdateGroup(requestId, group));
            }
        }
    }

    public void onMoveToSpace(long requestId, @NonNull Group group, @NonNull Space oldSpace) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onMoveToSpace: requestId=" + requestId + " group=" + group + " oldSpace=" + oldSpace);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onMoveToSpace(requestId, group, oldSpace));
            }
        }
    }

    @Override
    public void getGroupMember(@NonNull Originator group, @NonNull UUID groupMemberTwincodeId, @NonNull ConsumerWithError<GroupMember> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getGroupMember: group=" + group + " groupMemberTwincodeId=" + groupMemberTwincodeId);
        }

        GroupMember member;
        synchronized (mGroupMembers) {
            member = mGroupMembers.get(groupMemberTwincodeId);

            // If the cache contains an old member, remove and ignore it (use pointer equality for the test!).
            if (member != null && group != member.getGroup()) {
                mGroupMembers.remove(groupMemberTwincodeId);
                member = null;
            }
        }

        if (member != null) {
            final GroupMember lMember = member;
            mTwinlifeExecutor.execute(() -> consumer.onGet(ErrorCode.SUCCESS, lMember));

        } else {
            GetGroupMemberExecutor getGroupMemberExecutor = new GetGroupMemberExecutor(this, group, groupMemberTwincodeId, consumer);
            mTwinlifeExecutor.execute(getGroupMemberExecutor::start);
        }
    }

    public void onGetGroupMember(@Nullable GroupMember member, @NonNull org.twinlife.twinlife.Consumer<GroupMember> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetGroupMember: member=" + member);
        }

        if (member != null) {
            synchronized (mGroupMembers) {
                mGroupMembers.put(member.getPeerTwincodeOutboundId(), member);
            }
        }

        consumer.onGet(ErrorCode.SUCCESS, member);
    }

    public void fetchExistingMembers(@NonNull Originator subject,
                                     @NonNull List<UUID> members,
                                     @NonNull List<GroupMember> knownMembers,
                                     @NonNull List<UUID> unknownMembers) {
        if (DEBUG) {
            Log.d(LOG_TAG, "fetchExistingMembers: subject=" + subject + " members=" + members);
        }

        synchronized (mGroupMembers) {
            for (final UUID memberTwincodeId : members) {
                final GroupMember member = mGroupMembers.get(memberTwincodeId);

                if (member == null) {
                    unknownMembers.add(memberTwincodeId);
                } else if (subject != member.getGroup()) {
                    // If the cache contains an old member, remove and ignore it (use pointer equality for the test!).
                    mGroupMembers.remove(memberTwincodeId);
                    unknownMembers.add(memberTwincodeId);
                } else {
                    knownMembers.add(member);
                }
            }
        }
    }

    public void listGroupMembers(@NonNull Group group, @NonNull ConversationService.MemberFilter filter,
                                 @NonNull ConsumerWithError<List<GroupMember>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listGroupMember: group=" + group + " filter=" + filter);
        }

        final ListMembersExecutor getGroupMemberExecutor = new ListMembersExecutor(this, group,
                filter, null, consumer);
        mTwinlifeExecutor.execute(getGroupMemberExecutor::start);
    }

    public void listMembers(@NonNull Originator subject, @NonNull List<UUID> memberTwincodeList,
                            @NonNull ConsumerWithError<List<GroupMember>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listMembers: subject=" + subject + " memberTwincodeList=" + memberTwincodeList);
        }

        final ListMembersExecutor getGroupMemberExecutor = new ListMembersExecutor(this, subject,
                null, memberTwincodeList, consumer);
        mTwinlifeExecutor.execute(getGroupMemberExecutor::start);
    }

    @Override
    public void createGroup(long requestId, @NonNull String name, @Nullable String description, @Nullable Bitmap avatar, @Nullable File avatarFile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createGroup: requestId=" + requestId + " name=" + name + " avatarFile=" + avatarFile );
        }

        CreateGroupExecutor createGroupExecutor = new CreateGroupExecutor(this, requestId, mCurrentSpace,
                name, description, avatar, avatarFile);
        mTwinlifeExecutor.execute(createGroupExecutor::start);
    }

    @Override
    public void createGroup(long requestId, @NonNull ConversationService.InvitationDescriptor invitation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createGroup: requestId=" + requestId + " invitation=" + invitation);
        }

        CreateGroupExecutor createGroupExecutor = new CreateGroupExecutor(this, requestId, mCurrentSpace, invitation);
        mTwinlifeExecutor.execute(createGroupExecutor::start);
    }

    public void onCreateGroup(long requestId, @NonNull Group group, @NonNull GroupConversation conversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateGroup: requestId=" + requestId + " group=" + group);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onCreateGroup(requestId, group, conversation));
            }
        }
    }

    @Override
    public void updateGroup(long requestId, @NonNull Group group, @NonNull String name, @Nullable String description, @Nullable Bitmap avatar, @Nullable File avatarFile, @Nullable Capabilities capabilities) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateGroup: requestId=" + requestId + " group=" + group + " name=" + name + " avatarFile=" + avatarFile);
        }

        UpdateGroupExecutor updateGroupExecutor = new UpdateGroupExecutor(this, requestId, group, name, description, avatar, avatarFile, null, null, null, capabilities);
        mTwinlifeExecutor.execute(updateGroupExecutor::start);
    }

    @Override
    public void updateGroupProfile(long requestId, @NonNull Group group, @NonNull String name, @Nullable Bitmap avatar, @Nullable File avatarFile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateGroupProfile: requestId=" + requestId + " group=" + group + " name=" + name
                    + " avatarFile=" + avatarFile);
        }

        UpdateGroupExecutor updateGroupExecutor = new UpdateGroupExecutor(this, requestId, group, null, null, null, null, name, avatar, avatarFile, null);
        mTwinlifeExecutor.execute(updateGroupExecutor::start);
    }

    @Override
    public void deleteGroup(long requestId, @NonNull Group group) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteGroup: requestId=" + requestId + " group=" + group);
        }

        DeleteGroupExecutor deleteGroupExecutor = new DeleteGroupExecutor(this, requestId, group, DEFAULT_TIMEOUT);
        mTwinlifeExecutor.execute(deleteGroupExecutor::start);
    }

    public void onDeleteGroup(long requestId, @NonNull UUID groupId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteGroup: requestId=" + requestId + " groupId=" + groupId);
        }

        scheduleRefreshNotifications();

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onDeleteGroup(requestId, groupId));
            }
        }
    }


    //
    // Invocation management
    //

    public void acknowledgeInvocation(@NonNull UUID invocationId, @NonNull ErrorCode errorCode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "acknowledgeInvocation: invocationId=" + invocationId + " errorCode=" + errorCode);
        }

        // TBD check result of asynchronous operation
        getTwincodeInboundService().acknowledgeInvocation(invocationId, errorCode);
    }

    //
    // Level management
    //

    @Override
    public void setLevel(long requestId, @NonNull String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setLevel: requestId=" + requestId + " name=" + name);
        }

        // Map the setLevel("0") to the selection of the default space.
        if ("0".equals(name) || name.isEmpty()) {
            getDefaultSpace((ErrorCode errorCode, Space space) -> setCurrentSpace(requestId, space));
            return;
        }

        // Step #1: find the space with the given name.
        Predicate<Space> filter = (Space space) -> name.equals(space.getName());
        findSpaces(filter, (ErrorCode errorCode, List<Space> spaces) -> {
            if (spaces != null && !spaces.isEmpty()) {
                setCurrentSpace(requestId, spaces.get(0));
            } else {
                fireOnError(requestId, ErrorCode.ITEM_NOT_FOUND, name);
            }
        });
    }

    @Override
    public void setCurrentSpace(long requestId, @NonNull String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setCurrentSpace: requestId=" + requestId + " name=" + name);
        }

        // Filter spaces with the given name.
        Predicate<Space> filter = (Space space) -> name.equals(space.getName());

        // Set the current space and profile.
        findSpaces(filter, (ErrorCode errorCode, List<Space> spaces) -> {
            if (spaces != null && !spaces.isEmpty()) {
                setCurrentSpace(requestId, spaces.get(0));
            }
        });
    }

    @Override
    public void setCurrentSpace(long requestId, @NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setCurrentSpace: requestId=" + requestId + " space=" + space);
        }

        synchronized (mSpaces) {
            mCurrentProfile = space.getProfile();
            mCurrentSpace = space;
        }

        mTwinmeApplication.setDefaultProfile(mCurrentProfile);

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onSetCurrentSpace(requestId, space));
            }
        }
    }

    @Override
    public synchronized void setDefaultSpaceSettings(@NonNull SpaceSettings defaultSpaceSettings,
                                                     @NonNull String oldDefaultLabel) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setDefaultSpaceSettings: defaultSpaceSettings=" + defaultSpaceSettings);
        }

        mDefaultSpaceSettings = defaultSpaceSettings;
        mOldDefaultLabel = oldDefaultLabel;
        SpaceSettings.setDefaultSpaceSettings(defaultSpaceSettings);
    }

    @Override
    public void saveDefaultSpaceSettings(@NonNull SpaceSettings defaultSpaceSettings,
                                         @NonNull ConsumerWithError<SpaceSettings> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "saveDefaultSpaceSettings: defaultSpaceSettings=" + defaultSpaceSettings);
        }

        UpdateSettingsExecutor updateSettingsExecutor = new UpdateSettingsExecutor(this, defaultSpaceSettings,
                null, null, (ErrorCode errorCode, SpaceSettings settings) -> {
            if (errorCode == ErrorCode.SUCCESS && settings != null) {
                UUID saveId = null;
                synchronized (this) {
                    mDefaultSpaceSettings = settings;
                    if (mDefaultSettingsId == null) {
                        mDefaultSettingsId = settings.getId();
                        saveId = mDefaultSettingsId;
                    }
                }
                if (saveId != null) {
                    ConfigurationService configurationService = mTwinlifeImpl.getConfigurationService();
                    ConfigurationService.Configuration spaceConfiguration = configurationService.getConfiguration(DEFAULT_SPACE_ID);
                    spaceConfiguration.setStringConfig(DEFAULT_SETTINGS_ID, saveId.toString());
                    spaceConfiguration.save();
                }
            }
            consumer.onGet(errorCode, settings);
        });
        mTwinlifeExecutor.execute(updateSettingsExecutor::start);
    }

    @Override
    @NonNull
    public synchronized SpaceSettings getDefaultSpaceSettings() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDefaultSpaceSettings");
        }

        // Return a copy of the space settings.
        return new SpaceSettings(mDefaultSpaceSettings);
    }

    @Nullable
    public String getOldDefaultSpaceLabel() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getOldDefaultSpaceLabel");
        }

        return mOldDefaultLabel;
    }

    @Override
    public void createLevel(long requestId, @NonNull String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createLevel: requestId=" + requestId + " name=" + name);
        }

        if (name.isEmpty() || "0".equals(name)) {
            setLevel(requestId, "0");

            return;
        }

        // Step #1: find the space with the given name.
        Predicate<Space> filter = (Space space) -> name.equals(space.getName());
        findSpaces(filter, (ErrorCode errorCode, List<Space> spaces) -> {
            if (spaces != null && !spaces.isEmpty()) {
                // Step #2a: set the current space.
                setCurrentSpace(requestId, spaces.get(0));
            } else {
                SpaceSettings settings = new SpaceSettings(name);
                settings.setSecret(true);
                createSpace(requestId, settings, null);
            }
        });
    }

    @Override
    public void deleteLevel(long requestId, @NonNull String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteLevel: requestId=" + requestId + " name=" + name);
        }

        if (name.isEmpty() || "0".equals(name)) {

            fireOnError(requestId, ErrorCode.BAD_REQUEST, null);
            return;
        }

        DeleteLevelExecutor deleteLevelExecutor = new DeleteLevelExecutor(this, requestId, name);
        mTwinlifeExecutor.execute(deleteLevelExecutor::start);
    }

    public void onDeleteLevel(long requestId, @NonNull String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteLevel: requestId=" + requestId + " name=" + name);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onDeleteLevel(requestId));
            }
        }
    }

    @NonNull
    public synchronized Filter<RepositoryObject> createSpaceFilter() {
        if (DEBUG) {
            Log.d(LOG_TAG, "createSpaceFilter");
        }

        return new Filter<>(this.mCurrentSpace);
    }

    @Override
    public void getSpace(@NonNull UUID spaceId, @NonNull ConsumerWithError<Space> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSpace: spaceId=" + spaceId);
        }

        if (mGetSpacesDone) {
            Space lSpace;
            synchronized (mSpaces) {
                lSpace = mSpaces.get(spaceId);
            }

            if (lSpace != null) {
                consumer.onGet(ErrorCode.SUCCESS, lSpace);
            } else {
                consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
            }

        } else {
            findSpaces((Space lSpace) -> false, (ErrorCode errorCode, List<Space> spaces) -> {
                Space lSpace;
                synchronized (mSpaces) {
                    lSpace = mSpaces.get(spaceId);
                }
                if (lSpace != null) {
                    consumer.onGet(ErrorCode.SUCCESS, lSpace);
                } else {
                    consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                }
            });
        }
    }

    public void onGetSpace(long requestId, @NonNull Space space, @NonNull Consumer<Space> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetSpace: requestId=" + requestId + " space=" + space);
        }

        Space lSpace = putSpace(space);
        consumer.accept(lSpace);
    }

    @Override
    public void getCurrentSpace(@NonNull ConsumerWithError<Space> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getCurrentSpace: consumer=" + consumer);
        }

        final Space space;
        synchronized (mSpaces) {
            space = mCurrentSpace;
        }
        if (space != null) {
            consumer.onGet(ErrorCode.SUCCESS, space);
        } else {
            // Filter to get only the default space.
            Predicate<Space> filter = this::isDefaultSpace;

            // Select the current space.
            findSpaces(filter, (ErrorCode errorCode, List<Space> spaces) -> {
                if (spaces != null && !spaces.isEmpty()) {
                    final Space lSpace = spaces.get(0);

                    setCurrentSpace(BaseService.DEFAULT_REQUEST_ID, lSpace);
                    consumer.onGet(ErrorCode.SUCCESS, lSpace);
                } else {
                    consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                }
            });
        }
    }

    @Override
    public void getDefaultSpace(@NonNull ConsumerWithError<Space> consumer) {

        // Filter to get only the default space.
        Predicate<Space> filter = this::isDefaultSpace;

        findSpaces(filter, (ErrorCode errorCode, List<Space> spaces) -> {
            if (spaces == null || spaces.isEmpty()) {
                consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
            } else {
                if (mCurrentSpace == null) {
                    setCurrentSpace(0, spaces.get(0));
                }
                mTwinlifeExecutor.execute(() -> consumer.onGet(ErrorCode.SUCCESS, spaces.get(0)));
            }
        });
    }

    @Override
    public void createSpace(long requestId, @NonNull SpaceSettings settings,
                            @Nullable Bitmap spaceAvatar, @Nullable File spaceAvatarFile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createSpace: requestId=" + requestId + " settings=" + settings);
        }

        CreateSpaceExecutor createSpaceExecutor = new CreateSpaceExecutor(this, requestId, settings, spaceAvatar, spaceAvatarFile, null, false);
        mTwinlifeExecutor.execute(createSpaceExecutor::start);
    }

    @Override
    public void createSpace(long requestId, @NonNull SpaceSettings settings, @Nullable Bitmap spaceAvatar, @Nullable File spaceAvatarFile,
                            @Nullable String name, @Nullable Bitmap avatar, @Nullable File avatarFile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createSpace: requestId=" + requestId + " settings=" + settings + " name=" + name);
        }

        CreateSpaceExecutor createSpaceExecutor = new CreateSpaceExecutor(this, requestId, settings, spaceAvatar, spaceAvatarFile, name, avatar, avatarFile, false);
        mTwinlifeExecutor.execute(createSpaceExecutor::start);
    }

    public void createSpace(long requestId, @NonNull SpaceSettings spaceSettings, @Nullable Profile profile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createSpace: requestId=" + requestId + " spaceSettings=" + spaceSettings + " profile=" + profile);
        }

        CreateSpaceExecutor createSpaceExecutor = new CreateSpaceExecutor(this, requestId, spaceSettings, null, null, profile, false);
        mTwinlifeExecutor.execute(createSpaceExecutor::start);
    }

    public void createDefaultSpace(long requestId, @NonNull SpaceSettings spaceSettings, @NonNull Profile profile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createSpace: requestId=" + requestId + " spaceSettings=" + spaceSettings);
        }

        CreateSpaceExecutor createSpaceExecutor = new CreateSpaceExecutor(this, requestId, spaceSettings, null, null, profile, true);
        mTwinlifeExecutor.execute(createSpaceExecutor::start);
    }

    public void onCreateSpace(long requestId, @NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateSpace: requestId=" + requestId + " space=" + space);
        }

        Space lSpace = putSpace(space);

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onCreateSpace(requestId, lSpace));
            }
        }
    }

    @Override
    public void deleteSpace(long requestId, @NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteSpace: requestId=" + requestId + " space=" + space);
        }

        if (isDefaultSpace(space)) {

            Iterator<TwinlifeContext.Observer> iterator = observersIterator();
            while (iterator.hasNext()) {
                TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
                if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                    TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                    mTwinlifeExecutor.execute(() -> twinmeContextObserver.onError(requestId, ErrorCode.BAD_REQUEST, space.getId().toString()));
                }
            }
        } else {
            DeleteSpaceExecutor deleteSpaceExecutor = new DeleteSpaceExecutor(this, requestId, space);
            mTwinlifeExecutor.execute(deleteSpaceExecutor::start);
        }
    }

    public void onDeleteSpace(long requestId, @NonNull UUID spaceId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteSpace: requestId=" + requestId + " spaceId=" + spaceId);
        }

        removeSpace(spaceId);

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onDeleteSpace(requestId, spaceId));
            }
        }
    }

    @Override
    public boolean isVisible(@Nullable Originator originator) {
        if (DEBUG) {
            Log.d(LOG_TAG, "isVisible: originator=" + originator);
        }

        if (originator == null) {

            return false;
        }
        Space space = originator.getSpace();
        return mCurrentSpace == space || (space != null && !space.isSecret());
    }

    @Override
    public boolean isCurrentSpace(@Nullable RepositoryObject originator) {
        if (DEBUG) {
            Log.d(LOG_TAG, "isCurrentSpace: originator=" + originator);
        }

        if (!(originator instanceof Originator)) {

            return false;
        }
        Space space = ((Originator) originator).getSpace();
        return mCurrentSpace == space;
    }

    @Override
    public void findSpaces(@NonNull Predicate<Space> predicate, @NonNull ConsumerWithError<List<Space>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findSpaces: predicate=" + predicate + " consumer=" + consumer);
        }

        if (mGetSpacesDone || (!mHasSpaces && !mHasProfiles && !GET_REMOTE_OBJECTS)) {
            mTwinlifeExecutor.execute(() -> resolveFindSpaces(predicate, consumer));
        } else {
            Executor getSpacesExecutor;
            boolean created;

            // Make sure there is only one GetSpacesExecutor that can run at a given time.
            synchronized (mExecutors) {
                getSpacesExecutor = mExecutors.get(GetSpacesExecutor.class);
                created = getSpacesExecutor == null;
                if (created) {
                    getSpacesExecutor = new GetSpacesExecutor(this, 0, mEnableSpaces);
                    mExecutors.put(GetSpacesExecutor.class, getSpacesExecutor);
                }
            }
            getSpacesExecutor.execute(() -> resolveFindSpaces(predicate, consumer));
            if (created) {
                mTwinlifeExecutor.execute(getSpacesExecutor::start);
            }
        }
    }

    public void onGetSpaces(@NonNull List<Space> spaces) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetSpaces: spaces=" + spaces);
        }

        for (Space space : spaces) {
            putSpace(space);
        }

        Space setDefaultSpace = null;
        synchronized (mSpaces) {
            // Make sure we know a current space.
            if (mCurrentSpace == null && mDefaultSpaceId != null && !spaces.isEmpty()) {
                setDefaultSpace = spaces.get(0);
            }
        }
        if (setDefaultSpace != null) {
            setDefaultSpace(setDefaultSpace);
            setCurrentSpace(BaseService.DEFAULT_REQUEST_ID, setDefaultSpace);
        }
        mGetSpacesDone = true;

        // Cleanup the executor.
        synchronized (mExecutors) {
            mExecutors.remove(GetSpacesExecutor.class);
        }
    }

    private void resolveFindSpaces(@NonNull Predicate<Space> predicate, @NonNull org.twinlife.twinlife.Consumer<List<Space>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "resolveFindSpaces: predicate=" + predicate + " consumer=" + consumer);
        }

        final List<Space> result = new ArrayList<>();
        synchronized (mSpaces) {
            for (Space space : mSpaces.values()) {
                if (predicate.test(space)) {
                    result.add(space);
                }
            }
        }
        consumer.onGet(ErrorCode.SUCCESS, result);
    }

    @Override
    public void moveToSpace(long requestId, @NonNull Contact contact, @NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "moveToSpace: requestId=" + requestId + " contact=" + contact + " space=" + space);
        }

        UpdateContactAndIdentityExecutor updateGroupExecutor = new UpdateContactAndIdentityExecutor(this, requestId, contact, space);
        mTwinlifeExecutor.execute(updateGroupExecutor::start);
    }

    @Override
    public void moveToSpace(long requestId, @NonNull Group group, @NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "moveToSpace: requestId=" + requestId + " group=" + group + " space=" + space);
        }

        UpdateGroupExecutor updateGroupExecutor = new UpdateGroupExecutor(this, requestId, group, space);
        mTwinlifeExecutor.execute(updateGroupExecutor::start);
    }

    @Override
    public void updateSpace(long requestId, @NonNull Space space, @NonNull SpaceSettings settings,
                            @Nullable Bitmap spaceAvatar, @Nullable File spaceAvatarFile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateSpace: requestId=" + requestId + " space=" + space + " settings=" + settings);
        }

        UpdateSpaceExecutor updateSpaceExecutor = new UpdateSpaceExecutor(this, requestId, space, settings, spaceAvatar, spaceAvatarFile, null);
        mTwinlifeExecutor.execute(updateSpaceExecutor::start);
    }

    @Override
    public void updateSpace(long requestId, @NonNull Space space, @NonNull Profile profile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateSpace: requestId=" + requestId + " space=" + space + " profile=" + profile);
        }

        UpdateSpaceExecutor updateSpaceExecutor = new UpdateSpaceExecutor(this, requestId, space, null, null, null, profile);
        mTwinlifeExecutor.execute(updateSpaceExecutor::start);
    }

    public void onUpdateSpace(long requestId, @NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateSpace: requestId=" + requestId + " space=" + space);
        }

        Space lSpace = putSpace(space);
        Profile updatedProfile = null;

        // Detect if the profile associated with the space was changed.
        synchronized (mSpaces) {
            if (mCurrentSpace == lSpace && mCurrentProfile != mCurrentSpace.getProfile()) {
                mCurrentProfile = mCurrentSpace.getProfile();
                updatedProfile = mCurrentProfile;
            }
        }
        if (updatedProfile != null) {
            mTwinmeApplication.setDefaultProfile(updatedProfile);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onUpdateSpace(requestId, lSpace));
            }
        }
    }

    @Override
    public boolean isDefaultSpace(@NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "isDefaultSpace: space=" + space);
        }

        synchronized (mSpaces) {
            return space.getId().equals(mDefaultSpaceId);
        }
    }

    @Nullable
    public synchronized UUID getDefaultSpaceId() {

        return mDefaultSpaceId;
    }

    @Override
    public void setDefaultSpace(@NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setDefaultSpace: space=" + space);
        }

        synchronized (mSpaces) {
            if (!space.getId().equals(mDefaultSpaceId)) {

                mDefaultSpaceId = space.getId();
                ConfigurationService configurationService = mTwinlifeImpl.getConfigurationService();
                ConfigurationService.Configuration spaceConfiguration = configurationService.getConfiguration(DEFAULT_SPACE_ID);
                spaceConfiguration.setStringConfig(DEFAULT_SPACE_ID, mDefaultSpaceId.toString());
                spaceConfiguration.save();
            }
        }
    }

    //
    // Conversation management
    //
    @Override
    public void findConversations(@NonNull Filter<Conversation> filter, @NonNull Consumer<List<Conversation>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findConversations: filter=" + filter);
        }

        // Execute the listConversations() query from the twinlife thread to avoid blocking the UI thread!
        mTwinlifeExecutor.execute(() -> {
            List<Conversation> list = mTwinlifeImpl.getConversationService().listConversations(filter);
            consumer.accept(list);
        });
    }

    @Override
    public void findConversationDescriptors(@NonNull Filter<Conversation> filter, @NonNull DisplayCallsMode callsMode,
                                            @NonNull Consumer<Map<Conversation, Descriptor>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findConversationDescriptors: filter=" + filter + " callsMode=" + callsMode);
        }

        // Execute the listConversations() query from the twinlife thread to avoid blocking the UI thread!
        mTwinlifeExecutor.execute(() -> {
            Map<Conversation, Descriptor> result = mTwinlifeImpl.getConversationService().getLastConversationDescriptors(filter, callsMode);
            consumer.accept(result);
        });
    }

    @Override
    public void setActiveConversation(@NonNull Conversation conversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setActiveConversation: conversation=" + conversation);
        }

        mActiveConversationId.set(conversation.getId());

        // Update the pending notification from the twinlife thread to avoid blocking the main UI thread.
        mTwinlifeExecutor.execute(() -> {
            RepositoryObject subject = conversation.getSubject();
            List<org.twinlife.twinlife.Notification> notifications = getNotificationService().getPendingNotifications(subject);
            for (org.twinlife.twinlife.Notification notification : notifications) {
                NotificationType type = notification.getNotificationType();
                if (!notification.isAcknowledged() && (type == NotificationType.NEW_TEXT_MESSAGE
                        || type == NotificationType.NEW_AUDIO_MESSAGE
                        || type == NotificationType.NEW_IMAGE_MESSAGE
                        || type == NotificationType.NEW_VIDEO_MESSAGE
                        || type == NotificationType.NEW_FILE_MESSAGE
                        || type == NotificationType.NEW_GEOLOCATION
                        || type == NotificationType.NEW_GROUP_INVITATION
                        || type == NotificationType.UPDATED_CONTACT
                        || type == NotificationType.UPDATED_AVATAR_CONTACT
                        || type == NotificationType.NEW_CONTACT
                        || type == NotificationType.NEW_GROUP_JOINED
                        || type == NotificationType.RESET_CONVERSATION
                        || type == NotificationType.UPDATED_ANNOTATION)) {
                    acknowledgeNotification(BaseService.DEFAULT_REQUEST_ID, notification);
                }
            }
        });

        mNotificationCenter.onSetActiveConversation(conversation);
    }

    @Override
    public void resetActiveConversation(@NonNull Conversation conversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "resetActiveConversation: conversation=" + conversation);
        }

        UUID lConversationId = mActiveConversationId.get();
        if (conversation.getId().equals(lConversationId)) {
            mActiveConversationId.compareAndSet(lConversationId, null);
        }
    }

    @Override
    public void markDescriptorRead(long requestId, @NonNull ConversationService.DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "markDescriptorRead: requestId=" + requestId + " descriptorId=" + descriptorId);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().markDescriptorRead(requestId, descriptorId));
    }

    @Override
    public void markDescriptorDeleted(long requestId, @NonNull ConversationService.DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "markDescriptorDeleted: requestId=" + requestId + " descriptorId=" + descriptorId);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().markDescriptorDeleted(requestId, descriptorId));
    }

    @Override
    public void deleteDescriptor(long requestId, @NonNull ConversationService.DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteDescriptor: requestId=" + requestId + " descriptorId=" + descriptorId);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().deleteDescriptor(requestId, descriptorId));
    }

    @Override
    public void forwardDescriptor(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo,
                           @NonNull ConversationService.DescriptorId descriptorId, boolean copyAllowed, long expireTimeout) {
        if (DEBUG) {
            Log.d(LOG_TAG, "forwardDescriptor: requestId=" + requestId + " conversation=" + conversation);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().forwardDescriptor(requestId, conversation, sendTo, descriptorId, copyAllowed, expireTimeout));
    }

    @Override
    public void pushMessage(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo, @Nullable ConversationService.DescriptorId replyTo,
                     @NonNull String message, boolean copyAllowed, long expiration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "pushMessage: requestId=" + requestId + " conversation=" + conversation);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().pushMessage(requestId, conversation, sendTo, replyTo, message, copyAllowed, expiration));
    }

    @Override
    public void pushTransientObject(long requestId, @NonNull Conversation conversation, @NonNull Object object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "pushTransientObject: requestId=" + requestId + " conversation=" + conversation);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().pushTransientObject(requestId, conversation, object));
    }

    @Override
    public void pushGeolocation(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo,
                         @Nullable ConversationService.DescriptorId replyTo, double longitude, double latitude, double altitude,
                         double mapLongitudeDelta, double mapLatitudeDelta, @Nullable Uri localMapPath, long expiration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "pushGeolocation: requestId=" + requestId + " conversation=" + conversation);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().pushGeolocation(requestId, conversation, sendTo, replyTo, longitude, latitude,
                altitude, mapLongitudeDelta, mapLatitudeDelta, localMapPath, expiration));
    }

    @Override
    public void updateGeolocation(long requestId, @NonNull Conversation conversation, @NonNull ConversationService.DescriptorId descriptorId,
                           double longitude, double latitude, double altitude,
                           double mapLongitudeDelta, double mapLatitudeDelta, @Nullable Uri localMapPath) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateGeolocation: requestId=" + requestId + " conversation=" + conversation);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().updateGeolocation(requestId, conversation, descriptorId, longitude, latitude,
                altitude, mapLongitudeDelta, mapLatitudeDelta, localMapPath));
    }

    @Override
    public void saveGeolocationMap(long requestId, @NonNull Conversation conversation, @NonNull ConversationService.DescriptorId descriptorId,
                            @Nullable Uri localMapPath) {
        if (DEBUG) {
            Log.d(LOG_TAG, "saveGeolocationMap: requestId=" + requestId + " conversation=" + conversation);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().saveGeolocationMap(requestId, conversation, descriptorId, localMapPath));
    }

    @Override
    public void pushTwincode(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo, @Nullable ConversationService.DescriptorId replyTo,
                      @NonNull UUID twincodeId, @NonNull UUID schemaId, @Nullable String publicKey, boolean copyAllowed, long expiration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "pushTwincode: requestId=" + requestId + " conversation=" + conversation);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().pushTwincode(requestId, conversation, sendTo, replyTo,
                twincodeId, schemaId, publicKey, copyAllowed, expiration));
    }

    @Override
    public void withdrawInviteGroup(long requestId, @NonNull ConversationService.InvitationDescriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "withdrawInviteGroup: requestId=" + requestId + " descriptor=" + descriptor);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().withdrawInviteGroup(requestId, descriptor));
    }

    @Override
    public void deleteAnnotation(@NonNull DescriptorId descriptorId, @NonNull ConversationService.AnnotationType type) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteAnnotation: descriptorId=" + descriptorId + " type=" + type);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().deleteAnnotation(descriptorId, type));
    }

    @Override
    public void toggleAnnotation(@NonNull DescriptorId descriptorId,
                                 @NonNull ConversationService.AnnotationType type, int value) {
        if (DEBUG) {
            Log.d(LOG_TAG, "toggleAnnotation: descriptorId=" + descriptorId + " type=" + type + " value=" + value);
        }

        mTwinlifeExecutor.execute(() -> getConversationService().toggleAnnotation(descriptorId, type, value));
    }

    @Override
    public void listAnnotations(@NonNull DescriptorId descriptorId,
                                @NonNull ConsumerWithError<Map<TwincodeOutbound, DescriptorAnnotation>>consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listAnnotations: descriptorId=" + descriptorId);
        }

        mTwinlifeExecutor.execute(() -> {
            final Map<TwincodeOutbound, DescriptorAnnotation> annotations = getConversationService().listAnnotations(descriptorId);
            consumer.onGet(annotations == null ? ErrorCode.ITEM_NOT_FOUND : ErrorCode.SUCCESS, annotations);
        });
    }

    @Override
    public void getDescriptor(@NonNull DescriptorId descriptorId, @NonNull ConsumerWithError<Descriptor> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDescriptor: descriptorId=" + descriptorId);
        }

        mTwinlifeExecutor.execute(() -> {
            final Descriptor descriptor = getConversationService().getDescriptor(descriptorId);
            consumer.onGet(descriptor == null ? ErrorCode.ITEM_NOT_FOUND : ErrorCode.SUCCESS, descriptor);
        });
    }

    @Override
    public void roomSetName(long requestId, @NonNull Contact contact, @NonNull String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomSetName: contact=" + contact + " name=" + name);
        }

        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_SET_NAME, name);
        roomCommand(requestId, contact, command);
    }

    @Override
    public void roomSetImage(long requestId, @NonNull Contact contact, @NonNull Bitmap image, @Nullable File imageFile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomSetImage: contact=" + contact + " avatar=" + image + " imageFile=" + imageFile);
        }

        ImageTools imageTools = mTwinlifeImpl.getImageTools();
        byte[] imageData = null;
        if (imageFile != null) {
            int maxWidth = ImageService.NORMAL_IMAGE_WIDTH;
            int maxHeight = ImageService.NORMAL_IMAGE_HEIGHT;

            imageData = imageTools.getFileImageData(imageFile, maxWidth, maxHeight);
        }
        if (imageFile == null || imageData == null) {
            imageData = imageTools.getImageData(image);
        }
        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_SET_IMAGE, imageData);
        roomCommand(requestId, contact, command);
    }

    @Override
    public void roomSetWelcome(long requestId, @NonNull Contact contact, @NonNull String message) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomSetWelcome: contact=" + contact + " message=" + message);
        }

        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_SET_WELCOME, message);
        roomCommand(requestId, contact, command);
    }

    @Override
    public void roomSetConfig(long requestId, @NonNull Contact contact, @NonNull RoomConfig config) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomSetConfig: contact=" + contact + " config=" + config);
        }

        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_SET_CONFIG, config);
        roomCommand(requestId, contact, command);
    }

    @Override
    public void roomGetConfig(long requestId, @NonNull Contact contact) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomGetConfig: contact=" + contact);
        }

        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_GET_CONFIG);
        roomCommand(requestId, contact, command);
    }

    @Override
    public void roomChangeTwincode(long requestId, @NonNull Contact contact) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomChangeTwincode: contact=" + contact);
        }

        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_RENEW_TWINCODE);
        roomCommand(requestId, contact, command);
    }

    @Override
    public void roomDeleteMessage(long requestId, @NonNull Contact contact, @NonNull ConversationService.DescriptorId messageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomDeleteMessage: contact=" + contact + " messageId=" + messageId);
        }

        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_DELETE_MESSAGE, messageId);
        roomCommand(requestId, contact, command);
    }

    @Override
    public void roomForwardMessage(long requestId, @NonNull Contact contact, @NonNull ConversationService.DescriptorId messageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomForwardMessage: contact=" + contact + " messageId=" + messageId);
        }

        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_FORWARD_MESSAGE, messageId);
        roomCommand(requestId, contact, command);
    }

    @Override
    public void roomBlockSender(long requestId, @NonNull Contact contact, @NonNull ConversationService.DescriptorId messageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomBlockSender: contact=" + contact + " messageId=" + messageId);
        }

        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_BLOCK_SENDER, messageId);
        roomCommand(requestId, contact, command);
    }

    @Override
    public void roomDeleteMember(long requestId, @NonNull Contact contact, @NonNull UUID memberTwincodeOutboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomDeleteMember: contact=" + contact + " memberTwincodeOutboundId=" + memberTwincodeOutboundId);
        }

        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_DELETE_MEMBER, memberTwincodeOutboundId);
        roomCommand(requestId, contact, command);
    }

    @Override
    public void roomSignalMember(long requestId, @NonNull Contact contact, @NonNull UUID memberTwincodeOutboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomSignalMember: contact=" + contact + " memberTwincodeOutboundId=" + memberTwincodeOutboundId);
        }

        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_SIGNAL_MEMBER, memberTwincodeOutboundId);
        roomCommand(requestId, contact, command);
    }

    @Override
    public void roomSetRoles(long requestId, @NonNull Contact contact, @NonNull String role, @NonNull List<UUID> members) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomSetAdministrator: contact=" + contact + " role=" + role + " members=" + members);
        }

        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_SET_ROLES, role, members);
        roomCommand(requestId, contact, command);
    }

    @Override
    public void roomListMembers(long requestId, @NonNull Contact contact, @NonNull String filter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomListMembers: contact=" + contact + " filter=" + filter);
        }

        RoomCommand command = new RoomCommand(requestId, RoomCommand.Action.ROOM_LIST_MEMBERS, filter);
        roomCommand(requestId, contact, command);
    }

    private void roomCommand(long requestId, @NonNull Contact contact, @NonNull RoomCommand command) {
        if (DEBUG) {
            Log.d(LOG_TAG, "roomCommand: contact=" + contact + " command=" + command);
        }

        if (!contact.isTwinroom() || !contact.hasPeer()) {

            fireOnError(requestId, ErrorCode.BAD_REQUEST, null);
            return;
        }

        UUID twincodeOutboundId = contact.getTwincodeOutboundId();
        UUID twincodeInboundId = contact.getTwincodeInboundId();
        UUID peerTwincodeOutboundId = contact.getPeerTwincodeOutboundId();
        if (twincodeOutboundId == null || twincodeInboundId == null || peerTwincodeOutboundId == null) {

            fireOnError(requestId, ErrorCode.BAD_REQUEST, null);
            return;
        }

        ConversationService conversationService = getConversationService();
        Conversation conversation = conversationService.getOrCreateConversation(contact);
        if (conversation == null) {
            fireOnError(requestId, ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }
        conversationService.pushCommand(requestId, conversation, command);
    }

    //
    // Device migration
    //

    @Override
    public void getAccountMigration(@NonNull UUID deviceMigrationId, @NonNull ConsumerWithError<AccountMigration> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDeviceMigration deviceMigrationId=" + deviceMigrationId);
        }

        GetAccountMigrationExecutor getAccountMigrationExecutor = new GetAccountMigrationExecutor(this, deviceMigrationId, consumer);
        mTwinlifeExecutor.execute(getAccountMigrationExecutor::start);
    }

    @Override
    public void createAccountMigration(@NonNull ConsumerWithError<AccountMigration> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createDeviceMigration");
        }

        CreateAccountMigrationExecutor createAccountMigrationExecutor = new CreateAccountMigrationExecutor(this, consumer);
        mTwinlifeExecutor.execute(createAccountMigrationExecutor::start);
    }

    @Override
    public void bindAccountMigration(@NonNull AccountMigration accountMigration, @NonNull TwincodeOutbound peerTwincodeOutbound,
                                     @NonNull ConsumerWithError<AccountMigration> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "bindDeviceMigration deviceMigration=" + accountMigration + " peerTwincodeOutbound=" + peerTwincodeOutbound);
        }

        BindAccountMigrationExecutor bindAccountMigrationExecutor = new BindAccountMigrationExecutor(this, accountMigration, peerTwincodeOutbound, consumer);
        mTwinlifeExecutor.execute(bindAccountMigrationExecutor::start);
    }

    private void bindAccountMigration(@NonNull PairInviteInvocation invocation, @NonNull AccountMigration accountMigration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "bindAccountMigration: invocation=" + invocation + " deviceMigration=" + accountMigration);
        }

        BindAccountMigrationExecutor bindAccountMigrationExecutor = new BindAccountMigrationExecutor(this, invocation, accountMigration);
        mTwinlifeExecutor.execute(bindAccountMigrationExecutor::start);
    }

    @Override
    public void deleteAccountMigration(@NonNull AccountMigration accountMigration, @NonNull ConsumerWithError<UUID> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteDeviceMigration: deviceMigration=" + accountMigration);
        }

        DeleteAccountMigrationExecutor deleteAccountMigrationExecutor = new DeleteAccountMigrationExecutor(this, accountMigration, consumer);
        mTwinlifeExecutor.execute(deleteAccountMigrationExecutor::start);
    }

    public void onUpdateAccountMigration(long requestId, @NonNull AccountMigration accountMigration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateAccountMigration: requestId=" + requestId + " accountMigration=" + accountMigration);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onUpdateAccountMigration(requestId, accountMigration));
            }
        }
    }

    public void onDeleteAccountMigration(long requestId, @NonNull UUID accountMigrationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteAccountMigration: requestId=" + requestId + " accountMigrationId=" + accountMigrationId);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onDeleteAccountMigration(requestId, accountMigrationId));
            }
        }
    }


    //
    // Call Receiver
    //
    @Override
    public void createCallReceiver(long requestId, @NonNull Space space, @Nullable String name,
                                   @Nullable String description,
                                   @Nullable String identityName, @Nullable String identityDescription,
                                   @Nullable Bitmap avatar, @Nullable File avatarFile, @Nullable Capabilities capabilities, @NonNull Consumer<CallReceiver> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createCallReceiver: requestId=" + requestId + " space=" + space);
        }

        CreateCallReceiverExecutor executor = new CreateCallReceiverExecutor(this, requestId, space, name, description, identityName, identityDescription, avatar, avatarFile, capabilities, consumer);
        mTwinlifeExecutor.execute(executor::start);
    }

    public void onCreateCallReceiver(long requestId, CallReceiver callReceiver, @NonNull Consumer<CallReceiver> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateCallReceiver: requestId=" + requestId + " callReceiver=" + callReceiver);
        }

        consumer.accept(callReceiver);

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onCreateCallReceiver(requestId, callReceiver));
            }
        }
    }


    @Override
    public void getCallReceiver(@NonNull UUID callReceiverId, @NonNull ConsumerWithError<CallReceiver> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getCallReceiver: callReceiverId=" + callReceiverId);
        }

        mTwinlifeExecutor.execute(() -> getRepositoryService().getObject(callReceiverId, CallReceiverFactory.INSTANCE, (ErrorCode errorCode, RepositoryObject object) -> {
            if (object instanceof CallReceiver) {
                consumer.onGet(ErrorCode.SUCCESS, (CallReceiver) object);
            } else {
                consumer.onGet(errorCode, null);
            }
        }));
    }

    @Override
    public void findCallReceivers(@NonNull Filter<RepositoryObject> filter, @NonNull Consumer<List<CallReceiver>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findCallReceivers: filter=" + filter + " consumer=" + consumer);
        }

        mTwinlifeExecutor.execute(() -> getRepositoryService().listObjects(CallReceiverFactory.INSTANCE, filter, (ErrorCode errorCode, List<RepositoryObject> list) -> {
            final List<CallReceiver> result = new ArrayList<>(list != null ? list.size() : 0);

            if (list != null) {
                for (RepositoryObject object : list) {
                    result.add((CallReceiver) object);
                }
            }
            consumer.accept(result);
        }));
    }

    @Override
    public void deleteCallReceiver(long requestId, @NonNull CallReceiver callReceiver) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteCallReceiver: requestId=" + requestId + " callReceiver=" + callReceiver);
        }

        DeleteCallReceiverExecutor executor = new DeleteCallReceiverExecutor(this, requestId, callReceiver);
        mTwinlifeExecutor.execute(executor::start);
    }

    public void onDeleteCallReceiver(long requestId, @NonNull UUID callReceiverId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteCallReceiver: requestId=" + requestId + " callReceiverId=" + callReceiverId);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onDeleteCallReceiver(requestId, callReceiverId));
            }
        }
    }

    @Override
    public void updateCallReceiver(long requestId, @NonNull CallReceiver callReceiver, @NonNull String name, @Nullable String description, @NonNull String identityName,
                                   @Nullable String identityDescription, @Nullable Bitmap identityAvatar, @Nullable File identityAvatarFile,
                                   @Nullable Capabilities capabilities) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateCallReceiver: requestId=" + requestId + " identityName=" + identityName
                    + " identityAvatarFile=" + identityAvatarFile + " description=" + description
                    + " capabilities=" + capabilities);
        }

        UpdateCallReceiverExecutor executor = new UpdateCallReceiverExecutor(this, requestId, callReceiver, name, description, identityName, identityDescription, identityAvatar, identityAvatarFile, capabilities);
        mTwinlifeExecutor.execute(executor::start);
    }

    public void onUpdateCallReceiver(long requestId, @NonNull CallReceiver callReceiver) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateCallReceiver: requestId=" + requestId + " callReceiver=" + callReceiver);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onUpdateCallReceiver(requestId, callReceiver));
            }
        }
    }

    @Override
    public void changeCallReceiverTwincode(long requestId, @NonNull CallReceiver callReceiver) {
        if (DEBUG) {
            Log.d(LOG_TAG, "changeProfileTwincode: requestId=" + requestId + " callReceiver=" + callReceiver);
        }

        ChangeCallReceiverTwincodeExecutor executor = new ChangeCallReceiverTwincodeExecutor(this, requestId, callReceiver);
        mTwinlifeExecutor.execute(executor::start);
    }

    public void onChangeCallReceiverTwincode(long requestId, @NonNull CallReceiver callReceiver) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onChangeProfileTwincode: requestId=" + requestId + " callReceiver=" + callReceiver);
        }

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onChangeCallReceiverTwincode(requestId, callReceiver));
            }
        }
    }

    //
    // Notification management
    //

    @Override
    @NonNull
    public NotificationCenter getNotificationCenter() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getNotificationCenter");
        }

        return mNotificationCenter;
    }

    @Override
    public void getNotification(@NonNull UUID notificationId, @NonNull ConsumerWithError<Notification> consumer) {

        mTwinlifeExecutor.execute(() -> {
            Notification notification = getNotificationService().getNotification(notificationId);
            if (notification != null) {
                consumer.onGet(ErrorCode.SUCCESS, notification);
            } else {
                consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
            }
        });
    }

    @Override
    public void findNotifications(@NonNull Filter<Notification> filter, int maxDescriptors, @NonNull Consumer<List<Notification>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findNotifications: filter=" + filter +
                    " maxDescriptors=" + maxDescriptors + " consumer=" + consumer);
        }

        mTwinlifeExecutor.execute(() -> {
            List<Notification> notifications = getNotificationService().listNotifications(filter, maxDescriptors);
            consumer.accept(notifications);
        });
    }

    @Override
    public Notification  createNotification(@NonNull NotificationService.NotificationType notificationType,
                                            int notificationId, @NonNull Originator subject,
                                            @Nullable DescriptorId descriptorId,
                                            @Nullable TwincodeOutbound annotatingUser) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createNotification: notificationType=" + notificationType + " subject=" + subject
                    + " descriptorId=" + descriptorId + " annotatingUser=" + annotatingUser);
        }

        NotificationService notificationService = getNotificationService();
        if (notificationService == null) {
            return null;
        }

        // If the subject is a GroupMember, we must associate the notification to the group.
        if (subject instanceof GroupMember) {
            subject = ((GroupMember) subject).getGroup();
        }
        Notification notification = notificationService.createNotification(notificationId, notificationType,
                subject, descriptorId, annotatingUser);

        if (notification != null) {
            Iterator<TwinlifeContext.Observer> iterator = observersIterator();
            while (iterator.hasNext()) {
                TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
                if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                    TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                    mTwinlifeExecutor.execute(() -> twinmeContextObserver.onAddNotification(notification));
                }
            }
            scheduleRefreshNotifications();
        }

        return notification;
    }

    @Override
    public void acknowledgeNotification(long requestId, @NonNull Notification notification) {
        if (DEBUG) {
            Log.d(LOG_TAG, "acknowledgeNotification: requestId=" + requestId + " notification=" + notification);
        }

        UpdateNotificationExecutor updateNotificationExecutor = new UpdateNotificationExecutor(this, requestId, notification);
        mTwinlifeExecutor.execute(updateNotificationExecutor::start);
    }

    public void onUpdateNotification(long requestId, @NonNull Notification notification) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateNotification: requestId=" + requestId + " notification=" + notification);
        }

        mNotificationCenter.onAcknowledgeNotification(notification);

        Iterator<TwinlifeContext.Observer> iterator = observersIterator();
        while (iterator.hasNext()) {
            TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
            if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                mTwinlifeExecutor.execute(() -> twinmeContextObserver.onAcknowledgeNotification(requestId, notification));
            }
        }

        scheduleRefreshNotifications();
    }

    @Override
    public void deleteNotification(long requestId, @NonNull Notification notification) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteNotification: requestId=" + requestId + " notificationId=" + notification);
        }

        mTwinlifeExecutor.execute(() -> {
            getNotificationService().deleteNotification(notification);

            // Acknowledge the notification to cancel the system notification if any.
            if (!notification.isAcknowledged()) {
                // SCz notification.setAcknowledged(true);
                mNotificationCenter.onAcknowledgeNotification(notification);
            }

            Iterator<TwinlifeContext.Observer> iterator = observersIterator();
            while (iterator.hasNext()) {
                TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
                if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                    TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                    mTwinlifeExecutor.execute(() -> twinmeContextObserver.onDeleteNotification(requestId, notification.getId()));
                }
            }

            scheduleRefreshNotifications();
        });
    }

    @Override
    public void getSpaceNotificationStats(@NonNull ConsumerWithError<NotificationService.NotificationStat> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSpaceNotificationStats");
        }

        mTwinlifeExecutor.execute(() -> {
            final Map<UUID, NotificationStat> stats = getNotificationService().getNotificationStats();

            long pendingCount = 0;
            long acknowledgedCount = 0;
            synchronized (this) {
                if (mCurrentSpace != null) {
                    final NotificationStat stat = stats.get(mCurrentSpace.getId());
                    if (stat != null) {
                        pendingCount = stat.getPendingCount();
                        acknowledgedCount = stat.getAcknowledgedCount();
                    }
                }
            }
            consumer.onGet(ErrorCode.SUCCESS, new NotificationStat(acknowledgedCount, pendingCount));
            refreshNotifications(stats);
        });
    }

    @Override
    public void getNotificationStats(@NonNull ConsumerWithError<Map<Space, NotificationStat>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getNotificationStats");
        }

        mTwinlifeExecutor.execute(() -> {
            final Map<UUID, NotificationStat> stats = getNotificationService().getNotificationStats();
            final Map<Space, NotificationStat> result = new HashMap<>();
            synchronized (mSpaces) {
                for (Map.Entry<UUID, NotificationService.NotificationStat> spaceInfo : stats.entrySet()) {
                    UUID spaceId = spaceInfo.getKey();
                    Space space = mSpaces.get(spaceId);
                    if (space != null) {
                        result.put(space, spaceInfo.getValue());
                    }
                }
            }

            consumer.onGet(ErrorCode.SUCCESS, result);
        });
    }

    public void scheduleRefreshNotifications() {
        if (DEBUG) {
            Log.d(LOG_TAG, "scheduleRefreshNotifications");
        }

        synchronized (this) {
            if (mNotificationRefreshJob != null) {
                return;
            }

            mNotificationRefreshJob = getJobService().scheduleIn("Notifications", this::notificationJob,
                    NOTIFICATION_REFRESH_DELAY, JobService.Priority.MESSAGE);
        }
    }

    private void notificationJob() {
        if (DEBUG) {
            Log.d(LOG_TAG, "notificationJob");
        }

        synchronized (this) {
            mNotificationRefreshJob = null;
        }

        refreshNotifications(getNotificationService().getNotificationStats());
    }

    private void refreshNotifications(@NonNull Map<UUID, NotificationService.NotificationStat> stats) {
        if (DEBUG) {
            Log.d(LOG_TAG, "refreshNotifications stats=" + stats);
        }

        long pendingCount = 0;
        long acknowledgedCount = 0;
        long spacePendingCount = 0;
        synchronized (mSpaces) {
            for (Map.Entry<UUID, NotificationService.NotificationStat> spaceInfo : stats.entrySet()) {
                final NotificationService.NotificationStat stat = spaceInfo.getValue();
                final UUID spaceId = spaceInfo.getKey();
                final Space space = mSpaces.get(spaceId);

                if (space != null && (space == mCurrentSpace || !space.isSecret())) {
                    pendingCount += stat.getPendingCount();
                    acknowledgedCount += stat.getAcknowledgedCount();
                }
                if (space == mCurrentSpace) {
                    spacePendingCount += stat.getPendingCount();
                }
            }
        }

        boolean modified;
        synchronized (this) {
            modified = (mVisibleNotificationStat == null) || (mVisibleNotificationStat.getPendingCount() != pendingCount)
                    || (mVisibleNotificationStat.getAcknowledgedCount() != acknowledgedCount);
            if (modified) {
                mVisibleNotificationStat = new NotificationStat(acknowledgedCount, pendingCount);
            }
        }

        if (modified) {
            boolean hasPendingNotifications = spacePendingCount > 0;
            Iterator<TwinlifeContext.Observer> iterator = observersIterator();
            while (iterator.hasNext()) {
                TwinlifeContext.Observer twinlifeContextObserver = iterator.next();
                if (twinlifeContextObserver instanceof TwinmeContext.Observer) {
                    TwinmeContext.Observer twinmeContextObserver = (TwinmeContext.Observer) twinlifeContextObserver;
                    mTwinlifeExecutor.execute(() -> twinmeContextObserver.onUpdatePendingNotifications(BaseService.DEFAULT_REQUEST_ID, hasPendingNotifications));
                }
            }

            mNotificationCenter.updateApplicationBadgeNumber((int) pendingCount);
        }
    }

    @Override
    @Nullable
    public NotificationContent systemNotification(@NonNull android.content.Context context, @NonNull Map<String, String> parameters) {
        if (DEBUG) {
            Log.d(LOG_TAG, "systemNotification: context=" + context + " parameters=" + parameters);
        }

        // Connect to allow the GetReceiver executor to proceed and give a chance to retrieve the complete notification.
        connect();

        String type = parameters.get("notification-type");
        String version = parameters.get("notification-version");
        if ("IncomingPeerConnection".equals(type)) {
            if (version != null && version.startsWith("2.")) {
                //
                // <"notification-type","IncomingPeerConnection">
                // <"notification-version","2.0.0">
                //
                String content = parameters.get("notification-content");
                if (content != null) {
                    String notificationKey = getNotificationKey(context);
                    if (notificationKey != null) {
                        try {
                            byte[] ivEncryptedData = Utils.decodeBase64(content);
                            byte[] iv = new byte[IV_LENGTH];
                            System.arraycopy(ivEncryptedData, 0, iv, 0, IV_LENGTH);
                            byte[] encryptedData = new byte[ivEncryptedData.length - IV_LENGTH];
                            System.arraycopy(ivEncryptedData, IV_LENGTH, encryptedData, 0, encryptedData.length);
                            Cipher cipher = Cipher.getInstance(AES_MODE);
                            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Utils.decodeBase64(notificationKey), "AES"), new IvParameterSpec(iv));
                            byte[] data = cipher.doFinal(encryptedData);

                            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
                            BinaryDecoder binaryDecoder = new BinaryDecoder(inputStream);
                            UUID schemaId = binaryDecoder.readUUID();
                            int schemaVersion = binaryDecoder.readInt();
                            if (NotificationContent.SCHEMA_ID.equals(schemaId)) {
                                if (NotificationContent.SCHEMA_VERSION == schemaVersion) {
                                    NotificationContent notificationContent = (NotificationContent) NotificationContent.SERIALIZER.deserialize(null, binaryDecoder);

                                    EventMonitor.event("N " + notificationContent.getOperation() + " " + Utils.toLog(notificationContent.getSessionId()));

                                    RepositoryService.FindResult result = getReceiver(notificationContent.getTwincodeId());

                                    if (result.object != null) {
                                        notificationContent.setSubject(result.object);
                                    }

                                    // Return the notification to allow upper layer to decide which foreground service to start.
                                    return notificationContent;
                                }
                            }
                            Log.e(LOG_TAG, "Firebase message with schemaId " + schemaId + "." + schemaVersion + " not recognized");
                        } catch (Exception exception) {
                            Log.e(LOG_TAG, "init: deserialize exception=" + exception);
                        }
                    } else {
                        Log.e(LOG_TAG, "There is no notification key to decode " + content);
                    }
                }
            }
        } else if ("IncomingInvocation".equals(type)) {
            EventMonitor.event("N " + type);
            return new NotificationContent(type);
        }
        Log.e(LOG_TAG, "Firebase message " + type + "." + version + " not recognized");
        return null;
    }

    @Override
    public void reportStats(long requestId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "reportStats: requestId=" + requestId);
        }

        ReportStatsExecutor reportStatsExecutor = new ReportStatsExecutor(this, requestId, mTwinlifeImpl);

        mTwinlifeExecutor.execute(reportStatsExecutor::start);
    }

    public void onReportStats(long requestId, long nextDelay) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onReportStats: requestId=" + requestId + " nextDelay=" + nextDelay);
        }

        synchronized (this) {
            mReportRequestId = BaseService.DEFAULT_REQUEST_ID;
            if (mReportJob != null) {
                mReportJob.cancel();
            }
            mReportJob = getJobService().scheduleIn("Statistics", this::reportJob, nextDelay, JobService.Priority.REPORT);
        }
    }

    @Override
    public void startAction(@NonNull TwinmeAction action) {
        if (DEBUG) {
            Log.d(LOG_TAG, "startAction: action=" + action);
        }

        synchronized (this) {
            if (!mPendingActions.add(action)) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Action " + action + " already started");
                }
                return;
            }

            TwinmeAction firstAction = mPendingActions.first();
            if (firstAction != mFirstAction) {
                if (mActionTimeoutJob != null) {
                    mActionTimeoutJob.cancel();
                }
                mFirstAction = firstAction;
                mActionTimeoutJob = getJobService().scheduleAfter("Action timeout", this::actionTimeoutJob,
                        firstAction.getDeadline(), JobService.Priority.CONNECT);
            }
        }

        setObserver(action);
    }

    @Override
    public void finishAction(@NonNull TwinmeAction action) {
        if (DEBUG) {
            Log.d(LOG_TAG, "finishAction: action=" + action);
        }

        synchronized (this) {
            mPendingActions.remove(action);

            if (mPendingActions.isEmpty()) {
                mFirstAction = null;
                if (mActionTimeoutJob != null) {
                    mActionTimeoutJob.cancel();
                    mActionTimeoutJob = null;
                }
            } else {
                TwinmeAction firstAction = mPendingActions.first();
                if (firstAction != mFirstAction) {
                    if (mActionTimeoutJob != null) {
                        mActionTimeoutJob.cancel();
                    }
                    mFirstAction = firstAction;
                    mActionTimeoutJob = getJobService().scheduleAfter("Action timeout", this::actionTimeoutJob,
                            firstAction.getDeadline(), JobService.Priority.MESSAGE);
                }
            }
        }

        removeObserver(action);
    }

    @Override
    public void setDynamicShortcuts() {
        if (DEBUG) {
            Log.d(LOG_TAG, "setDynamicShortcuts");
        }

        // We want to get all conversations regardless of space,
        // and findConversationDescriptors() is actually fine with a null filter.
        findConversationDescriptors(null, DisplayCallsMode.ALL, mNotificationCenter::setDynamicShortcuts);
    }

    public void removeAllDynamicShortcuts() {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeAllDynamicShortcuts");
        }

        mNotificationCenter.removeAllDynamicShortcuts();
    }

    private void actionTimeoutJob() {
        if (DEBUG) {
            Log.d(LOG_TAG, "actionTimeoutJob");
        }

        List<TwinmeAction> expired = null;
        synchronized (this) {
            mActionTimeoutJob = null;
            mFirstAction = null;

            if (!mPendingActions.isEmpty()) {
                long now = System.currentTimeMillis();
                for (TwinmeAction action : mPendingActions) {
                    if (action.getDeadline() > now) {
                        break;
                    }
                    if (expired == null) {
                        expired = new ArrayList<>();
                    }
                    expired.add(action);
                }
            }
        }

        if (expired != null) {
            for (TwinmeAction action : expired) {
                action.fireTimeout();
            }
        }
    }

    private void reportJob() {
        if (DEBUG) {
            Log.d(LOG_TAG, "reportJob");
        }

        synchronized (this) {
            mReportJob = null;
        }

        // Connect to the Twinlife server (necessary for a job).
        connect();

        long requestId = BaseService.DEFAULT_REQUEST_ID;
        synchronized (this) {
            if (mReportRequestId == BaseService.DEFAULT_REQUEST_ID) {
                mReportRequestId = newRequestId();
                requestId = mReportRequestId;
            }
        }
        if (requestId != BaseService.DEFAULT_REQUEST_ID) {
            reportStats(requestId);
        }

        // @todo wait for the reportStats to finish.
    }

    //
    // Protected Methods
    //

    @Override
    protected final void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        super.onTwinlifeReady();

        getConversationService().addServiceObserver(mConversationServiceObserver);

        PeerConnectionService peerConnectionService = getPeerConnectionService();
        if (peerConnectionService != null) {
            getPeerConnectionService().addServiceObserver(mPeerConnectionServiceObserver);
        }

        TwincodeInboundService twincodeInboundService = getTwincodeInboundService();
        twincodeInboundService.addListener(PairProtocol.ACTION_PAIR_BIND, mTwincodeInboundServiceObserver);
        twincodeInboundService.addListener(PairProtocol.ACTION_PAIR_UNBIND, mTwincodeInboundServiceObserver);
        twincodeInboundService.addListener(PairProtocol.ACTION_PAIR_INVITE, mTwincodeInboundServiceObserver);
        twincodeInboundService.addListener(PairProtocol.ACTION_PAIR_REFRESH, mTwincodeInboundServiceObserver);

        twincodeInboundService.addListener(GroupProtocol.ACTION_GROUP_REGISTERED, mTwincodeInboundServiceObserver);
        twincodeInboundService.addListener(GroupProtocol.ACTION_GROUP_SUBSCRIBE, mTwincodeInboundServiceObserver);

        getTwincodeOutboundService().addServiceObserver(mTwincodeOutboundServiceObserver);
        getNotificationService().addServiceObserver(mNotificationServiceObserver);

        getConversationService().acceptPushTwincode(Invitation.SCHEMA_ID);

        RepositoryService repositoryService = getRepositoryService();
        repositoryService.addServiceObserver(mRepositoryServiceObserver);
        boolean hasProfiles = repositoryService.hasObjects(Profile.SCHEMA_ID);
        boolean hasSpaces = repositoryService.hasObjects(Space.SCHEMA_ID);

        // Update the status at the same time.
        synchronized (this) {
            mHasProfiles = hasProfiles;
            mHasSpaces = hasSpaces;
        }
        connect();

        // Load the default settings from the local database only.
        if (mDefaultSettingsId != null) {
            repositoryService.getObject(mDefaultSettingsId, SpaceSettingsFactory.INSTANCE,
                    (ErrorCode errorCode, RepositoryObject object) -> {
                        if (errorCode == ErrorCode.SUCCESS && object instanceof SpaceSettings) {
                            mDefaultSpaceSettings = (SpaceSettings) object;
                            // Always use the name provided by setDefaultSpaceSettings(): this is the application default.
                            if (!mDefaultSpaceSettings.getName().isEmpty()) {
                                mDefaultSpaceSettings.setName(mDefaultSpaceSettings.getName());
                            }
                        }
                    });
        }
    }

    @Override
    protected final void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        super.onTwinlifeOnline();

        getTwincodeInboundService().triggerPendingInvocations(this::onTriggerPendingInvocations);

        // Setup the job to send the report periodically.
        ReportStatsExecutor r = new ReportStatsExecutor(this, BaseService.DEFAULT_REQUEST_ID, mTwinlifeImpl);
        long delay = r.getNextDelay();
        onReportStats(BaseService.DEFAULT_REQUEST_ID, delay);
    }

    private void onTriggerPendingInvocations() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTriggerPendingInvocations");
        }

        getPeerConnectionService().sessionPing();
    }

    @Override
    protected final void onSignOut() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignOut");
        }

        super.onSignOut();

        ConfigurationService configurationService = getConfigurationService();
        ConfigurationService.Configuration savedConfig = configurationService.getConfiguration(DEFAULT_SPACE_ID);
        configurationService.deleteConfiguration(savedConfig);

        if (mReportJob != null) {
            mReportJob.cancel();
            mReportJob = null;
        }
        mPendingActions.clear();
        mFirstAction = null;
        if (mActionTimeoutJob != null) {
            mActionTimeoutJob.cancel();
            mActionTimeoutJob = null;
        }

        mCurrentProfile = null;

        synchronized (mSpaces) {
            mSpaces.clear();
        }
        mGetSpacesDone = false;
    }

    //
    // Private Methods
    //

    private Space putSpace(@NonNull Space space) {
        if (DEBUG) {
            Log.d(LOG_TAG, "putContact: space=" + space);
        }

        Space lSpace;
        Space setCurrentSpace = null;
        synchronized (mSpaces) {
            lSpace = space;
            mSpaces.put(lSpace.getId(), lSpace);

            // Check the default space validity.
            if (mDefaultSpaceId == null) {
                setDefaultSpace(lSpace);
            }

            // Make sure we know a current space.
            if (mCurrentSpace == null && mDefaultSpaceId.equals(space.getId())) {
                setCurrentSpace = lSpace;
            }

            mHasSpaces = true;
        }

        if (setCurrentSpace != null) {
            setCurrentSpace(BaseService.DEFAULT_REQUEST_ID, setCurrentSpace);
        }

        return lSpace;
    }

    private void removeSpace(@NonNull UUID spaceId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeSpace: spaceId=" + spaceId);
        }

        Space setCurrentSpace = null;
        synchronized (mSpaces) {
            mSpaces.remove(spaceId);

            // If the current space was deleted, invalidate and switch to the default space if there is one.
            if (mCurrentSpace != null && spaceId.equals(mCurrentSpace.getId())) {
                mCurrentSpace = null;
                mCurrentProfile = null;
                if (mDefaultSpaceId != null) {
                    setCurrentSpace = mSpaces.get(mDefaultSpaceId);
                }
            }
        }
        if (setCurrentSpace != null) {
            setCurrentSpace(BaseService.DEFAULT_REQUEST_ID, setCurrentSpace);
        }

        setDynamicShortcuts();
    }

    private void onLeaveGroup(@NonNull UUID memberId, GroupConversation conversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onLeaveGroup: memberId=" + memberId);
        }

        // Remove the member's twincode from our local database.
        getTwincodeOutboundService().evictTwincode(memberId);

        // And make sure the group member cache is also cleared (in case we are re-invited in the same group).
        synchronized (mGroupMembers) {
            mGroupMembers.remove(memberId);
        }

        mNotificationCenter.onLeaveGroup(conversation);
    }

    private void onRevoked(@NonNull Conversation conversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRevoked: conversation=" + conversation);
        }

        final RepositoryObject subject = conversation.getSubject();
        if (subject instanceof Contact) {
            unbindContact(BaseService.DEFAULT_REQUEST_ID, null, (Contact)subject);
        }
    }

    private void onSignatureInfo(@NonNull Conversation conversation, @NonNull TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignatureInfo: twincodeOutbound=" + twincodeOutbound);
        }

        final RepositoryObject subject = conversation.getSubject();
        if (subject instanceof Contact) {
            Contact contact = (Contact) subject;

            if (!Objects.equals(contact.getPeerTwincodeOutboundId(), twincodeOutbound.getId()) || !twincodeOutbound.isSigned()) {
                return;
            }

            Capabilities caps = new Capabilities(contact.getIdentityCapabilities().toAttributeValue());

            caps.setTrusted(twincodeOutbound.getId());

            updateContactIdentity(BaseService.DEFAULT_REQUEST_ID, contact, contact.getIdentityName(), null, null, contact.getDescription(), caps, contact.getPrivateCapabilities());
        }
    }

    private void onInvalidObject(@NonNull RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvalidObject: object=" + object);
        }

        if (object instanceof Contact) {
            deleteContact(BaseService.DEFAULT_REQUEST_ID, (Contact) object);
        } else if (object instanceof Group) {
            deleteGroup(BaseService.DEFAULT_REQUEST_ID, (Group) object);
        } else if (object instanceof Space) {
            deleteSpace(BaseService.DEFAULT_REQUEST_ID, (Space) object);
        } else if (object instanceof CallReceiver) {
            deleteCallReceiver(BaseService.DEFAULT_REQUEST_ID, (CallReceiver) object);
        } else if (object instanceof Invitation) {
            deleteInvitation(BaseService.DEFAULT_REQUEST_ID, (Invitation) object);
        } else if (object instanceof Profile) {
            deleteProfile(BaseService.DEFAULT_REQUEST_ID, (Profile) object);
        } else if (object instanceof AccountMigration) {
            deleteAccountMigration((AccountMigration) object, (ErrorCode errorCode, UUID id) -> {});
        } else {
            RepositoryService repositoryService = getRepositoryService();
            repositoryService.deleteObject(object, (BaseService.ErrorCode status, UUID objectId) -> {
            });
        }
    }

    private void onProcessInvocation(@NonNull Invocation invocation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onProcessInvocation: invocation=" + invocation);
        }

        final RepositoryObject receiver = invocation.getReceiver();
        if (invocation.getBackground()) {
            if (receiver instanceof Profile) {
                if (invocation instanceof PairInviteInvocation) {
                    PairInviteInvocation pairInviteInvocation = (PairInviteInvocation) invocation;
                    createContactPhase2(pairInviteInvocation, (Profile) receiver);
                } else {
                    assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                    acknowledgeInvocation(invocation.getId(), ErrorCode.BAD_REQUEST);
                }
            } else if (receiver instanceof Invitation) {
                if (invocation instanceof PairInviteInvocation) {
                    PairInviteInvocation pairInviteInvocation = (PairInviteInvocation) invocation;
                    createContactPhase2(pairInviteInvocation, (Invitation) receiver);

                } else if (invocation instanceof GroupSubscribeInvocation) {
                    GroupSubscribeInvocation groupSubscribeInvocation = (GroupSubscribeInvocation) invocation;
                    GroupSubscribeExecutor groupExecutor = new GroupSubscribeExecutor(this,
                            groupSubscribeInvocation, (Invitation) receiver);
                    mTwinlifeExecutor.execute(groupExecutor::start);

                } else {
                    assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                    acknowledgeInvocation(invocation.getId(), ErrorCode.BAD_REQUEST);
                }
            } else if (receiver instanceof Contact) {
                Contact contact = (Contact) receiver;
                if (invocation instanceof PairBindInvocation) {
                    PairBindInvocation pairBindInvocation = (PairBindInvocation) invocation;

                    // Post a notification for the new contact (contactPhase1 creation).
                    if (isVisible(contact)) {
                        mNotificationCenter.onNewContact(contact);
                    }
                    bindContact(pairBindInvocation, contact);

                } else if (invocation instanceof PairUnbindInvocation) {
                    PairUnbindInvocation pairUnbindInvocation = (PairUnbindInvocation) invocation;
                    unbindContact(BaseService.DEFAULT_REQUEST_ID, pairUnbindInvocation.getId(), contact);

                } else if (invocation instanceof PairRefreshInvocation) {
                    PairRefreshInvocation pairRefreshInvocation = (PairRefreshInvocation) invocation;
                    refreshRepositoryObject(pairRefreshInvocation, contact);

                } else {
                    assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                    acknowledgeInvocation(invocation.getId(), ErrorCode.BAD_REQUEST);
                }
            } else if (receiver instanceof Group) {
                final Group group = (Group) receiver;
                if (invocation instanceof GroupRegisteredInvocation) {
                    GroupRegisteredInvocation groupRegisteredInvocation = (GroupRegisteredInvocation) invocation;
                    GroupRegisteredExecutor groupExecutor
                            = new GroupRegisteredExecutor(this, groupRegisteredInvocation, group);
                    mTwinlifeExecutor.execute(groupExecutor::start);

                } else if (invocation instanceof PairRefreshInvocation) {
                    PairRefreshInvocation pairRefreshInvocation = (PairRefreshInvocation) invocation;
                    refreshRepositoryObject(pairRefreshInvocation, group);

                } else {
                    assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                    acknowledgeInvocation(invocation.getId(), ErrorCode.BAD_REQUEST);
                }

            } else if (receiver instanceof AccountMigration) {
                final AccountMigration accountMigration = (AccountMigration) receiver;

                if (invocation instanceof PairInviteInvocation) {
                    PairInviteInvocation pairBindInvocation = (PairInviteInvocation) invocation;
                    bindAccountMigration(pairBindInvocation, accountMigration);

                } else if (invocation instanceof PairUnbindInvocation) {
                    deleteAccountMigration(accountMigration, (ErrorCode status, UUID deviceMigrationId) -> acknowledgeInvocation(invocation.getId(), ErrorCode.SUCCESS));

                } else {
                    assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                    acknowledgeInvocation(invocation.getId(), ErrorCode.BAD_REQUEST);
                }

            } else {
                assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                acknowledgeInvocation(invocation.getId(), ErrorCode.BAD_REQUEST);
            }
        } else {
            if (receiver instanceof Profile) {
                onUpdateProfile(BaseService.DEFAULT_REQUEST_ID, (Profile) receiver);
            } else if (receiver instanceof Contact) {
                onUpdateContact(BaseService.DEFAULT_REQUEST_ID, (Contact) receiver);
            } else {
                assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                acknowledgeInvocation(invocation.getId(), ErrorCode.BAD_REQUEST);
            }
        }
    }

    private void onIncomingPeerConnection(@NonNull UUID peerConnectionId, @NonNull String peerId, @NonNull Offer offer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onIncomingPeerConnection: peerConnectionId=" + peerConnectionId + " peerId=" + peerId + " offer=" + offer);
        }

        final int sep = peerId.indexOf('@');
        final String domain = peerId.substring(sep + 1);
        if (!domain.startsWith("inbound.twincode.twinlife")) {

            return;
        }

        final UUID twincodeInboundId = Utils.UUIDFromString(peerId.substring(0, sep));
        if (twincodeInboundId == null) {
            getPeerConnectionService().terminatePeerConnection(peerConnectionId, TerminateReason.GENERAL_ERROR);

            return;
        }

        final int slashPos = peerId.indexOf('/');
        final RepositoryService.FindResult result = getReceiver(twincodeInboundId);
        final UUID callingUserTwincodeId = slashPos > 0 ? Utils.UUIDFromString(peerId.substring(slashPos + 1)) : null;
        if (callingUserTwincodeId != null && result.object instanceof Group) {
            // Group call: the "resource" part of the JID is the group member's twincodeOutbound.
            getGroupMember(
                    (Group) result.object,
                    callingUserTwincodeId,
                    (ErrorCode errorCode, GroupMember groupMember) ->
                            onIncomingPeerConnection(result.errorCode, groupMember, callingUserTwincodeId, peerConnectionId, offer)
            );
            return;
        }

        onIncomingPeerConnection(result.errorCode, result.object, callingUserTwincodeId, peerConnectionId, offer);
    }

    private void onIncomingPeerConnection(@NonNull ErrorCode status, @Nullable RepositoryObject receiver,
                                          @Nullable UUID callingUserTwincodeId, @NonNull UUID peerConnectionId,
                                          @NonNull Offer offer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onIncomingPeerConnection: peerConnectionId=" + peerConnectionId
                    + " status=" + status + " peerConnectionId=" + peerConnectionId + " offer=" + offer);
        }

        // Contact or group was not found, send a terminate to close the P2P connection.
        if (status != ErrorCode.SUCCESS || receiver == null) {
            getPeerConnectionService().terminatePeerConnection(peerConnectionId, TerminateReason.GONE);

            return;
        }

        Originator originator = null;
        Group group = null;
        UUID twincodeInboundId = null;
        if (receiver instanceof Contact) {
            originator = (Contact) receiver;
            twincodeInboundId = originator.getTwincodeInboundId();

        } else if (receiver instanceof CallReceiver) {
            originator = (CallReceiver) receiver;
            twincodeInboundId = originator.getTwincodeInboundId();

        } else if (receiver instanceof Group) {
            group = (Group) receiver;
            originator = group;
            twincodeInboundId = group.getTwincodeInboundId();

        } else if (receiver instanceof GroupMember) {
            originator = (GroupMember) receiver;
            group = (Group) ((GroupMember) receiver).getGroup();
            twincodeInboundId = group.getTwincodeInboundId();

        } else if (receiver instanceof AccountMigration) {
            mNotificationCenter.onIncomingAccountMigration((AccountMigration) receiver, peerConnectionId);
            return;

        } else if (receiver instanceof Profile && callingUserTwincodeId != null) {
            // The peer is doing a P2P invocation on our Profile which is not allowed but occurs due to
            // a bug on the caller's side: its contact is not configured correctly (the `pair::bind` invocation
            // was not saved correctly).  We look at the contact with the twincode ID used by the caller and try
            // to see a contact which such twincode ID:
            // - if we find it, it means the `pair::bind` was lost, missed or not saved correctly on its side.
            //   we can issue a second `pair:bind` with the same information.
            // - if we don't find such contact, there is nothing to do.
            // In both cases, the incoming P2P is terminated immediately.
            // There is no security issue in doing this because we have validated that contact when it was created.
            RebindContactExecutor rebindContactExecutor = new RebindContactExecutor(this, callingUserTwincodeId);
            rebindContactExecutor.start();
            getPeerConnectionService().terminatePeerConnection(peerConnectionId, TerminateReason.GENERAL_ERROR);
            return;

        } else {
            assertion(TwinmeAssertPoint.INCOMING_PEER_CONNECTION, AssertPoint.create(receiver).putPeerConnectionId(peerConnectionId));
        }

        if (twincodeInboundId == null) {
            getPeerConnectionService().terminatePeerConnection(peerConnectionId, TerminateReason.GENERAL_ERROR);
            return;
        }

        if (offer.data && !(offer.audio | offer.video)) {
            onIncomingDataPeerConnection(peerConnectionId, originator, group, callingUserTwincodeId);

        } else if (offer.video || offer.audio) {
            if (isVisible(originator)) {
                Bitmap avatar = null;
                if (group != null && group.getAvatarId() != null) {
                    avatar = getImageService().getImage(group.getAvatarId(), ImageService.Kind.THUMBNAIL);
                } else if (originator.getAvatarId() != null) {
                    avatar = getImageService().getImage(originator.getAvatarId(), ImageService.Kind.THUMBNAIL);
                }
                mNotificationCenter.onIncomingCall(originator, avatar, peerConnectionId, offer);
            } else {
                getPeerConnectionService().terminatePeerConnection(peerConnectionId, TerminateReason.BUSY);

                NotificationType notificationType = offer.video ? NotificationType.MISSED_VIDEO_CALL : NotificationType.MISSED_AUDIO_CALL;
                createNotification(notificationType, 0 /* Notification.NO_NOTIFICATION_ID*/, originator, null, null);
            }
        } else {
            getPeerConnectionService().terminatePeerConnection(peerConnectionId, TerminateReason.GENERAL_ERROR);
        }
    }

    private void onIncomingDataPeerConnection(@NonNull UUID peerConnectionId, @Nullable Originator originator,
                                              @Nullable Group group, @Nullable UUID callingUserTwincodeId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onIncomingDataPeerConnection: peerConnectionId=" + peerConnectionId
                    + " originator=" + originator + " group=" + group + " callingUserTwincodeId=" + callingUserTwincodeId);
        }

        final UUID twincodeInboundId = originator != null ? originator.getTwincodeInboundId() : null;
        if (twincodeInboundId == null) {
            getPeerConnectionService().terminatePeerConnection(peerConnectionId, TerminateReason.GENERAL_ERROR);
            return;
        }

        // For the incoming data P2P, we have to wait that every incoming invocation for
        // the twincode are processed so that we have a chance to handle the `pair::bind`
        // before accepting the session (if we accept the session earlier, we won't have
        // the peer secret keys to decrypt the SDPs).
        getTwincodeInboundService().waitInvocations(twincodeInboundId, () -> {
            final UUID twincodeOutboundId = originator.getTwincodeOutboundId();
            final TwincodeOutbound peerTwincodeOutbound = originator.getPeerTwincodeOutbound();

            // Now, we can verify that incoming P2P are accepted for the given calling twincode.
            // It could be rejected also due to a `pair::bind` that was not handled.
            final boolean acceptP2P = originator.canAcceptP2P(callingUserTwincodeId);
            if (group == null && twincodeOutboundId != null && acceptP2P && peerTwincodeOutbound != null) {
                if (INFO) {
                    Log.i(LOG_TAG, "Incoming p2p=" + Utils.toLog(peerConnectionId) + " in=" + Utils.toLog(twincodeInboundId) +
                            " out=" + Utils.toLog(twincodeOutboundId) + " contact="
                            + Utils.toLog(originator.getId()));
                }
                getConversationService().incomingPeerConnection(peerConnectionId, originator, peerTwincodeOutbound, true);

            } else if (group != null && group.getGroupTwincodeOutboundId() != null && twincodeOutboundId != null && peerTwincodeOutbound != null) {
                if (INFO) {
                    Log.i(LOG_TAG, "Incoming p2p=" + Utils.toLog(peerConnectionId) + " in=" + Utils.toLog(twincodeInboundId) +
                            " out=" + Utils.toLog(twincodeOutboundId) + " group="
                            + Utils.toLog(group.getGroupTwincodeOutboundId()));
                }
                getConversationService().incomingPeerConnection(peerConnectionId, group, peerTwincodeOutbound, false);

            } else {
                // If the receiver does not accept P2P connection, terminate with NotAuthorized.
                getPeerConnectionService().terminatePeerConnection(peerConnectionId, acceptP2P ? TerminateReason.GENERAL_ERROR : TerminateReason.NOT_AUTHORIZED);
            }
        });
    }

    @Nullable
    private ErrorCode onInvokeTwincodeInbound(@NonNull TwincodeInvocation invocation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvokeTwincodeInbound: invocation=" + invocation );
        }

        ProcessInvocationExecutor processInvocationExecutor = new ProcessInvocationExecutor(this, invocation,
                (ErrorCode errorCode, Invocation newInvocation) -> {
            if (errorCode != ErrorCode.SUCCESS || newInvocation == null) {
                acknowledgeInvocation(invocation.invocationId, errorCode);
                return;
            }
            onProcessInvocation(newInvocation);
        });
        mTwinlifeExecutor.execute(processInvocationExecutor::start);
        return null;
    }

    private void onRefreshTwincodeOutbound(@NonNull TwincodeOutbound twincodeOutbound,
                                           @NonNull List<AttributeNameValue> previousAttributes) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRefreshTwincodeOutbound: twincodeOutbound=" + twincodeOutbound);
        }

        ExportedImageId oldAvatarId = null;
        ImageId newAvatarId = twincodeOutbound.getAvatarId();
        AttributeNameValue oldAvatarAttribute = AttributeNameValue.getAttribute(previousAttributes, Twincode.AVATAR_ID);
        if (oldAvatarAttribute != null && oldAvatarAttribute.value instanceof ExportedImageId) {
            oldAvatarId = (ExportedImageId) oldAvatarAttribute.value;
        }

        Filter<RepositoryObject> filter = new Filter<>(null);
        filter.withTwincode(twincodeOutbound);
        findContacts(filter, (List<Contact> contacts) -> {
            for (Contact contact : contacts) {
                onUpdateContact(BaseService.DEFAULT_REQUEST_ID, contact);
            }
        });

        findGroups(filter, (List<Group> groups) -> {
            for (Group group : groups) {
                onUpdateGroup(BaseService.DEFAULT_REQUEST_ID, group);
            }
        });

        // Detect a change of the avatar to cleanup our database and get the new image.
        if ((oldAvatarId == null && newAvatarId != null) || (oldAvatarId != null && !oldAvatarId.equals(newAvatarId))) {
            ImageService imageService = mTwinlifeImpl.getImageService();
            if (oldAvatarId != null) {
                imageService.evictImage(oldAvatarId);
            }
            if (newAvatarId != null) {
                imageService.getImage(newAvatarId, ImageService.Kind.THUMBNAIL);
            }
        }
    }

    private void onPushDescriptor(@NonNull Conversation conversation, @NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPushDescriptor: conversation=" + conversation + " descriptor=" + descriptor);
        }

        switch (descriptor.getType()) {
            case OBJECT_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_MESSAGE_SENT);
                break;

            case FILE_DESCRIPTOR:
            case NAMED_FILE_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_FILE_SENT);
                break;

            case IMAGE_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_IMAGE_SENT);
                break;

            case VIDEO_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_VIDEO_SENT);
                break;

            case AUDIO_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_AUDIO_SENT);
                break;

            case GEOLOCATION_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_GEOLOCATION_SENT);
                if (ENABLE_REPORT_LOCATION) {
                    LocationReport.recordGeolocation(mTwinlifeImpl, (ConversationService.GeolocationDescriptor) descriptor);
                }
                break;

            case TWINCODE_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_TWINCODE_SENT);
                break;

            case CALL_DESCRIPTOR: {
                ConversationService.CallDescriptor callDescriptor = (ConversationService.CallDescriptor) descriptor;

                incrementStat(conversation.getSubject(),
                        callDescriptor.isVideo() ? StatType.NB_VIDEO_CALL_SENT : StatType.NB_AUDIO_CALL_SENT);
                break;
            }
        }
    }

    private void onPopDescriptor(@NonNull Conversation conversation, @NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPopDescriptor: conversation=" + conversation + " descriptor=" + descriptor);
        }

        switch (descriptor.getType()) {
            case OBJECT_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_MESSAGE_RECEIVED);
                break;

            case FILE_DESCRIPTOR:
            case NAMED_FILE_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_FILE_RECEIVED);
                break;

            case IMAGE_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_IMAGE_RECEIVED);
                break;

            case VIDEO_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_VIDEO_RECEIVED);
                break;

            case AUDIO_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_AUDIO_RECEIVED);
                break;

            case GEOLOCATION_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_GEOLOCATION_RECEIVED);
                break;

            case TWINCODE_DESCRIPTOR:
                incrementStat(conversation.getSubject(), StatType.NB_TWINCODE_RECEIVED);
                break;

            case CALL_DESCRIPTOR: {
                ConversationService.CallDescriptor callDescriptor = (ConversationService.CallDescriptor) descriptor;

                incrementStat(conversation.getSubject(),
                        callDescriptor.isVideo() ? StatType.NB_VIDEO_CALL_RECEIVED : StatType.NB_AUDIO_CALL_RECEIVED);

                // When an incoming audio/video call is received, we don't need to proceed since it is handled specifically.
                return;
            }

        }

        if (conversation.isConversation(mActiveConversationId.get())) {

            return;
        }

        if (conversation.isGroup() || !descriptor.getTwincodeOutboundId().equals(conversation.getPeerTwincodeOutboundId())) {
            getGroupMember((Originator) conversation.getSubject(), descriptor.getTwincodeOutboundId(),
                    (ErrorCode status, GroupMember groupMember) -> onPopDescriptor(status, groupMember, conversation, descriptor));
        } else {
            onPopDescriptor(ErrorCode.SUCCESS, conversation.getSubject(), conversation, descriptor);
        }
    }

    private void incrementStat(@NonNull RepositoryObject object, StatType kind) {
        if (DEBUG) {
            Log.d(LOG_TAG, "incrementStat: object=" + object + " kind=" + kind);
        }

        getRepositoryService().incrementStat(object, kind);

        if (object instanceof Originator) {
            Originator originator = (Originator) object;
            if (allowShortcutForOriginator(originator)) {
                mNotificationCenter.pushDynamicShortcut(originator, kind.isIncoming());
            }
        }
    }

    private boolean allowShortcutForOriginator(@NonNull Originator originator) {
        return mTwinmeApplication.getDisplayNotificationSender() && !mTwinmeApplication.screenLocked() &&
                (originator.getSpace() == null || !originator.getSpace().isSecret()) &&
                !originator.getIdentityCapabilities().hasDiscreet();
    }

    private void onPopDescriptor(@NonNull ErrorCode status, @Nullable RepositoryObject receiver, @NonNull Conversation conversation, @NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPopDescriptor: status=" + status + " receiver=" + receiver
                    + " conversation=" + conversation + " descriptor=" + descriptor);
        }

        if (status != ErrorCode.SUCCESS || receiver == null) {

            return;
        }

        if (!(receiver instanceof Originator)) {
            assertion(TwinmeAssertPoint.ON_POP_DESCRIPTOR, AssertPoint.create(receiver));
            return;
        }

        Originator originator = (Originator) receiver;
        if (isVisible(originator)) {
            mNotificationCenter.onPopDescriptor(originator, conversation, conversation.getPeerConnectionId(), descriptor);
        } else {
            NotificationType notificationType = null;
            switch (descriptor.getType()) {
                case OBJECT_DESCRIPTOR:
                    notificationType = NotificationType.NEW_TEXT_MESSAGE;
                    break;

                case IMAGE_DESCRIPTOR:
                    notificationType = NotificationType.NEW_IMAGE_MESSAGE;
                    break;

                case AUDIO_DESCRIPTOR:
                    notificationType = NotificationType.NEW_AUDIO_MESSAGE;
                    break;

                case VIDEO_DESCRIPTOR:
                    notificationType = NotificationType.NEW_VIDEO_MESSAGE;
                    break;

                case NAMED_FILE_DESCRIPTOR:
                    notificationType = NotificationType.NEW_FILE_MESSAGE;
                    break;

                case INVITATION_DESCRIPTOR:
                    notificationType = NotificationType.NEW_GROUP_INVITATION;
                    break;

                case GEOLOCATION_DESCRIPTOR:
                    notificationType = NotificationType.NEW_GEOLOCATION;
                    break;

                case TWINCODE_DESCRIPTOR:
                    //  look at twincode schema Id.
                    notificationType = NotificationType.NEW_CONTACT_INVITATION;
                    break;

                case CLEAR_DESCRIPTOR:
                    notificationType = NotificationType.RESET_CONVERSATION;
                    break;

                default:
                    break;
            }
            if (notificationType != null) {
                createNotification(notificationType, 0 /* Notification.NO_NOTIFICATION_ID,*/, originator, descriptor.getDescriptorId(), null);
            }
        }
    }

    private void onUpdateDescriptor(@NonNull Conversation conversation, @NonNull Descriptor descriptor, @NonNull UpdateType updateType) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateDescriptor: conversation=" + conversation + " descriptor=" + descriptor + " updateType=" + updateType);
        }

        switch (descriptor.getType()) {
            case TWINCODE_DESCRIPTOR:
                if (updateType == UpdateType.TIMESTAMPS && (descriptor.getPeerDeletedTimestamp() != 0 || descriptor.getDeletedTimestamp() != 0)) {
                    deleteInvitation((ConversationService.TwincodeDescriptor) descriptor);
                }
                break;

            case CALL_DESCRIPTOR:
                if (updateType == UpdateType.CONTENT) {
                    ConversationService.CallDescriptor callDescriptor = (ConversationService.CallDescriptor) descriptor;

                    StatType kind;
                    if (callDescriptor.isVideo()) {
                        kind = (callDescriptor.isIncoming() ? StatType.VIDEO_CALL_RECEIVED_DURATION : StatType.VIDEO_CALL_SENT_DURATION);
                    } else {
                        kind = (callDescriptor.isIncoming() ? StatType.AUDIO_CALL_RECEIVED_DURATION : StatType.AUDIO_CALL_SENT_DURATION);
                    }
                    if (callDescriptor.getDuration() > 0) {
                        getRepositoryService().updateStat(conversation.getSubject(), kind, callDescriptor.getDuration());
                    } else if (callDescriptor.isIncoming() && callDescriptor.getTerminateReason() == TerminateReason.TIMEOUT) {
                        incrementStat(conversation.getSubject(),
                                callDescriptor.isVideo() ? StatType.NB_VIDEO_CALL_MISSED : StatType.NB_AUDIO_CALL_MISSED);
                    }
                }

                // When an incoming audio/video call is updated or terminated, we don't need to proceed since it is handled specifically.
                return;

            default:
                break;
        }

        if (conversation.isConversation(mActiveConversationId.get())) {

            return;
        }

        if (conversation.isGroup() || !descriptor.getTwincodeOutboundId().equals(conversation.getPeerTwincodeOutboundId())) {
            getGroupMember((Originator) conversation.getSubject(), descriptor.getTwincodeOutboundId(), (ErrorCode status, GroupMember groupMember) -> onUpdateDescriptor(status, groupMember, conversation, descriptor, updateType));
        } else {
            onUpdateDescriptor(ErrorCode.SUCCESS, conversation.getSubject(), conversation, descriptor, updateType);
        }
    }

    private void onUpdateDescriptor(@NonNull ErrorCode status, @Nullable RepositoryObject receiver, @NonNull Conversation conversation,
                                    @NonNull Descriptor descriptor, @NonNull UpdateType updateType) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateDescriptor: status=" + status + " receiver=" + receiver
                    + " conversation=" + conversation + " descriptor=" + descriptor + " updateType=" + updateType);
        }

        if (status != ErrorCode.SUCCESS || receiver == null) {

            return;
        }

        if (!(receiver instanceof Originator)) {
            assertion(TwinmeAssertPoint.ON_UPDATE_DESCRIPTOR, AssertPoint.create(receiver));
            return;
        }

        Originator originator = (Originator) receiver;
        if (isVisible(originator)) {
            mNotificationCenter.onUpdateDescriptor(originator, conversation, descriptor, updateType);
        } else {
            NotificationType notificationType = null;
            switch (descriptor.getType()) {

                case IMAGE_DESCRIPTOR:
                    notificationType = NotificationType.NEW_IMAGE_MESSAGE;
                    break;

                case AUDIO_DESCRIPTOR:
                    notificationType = NotificationType.NEW_AUDIO_MESSAGE;
                    break;

                case VIDEO_DESCRIPTOR:
                    notificationType = NotificationType.NEW_VIDEO_MESSAGE;
                    break;

                case NAMED_FILE_DESCRIPTOR:
                    notificationType = NotificationType.NEW_FILE_MESSAGE;
                    break;

                case INVITATION_DESCRIPTOR:
                case GEOLOCATION_DESCRIPTOR:
                case TWINCODE_DESCRIPTOR:
                default:
                    break;
            }
            if (notificationType != null) {
                createNotification(notificationType, 0 /* Notification.NO_NOTIFICATION_ID*/, originator,
                        descriptor.getDescriptorId(), null);
            }
        }
    }

    private void onUpdateAnnotation(@NonNull Conversation conversation, @NonNull Descriptor descriptor,
                                    @NonNull TwincodeOutbound annotatingUser) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateAnnotation: conversation=" + conversation + " descriptor=" + descriptor
                    + " annotatingUser=" + annotatingUser + " annotatingUser=" + annotatingUser);
        }

        if (conversation.isConversation(mActiveConversationId.get())) {

            return;
        }

        if (conversation.isGroup() || !annotatingUser.getId().equals(conversation.getPeerTwincodeOutboundId())) {
            getGroupMember((Originator) conversation.getSubject(), annotatingUser.getId(), (ErrorCode status, GroupMember groupMember) -> onUpdateAnnotation(status, groupMember, conversation, descriptor, annotatingUser));
        } else {
            onUpdateAnnotation(ErrorCode.SUCCESS, conversation.getSubject(), conversation, descriptor, annotatingUser);
        }
    }

    private void onUpdateAnnotation(@NonNull ErrorCode status, @Nullable RepositoryObject receiver, @NonNull Conversation conversation,
                                    @NonNull Descriptor descriptor, @NonNull TwincodeOutbound annotatingUser) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateAnnotation: status=" + status + " receiver=" + receiver
                    + " conversation=" + conversation + " descriptor=" + descriptor + " annotatingUser=" + annotatingUser);
        }

        if (status != ErrorCode.SUCCESS || receiver == null) {

            return;
        }

        if (!(receiver instanceof Originator)) {
            assertion(TwinmeAssertPoint.ON_UPDATE_ANNOTATION, AssertPoint.create(receiver));
            return;
        }

        Originator originator = (Originator) receiver;
        if (isVisible(originator)) {
            mNotificationCenter.onUpdateAnnotation(originator, conversation, descriptor, annotatingUser);
        } else {
            createNotification(NotificationType.UPDATED_ANNOTATION, 0 /* Notification.NO_NOTIFICATION_ID*/, originator,
                    descriptor.getDescriptorId(), annotatingUser);
        }
    }
}
