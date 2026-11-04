package com.vegalife.dto.response.subscription;

import com.vegalife.enums.subscription.AcquisitionSource;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One acquisition row (issue #181, ADR-021): a plan this member holds, bought or claimed.
 *
 * <p>Lifecycle fields are gone — there is no status, renewal, or cancellation because grants never
 * expire. {@code paymentId}/{@code amount}/{@code currency}/{@code status} are null for a claim.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionHistoryResponse {

  private String planCode;
  private String planName;
  private AcquisitionSource source;
  private Instant grantedAt;
  private UUID paymentId;
  private Long amount;
  private String currency;
  private String status;
  private Instant createdAt;
}
