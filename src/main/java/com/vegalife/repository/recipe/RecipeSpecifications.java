package com.vegalife.repository.recipe;

import com.vegalife.enums.recipe.Difficulty;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class RecipeSpecifications {

  /** Escape character for user-supplied {@code LIKE} patterns. */
  private static final char LIKE_ESCAPE = '\\';

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
   * The recipe catalogue: non-deleted recipes, optionally narrowed by author, review state, dish,
   * difficulty, total time, and keyword. The public route passes {@code moderationStatus =
   * APPROVED} and no author; the author route passes the caller and an optional state.
   *
   * <p>Every supplied filter narrows the result and none can widen the visibility predicates the
   * caller passes in.
   */
  public static Specification<Recipe> catalogue(
      UUID userId,
      ModerationStatus moderationStatus,
      UUID dishId,
      Difficulty difficulty,
      Integer maxTotalTimeMinutes,
      String keyword) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("deletedAt")));

      if (userId != null) {
        predicates.add(cb.equal(root.get("user").get("id"), userId));
      }
      if (moderationStatus != null) {
        predicates.add(cb.equal(root.get("moderationStatus"), moderationStatus));
      }
      if (dishId != null) {
        predicates.add(cb.equal(root.get("dish").get("id"), dishId));
      }
      if (difficulty != null) {
        predicates.add(cb.equal(root.get("difficulty"), difficulty));
      }
      if (maxTotalTimeMinutes != null) {
        // A null prep or cook time makes the sum null, so the recipe does not match: unknown is
        // not "short enough".
        predicates.add(
            cb.lessThanOrEqualTo(
                cb.sum(root.<Integer>get("prepTimeMinutes"), root.<Integer>get("cookTimeMinutes")),
                maxTotalTimeMinutes));
      }
      if (keyword != null && !keyword.isBlank()) {
        predicates.add(keywordMatches(root, query, cb, keyword));
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
      predicates.add(textMatches(root, cb, query));

      if (createdFrom != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
      }
      if (createdTo != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /** Case-insensitive whole-phrase match across the recipe's own text fields. */
  private static Predicate textMatches(Root<Recipe> root, CriteriaBuilder cb, String keyword) {
    String pattern = likePattern(keyword);
    return cb.or(
        cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
        cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE),
        cb.like(cb.lower(root.get("instructions")), pattern, LIKE_ESCAPE));
  }

  /**
   * The catalogue keyword: the recipe's text, its dish name, or any attached ingredient name. The
   * ingredient side is an {@code EXISTS} subquery so a recipe with several matching lines is
   * returned once and the page count stays correct.
   */
  private static Predicate keywordMatches(
      Root<Recipe> root, CriteriaQuery<?> query, CriteriaBuilder cb, String keyword) {
    String pattern = likePattern(keyword);
    Predicate dish = cb.like(cb.lower(root.get("dish").get("name")), pattern, LIKE_ESCAPE);

    Subquery<UUID> ingredientMatch = query.subquery(UUID.class);
    Root<RecipeIngredient> line = ingredientMatch.from(RecipeIngredient.class);
    Join<RecipeIngredient, Ingredient> ingredient = line.join("ingredient", JoinType.INNER);
    ingredientMatch
        .select(line.get("recipeId"))
        .where(
            cb.equal(line.get("recipeId"), root.get("id")),
            cb.like(cb.lower(ingredient.get("name")), pattern, LIKE_ESCAPE));

    return cb.or(textMatches(root, cb, keyword), dish, cb.exists(ingredientMatch));
  }

  private static String likePattern(String keyword) {
    String escaped =
        keyword
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }
}
