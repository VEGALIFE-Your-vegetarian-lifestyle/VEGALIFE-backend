package com.vegalife.dto.response.restaurant;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantResponse {

  private UUID id;
  private String name;
  private String address;
  private BigDecimal latitude;
  private BigDecimal longitude;
  private String phoneNumber;

  /** Full stored opening-hours document, parsed from the {@code jsonb} column (issue #9). */
  private JsonNode openingHours;

  private String websiteUrl;
  private BigDecimal rating;
  private Integer priceLevel;

  /** Curated gallery of absolute image URLs, or {@code null} when unset (issue #159). */
  private List<String> imageUrls;

  private String description;

  /** Distance from the search centre in metres; only set by {@code GET /api/restaurants/nearby}. */
  private Double distance;

  /**
   * Curated dishes, ordered by name, soft-deleted dishes filtered out (issue #195). Only populated
   * by {@code GET /api/restaurants/{restaurantId}}; left {@code null} by list/nearby.
   */
  private List<RestaurantDishResponse> dishes;
}
