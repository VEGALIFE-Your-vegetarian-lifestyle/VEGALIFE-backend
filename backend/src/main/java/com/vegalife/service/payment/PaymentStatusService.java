package com.vegalife.service.payment;

import com.vegalife.dto.mapper.subscription.SubscriptionMapper;
import com.vegalife.dto.response.payment.PaymentStatusResponse;
import com.vegalife.model.subscription.AiPlan;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.AiPlanRepository;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads one payment's status for its owner (FR-007, FR-008; NFR-SEC-001, NFR-SEC-002; ADR-008).
 *
 * <p>The lookup is scoped to {@code (paymentId, userId)}, so another member's payment id resolves
 * exactly like an unknown one and nothing outside the caller's own ledger rows is observable. The
 * row is read as-is: no gateway call, no QueryDR, no fulfilment side effects — the IPN webhook
 * stays the only source of truth for status transitions (BR-PAY-001).
 */
@Service
@RequiredArgsConstructor
public class PaymentStatusService {

  private final PaymentLedgerRepository paymentLedgerRepository;
  private final AiPlanRepository planRepository;
  private final SubscriptionMapper subscriptionMapper;

  @Transactional(readOnly = true)
  public PaymentStatusResponse getPaymentStatus(UUID userId, UUID paymentId) {
    PaymentLedger ledger =
        paymentLedgerRepository
            .findByIdAndUserId(paymentId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

    AiPlan plan =
        planRepository
            .findById(ledger.getPlanId())
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

    return PaymentStatusResponse.builder()
        .paymentId(ledger.getId().toString())
        .status(ledger.getStatus().name())
        .plan(subscriptionMapper.toPlanSummary(plan))
        .amount(ledger.getAmount())
        .currency(ledger.getCurrency())
        .createdAt(ledger.getCreatedAt())
        .paidAt(ledger.getPaidAt())
        .build();
  }
}
