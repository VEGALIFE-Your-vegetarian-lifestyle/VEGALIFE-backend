package com.vegalife.repository.subscription;

import com.vegalife.model.subscription.PaymentLedger;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteria predicates for the admin payment list (FR-004, FR-005; NFR-MAINT-002), mirroring {@code
 * UserSpecifications}. Filters are optional and combined with AND; {@code createdFrom} and {@code
 * createdTo} are inclusive. Member requests never reach this class — they use {@code findByUserId}
 * scoped by the authenticated principal (NFR-SEC-001).
 */
public final class PaymentLedgerSpecifications {

  private PaymentLedgerSpecifications() {}

  public static Specification<PaymentLedger> withFilters(
      UUID userId, PaymentLedger.Status status, Instant createdFrom, Instant createdTo) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (userId != null) {
        predicates.add(cb.equal(root.get("userId"), userId));
      }
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
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
