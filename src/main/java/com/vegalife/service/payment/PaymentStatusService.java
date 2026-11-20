package com.vegalife.service.payment;

import com.vegalife.dto.response.payment.PaymentStatusResponse;
import com.vegalife.model.subscription.PaymentLedger;
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
 * stays the only source of truth for a payment's status (BR-PAY-004).
 *
 * <p>Money only since issue #181: the ledger no longer names a plan, so the response does not
 * either. Callers that need the plan read the acquisition history.
 */
@Service
@RequiredArgsConstructor
public class PaymentStatusService {

  private final PaymentLedgerRepository paymentLedgerRepository;

  @Transactional(readOnly = true)
  public PaymentStatusResponse getPaymentStatus(UUID userId, UUID paymentId) {
    PaymentLedger ledger =
        paymentLedgerRepository
            .findByIdAndUserId(paymentId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

    return PaymentStatusResponse.builder()
        .paymentId(ledger.getId().toString())
        .status(ledger.getStatus().name())
        .amount(ledger.getAmount())
        .currency(ledger.getCurrency())
        .createdAt(ledger.getCreatedAt())
        .paidAt(ledger.getPaidAt())
        .build();
  }
}
