package com.vegalife.dto.response.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRecipeListResponse {

  private UUID id;
  private String name;
  private String description;
  private String instructions;
  private Integer prepTimeMinutes;
  private Integer cookTimeMinutes;
  private Integer servings;
  private String difficulty;

  /** Derived from deletedAt (no stored column): ACTIVE or DELETED. */
  private String status;

  private UUID dishId;
  private String dishName;
  private Set<UUID> categoryIds;
  private List<IngredientItem> ingredients;
  private Instant createdAt;
  private Instant updatedAt;

  private UUID userId;
  private String username;
  private String email;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class IngredientItem {

    private UUID ingredientId;
    private String name;
    private BigDecimal amount;
    private String unit;
  }
}
