package com.vegalife.dto.response.location;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationResponse {

  private UUID id;
  private String name;
  private String address;
  private BigDecimal latitude;
  private BigDecimal longitude;
  private String phoneNumber;
  private String placeType;

  /** Full stored opening-hours document, parsed from the {@code jsonb} column (issue #9). */
  private JsonNode openingHours;

  private String websiteUrl;
  private BigDecimal rating;
  private Integer priceLevel;

  /** Distance from the search centre in metres; only set by {@code GET /api/locations/nearby}. */
  private Double distance;
}
