package com.vegalife.dto.response.ingredient;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nutrition columns are intentionally excluded — this is an autocomplete suggestion, not a label.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientResponse {

  private UUID id;
  private String name;
  private Instant createdAt;
}
