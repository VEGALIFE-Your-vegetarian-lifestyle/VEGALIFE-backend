package com.vegalife.repository.recipe;

import com.vegalife.model.recipe.Dish;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class DishSpecifications {

  private DishSpecifications() {}

  public static Specification<Dish> activeWithNameFilter(String name) {
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
