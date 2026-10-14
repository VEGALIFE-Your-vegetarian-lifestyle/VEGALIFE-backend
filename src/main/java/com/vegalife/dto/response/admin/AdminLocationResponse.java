package com.vegalife.dto.response.admin;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Same stored attributes as the public {@code LocationResponse}, plus {@code createdAt}/{@code
 * updatedAt} for admin audit visibility (issue #144). The public list/nearby endpoints (issue #9,
 * #10) never expose these timestamps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLocationResponse {

  private UUID id;
  private String name;
  private String address;
  private BigDecimal latitude;
  private BigDecimal longitude;
  private String phoneNumber;
  private String placeType;
  private JsonNode openingHours;
  private String websiteUrl;
  private BigDecimal rating;
  private Integer priceLevel;
  private Instant createdAt;
  private Instant updatedAt;
}
