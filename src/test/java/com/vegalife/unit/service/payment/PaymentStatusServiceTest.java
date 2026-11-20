package com.vegalife.unit.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vegalife.dto.response.payment.PaymentStatusResponse;
import com.vegalife.enums.subscription.PaymentLedgerStatus;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.service.payment.PaymentStatusService;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Money-only payment status (issue #181): no plan lookup exists any more. */
@ExtendWith(MockitoExtension.class)
class PaymentStatusServiceTest {

  @Mock private PaymentLedgerRepository paymentLedgerRepository;

  @InjectMocks private PaymentStatusService paymentStatusService;

  private UUID userId;
  private UUID paymentId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    paymentId = UUID.randomUUID();
  }

  @Test
  void getPaymentStatus_ownedPendingRow_returnsLedgerFieldsWithoutGatewayCall() {
    Instant createdAt = Instant.parse("2026-03-01T10:00:00Z");
    PaymentLedger ledger = pendingLedger(createdAt);
    when(paymentLedgerRepository.findByIdAndUserId(paymentId, userId))
        .thenReturn(Optional.of(ledger));

    PaymentStatusResponse response = paymentStatusService.getPaymentStatus(userId, paymentId);

    assertThat(response.getPaymentId()).isEqualTo(paymentId.toString());
    assertThat(response.getStatus()).isEqualTo("pending");
    assertThat(response.getAmount()).isEqualTo(49000L);
    assertThat(response.getCurrency()).isEqualTo("VND");
    assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    assertThat(response.getPaidAt()).isNull();
  }

  @Test
  void getPaymentStatus_fulfilledRow_exposesPaidAtFromLedger() {
    Instant paidAt = Instant.parse("2026-03-01T10:05:00Z");
    PaymentLedger ledger = pendingLedger(Instant.parse("2026-03-01T10:00:00Z"));
    ledger.setStatus(PaymentLedgerStatus.succeeded);
    ledger.setPaidAt(paidAt);
    when(paymentLedgerRepository.findByIdAndUserId(paymentId, userId))
        .thenReturn(Optional.of(ledger));

    PaymentStatusResponse response = paymentStatusService.getPaymentStatus(userId, paymentId);

    assertThat(response.getStatus()).isEqualTo("succeeded");
    assertThat(response.getPaidAt()).isEqualTo(paidAt);
  }

  @Test
  void getPaymentStatus_unknownOrForeignPayment_throwsNotFound() {
    when(paymentLedgerRepository.findByIdAndUserId(paymentId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> paymentStatusService.getPaymentStatus(userId, paymentId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Payment not found");
  }

  private PaymentLedger pendingLedger(Instant createdAt) {
    return PaymentLedger.builder()
        .id(paymentId)
        .userId(userId)
        .amount(49000L)
        .currency("VND")
        .status(PaymentLedgerStatus.pending)
        .provider("vnpay")
        .createdAt(createdAt)
        .build();
  }
}
