package com.vegalife.repository.recipe;

import com.vegalife.model.recipe.Ingredient;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class IngredientSpecifications {

  private IngredientSpecifications() {}

  /**
   * The {@code ingredient} table has no {@code deleted_at} column (data dictionary Table 11), so
   * unlike {@code CategorySpecifications} there is no soft-delete predicate here — every row is
   * listable.
   */
  public static Specification<Ingredient> withNameFilter(String name) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (name != null && !name.isBlank()) {
        predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.trim().toLowerCase() + "%"));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
