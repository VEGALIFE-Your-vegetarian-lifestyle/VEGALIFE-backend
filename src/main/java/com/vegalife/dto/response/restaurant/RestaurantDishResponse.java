package com.vegalife.dto.response.restaurant;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nested dish shape for a restaurant's curated {@code dishes} array (issue #195). Deliberately
 * smaller than {@code DishResponse}: no {@code createdAt}, no moderation/audit fields — a
 * restaurant response is never the place to expose those.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDishResponse {

  private UUID id;
  private String name;
  private String description;
  private String imageUrl;
  private String cuisineType;
}
