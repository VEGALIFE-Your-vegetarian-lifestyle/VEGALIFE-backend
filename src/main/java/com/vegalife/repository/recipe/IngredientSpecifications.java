package com.vegalife.repository.recipe;

import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.recipe.Ingredient;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class IngredientSpecifications {

  private IngredientSpecifications() {}

  /**
   * Ingredients a member may see: not soft-deleted and approved by an admin (ADR-016).
   *
   * <p>{@code ingredient} gained {@code deleted_at} with the moderation columns, so unlike the
   * original version of this query there is now a soft-delete predicate as well.
   */
  public static Specification<Ingredient> withNameFilter(String name) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("deletedAt")));
      predicates.add(cb.equal(root.get("moderationStatus"), ModerationStatus.APPROVED));

      if (name != null && !name.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.trim().toLowerCase() + "%"));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Admin listing: every state, including pending and rejected, for review.
   *
   * @param status narrow to one state, or null for all of them
   */
  public static Specification<Ingredient> anyModerationWithNameFilter(
      String name, ModerationStatus status) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("deletedAt")));

      if (status != null) {
        predicates.add(cb.equal(root.get("moderationStatus"), status));
      }
      if (name != null && !name.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.trim().toLowerCase() + "%"));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
