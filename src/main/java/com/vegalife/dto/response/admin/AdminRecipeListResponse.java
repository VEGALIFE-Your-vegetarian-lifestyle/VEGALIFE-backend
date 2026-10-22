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

  /**
   * {@code PENDING}, {@code APPROVED} or {@code REJECTED} (ADR-016). Distinct from {@link #status},
   * which is about deletion: a recipe can be ACTIVE and still awaiting review.
   */
  private String moderationStatus;

  /** Relevance score at submission time; {@code null} means never scored. */
  private Double score;

  private UUID dishId;
  private String dishName;

  /** The shared dish name's own review state; approving this recipe does not change it. */
  private String dishModerationStatus;

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

    /** The shared ingredient name's own review state. */
    private String moderationStatus;

    private BigDecimal amount;
    private String unit;
  }
}
