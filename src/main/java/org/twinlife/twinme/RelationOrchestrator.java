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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeInboundService;
import org.twinlife.twinlife.TwincodeInvocation;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.conversation.GroupProtocol;
import org.twinlife.twinme.executors.BindAccountMigrationExecutor;
import org.twinlife.twinme.executors.BindContactExecutor;
import org.twinlife.twinme.executors.CreateContactPhase2Executor;
import org.twinlife.twinme.executors.GroupRegisteredExecutor;
import org.twinlife.twinme.executors.GroupSubscribeExecutor;
import org.twinlife.twinme.executors.ProcessInvocationExecutor;
import org.twinlife.twinme.executors.RefreshObjectExecutor;
import org.twinlife.twinme.models.AccountMigration;
import org.twinlife.twinme.models.CallReceiver;
import org.twinlife.twinme.models.Contact;
import org.twinlife.twinme.models.Group;
import org.twinlife.twinme.models.GroupRegisteredInvocation;
import org.twinlife.twinme.models.GroupSubscribeInvocation;
import org.twinlife.twinme.models.Invitation;
import org.twinlife.twinme.models.Invocation;
import org.twinlife.twinme.models.Originator;
import org.twinlife.twinme.models.PairBindInvocation;
import org.twinlife.twinme.models.PairInviteInvocation;
import org.twinlife.twinme.models.PairProtocol;
import org.twinlife.twinme.models.PairRefreshInvocation;
import org.twinlife.twinme.models.PairUnbindInvocation;
import org.twinlife.twinme.models.Profile;
import org.twinlife.twinme.models.Space;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Manages orchestration of relations with their setup, update, refresh and cleanup.
 * Handles the following invocations:
 * - pair::invite,
 * - pair::refresh,
 * - pair::bind,
 * - pair::unbind
 * The following specific invocations are also handled (may be we should removed them):
 * - twinlife::conversation::registered
 * - twinlife::conversation::subscribe
 */
final class RelationOrchestrator implements TwincodeInboundService.InvocationListener, TwincodeOutboundService.ServiceObserver, RepositoryService.ServiceObserver {
    private static final String LOG_TAG = "RelationOrchestrator";
    private static final boolean DEBUG = false;

    private final TwinmeContextImpl mTwinmeContext;
    private final ExecutorService mTwinlifeExecutor;

    RelationOrchestrator(@NonNull TwinmeContextImpl twinmeContext, @NonNull ExecutorService twinlifeExecutor) {

        mTwinmeContext = twinmeContext;
        mTwinlifeExecutor = twinlifeExecutor;
    }

    void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        final TwincodeInboundService twincodeInboundService = mTwinmeContext.getTwincodeInboundService();

        twincodeInboundService.addListener(PairProtocol.ACTION_PAIR_BIND, this);
        twincodeInboundService.addListener(PairProtocol.ACTION_PAIR_UNBIND, this);
        twincodeInboundService.addListener(PairProtocol.ACTION_PAIR_INVITE, this);
        twincodeInboundService.addListener(PairProtocol.ACTION_PAIR_REFRESH, this);

        twincodeInboundService.addListener(GroupProtocol.ACTION_GROUP_REGISTERED, this);
        twincodeInboundService.addListener(GroupProtocol.ACTION_GROUP_SUBSCRIBE, this);

        mTwinmeContext.getTwincodeOutboundService().addServiceObserver(this);

