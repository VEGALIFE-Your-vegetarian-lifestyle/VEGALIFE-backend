package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin partial update of an ingredient: an omitted field keeps its current value.
 *
 * <p>Editing does not reset the moderation state (BR-DISH-001) — see {@link AdminDishUpdateRequest}
 * for why.
 *
 * <p>Nutrition is all-or-nothing <em>after the merge</em>, not per request. Sending only {@code
 * calories} is accepted when the other four already exist, so a single wrong value can be
 * corrected; it is refused when the ingredient has none, because that would leave a half-answer
 * that produces a plausible-looking recipe total.
 *
 * <p>Changing {@code unit} reinterprets every existing recipe amount for this ingredient — {@code
 * 200 g} of rice becomes {@code 200 ml} of rice. That is allowed deliberately (ADR-022), and every
 * recipe using it is recomputed in the same transaction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminIngredientUpdateRequest {

  @Size(max = 100, message = "name must be at most 100 characters")
  private String name;

  @Size(max = 30, message = "unit must be at most 30 characters")
  private String unit;

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
