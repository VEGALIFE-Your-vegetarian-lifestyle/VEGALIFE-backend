package com.vegalife.dto.request.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.vegalife.shared.validation.AbsoluteHttpUrl;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationCreateRequest {

  @NotBlank(message = "Name is required")
  @Size(max = 255, message = "Name must be at most 255 characters")
  private String name;

  @NotBlank(message = "Address is required")
  @Size(max = 255, message = "Address must be at most 255 characters")
  private String address;

  @NotNull(message = "Latitude is required")
  @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
  @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
  private BigDecimal latitude;

  @NotNull(message = "Longitude is required")
  @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
  @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
  private BigDecimal longitude;

  @Size(max = 20, message = "Phone number must be at most 20 characters")
  private String phoneNumber;

  @NotBlank(message = "placeType is required")
  @Pattern(
      regexp = "^(restaurant|grocery|cafe|market)$",
      message = "placeType must be one of: restaurant, grocery, cafe, market")
  private String placeType;

  /** See `docs/apis/locations/get-locations.md` for the expected shape. */
  private JsonNode openingHours;

  @AbsoluteHttpUrl private String websiteUrl;

  @DecimalMin(value = "0.0", message = "Rating must be between 0.0 and 5.0")
  @DecimalMax(value = "5.0", message = "Rating must be between 0.0 and 5.0")
  private BigDecimal rating;

  @Min(value = 1, message = "priceLevel must be between 1 and 4")
  @Max(value = 4, message = "priceLevel must be between 1 and 4")
  private Integer priceLevel;

  /** Curated gallery of absolute http/https image URLs (issue #159). */
  @AbsoluteHttpUrl private List<String> imageUrls;

  private String description;

  @AssertTrue(message = "openingHours must be a JSON object")
  public boolean isOpeningHoursObject() {
    return openingHours == null || openingHours.isObject();
  }
}
