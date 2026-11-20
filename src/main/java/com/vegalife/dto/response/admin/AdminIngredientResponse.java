package com.vegalife.dto.response.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin view of an ingredient: its review state, its single measurement unit, and its nutrition per
 * one of that unit (ADR-022).
 *
 * <p>Nutrition is null-across-the-board when it has not been curated yet; it is never 0 to mean
 * unknown, because water is legitimately 0 kcal.
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

  /**
   * {@code G}, {@code ML} or {@code PIECE}; null when undecided, which makes every recipe using
   * this ingredient uncomputable rather than wrong.
   */
  private String unit;

  /** Per one {@link #unit}, never per 100 anything. */
  private BigDecimal calories;

  private BigDecimal proteinG;
  private BigDecimal carbohydrateG;
  private BigDecimal fatG;
  private BigDecimal fiberG;

  /** True when the unit and all five nutrition values are present. */
  private boolean nutritionResolved;

  private Instant createdAt;
  private Instant updatedAt;
}
