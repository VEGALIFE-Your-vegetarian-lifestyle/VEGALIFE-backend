package com.vegalife.service.payment;

import com.vegalife.dto.mapper.subscription.SubscriptionMapper;
import com.vegalife.dto.request.admin.AdminPaymentListRequest;
import com.vegalife.dto.request.payment.PaymentListRequest;
import com.vegalife.dto.response.payment.PaymentHistoryItemResponse;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.subscription.PaymentLedgerSpecifications;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ValidationException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only payment history for members (FR-003, FR-009; NFR-SEC-001, NFR-PERF-001). Rendering a
 * page runs one page query plus at most two batch loads (distinct plan ids, distinct subscription
 * ids) — never a row-by-row lookup. Never writes {@code payment_ledger} (BR-PAY-004, ADR-008).
 */
@Service
@RequiredArgsConstructor
public class PaymentHistoryService {

  private final PaymentLedgerRepository paymentLedgerRepository;
  private final AiPlanRepository planRepository;
  private final AiSubscriptionRepository subscriptionRepository;
  private final SubscriptionMapper subscriptionMapper;

  /**
   * The caller's own ledger rows, newest first. The repository predicate carries the authenticated
   * user id (NFR-SEC-001); a page beyond the last returns an empty content list, not an error
   * (FR-006).
   */
  @Transactional(readOnly = true)
  public PageResponse<PaymentHistoryItemResponse> listMyPayments(
      UUID userId, PaymentListRequest request) {
    Pageable pageable =
        PageRequest.of(
            request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<PaymentLedger> page = paymentLedgerRepository.findByUserId(userId, pageable);
    return hydrate(page, false);
  }

  /**
   * Every ledger row across all users, newest first (FR-004, FR-005; NFR-SEC-001 — the caller is
   * already scoped to {@code ROLE_ADMIN} by {@code SecurityConfig}). Filters are optional and
   * ANDed; {@code createdFrom}/{@code createdTo} are inclusive. Same hydration path as the member
   * endpoint, with {@code userId} present on every row.
   */
  @Transactional(readOnly = true)
  public PageResponse<PaymentHistoryItemResponse> listPayments(AdminPaymentListRequest request) {
    PaymentLedger.Status status = parseStatus(request.getStatus());
    if (request.getCreatedFrom() != null
        && request.getCreatedTo() != null
        && request.getCreatedFrom().isAfter(request.getCreatedTo())) {
      throw new ValidationException("createdFrom must be before createdTo");
    }

    Pageable pageable =
        PageRequest.of(
            request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<PaymentLedger> page =
        paymentLedgerRepository.findAll(
            PaymentLedgerSpecifications.withFilters(
                request.getUserId(), status, request.getCreatedFrom(), request.getCreatedTo()),
            pageable);
    return hydrate(page, true);
  }

  private PaymentLedger.Status parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return PaymentLedger.Status.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new ValidationException("Status must be one of: pending, succeeded, failed, refunded");
    }
  }

  /** One page query + at most two {@code IN} batch loads, then map rows to items (FR-009). */
  private PageResponse<PaymentHistoryItemResponse> hydrate(
      Page<PaymentLedger> page, boolean includeUserId) {
    List<PaymentLedger> rows = page.getContent();
    Set<UUID> planIds = rows.stream().map(PaymentLedger::getPlanId).collect(Collectors.toSet());
    Set<UUID> subscriptionIds =
        rows.stream()
            .map(PaymentLedger::getSubscriptionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    Map<UUID, AiPlan> plans =
        planRepository.findAllById(planIds).stream()
            .collect(Collectors.toMap(AiPlan::getId, Function.identity()));
    Map<UUID, AiSubscription> subscriptions =
        subscriptionRepository.findAllById(subscriptionIds).stream()
            .collect(Collectors.toMap(AiSubscription::getId, Function.identity()));

    Page<PaymentHistoryItemResponse> items =
        page.map(
            row ->
                toItem(
                    row,
                    plans.get(row.getPlanId()),
                    row.getSubscriptionId() == null
                        ? null
                        : subscriptions.get(row.getSubscriptionId()),
                    includeUserId));
    return PageResponse.from(items);
  }

  private PaymentHistoryItemResponse toItem(
      PaymentLedger row, AiPlan plan, AiSubscription subscription, boolean includeUserId) {
    return PaymentHistoryItemResponse.builder()
        .id(row.getId())
        .amount(row.getAmount())
        .currency(row.getCurrency())
        .status(row.getStatus().name())
        .provider(row.getProvider())
        .txnRef(row.getTxnRef())
        .paidAt(row.getPaidAt())
        .createdAt(row.getCreatedAt())
        .subscription(
            subscription == null
                ? null
                : PaymentHistoryItemResponse.SubscriptionContext.builder()
                    .id(subscription.getId())
                    .status(subscription.getStatus().name())
                    .startedAt(subscription.getStartedAt())
                    .renewalDate(subscription.getRenewalDate())
                    .build())
        .plan(plan == null ? null : subscriptionMapper.toPlanSummary(plan))
        .userId(includeUserId ? row.getUserId() : null)
        .build();
  }
}
