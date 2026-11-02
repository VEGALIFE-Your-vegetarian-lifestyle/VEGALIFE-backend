package com.vegalife.dto.request.location;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationNearbyRequest {

  @NotNull(message = "lat is required")
  @DecimalMin(value = "-90", message = "lat must be between -90 and 90")
  @DecimalMax(value = "90", message = "lat must be between -90 and 90")
  private BigDecimal lat;

  @NotNull(message = "lng is required")
  @DecimalMin(value = "-180", message = "lng must be between -180 and 180")
  @DecimalMax(value = "180", message = "lng must be between -180 and 180")
  private BigDecimal lng;

  /** Search radius in metres; must be positive (issue #10). */
  @NotNull(message = "radius is required")
  @DecimalMin(value = "0.0", inclusive = false, message = "radius must be greater than 0")
  private BigDecimal radius;
}
