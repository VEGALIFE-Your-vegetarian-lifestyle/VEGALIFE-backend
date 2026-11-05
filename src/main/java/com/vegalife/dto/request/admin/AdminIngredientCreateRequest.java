package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin creation of an ingredient, including the measurement unit its nutrition is per (ADR-022).
 *
 * <p>Creating a name is approving it, so no {@code moderationStatus} field is accepted here.
 *
 * <p>Nutrition is optional at creation and all-or-nothing when present: five values or none. A
 * partially filled ingredient could produce a recipe total that looks authoritative while silently
 * omitting a macro, so the service refuses it rather than storing a half-answer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminIngredientCreateRequest {

  @NotBlank(message = "name is required")
  @Size(max = 100, message = "name must be at most 100 characters")
  private String name;

  /**
   * {@code G}, {@code ML} or {@code PIECE} (and their accepted spelling variants). Required here,
   * unlike on a member's recipe line: an admin is deliberately recording the basis the nutrition
   * below is measured on, and that basis cannot be guessed.
   */
  @Size(max = 30, message = "unit must be at most 30 characters")
  private String unit;

  /**
   * Energy per one {@code unit}. For {@code PIECE} this is the estimate for one whole piece,
   * entered as that estimate — there is no separate piece weight to record.
   */
  @DecimalMin(value = "0", message = "calories must be >= 0")
  private BigDecimal calories;

  @DecimalMin(value = "0", message = "proteinG must be >= 0")
  private BigDecimal proteinG;

  @DecimalMin(value = "0", message = "carbohydrateG must be >= 0")
  private BigDecimal carbohydrateG;

  @DecimalMin(value = "0", message = "fatG must be >= 0")
  private BigDecimal fatG;

  @DecimalMin(value = "0", message = "fiberG must be >= 0")
  private BigDecimal fiberG;
}
