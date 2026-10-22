package com.vegalife.dto.response.post;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A semantically similar post and its similarity score (issue #7). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatedPostResponse {

  private PostListResponse post;

  /**
   * Cosine similarity to the source post's title+content embedding, in [-1, 1], higher = closer.
   */
  private double similarityScore;
}
