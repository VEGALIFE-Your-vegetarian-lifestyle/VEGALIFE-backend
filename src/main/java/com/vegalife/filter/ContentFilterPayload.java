package com.vegalife.filter;

import java.util.UUID;

/**
 * CONTENT_FILTER queue payload (ADR-005): which post to filter and whether the enqueuer requested
 * publishing (BR-FILTER-005). The PENDING flag and {@code filter_queued_at} handshake are set by
 * the enqueuer (Phase 10), never by this payload's consumer.
 *
 * @param postId the post to filter
 * @param requestedPublish whether the caller wants the post published once it passes
 */
public record ContentFilterPayload(UUID postId, boolean requestedPublish) {}
