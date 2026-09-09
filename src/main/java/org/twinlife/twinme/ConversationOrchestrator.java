/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinme;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ConversationService.InvitationDescriptor;
import org.twinlife.twinlife.ErrorCode;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.NotificationService.NotificationType;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService.StatType;
import org.twinlife.twinlife.ShareInvitationMode;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.GroupMember;
import org.twinlife.twinme.models.Invitation;
import org.twinlife.twinme.models.Originator;
import org.twinlife.twinme.models.Space;
import org.twinlife.twinme.util.LocationReport;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Manages orchestration of conversations, namely:
 * <ul>
 *     <li>incoming/outgoing messages
 *     <li>message update
 *     <li>annotations
 * </ul>
 */
final class ConversationOrchestrator extends ConversationService.DefaultServiceObserver {
    private static final String LOG_TAG = "ConversationOrchestrator";
    private static final boolean DEBUG = false;

    private static final Map<Descriptor.Type, StatType> INCOMING_DESCRIPTOR_TO_STAT = Map.of(
            Descriptor.Type.OBJECT_DESCRIPTOR, StatType.NB_MESSAGE_RECEIVED,
            Descriptor.Type.FILE_DESCRIPTOR, StatType.NB_FILE_RECEIVED,
            Descriptor.Type.NAMED_FILE_DESCRIPTOR, StatType.NB_FILE_RECEIVED,
            Descriptor.Type.IMAGE_DESCRIPTOR, StatType.NB_IMAGE_RECEIVED,
            Descriptor.Type.VIDEO_DESCRIPTOR, StatType.NB_VIDEO_RECEIVED,
            Descriptor.Type.AUDIO_DESCRIPTOR, StatType.NB_AUDIO_RECEIVED,
            Descriptor.Type.GEOLOCATION_DESCRIPTOR, StatType.NB_GEOLOCATION_RECEIVED,
            Descriptor.Type.TWINCODE_DESCRIPTOR, StatType.NB_TWINCODE_RECEIVED,
            Descriptor.Type.POLL_DESCRIPTOR, StatType.NB_POLL_RECEIVED,
            Descriptor.Type.CONTACT_SHARE_DESCRIPTOR, StatType.NB_CONTACT_SHARE_RECEIVED
    );

    private static final Map<Descriptor.Type, StatType> OUTGOING_DESCRIPTOR_TO_STAT = Map.of(
            Descriptor.Type.OBJECT_DESCRIPTOR, StatType.NB_MESSAGE_SENT,
            Descriptor.Type.FILE_DESCRIPTOR, StatType.NB_FILE_SENT,
            Descriptor.Type.NAMED_FILE_DESCRIPTOR, StatType.NB_FILE_SENT,
            Descriptor.Type.IMAGE_DESCRIPTOR, StatType.NB_IMAGE_SENT,
            Descriptor.Type.VIDEO_DESCRIPTOR, StatType.NB_VIDEO_SENT,
            Descriptor.Type.AUDIO_DESCRIPTOR, StatType.NB_AUDIO_SENT,
            Descriptor.Type.TWINCODE_DESCRIPTOR, StatType.NB_TWINCODE_SENT,
            Descriptor.Type.GEOLOCATION_DESCRIPTOR, StatType.NB_GEOLOCATION_SENT,
            Descriptor.Type.POLL_DESCRIPTOR, StatType.NB_POLL_SENT,
            Descriptor.Type.CONTACT_SHARE_DESCRIPTOR, StatType.NB_CONTACT_SHARE_SENT
    );

