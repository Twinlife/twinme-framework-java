/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryImportService;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.util.Utils;

import java.util.List;
import java.util.UUID;

/**
 * Factory used by the RepositoryService to create AccountMigration object.
 */
public class AccountMigrationFactory extends TwinmeObjectFactory implements RepositoryObjectFactory<AccountMigration> {

    public static final AccountMigrationFactory INSTANCE = new AccountMigrationFactory();

    @Override
    @NonNull
    public AccountMigration createObject(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                                     long creationDate, @Nullable String name, @Nullable String description,
                                     @Nullable List<AttributeNameValue> attributes,
                                     long modificationDate) {

        return new AccountMigration(identifier, uuid, creationDate, name, description, attributes, modificationDate);
    }

    @Override
    public void loadObject(@NonNull AccountMigration object, String name, String description,
                           @Nullable List<BaseService.AttributeNameValue> attributes, long modificationDate) {

        object.update(name, description, attributes, modificationDate);
    }

    @Override
    @Nullable
    public AccountMigration importObject(@NonNull RepositoryImportService upgradeService,
                                     @NonNull DatabaseIdentifier identifier, @NonNull UUID uuid, @Nullable UUID key,
                                     long creationDate, @NonNull List<AttributeNameValue> attributes) {
        UUID twincodeInboundId = key, twincodeOutboundId = null, peerTwincodeOutboundId = null, twincodeFactoryId = null;
        for (BaseService.AttributeNameValue attribute : attributes) {
            switch (attribute.name) {

                case "twincodeFactoryId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        twincodeFactoryId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

                case "twincodeInboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        twincodeInboundId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

                case "twincodeOutboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        twincodeOutboundId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

                case "peerTwincodeOutboundId":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        peerTwincodeOutboundId = Utils.UUIDFromString((String) ((BaseService.AttributeNameStringValue) attribute).value);
                    }
                    break;

            }
        }

        // 4 attributes: twincodeInboundId, twincodeFactoryId, twincodeOutboundId, peerTwincodeOutboundId
        // are mapped to repository columns and they are dropped.
        final AccountMigration accountMigration = new AccountMigration(identifier, uuid, creationDate, null, null, attributes, creationDate);
        upgradeService.importObject(accountMigration, twincodeFactoryId, twincodeInboundId, twincodeOutboundId, peerTwincodeOutboundId, null);
        return accountMigration;
    }

    private AccountMigrationFactory() {
        super(AccountMigration.SCHEMA_ID, AccountMigration.SCHEMA_VERSION,
                RepositoryObjectFactory.USE_INBOUND | RepositoryObjectFactory.USE_OUTBOUND | RepositoryObjectFactory.USE_PEER_OUTBOUND,
                null);
    }
}
