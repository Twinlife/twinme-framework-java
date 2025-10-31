/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.export;

public enum ExportState {

    // Exporter is ready to scan the media and files to be exported.
    EXPORT_READY,

    // Exporter is scanning the selected contact & groups conversations.
    EXPORT_SCANNING,

    // Exporter has finished scanning and is waiting for the export action.
    EXPORT_WAIT,

    // Exporter is exporting media files.
    EXPORT_EXPORTING,

    // Exporter has finished successfully.
    EXPORT_DONE,

    // Exporter stopped with an error.
    EXPORT_ERROR
}
