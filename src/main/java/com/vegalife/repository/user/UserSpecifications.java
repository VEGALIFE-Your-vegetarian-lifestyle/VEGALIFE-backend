package com.vegalife.repository.user;

import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.user.User;
import com.vegalife.repository.LikePatterns;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

  private UserSpecifications() {}

  public static Specification<User> activeWithFilters(
      UserStatus status, UserRole role, Instant createdFrom, Instant createdTo) {
    return activeWithFilters(status, role, createdFrom, createdTo, null, null);
  }

  /**
   * Non-deleted accounts, optionally narrowed by status, role, createdAt range, a case-insensitive
   * {@code keyword} matching {@code displayName} or {@code email}, and an {@code emailVerified}
   * flag.
   *
   * <p>Every supplied filter narrows the result; none can bring a soft-deleted account back into
   * the list. The keyword spans the two columns an administrator identifies an account by, so a
   * search for an email address and a search for a display name behave the same way.
   *
   * @param keyword the phrase to look for, or {@code null}/blank for no keyword filter
   * @param emailVerified the flag to match, or {@code null} for both verified and unverified
   */
  public static Specification<User> activeWithFilters(
      UserStatus status,
      UserRole role,
      Instant createdFrom,
      Instant createdTo,
      String keyword,
      Boolean emailVerified) {
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
      if (emailVerified != null) {
        predicates.add(cb.equal(root.get("emailVerified"), emailVerified));
      }
      if (keyword != null && !keyword.isBlank()) {
        predicates.add(keywordMatches(root, cb, keyword));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Case-insensitive whole-phrase match on {@code displayName} or {@code email}. {@code %}, {@code
   * _} and {@code \} are treated literally.
   */
  private static Predicate keywordMatches(Root<User> root, CriteriaBuilder cb, String keyword) {
    String pattern = LikePatterns.contains(keyword);
    return cb.or(
        cb.like(cb.lower(root.get("displayName")), pattern, LikePatterns.ESCAPE),
        cb.like(cb.lower(root.get("email")), pattern, LikePatterns.ESCAPE));
  }
}