    private static final Map<Descriptor.Type, NotificationType> DESCRIPTOR_TO_NOTIFICATION = Map.ofEntries(
            Map.entry(Descriptor.Type.OBJECT_DESCRIPTOR, NotificationType.NEW_TEXT_MESSAGE),
            Map.entry(Descriptor.Type.IMAGE_DESCRIPTOR, NotificationType.NEW_IMAGE_MESSAGE),
            Map.entry(Descriptor.Type.AUDIO_DESCRIPTOR, NotificationType.NEW_AUDIO_MESSAGE),
            Map.entry(Descriptor.Type.VIDEO_DESCRIPTOR, NotificationType.NEW_VIDEO_MESSAGE),
            Map.entry(Descriptor.Type.NAMED_FILE_DESCRIPTOR, NotificationType.NEW_FILE_MESSAGE),
            Map.entry(Descriptor.Type.INVITATION_DESCRIPTOR, NotificationType.NEW_GROUP_INVITATION),
            Map.entry(Descriptor.Type.GEOLOCATION_DESCRIPTOR, NotificationType.NEW_GEOLOCATION),
            Map.entry(Descriptor.Type.TWINCODE_DESCRIPTOR, NotificationType.NEW_CONTACT_INVITATION),
            Map.entry(Descriptor.Type.CLEAR_DESCRIPTOR, NotificationType.RESET_CONVERSATION),
            Map.entry(Descriptor.Type.POLL_DESCRIPTOR, NotificationType.NEW_POLL_MESSAGE),
            Map.entry(Descriptor.Type.CONTACT_SHARE_DESCRIPTOR, NotificationType.NEW_CONTACT_SHARE)
    );

    @NonNull
    private final TwinmeContextImpl mTwinmeContext;

    @NonNull
    private final TwinmeApplication mTwinmeApplication;

    ConversationOrchestrator(@NonNull TwinmeContextImpl twinmeContext, @NonNull TwinmeApplication twinmeApplication) {
        mTwinmeContext = twinmeContext;
        mTwinmeApplication = twinmeApplication;
    }

