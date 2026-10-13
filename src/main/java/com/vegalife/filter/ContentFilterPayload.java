package com.vegalife.filter;

import java.util.UUID;

/**
 * CONTENT_FILTER queue payload (ADR-005): which post to filter. The PENDING flag is set by the
 * enqueuer, never by this payload's consumer; the queue row's creation time doubles as the enqueue
 * clock (BR-FILTER-009).
 *
 * @param postId the post to filter
 */
public record ContentFilterPayload(UUID postId) {}