        RepositoryService repositoryService = mTwinmeContext.getRepositoryService();
        repositoryService.addServiceObserver(this);
    }

    @Override
    public void onInvalidObject(@NonNull RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvalidObject: object=" + object);
        }

        if (object instanceof Contact) {
            mTwinmeContext.deleteContact(BaseService.DEFAULT_REQUEST_ID, (Contact) object);
        } else if (object instanceof Group) {
            mTwinmeContext.deleteGroup(BaseService.DEFAULT_REQUEST_ID, (Group) object);
        } else if (object instanceof Space) {
            mTwinmeContext.deleteSpace(BaseService.DEFAULT_REQUEST_ID, (Space) object);
        } else if (object instanceof CallReceiver) {
            mTwinmeContext.deleteCallReceiver(BaseService.DEFAULT_REQUEST_ID, (CallReceiver) object);
        } else if (object instanceof Invitation) {
            mTwinmeContext.deleteInvitation(BaseService.DEFAULT_REQUEST_ID, (Invitation) object);
        } else if (object instanceof Profile) {
            mTwinmeContext.deleteProfile(BaseService.DEFAULT_REQUEST_ID, (Profile) object);
        } else if (object instanceof AccountMigration) {
            mTwinmeContext.deleteAccountMigration((AccountMigration) object, (BaseService.ErrorCode errorCode, UUID id) -> {});
        } else {
            RepositoryService repositoryService = mTwinmeContext.getRepositoryService();
            repositoryService.deleteObject(object, (BaseService.ErrorCode status, UUID objectId) -> {
            });
        }
    }

    @Override
    @Nullable
    public BaseService.ErrorCode onInvokeTwincode(@NonNull TwincodeInvocation invocation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "ConversationSynchronizeInvocation.onInvokeTwincode: invocation=" + invocation);
        }

        final ProcessInvocationExecutor processInvocationExecutor = new ProcessInvocationExecutor(mTwinmeContext, invocation,
                (BaseService.ErrorCode errorCode, Invocation newInvocation) -> {
                    if (errorCode != BaseService.ErrorCode.SUCCESS || newInvocation == null) {
                        mTwinmeContext.acknowledgeInvocation(invocation.invocationId, errorCode);
                        return;
                    }
                    onProcessInvocation(newInvocation);
                });
        mTwinlifeExecutor.execute(processInvocationExecutor::start);
        return null;
    }

    @Override
    public void onRefreshTwincode(@NonNull TwincodeOutbound twincodeOutbound,
                                  @NonNull List<BaseService.AttributeNameValue> previousAttributes) {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwincodeOutboundServiceObserver.onRefreshTwincode: twincodeOutbound=" + twincodeOutbound);
        }

        onRefreshTwincodeOutbound(twincodeOutbound, previousAttributes);
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
                    mTwinmeContext.assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                    mTwinmeContext.acknowledgeInvocation(invocation.getId(), BaseService.ErrorCode.BAD_REQUEST);
                }
            } else if (receiver instanceof Invitation) {
                if (invocation instanceof PairInviteInvocation) {
                    PairInviteInvocation pairInviteInvocation = (PairInviteInvocation) invocation;
                    createContactPhase2(pairInviteInvocation, (Invitation) receiver);

                } else if (invocation instanceof GroupSubscribeInvocation) {
                    GroupSubscribeInvocation groupSubscribeInvocation = (GroupSubscribeInvocation) invocation;
                    GroupSubscribeExecutor groupExecutor = new GroupSubscribeExecutor(mTwinmeContext,
                            groupSubscribeInvocation, (Invitation) receiver);
                    mTwinlifeExecutor.execute(groupExecutor::start);

                } else {
                    mTwinmeContext.assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                    mTwinmeContext.acknowledgeInvocation(invocation.getId(), BaseService.ErrorCode.BAD_REQUEST);
                }
            } else if (receiver instanceof Contact) {
                Contact contact = (Contact) receiver;
                if (invocation instanceof PairBindInvocation) {
                    PairBindInvocation pairBindInvocation = (PairBindInvocation) invocation;

                    // Post a notification for the new contact (contactPhase1 creation).
                    if (mTwinmeContext.isVisible(contact)) {
                        mTwinmeContext.getNotificationCenter().onNewContact(contact);
                    }
                    bindContact(pairBindInvocation, contact);

                } else if (invocation instanceof PairUnbindInvocation) {
                    PairUnbindInvocation pairUnbindInvocation = (PairUnbindInvocation) invocation;
                    mTwinmeContext.unbindContact(BaseService.DEFAULT_REQUEST_ID, pairUnbindInvocation.getId(), contact);

                } else if (invocation instanceof PairRefreshInvocation) {
                    PairRefreshInvocation pairRefreshInvocation = (PairRefreshInvocation) invocation;
                    refreshRepositoryObject(pairRefreshInvocation, contact);

                } else {
                    mTwinmeContext.assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                    mTwinmeContext.acknowledgeInvocation(invocation.getId(), BaseService.ErrorCode.BAD_REQUEST);
                }
            } else if (receiver instanceof Group) {
                final Group group = (Group) receiver;
                if (invocation instanceof GroupRegisteredInvocation) {
                    GroupRegisteredInvocation groupRegisteredInvocation = (GroupRegisteredInvocation) invocation;
                    GroupRegisteredExecutor groupExecutor
                            = new GroupRegisteredExecutor(mTwinmeContext, groupRegisteredInvocation, group);
                    mTwinlifeExecutor.execute(groupExecutor::start);

                } else if (invocation instanceof PairRefreshInvocation) {
                    PairRefreshInvocation pairRefreshInvocation = (PairRefreshInvocation) invocation;
                    refreshRepositoryObject(pairRefreshInvocation, group);

                } else {
                    mTwinmeContext.assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                    mTwinmeContext.acknowledgeInvocation(invocation.getId(), BaseService.ErrorCode.BAD_REQUEST);
                }

            } else if (receiver instanceof AccountMigration) {
                final AccountMigration accountMigration = (AccountMigration) receiver;

                if (invocation instanceof PairInviteInvocation) {
                    PairInviteInvocation pairBindInvocation = (PairInviteInvocation) invocation;
                    bindAccountMigration(pairBindInvocation, accountMigration);

                } else if (invocation instanceof PairUnbindInvocation) {
                    mTwinmeContext.deleteAccountMigration(accountMigration, (BaseService.ErrorCode status, UUID deviceMigrationId) -> mTwinmeContext.acknowledgeInvocation(invocation.getId(), BaseService.ErrorCode.SUCCESS));

                } else {
                    mTwinmeContext.assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                    mTwinmeContext.acknowledgeInvocation(invocation.getId(), BaseService.ErrorCode.BAD_REQUEST);
                }
            } else {
                mTwinmeContext.assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                mTwinmeContext.acknowledgeInvocation(invocation.getId(), BaseService.ErrorCode.BAD_REQUEST);
            }
        } else {
            if (receiver instanceof Profile) {
                mTwinmeContext.onUpdateProfile(BaseService.DEFAULT_REQUEST_ID, (Profile) receiver);
            } else if (receiver instanceof Contact) {
                mTwinmeContext.onUpdateContact(BaseService.DEFAULT_REQUEST_ID, (Contact) receiver);
            } else {
                mTwinmeContext.assertion(TwinmeAssertPoint.PROCESS_INVOCATION, AssertPoint.create(receiver).putInvocationId(invocation.getId()));

                mTwinmeContext.acknowledgeInvocation(invocation.getId(), BaseService.ErrorCode.BAD_REQUEST);
            }
        }
    }

    private void createContactPhase2(@NonNull PairInviteInvocation invocation, @NonNull Profile profile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createContactPhase2: invocation=" + invocation + " profile=" + profile);
        }

        CreateContactPhase2Executor createContactPhase2Executor = new CreateContactPhase2Executor(mTwinmeContext, invocation, profile);
        mTwinlifeExecutor.execute(createContactPhase2Executor::start);
    }

    private void createContactPhase2(@NonNull PairInviteInvocation invocation, @NonNull Invitation invitation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createContactPhase2: invocation=" + invocation + " invitation=" + invitation);
        }

        CreateContactPhase2Executor createContactPhase2Executor = new CreateContactPhase2Executor(mTwinmeContext, invocation, invitation);
        mTwinlifeExecutor.execute(createContactPhase2Executor::start);
    }

    private void bindContact(@NonNull PairBindInvocation invocation, @NonNull Contact contact) {
        if (DEBUG) {
            Log.d(LOG_TAG, "bindContact: invocation=" + invocation + " contact=" + contact);
        }

        BindContactExecutor bindContactExecutor = new BindContactExecutor(mTwinmeContext, invocation, contact);
        mTwinlifeExecutor.execute(bindContactExecutor::start);
    }

    private void refreshRepositoryObject(@NonNull PairRefreshInvocation invocation, @NonNull Originator subject) {
        if (DEBUG) {
            Log.d(LOG_TAG, "refreshRepositoryObject: invocation=" + invocation + " subject=" + subject);
        }

        RefreshObjectExecutor refreshObjectExecutor = new RefreshObjectExecutor(mTwinmeContext, invocation, subject);
        mTwinlifeExecutor.execute(refreshObjectExecutor::start);
    }

    private void onRefreshTwincodeOutbound(@NonNull TwincodeOutbound twincodeOutbound,
                                           @NonNull List<BaseService.AttributeNameValue> previousAttributes) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRefreshTwincodeOutbound: twincodeOutbound=" + twincodeOutbound);
        }

        ExportedImageId oldAvatarId = null;
        ImageId newAvatarId = twincodeOutbound.getAvatarId();
        BaseService.AttributeNameValue oldAvatarAttribute = BaseService.AttributeNameValue.getAttribute(previousAttributes, Twincode.AVATAR_ID);
        if (oldAvatarAttribute != null && oldAvatarAttribute.value instanceof ExportedImageId) {
            oldAvatarId = (ExportedImageId) oldAvatarAttribute.value;
        }

        Filter<RepositoryObject> filter = new Filter<>(null);
        filter.withTwincode(twincodeOutbound);
        mTwinmeContext.findContacts(filter, (List<Contact> contacts) -> {
            for (Contact contact : contacts) {
                mTwinmeContext.onUpdateContact(BaseService.DEFAULT_REQUEST_ID, contact);
            }
        });

        mTwinmeContext.findGroups(filter, (List<Group> groups) -> {
            for (Group group : groups) {
                mTwinmeContext.onUpdateGroup(BaseService.DEFAULT_REQUEST_ID, group);
            }
        });

        // Detect a change of the avatar to cleanup our database and get the new image.
        if ((oldAvatarId == null && newAvatarId != null) || (oldAvatarId != null && !oldAvatarId.equals(newAvatarId))) {
            ImageService imageService = mTwinmeContext.getImageService();
            if (oldAvatarId != null) {
                imageService.evictImage(oldAvatarId);
            }
            if (newAvatarId != null) {
                imageService.getImage(newAvatarId, ImageService.Kind.THUMBNAIL);
            }
        }
    }

    private void bindAccountMigration(@NonNull PairInviteInvocation invocation, @NonNull AccountMigration accountMigration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "bindAccountMigration: invocation=" + invocation + " deviceMigration=" + accountMigration);
        }

        BindAccountMigrationExecutor bindAccountMigrationExecutor = new BindAccountMigrationExecutor(mTwinmeContext, invocation, accountMigration);
        mTwinlifeExecutor.execute(bindAccountMigrationExecutor::start);
    }
}