    void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        mTwinmeContext.getConversationService().addServiceObserver(this);
    }

    //
    // Incoming messages
    //

    @Override
    public void onPopDescriptor(long requestId, @NonNull ConversationService.Conversation conversation, @NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPopDescriptor: requestId=" + requestId + " conversation=" + conversation + " descriptor=" + descriptor);
        }

        Descriptor.Type type = descriptor.getType();

        if (type == Descriptor.Type.CALL_DESCRIPTOR) {
            ConversationService.CallDescriptor callDescriptor = (ConversationService.CallDescriptor) descriptor;

            incrementStat(conversation.getSubject(),
                    callDescriptor.isVideo() ? StatType.NB_VIDEO_CALL_RECEIVED : StatType.NB_AUDIO_CALL_RECEIVED);

            // When an incoming audio/video call is received, we don't need to proceed since it is handled specifically.
            return;
        }

        StatType statType = INCOMING_DESCRIPTOR_TO_STAT.get(type);

        if (statType != null) {
            incrementStat(conversation.getSubject(), INCOMING_DESCRIPTOR_TO_STAT.get(descriptor.getType()));
        }

        if (conversation.isConversation(mTwinmeContext.getActiveConversationId())) {

            return;
        }

        if (conversation.isGroup() || !descriptor.getTwincodeOutboundId().equals(conversation.getPeerTwincodeOutboundId())) {
            mTwinmeContext.getGroupMember((Originator) conversation.getSubject(), descriptor.getTwincodeOutboundId(),
                    (ErrorCode status, GroupMember groupMember) -> onPopDescriptor(status, groupMember, conversation, descriptor));
        } else {
            onPopDescriptor(ErrorCode.SUCCESS, conversation.getSubject(), conversation, descriptor);
        }
    }

    private void onPopDescriptor(@NonNull ErrorCode status, @Nullable RepositoryObject receiver, @NonNull ConversationService.Conversation conversation, @NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPopDescriptor: status=" + status + " receiver=" + receiver
                    + " conversation=" + conversation + " descriptor=" + descriptor);
        }

        if (status != ErrorCode.SUCCESS || receiver == null) {

            return;
        }

        if (!(receiver instanceof Originator)) {
            mTwinmeContext.assertion(TwinmeAssertPoint.ON_POP_DESCRIPTOR, AssertPoint.create(receiver));
            return;
        }

        boolean skipNotification = false;

        if (descriptor.getType() == Descriptor.Type.CONTACT_SHARE_DESCRIPTOR) {
            if (mTwinmeApplication.getShareInvitationMode() != ShareInvitationMode.ASK) {
                InvitationDescriptor.Status answer = mTwinmeApplication.getShareInvitationMode() == ShareInvitationMode.AUTOMATIC ? InvitationDescriptor.Status.ACCEPTED : InvitationDescriptor.Status.REFUSED;
                Space space = ((Originator) receiver).getSpace();

                mTwinmeContext.answerContactShare(conversation, (ConversationService.ContactShareDescriptor) descriptor, space, answer, true);

                if (answer == InvitationDescriptor.Status.REFUSED) {
                    skipNotification = true;
                }
            }
        }

        if (!skipNotification) {
            Originator originator = (Originator) receiver;
            if (mTwinmeContext.isVisible(originator)) {
                mTwinmeContext.getNotificationCenter().onPopDescriptor(originator, conversation, descriptor);
            } else {
                NotificationType notificationType = DESCRIPTOR_TO_NOTIFICATION.get(descriptor.getType());
                if (notificationType != null) {
                    mTwinmeContext.createNotification(notificationType, 0 /* Notification.NO_NOTIFICATION_ID,*/, originator, descriptor.getDescriptorId(), null, null);
                }
            }
        }
    }


    //
    // Outgoing messages
    //

    @Override
    public void onPushDescriptor(long requestId, @NonNull ConversationService.Conversation conversation, @NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPushDescriptor: requestId=" + requestId + " conversation=" + conversation + " descriptor=" + descriptor);
        }

        Descriptor.Type type = descriptor.getType();

        StatType statType;
        if (type == Descriptor.Type.CALL_DESCRIPTOR) {
            ConversationService.CallDescriptor callDescriptor = (ConversationService.CallDescriptor) descriptor;

            statType = callDescriptor.isVideo() ? StatType.NB_VIDEO_CALL_SENT : StatType.NB_AUDIO_CALL_SENT;
        } else {
            statType = OUTGOING_DESCRIPTOR_TO_STAT.get(type);
        }

        if (statType != null) {
            incrementStat(conversation.getSubject(), statType);
        }

        if (type == Descriptor.Type.GEOLOCATION_DESCRIPTOR && TwinmeContext.ENABLE_REPORT_LOCATION) {
            LocationReport.recordGeolocation(mTwinmeContext.getConfigurationService(), (ConversationService.GeolocationDescriptor) descriptor);
        }
    }


    //
    // Update messages
    //

    @Override
    public void onUpdateDescriptor(long requestId, @NonNull ConversationService.Conversation conversation, @NonNull Descriptor descriptor, ConversationService.UpdateType updateType) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateDescriptor: requestId=" + requestId + " conversation=" + conversation + " descriptor=" + descriptor + " updateType=" + updateType);
        }

        switch (descriptor.getType()) {
            case TWINCODE_DESCRIPTOR:
                if (updateType == ConversationService.UpdateType.TIMESTAMPS && (descriptor.getPeerDeletedTimestamp() != 0 || descriptor.getDeletedTimestamp() != 0)) {
                    deleteInvitation((ConversationService.TwincodeDescriptor) descriptor);
                }
                break;

            case CONTACT_SHARE_DESCRIPTOR:
                handleContactShareDescriptorUpdate(conversation, (ConversationService.ContactShareDescriptor) descriptor, updateType);
                break;

            case CALL_DESCRIPTOR:
                if (updateType == ConversationService.UpdateType.CONTENT) {
                    ConversationService.CallDescriptor callDescriptor = (ConversationService.CallDescriptor) descriptor;

                    StatType kind;
                    if (callDescriptor.isVideo()) {
                        kind = (callDescriptor.isIncoming() ? StatType.VIDEO_CALL_RECEIVED_DURATION : StatType.VIDEO_CALL_SENT_DURATION);
                    } else {
                        kind = (callDescriptor.isIncoming() ? StatType.AUDIO_CALL_RECEIVED_DURATION : StatType.AUDIO_CALL_SENT_DURATION);
                    }
                    if (callDescriptor.getDuration() > 0) {
                        mTwinmeContext.getRepositoryService().updateStat(conversation.getSubject(), kind, callDescriptor.getDuration());
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

        if (conversation.isConversation(mTwinmeContext.getActiveConversationId())) {

            return;
        }

        if (conversation.isGroup() || !descriptor.getTwincodeOutboundId().equals(conversation.getPeerTwincodeOutboundId())) {
            mTwinmeContext.getGroupMember((Originator) conversation.getSubject(), descriptor.getTwincodeOutboundId(), (ErrorCode status, GroupMember groupMember) -> onUpdateDescriptor(status, groupMember, conversation, descriptor, updateType));
        } else {
            onUpdateDescriptor(ErrorCode.SUCCESS, conversation.getSubject(), conversation, descriptor, updateType);
        }
    }

    private void onUpdateDescriptor(@NonNull ErrorCode status, @Nullable RepositoryObject receiver, @NonNull ConversationService.Conversation conversation,
                                    @NonNull Descriptor descriptor, @NonNull ConversationService.UpdateType updateType) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateDescriptor: status=" + status + " receiver=" + receiver
                    + " conversation=" + conversation + " descriptor=" + descriptor + " updateType=" + updateType);
        }

        if (status != ErrorCode.SUCCESS || receiver == null) {

            return;
        }

        if (!(receiver instanceof Originator)) {
            mTwinmeContext.assertion(TwinmeAssertPoint.ON_UPDATE_DESCRIPTOR, AssertPoint.create(receiver));
            return;
        }

        Originator originator = (Originator) receiver;
        if (mTwinmeContext.isVisible(originator)) {
            mTwinmeContext.getNotificationCenter().onUpdateDescriptor(originator, conversation, descriptor, updateType);
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
                mTwinmeContext.createNotification(notificationType, 0 /* Notification.NO_NOTIFICATION_ID*/, originator,
                        descriptor.getDescriptorId(), null, null);
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
        mTwinmeContext.findInvitations(filter, (List<Invitation> invitations) -> {
        });
    }

    private void handleContactShareDescriptorUpdate(@NonNull ConversationService.Conversation conversation, @NonNull ConversationService.ContactShareDescriptor descriptor, @NonNull ConversationService.UpdateType updateType) {
        if (DEBUG) {
            Log.d(LOG_TAG, "handleContactShareDescriptorUpdate: conversation=" + conversation + " descriptor=" + descriptor + " updateType=" + updateType);
        }

        if ((descriptor.getDescriptorId().twincodeOutboundId.equals(conversation.getTwincodeOutboundId())) && updateType == ConversationService.UpdateType.CONTENT) {
            // The peer has updated our ContactShareDescriptor, we might need to take action.

            switch (descriptor.getStatus()) {
                case ACCEPTED:
                    // The peer has accepted the contact share => forward the invitation to the target contact.
                    if (descriptor.getTargetContactId() != null && descriptor.getInvitationTwincodeOutboundId() != null) {
                        mTwinmeContext.getContact(descriptor.getTargetContactId(), (errorCode, contact) -> {
                            if (errorCode != ErrorCode.SUCCESS || contact == null) {
                                Log.e(LOG_TAG, "Contact " + descriptor.getTargetContactId() + " not found");
                                return;
                            }

                            ConversationService.Conversation targetConversation = mTwinmeContext.getConversationService().getOrCreateConversation(contact);

                            if (targetConversation == null) {
                                Log.e(LOG_TAG, "Conversation for contact " + contact + " not found");
                                return;
                            }

                            mTwinmeContext.getConversationService().pushTwincode(BaseService.DEFAULT_REQUEST_ID, targetConversation,
                                    null, null, descriptor.getInvitationTwincodeOutboundId(),
                                    Invitation.CONTACT_SHARE_SCHEMA_ID, descriptor.getInvitationTwincodeOutboundPubkey(), false, 0L);
                        });
                    }
                    break;

                case JOINED:
                    // The relation is established => clean up the invitation's twincodeOutbound and its TwincodeDescriptor.
                    if (descriptor.getTargetContactId() != null && descriptor.getInvitationTwincodeOutboundId() != null) {
                        mTwinmeContext.getContact(descriptor.getTargetContactId(), (errorCode, contact) -> {
                            ConversationService.Conversation targetConversation = null;
                            if (contact != null) {
                                targetConversation = mTwinmeContext.getConversationService().getConversation(contact);
                            }

                            mTwinmeContext.getConversationService().cleanupContactShare(descriptor, targetConversation);
                        });
                    }
                    break;
            }
        }
    }

    //
    // Annotations
    //

    @Override
    public void onUpdateAnnotation(long requestId, @NonNull ConversationService.Conversation conversation, @NonNull Descriptor descriptor,
                                   @NonNull TwincodeOutbound annotatingUser, @NonNull Set<ConversationService.DescriptorAnnotation> updatedAnnotations) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateAnnotation: requestId=" + requestId + " conversation=" + conversation + " descriptor=" + descriptor + " annotatingUser=" + annotatingUser + " updatedAnnotations=" + updatedAnnotations);
        }

        if (conversation.isConversation(mTwinmeContext.getActiveConversationId())) {

            return;
        }

        if (conversation.isGroup() || !annotatingUser.getId().equals(conversation.getPeerTwincodeOutboundId())) {
            mTwinmeContext.getGroupMember((Originator) conversation.getSubject(), annotatingUser.getId(), (ErrorCode status, GroupMember groupMember) -> onUpdateAnnotation(status, groupMember, conversation, descriptor, annotatingUser, updatedAnnotations));
        } else {
            onUpdateAnnotation(ErrorCode.SUCCESS, conversation.getSubject(), conversation, descriptor, annotatingUser, updatedAnnotations);
        }
    }

    private void onUpdateAnnotation(@NonNull ErrorCode status, @Nullable RepositoryObject receiver, @NonNull ConversationService.Conversation conversation,
                                    @NonNull Descriptor descriptor, @NonNull TwincodeOutbound annotatingUser, @NonNull Set<ConversationService.DescriptorAnnotation> updatedAnnotations) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateAnnotation: status=" + status + " receiver=" + receiver
                    + " conversation=" + conversation + " descriptor=" + descriptor + " annotatingUser=" + annotatingUser + " updatedAnnotations=" + updatedAnnotations);
        }

        if (status != ErrorCode.SUCCESS || receiver == null) {

            return;
        }

        if (!(receiver instanceof Originator)) {
            mTwinmeContext.assertion(TwinmeAssertPoint.ON_UPDATE_ANNOTATION, AssertPoint.create(receiver));
            return;
        }

        Originator originator = (Originator) receiver;
        if (mTwinmeContext.isVisible(originator)) {
            mTwinmeContext.getNotificationCenter().onUpdateAnnotations(originator, conversation, descriptor, annotatingUser, updatedAnnotations);
        } else {
            for (ConversationService.DescriptorAnnotation annotation : updatedAnnotations) {
                mTwinmeContext.createNotification(NotificationType.UPDATED_ANNOTATION, 0 /* Notification.NO_NOTIFICATION_ID*/, originator,
                        descriptor.getDescriptorId(), annotatingUser, annotation);
            }
        }
    }


    //
    // Misc
    //

    @Override
    public void onLeaveGroup(long requestId, @NonNull ConversationService.GroupConversation conversation, @NonNull UUID memberId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onLeaveGroup: conversation=" + conversation + " memberId=" + memberId);
        }

        // Remove the member's twincode from our local database.
        mTwinmeContext.getTwincodeOutboundService().evictTwincode(memberId);

        // And make sure the group member cache is also cleared (in case we are re-invited in the same group).
        mTwinmeContext.evictGroupMember(memberId);

        mTwinmeContext.getNotificationCenter().onLeaveGroup(conversation);
    }

    @Override
    public void onRevoked(@NonNull ConversationService.Conversation conversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRevoked: conversation=" + conversation);
        }

        final RepositoryObject subject = conversation.getSubject();
        if (subject instanceof Contact) {
            mTwinmeContext.unbindContact(BaseService.DEFAULT_REQUEST_ID, null, (Contact)subject);
        }
    }

    @Override
    public void onSignatureInfo(@NonNull ConversationService.Conversation conversation, @NonNull TwincodeOutbound twincodeOutbound) {
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

            mTwinmeContext.updateContactIdentity(BaseService.DEFAULT_REQUEST_ID, contact, contact.getIdentityName(), null, null, contact.getDescription(), caps, contact.getPrivateCapabilities());
        }
    }

    //
    // Utils
    //

    private void incrementStat(@NonNull RepositoryObject object, StatType kind) {
        if (DEBUG) {
            Log.d(LOG_TAG, "incrementStat: object=" + object + " kind=" + kind);
        }

        mTwinmeContext.getRepositoryService().incrementStat(object, kind);

        if (object instanceof Originator) {
            Originator originator = (Originator) object;
            if (allowShortcutForOriginator(originator)) {
                mTwinmeContext.getNotificationCenter().pushDynamicShortcut(originator, kind.isIncoming());
            }
        }
    }

    private boolean allowShortcutForOriginator(@NonNull Originator originator) {

        return mTwinmeApplication.getDisplayNotificationSender() && !mTwinmeApplication.screenLocked() &&
                (originator.getSpace() == null || !originator.getSpace().isSecret()) &&
                !originator.getIdentityCapabilities().hasDiscreet();
    }
}
