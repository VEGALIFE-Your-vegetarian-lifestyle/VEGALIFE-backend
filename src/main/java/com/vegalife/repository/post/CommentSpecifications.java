package com.vegalife.repository.post;

import com.vegalife.model.post.Comment;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class CommentSpecifications {

  private CommentSpecifications() {}

  /**
   * Optional admin filters for the comment list. Status derives from {@code deleted_at}: {@code
   * active} = IS NULL, {@code removed} = IS NOT NULL; null status means both (issue #2).
   */
  public static Specification<Comment> withFilters(
      String status, UUID userId, UUID postId, Instant createdFrom, Instant createdTo) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if ("active".equals(status)) {
        predicates.add(cb.isNull(root.get("deletedAt")));
      } else if ("removed".equals(status)) {
        predicates.add(cb.isNotNull(root.get("deletedAt")));
      }
      if (userId != null) {
        predicates.add(cb.equal(root.get("userId"), userId));
      }
      if (postId != null) {
        predicates.add(cb.equal(root.get("postId"), postId));
      }
      if (createdFrom != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
      }
      if (createdTo != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
