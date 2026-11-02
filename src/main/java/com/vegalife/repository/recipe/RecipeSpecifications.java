package com.vegalife.repository.recipe;

import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Recipe;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class RecipeSpecifications {

  private RecipeSpecifications() {}

  /**
   * Every recipe — including soft-deleted ones, whose status the API derives from {@code deletedAt}
   * — optionally narrowed by author, category, and createdAt range. The category join goes recipe
   * -> post_recipe -> post -> post_category and is de-duplicated so a matching recipe is never
   * returned twice.
   */
  public static Specification<Recipe> allWithFilters(
      UUID userId, UUID categoryId, Instant createdFrom, Instant createdTo) {
    return allWithFilters(userId, categoryId, createdFrom, createdTo, null);
  }

  /**
   * Same query, additionally narrowed to one review state (ADR-016).
   *
   * @param moderationStatus the state to keep, or {@code null} for every state
   */
  public static Specification<Recipe> allWithFilters(
      UUID userId,
      UUID categoryId,
      Instant createdFrom,
      Instant createdTo,
      ModerationStatus moderationStatus) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (userId != null) {
        predicates.add(cb.equal(root.get("user").get("id"), userId));
      }
      if (createdFrom != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
      }
      if (createdTo != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
      }
      if (moderationStatus != null) {
        predicates.add(cb.equal(root.get("moderationStatus"), moderationStatus));
      }
      if (categoryId != null) {
        Join<Recipe, Post> post = root.join("posts", JoinType.INNER);
        Join<Post, Category> category = post.join("categories", JoinType.INNER);
        predicates.add(cb.equal(category.get("id"), categoryId));
        if (query != null) {
          query.distinct(true);
        }
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Non-deleted, approved recipes whose name, description, or instructions contains {@code query}
   * (case-insensitive), optionally narrowed by createdAt range (issue #40). No category filter: a
   * recipe has no category of its own, only categories reachable indirectly through an attached
   * post, and search deliberately does not follow that join — a category filter never excludes
   * recipes.
   *
   * <p>Search is public, so the approval predicate belongs here rather than at the call site: a
   * recipe still awaiting review must not be discoverable through its instructions (ADR-016). Rows
   * that predate the rule were backfilled to {@code APPROVED}, so nothing previously indexed
   * disappears.
   */
  public static Specification<Recipe> searchable(
      String query, Instant createdFrom, Instant createdTo) {
    return (root, cq, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("deletedAt")));
      predicates.add(cb.equal(root.get("moderationStatus"), ModerationStatus.APPROVED));

      String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
      predicates.add(
          cb.or(
              cb.like(cb.lower(root.get("name")), pattern),
              cb.like(cb.lower(root.get("description")), pattern),
              cb.like(cb.lower(root.get("instructions")), pattern)));

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
