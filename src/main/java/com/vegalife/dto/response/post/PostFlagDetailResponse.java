package com.vegalife.dto.response.post;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The {@code flag_detail} snapshot of a post, as the API exposes it (ADR-024, ADR-025).
 *
 * <p>The stored value is one JSON object per verdict, so the shape is stable but sparse: {@link
 * #kind} always identifies the verdict (and always equals {@code flag}), and only the fields that
 * verdict owns are present. Absent fields are omitted from the payload rather than sent as {@code
 * null}.
 *
 * <ul>
 *   <li>{@code FILTER_PASSED} — {@code score}, {@code threshold}
 *   <li>{@code FILTER_FAILED} — {@code outcome}, {@code score}, {@code threshold}
 *   <li>{@code SIMILAR_CONTENT} — {@code postId}, {@code similarity}, {@code threshold}
 *   <li>{@code FILTER_SKIPPED} — {@code cause}
 * </ul>
 *
 * <p>Only the admin post endpoints expose this: a published post carrying {@code FILTER_FAILED}
 * means an admin overrode the filter, and that is a moderation fact, not feed content (ADR-025).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostFlagDetailResponse {

  /**
   * Always equal to the post's {@code flag}: {@code FILTER_PASSED}, {@code FILTER_FAILED}, {@code
   * SIMILAR_CONTENT} or {@code FILTER_SKIPPED}.
   */
  private String kind;

  /** For {@code FILTER_FAILED}: {@code FAIL} (below the reject threshold) or {@code UNCERTAIN}. */
  private String outcome;

  /** For {@code FILTER_PASSED}/{@code FILTER_FAILED}: the relevance score, 0–1. */
  private Double score;

  /**
   * The boundary in force for that run: the accept/reject threshold for {@code
   * FILTER_PASSED}/{@code FILTER_FAILED}, the duplicate threshold for {@code SIMILAR_CONTENT}.
   * Absent on rows written before ADR-024 and when the caller could not know it.
   */
  private Double threshold;

  /** For {@code SIMILAR_CONTENT}: the id of the published post this one resembled. */
  private String postId;

  /** For {@code SIMILAR_CONTENT}: cosine similarity in 0–1; render as a percentage. */
  private Double similarity;

  /** For {@code FILTER_SKIPPED}: {@code DISABLED}, {@code SWEPT} or {@code UNAVAILABLE}. */
  private String cause;

  /** Reads a stored snapshot; {@code null} in, {@code null} out. */
  public static PostFlagDetailResponse from(JsonNode node) {
    if (node == null || node.isNull() || !node.isObject()) {
      return null;
    }
    return PostFlagDetailResponse.builder()
        .kind(text(node, "kind"))
        .outcome(text(node, "outcome"))
        .score(number(node, "score"))
        .threshold(number(node, "threshold"))
        .postId(text(node, "postId"))
        .similarity(number(node, "similarity"))
        .cause(text(node, "cause"))
        .build();
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static Double number(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || !value.isNumber() ? null : value.asDouble();
  }
}
