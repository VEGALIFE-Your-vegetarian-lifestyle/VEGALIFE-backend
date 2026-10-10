package com.vegalife.repository.post;

import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class PostSpecifications {

  private PostSpecifications() {}

  /**
   * Every non-deleted post, optionally narrowed by status, filter flag, author, category, and
   * createdAt range. The category join is de-duplicated so a matching post is never returned twice.
   */
  public static Specification<Post> allWithFilters(
      Post.Status status,
      Post.Flag flag,
      UUID userId,
      UUID categoryId,
      Instant createdFrom,
      Instant createdTo) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("deletedAt")));

      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (flag != null) {
        predicates.add(cb.equal(root.get("flag"), flag));
      }
      if (userId != null) {
        predicates.add(cb.equal(root.get("user").get("id"), userId));
      }
      if (createdFrom != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
      }
      if (createdTo != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
      }
      if (categoryId != null) {
        Join<Post, Category> category = root.join("categories", JoinType.INNER);
        predicates.add(cb.equal(category.get("id"), categoryId));
        if (query != null) {
          query.distinct(true);
        }
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
