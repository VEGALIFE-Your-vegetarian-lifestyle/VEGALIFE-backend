package com.vegalife.repository.recipe;

import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Recipe;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
}
