package com.vegalife.service.payment;

import com.vegalife.dto.request.admin.AdminPaymentListRequest;
import com.vegalife.dto.request.payment.PaymentListRequest;
import com.vegalife.dto.response.payment.PaymentHistoryItemResponse;
import com.vegalife.enums.subscription.PaymentLedgerStatus;
import com.vegalife.model.subscription.PaymentLedger;
import com.vegalife.repository.subscription.PaymentLedgerRepository;
import com.vegalife.repository.subscription.PaymentLedgerSpecifications;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ValidationException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only payment history for members and admins (FR-003, FR-004, FR-009; NFR-SEC-001,
 * NFR-PERF-001). One page query and nothing else: since issue #181 the ledger is money-only, so a
 * row needs no plan or subscription hydration.
 *
 * <p>Never writes {@code payment_ledger} (BR-PAY-004, ADR-008).
 */
@Service
@RequiredArgsConstructor
public class PaymentHistoryService {

  private final PaymentLedgerRepository paymentLedgerRepository;

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
    return toPage(page, false);
  }

  /**
   * Every ledger row across all users, newest first (FR-004, FR-005; NFR-SEC-001 — the caller is
   * already scoped to {@code ROLE_ADMIN} by {@code SecurityConfig}). Filters are optional and
   * ANDed; {@code createdFrom}/{@code createdTo} are inclusive. Same mapping path as the member
   * endpoint, with {@code userId} present on every row.
   */
  @Transactional(readOnly = true)
  public PageResponse<PaymentHistoryItemResponse> listPayments(AdminPaymentListRequest request) {
    PaymentLedgerStatus status = parseStatus(request.getStatus());
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
    return toPage(page, true);
  }

  private PaymentLedgerStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return PaymentLedgerStatus.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new ValidationException("Status must be one of: pending, succeeded, failed, refunded");
    }
  }

  private PageResponse<PaymentHistoryItemResponse> toPage(
      Page<PaymentLedger> page, boolean includeUserId) {
    return PageResponse.from(page.map(row -> toItem(row, includeUserId)));
  }

  private PaymentHistoryItemResponse toItem(PaymentLedger row, boolean includeUserId) {
    return PaymentHistoryItemResponse.builder()
        .id(row.getId())
        .amount(row.getAmount())
        .currency(row.getCurrency())
        .status(row.getStatus().name())
        .provider(row.getProvider())
        .txnRef(row.getTxnRef())
        .paidAt(row.getPaidAt())
        .createdAt(row.getCreatedAt())
        .userId(includeUserId ? row.getUserId() : null)
        .build();
  }
}
