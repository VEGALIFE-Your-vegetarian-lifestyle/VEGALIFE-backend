package com.vegalife.dto.response.search;

import com.vegalife.dto.request.search.SearchType;
import java.time.Instant;
import java.util.UUID;

public record SearchResultResponse(
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
    String highlightedTitle,
    String highlightedSnippet) {}
