package com.vegalife.repository.recipe;

import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.ModerationStatus;
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

  /** Admin listing: every state, including pending and rejected, for review. */
  public static Specification<Dish> anyModerationWithNameFilter(String name) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("deletedAt")));

      if (name != null && !name.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.trim().toLowerCase() + "%"));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
