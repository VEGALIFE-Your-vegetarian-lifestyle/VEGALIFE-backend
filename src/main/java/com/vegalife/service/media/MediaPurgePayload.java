package com.vegalife.service.media;

import java.util.UUID;

/**
 * MEDIA_PURGE queue payload (ADR-005): which media row to purge. Everything else the adapter needs
 * (external id, mime type) is read back from the row, which survives soft delete.
 *
 * @param mediaId the soft-deleted media whose provider object should be destroyed
 */
public record MediaPurgePayload(UUID mediaId) {}
