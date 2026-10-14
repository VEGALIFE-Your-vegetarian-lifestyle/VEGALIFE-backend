package com.vegalife.service.search;

import com.vegalife.dto.request.search.SearchType;
import com.vegalife.dto.response.search.SearchResultResponse;
import com.vegalife.repository.search.SearchDao;
import com.vegalife.repository.search.SearchRow;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchService {

  private static final int MAX_SNIPPET_LENGTH = 200;

  private final SearchDao searchDao;

  public PageResponse<SearchResultResponse> search(
      String query,
      SearchType type,
      Instant from,
      Instant to,
      UUID categoryId,
      int page,
      int size) {
    String normalizedQuery = query.trim();
    if (from != null && to != null && to.isBefore(from)) {
      throw new ValidationException("'to' must be greater than or equal to 'from'");
    }
    SearchDao.SearchPage result =
        searchDao.search(normalizedQuery, type, from, to, categoryId, page, size);
    List<SearchResultResponse> content =
        result.rows().stream().map(row -> toResponse(row, normalizedQuery)).toList();
    return PageResponse.from(new PageImpl<>(content, PageRequest.of(page, size), result.total()));
  }

  private SearchResultResponse toResponse(SearchRow row, String query) {
    String summary = truncate(row.summary(), MAX_SNIPPET_LENGTH);
    String snippet = snippet(row.snippetSource(), query);
    return new SearchResultResponse(
        row.id(),
        row.type(),
        row.title(),
        summary,
        row.thumbnailUrl(),
        row.mediaUrl(),
        row.relatedPostId(),
        row.ownerId(),
        row.date(),
        row.score(),
        highlight(row.title(), query),
        snippet == null ? null : highlight(snippet, query));
  }

  private String snippet(String source, String query) {
    if (source == null || source.isBlank()) {
      return null;
    }
    String loweredSource = source.toLowerCase(Locale.ROOT);
    int match = loweredSource.indexOf(query.toLowerCase(Locale.ROOT));
    if (match < 0) {
      return null;
    }
    int start = Math.max(0, match - (MAX_SNIPPET_LENGTH / 3));
    int end = Math.min(source.length(), start + MAX_SNIPPET_LENGTH);
    if (end - start < MAX_SNIPPET_LENGTH) {
      start = Math.max(0, end - MAX_SNIPPET_LENGTH);
    }
    String excerpt = source.substring(start, end).trim();
    return (start > 0 ? "…" : "") + excerpt + (end < source.length() ? "…" : "");
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength).trim() + "…";
  }

  private String highlight(String text, String query) {
    if (text == null) {
      return null;
    }
    String loweredText = text.toLowerCase(Locale.ROOT);
    String loweredQuery = query.toLowerCase(Locale.ROOT);
    StringBuilder highlighted = new StringBuilder();
    int cursor = 0;
    int match = loweredText.indexOf(loweredQuery, cursor);
    while (match >= 0) {
      highlighted.append(escapeHtml(text.substring(cursor, match)));
      highlighted
          .append("<mark>")
          .append(escapeHtml(text.substring(match, match + query.length())))
          .append("</mark>");
      cursor = match + query.length();
      match = loweredText.indexOf(loweredQuery, cursor);
    }
    highlighted.append(escapeHtml(text.substring(cursor)));
    return highlighted.toString();
  }

  private String escapeHtml(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
