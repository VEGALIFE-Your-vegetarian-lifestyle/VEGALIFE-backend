package com.vegalife.dto.response.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin view of an ingredient name, including its review state.
 *
 * <p>Nutrition values are included here, unlike the member-facing suggestion response, because an
 * admin reviewing a name needs to see whether the data behind it is plausible.
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

  private BigDecimal calories;
  private BigDecimal proteinG;
  private BigDecimal carbohydrateG;
  private BigDecimal fatG;
  private BigDecimal fiberG;

  private Instant createdAt;
  private Instant updatedAt;
}
