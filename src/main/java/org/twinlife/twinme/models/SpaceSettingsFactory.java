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
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryImportService;
import org.twinlife.twinlife.RepositoryObjectFactory;

import java.util.List;
import java.util.UUID;

/**
 * Factory used by the RepositoryService to create SpaceSettings object.
 */
public class SpaceSettingsFactory extends TwinmeObjectFactory implements RepositoryObjectFactory<SpaceSettings> {

    public static final SpaceSettingsFactory INSTANCE = new SpaceSettingsFactory();

    @Override
    @NonNull
    public SpaceSettings createObject(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                                      long creationDate, @Nullable String name, @Nullable String description,
                                      @Nullable List<BaseService.AttributeNameValue> attributes,
                                      long modificationDate) {

        return new SpaceSettings(identifier, uuid, creationDate, name, description, attributes, modificationDate);
    }

    @Override
    @Nullable
    public SpaceSettings importObject(@NonNull RepositoryImportService upgradeService,
                                      @NonNull DatabaseIdentifier identifier, @NonNull UUID uuid, @Nullable UUID key,
                                      long creationDate, @NonNull List<BaseService.AttributeNameValue> attributes) {
        String name = null, description = null;
        for (BaseService.AttributeNameValue attribute : attributes) {
            switch (attribute.name) {
                case "name":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        name = (String) ((BaseService.AttributeNameStringValue) attribute).value;
                    }
                    break;

                case "description":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        description = (String) attribute.value;
                    }
                    break;
            }
        }

        return new SpaceSettings(identifier, uuid, creationDate, name, description, attributes, creationDate);
    }

    @Override
    public void loadObject(@NonNull SpaceSettings object, String name, String description,
                           @Nullable List<BaseService.AttributeNameValue> attributes, long modificationDate) {

        object.update(name, description, attributes, modificationDate);
    }

    private SpaceSettingsFactory() {
        super(SpaceSettings.SCHEMA_ID, SpaceSettings.SCHEMA_VERSION, 0, null);
    }
}
