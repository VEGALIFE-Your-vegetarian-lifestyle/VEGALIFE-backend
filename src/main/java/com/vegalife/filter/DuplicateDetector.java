package com.vegalife.filter;

import com.vegalife.repository.post.PostEmbeddingRepository;
import com.vegalife.repository.post.PostEmbeddingRepository.NearestMatch;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Detects a post that duplicates an existing published post (issue #156, ADR-015).
 *
 * <p>Search runs in the database; only the threshold comparison lives here, so the rule can be
 * unit-tested without a database and the SQL stays a single query.
 *
 * <p>Above the threshold the post is routed to admin review — never auto-rejected. A false positive
 * on Vietnamese text is plausible enough that a model's opinion must not silently destroy a
 * member's post.
 */
@Component
@RequiredArgsConstructor
public class DuplicateDetector {

  private final PostEmbeddingRepository postEmbeddingRepository;
  private final ActiveEmbeddingModel activeEmbeddingModel;
  private final FilterProperties filterProperties;

  /**
   * Looks for a published post similar enough to the candidate to count as a duplicate.
   *
   * @param embedding the candidate's vector
   * @param candidatePostId the candidate's id, excluded from the search so it cannot match itself
   * @return the duplicate match at or above the threshold, or empty
   */
  public Optional<NearestMatch> findDuplicate(float[] embedding, UUID candidatePostId) {
    if (!filterProperties.isDuplicateEnabled()) {
      return Optional.empty();
    }
    double threshold = filterProperties.getDuplicateThreshold();
    return postEmbeddingRepository
        .findNearestPublished(embedding, activeEmbeddingModel.name(), candidatePostId)
        .filter(match -> match.similarity() >= threshold);
  }
}
