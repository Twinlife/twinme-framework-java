/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinme.export;

import androidx.annotation.NonNull;

/**
 * Export observer to report asynchronous progress of the export process done by the ExportExecutor.
 *
 * The observer methods are called from an exporter thread (not the UI thread).
 */
public interface ExportObserver {

    /**
     * Give information about the exporter progress.
     *
     * @param state the current export state.
     * @param stats the current stats about the export.
     */
    void onProgress(@NonNull ExportState state, @NonNull ExportStats stats);

    /**
     * Report an error raised while exporting medias.
     *
     * @param message the error message.
     */
    void onError(@NonNull String message);
}
