package com.vegalife.service.subscription;

import com.vegalife.dto.request.subscription.SubscriptionHistoryRequest;
import com.vegalife.dto.response.subscription.SubscriptionHistoryResponse;
import com.vegalife.enums.subscription.AcquisitionSource;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.AiSubscription;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.AiSubscriptionRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.shared.dto.PageResponse;
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
 * The member's acquisition history (issue #181, ADR-021): every plan they hold, bought or claimed.
 *
 * <p>This is the only endpoint that can name a plan. {@code payment_ledger} no longer references
 * the catalogue, so the payment endpoints answer money questions and this one answers "what do I
 * own". Plans and payments are batch-loaded per page, so a page of N acquisitions costs two extra
 * queries, not N.
 */
@Service
@RequiredArgsConstructor
public class AcquisitionHistoryService {

  private final AiSubscriptionRepository subscriptionRepository;
  private final AiPlanRepository planRepository;
  private final PaymentLedgerRepository paymentLedgerRepository;

  @Transactional(readOnly = true)
  public PageResponse<SubscriptionHistoryResponse> getMyAcquisitions(
      UUID userId, SubscriptionHistoryRequest request) {
    Pageable pageable =
        PageRequest.of(
            request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<AiSubscription> page = subscriptionRepository.findByUserId(userId, pageable);

    List<AiSubscription> rows = page.getContent();
    Map<UUID, AiPlan> plans = loadPlans(rows);
    Map<UUID, PaymentLedger> payments = loadPayments(rows);

    return PageResponse.from(page.map(acquisition -> toResponse(acquisition, plans, payments)));
  }

  private Map<UUID, AiPlan> loadPlans(List<AiSubscription> rows) {
    Set<UUID> planIds = rows.stream().map(AiSubscription::getPlanId).collect(Collectors.toSet());
    return planRepository.findAllById(planIds).stream()
        .collect(Collectors.toMap(AiPlan::getId, Function.identity()));
  }

  private Map<UUID, PaymentLedger> loadPayments(List<AiSubscription> rows) {
    Set<UUID> paymentIds =
        rows.stream()
            .map(AiSubscription::getPaymentId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (paymentIds.isEmpty()) {
      return Map.of();
    }
    return paymentLedgerRepository.findAllById(paymentIds).stream()
        .collect(Collectors.toMap(PaymentLedger::getId, Function.identity()));
  }

  private SubscriptionHistoryResponse toResponse(
      AiSubscription acquisition, Map<UUID, AiPlan> plans, Map<UUID, PaymentLedger> payments) {
    AiPlan plan = plans.get(acquisition.getPlanId());
    if (plan == null) {
      throw new IllegalStateException("Plan of acquisition not found: " + acquisition.getPlanId());
    }
    PaymentLedger payment =
        acquisition.getPaymentId() == null ? null : payments.get(acquisition.getPaymentId());

    return SubscriptionHistoryResponse.builder()
        .planCode(plan.getCode())
        .planName(plan.getName())
        .source(
            acquisition.getPaymentId() == null
                ? AcquisitionSource.CLAIM
                : AcquisitionSource.PURCHASE)
        .grantedAt(acquisition.getGrantedAt())
        .paymentId(acquisition.getPaymentId())
        .amount(payment == null ? null : payment.getAmount())
        .currency(payment == null ? null : payment.getCurrency())
        .status(payment == null ? null : payment.getStatus().name())
        .createdAt(acquisition.getCreatedAt())
        .build();
  }
}
