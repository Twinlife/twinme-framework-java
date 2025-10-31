/*
 *  Copyright (c) 2021-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme;

import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.TwinlifeConfiguration;
import org.twinlife.twinme.models.AccountMigrationFactory;
import org.twinlife.twinme.models.CallReceiverFactory;
import org.twinlife.twinme.models.ContactFactory;
import org.twinlife.twinme.models.GroupFactory;
import org.twinlife.twinme.models.InvitationFactory;
import org.twinlife.twinme.models.ProfileFactory;
import org.twinlife.twinme.models.SpaceFactory;
import org.twinlife.twinme.models.SpaceSettingsFactory;

public class TwinmeConfiguration extends TwinlifeConfiguration {

    final boolean enableSpaces;

    protected TwinmeConfiguration(boolean enableSpaces) {
        this.enableSpaces = enableSpaces;

        // The repository object factories in the order in which they are migrated.
        this.factories = new RepositoryObjectFactory[] {
                SpaceSettingsFactory.INSTANCE,
                SpaceFactory.INSTANCE,
                ProfileFactory.INSTANCE,
                ContactFactory.INSTANCE,
                CallReceiverFactory.INSTANCE,
                GroupFactory.INSTANCE,
                InvitationFactory.INSTANCE,
                AccountMigrationFactory.INSTANCE
        };
    }
}
