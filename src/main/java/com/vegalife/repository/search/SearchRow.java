package com.vegalife.repository.search;

import com.vegalife.dto.request.search.SearchType;
import java.time.Instant;
import java.util.UUID;

public record SearchRow(
    UUID id,
    SearchType type,
    String title,
    String summary,
    String thumbnailUrl,
    String mediaUrl,
    UUID relatedPostId,
    UUID ownerId,
    Instant date,
    double score,
    String snippetSource) {}
