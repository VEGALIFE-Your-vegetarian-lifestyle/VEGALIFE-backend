package com.vegalife.unit.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.admin.AdminPaymentListRequest;
import com.vegalife.dto.request.payment.PaymentListRequest;
import com.vegalife.dto.response.payment.PaymentHistoryItemResponse;
import com.vegalife.enums.subscription.PaymentLedgerStatus;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.service.payment.PaymentHistoryService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Since issue #181 the ledger is money-only, so these tests assert the money fields and that no
 * plan or acquisition hydration happens at all.
 */
@ExtendWith(MockitoExtension.class)
class PaymentHistoryServiceTest {

  @Mock private PaymentLedgerRepository paymentLedgerRepository;

  @InjectMocks private PaymentHistoryService paymentHistoryService;

  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
  }

  @Test
  void listMyPayments_scopesQueryToCallerAndSortsNewestFirst() {
    PaymentLedger row = succeededLedger(Instant.parse("2026-03-01T10:00:00Z"));
    when(paymentLedgerRepository.findByUserId(any(UUID.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(row)));

    PageResponse<PaymentHistoryItemResponse> response =
        paymentHistoryService.listMyPayments(userId, new PaymentListRequest());

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(paymentLedgerRepository).findByUserId(eq(userId), pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isZero();
    assertThat(pageable.getPageSize()).isEqualTo(20);
    assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));

    assertThat(response.getContent()).hasSize(1);
    PaymentHistoryItemResponse item = response.getContent().getFirst();
    assertThat(item.getId()).isEqualTo(row.getId());
    assertThat(item.getStatus()).isEqualTo("succeeded");
    assertThat(item.getAmount()).isEqualTo(49000L);
    assertThat(item.getCurrency()).isEqualTo("VND");
    assertThat(item.getTxnRef()).isEqualTo("TXN-1");
    assertThat(item.getUserId()).isNull();
  }

  @Test
  void listMyPayments_outOfRangePage_returnsEmptyContentNotError() {
    when(paymentLedgerRepository.findByUserId(any(UUID.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(5, 20), 0));

    PageResponse<PaymentHistoryItemResponse> response =
        paymentHistoryService.listMyPayments(userId, new PaymentListRequest());

    assertThat(response.getContent()).isEmpty();
    assertThat(response.getTotalElements()).isZero();
    assertThat(response.getPage()).isEqualTo(5);
  }

  @Test
  void listPayments_includesUserIdOnEveryRowAndSortsNewestFirst() {
    PaymentLedger row = succeededLedger(Instant.parse("2026-03-01T10:00:00Z"));
    when(paymentLedgerRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(row)));

    PageResponse<PaymentHistoryItemResponse> response =
        paymentHistoryService.listPayments(new AdminPaymentListRequest());

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(paymentLedgerRepository).findAll(any(Specification.class), pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber()).isZero();
    assertThat(pageable.getPageSize()).isEqualTo(20);
    assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));

    PaymentHistoryItemResponse item = response.getContent().getFirst();
    assertThat(item.getUserId()).isEqualTo(userId);
  }

  @Test
  void listPayments_invalidDateRange_throwsValidationException() {
    AdminPaymentListRequest request =
        AdminPaymentListRequest.builder()
            .createdFrom(Instant.parse("2026-03-02T00:00:00Z"))
            .createdTo(Instant.parse("2026-03-01T00:00:00Z"))
            .build();

    assertThatThrownBy(() -> paymentHistoryService.listPayments(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("createdFrom must be before createdTo");
    verify(paymentLedgerRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  void listPayments_invalidStatus_throwsValidationException() {
    AdminPaymentListRequest request = AdminPaymentListRequest.builder().status("bogus").build();

    assertThatThrownBy(() -> paymentHistoryService.listPayments(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Status must be one of: pending, succeeded, failed, refunded");
    verify(paymentLedgerRepository, never()).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  void listPayments_emptyFilters_delegatesWithAllNulls() {
    when(paymentLedgerRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(3, 20), 0));

    PageResponse<PaymentHistoryItemResponse> response =
        paymentHistoryService.listPayments(
            AdminPaymentListRequest.builder().page(3).size(20).build());

    assertThat(response.getContent()).isEmpty();
    assertThat(response.getTotalElements()).isZero();
    assertThat(response.getPage()).isEqualTo(3);
  }

  private PaymentLedger succeededLedger(Instant createdAt) {
    return PaymentLedger.builder()
        .id(UUID.randomUUID())
        .userId(userId)
        .amount(49000L)
        .currency("VND")
        .status(PaymentLedgerStatus.succeeded)
        .provider("vnpay")
        .txnRef("TXN-1")
        .paidAt(createdAt)
        .createdAt(createdAt)
        .build();
  }
}
