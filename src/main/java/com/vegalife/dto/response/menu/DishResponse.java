package com.vegalife.dto.response.menu;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The dish behind one meal of a meal plan, embedded in full so a caller describing a plan never has
 * to resolve an id. Deliberately narrower than the {@code Dish} entity: the soft-delete column
 * {@code deletedAt} and the audit timestamps are persistence concerns, not read-contract data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishResponse {

  private UUID id;
  private String name;
  private String description;
  private String imageUrl;
  private String cuisineType;
}
