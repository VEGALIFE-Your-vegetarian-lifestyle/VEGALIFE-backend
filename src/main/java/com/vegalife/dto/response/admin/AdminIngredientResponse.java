package com.vegalife.dto.response.admin;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin view of an ingredient name, including its review state.
 *
 * <p>Carries no nutrition values: since issue #163 nutrition is an author-entered total on {@code
 * recipe}, not a per-ingredient figure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminIngredientResponse {

  private UUID id;
  private String name;

  /** {@code PENDING}, {@code APPROVED} or {@code REJECTED}. */
  private String moderationStatus;

  private Instant createdAt;
  private Instant updatedAt;
}
