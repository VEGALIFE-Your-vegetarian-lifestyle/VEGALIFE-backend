package com.vegalife.repository.menu;

import com.vegalife.model.menu.Menu;
import com.vegalife.model.menu.MenuStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class MenuSpecifications {

  private MenuSpecifications() {}

  /**
   * The caller's own menus intersecting an optional inclusive window (BR-MENU-002) and optional
   * status. Only the clauses whose bound is present are added, so PostgreSQL never sees an untyped
   * {@code ? is null} parameter. A menu intersects when {@code startDate <= to AND endDate >=
   * from}.
   */
  public static Specification<Menu> ownedByWithinWindowAndStatus(
      UUID userId, LocalDate from, LocalDate to, MenuStatus status) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("userId"), userId));
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("endDate"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("startDate"), to));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
