package com.vegalife.repository.post;

import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.repository.LikePatterns;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class PostSpecifications {

  private PostSpecifications() {}

  /**
   * Every non-deleted post, optionally narrowed by status, filter flag, author, category, keyword,
   * and createdAt range. The category join is de-duplicated so a matching post is never returned
   * twice.
   */
  public static Specification<Post> allWithFilters(
      PostStatus status,
      PostFlag flag,
      UUID userId,
      UUID categoryId,
      Instant createdFrom,
      Instant createdTo) {
    return filtering(status, flag, userId, categoryId, createdFrom, createdTo, null);
  }

  /**
   * The admin filter set: every non-deleted post, optionally narrowed by status, flag, author,
   * category, createdAt range and keyword.
   *
   * <p>Delegates to {@link #filtering} rather than repeating the predicate list, so keyword search
   * means exactly the same thing here as on the member feed.
   */
  public static Specification<Post> allWithFilters(
      PostStatus status,
      PostFlag flag,
      UUID userId,
      UUID categoryId,
      Instant createdFrom,
      Instant createdTo,
      String keyword) {
    return filtering(status, flag, userId, categoryId, createdFrom, createdTo, keyword);
  }

  /**
   * The same visibility-aware query with an optional keyword. Every supplied filter narrows the
   * result; none of them can widen the status/author predicates the caller passes in.
   */
  public static Specification<Post> filtering(
      PostStatus status,
      PostFlag flag,
      UUID userId,
      UUID categoryId,
      Instant createdFrom,
      Instant createdTo,
      String keyword) {
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
      if (keyword != null && !keyword.isBlank()) {
        predicates.add(keywordMatches(root, cb, keyword));
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

  /**
   * Published, non-deleted posts whose title or content contains {@code query} (case-insensitive),
   * optionally narrowed by category and createdAt range (issue #40). Only published content is
   * searchable — drafts and moderated-out posts never surface in search.
   */
  public static Specification<Post> searchable(
      String query, UUID categoryId, Instant createdFrom, Instant createdTo) {
    return filtering(PostStatus.published, null, null, categoryId, createdFrom, createdTo, query);
  }

  /**
   * Case-insensitive whole-phrase match across the post's searchable text. {@code %}, {@code _},
   * and {@code \} are treated literally.
   */
  private static Predicate keywordMatches(Root<Post> root, CriteriaBuilder cb, String keyword) {
    String pattern = LikePatterns.contains(keyword);
    return cb.or(
        cb.like(cb.lower(root.get("title")), pattern, LikePatterns.ESCAPE),
        cb.like(cb.lower(root.get("content")), pattern, LikePatterns.ESCAPE));
  }
}
