package com.vegalife.repository.user;

import com.vegalife.model.user.User;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

  private UserSpecifications() {}

  public static Specification<User> activeWithFilters(
      User.Status status, User.Role role, Instant createdFrom, Instant createdTo) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("deletedAt")));

      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (role != null) {
        predicates.add(cb.equal(root.get("role"), role));
      }
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
