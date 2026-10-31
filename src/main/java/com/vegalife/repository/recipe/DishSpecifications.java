package com.vegalife.repository.recipe;

import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.recipe.Dish;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class DishSpecifications {

  private DishSpecifications() {}

  /**
   * Dishes a member may see: not soft-deleted and approved by an admin.
   *
   * <p>The approval predicate lives here rather than in the caller so a new listing endpoint cannot
   * inadvertently expose unreviewed names — the leak would be a missing predicate, which is
   * invisible in review (ADR-016).
   */
  public static Specification<Dish> activeWithNameFilter(String name) {
    return activeWithFilters(name, null);
  }

  /**
   * Member-visible dishes, optionally narrowed by name substring and cuisine type (issue #163).
   *
   * <p>The approval predicate stays here rather than in the caller for the reason above (ADR-016).
   * {@code cuisineType} is an exact, case-insensitive match: it is a short controlled value, not
   * free text, so a substring search would only invite near-duplicates.
   */
  public static Specification<Dish> activeWithFilters(String name, String cuisineType) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("deletedAt")));
      predicates.add(cb.equal(root.get("moderationStatus"), ModerationStatus.APPROVED));

      if (name != null && !name.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.trim().toLowerCase() + "%"));
      }
      if (cuisineType != null && !cuisineType.isBlank()) {
        predicates.add(
            cb.equal(cb.lower(root.get("cuisineType")), cuisineType.trim().toLowerCase()));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Admin listing: every state, including pending and rejected, for review.
   *
   * @param status narrow to one state, or null for all of them
   */
  public static Specification<Dish> anyModerationWithNameFilter(
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
