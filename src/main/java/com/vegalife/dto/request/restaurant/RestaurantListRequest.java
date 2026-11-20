package com.vegalife.dto.request.restaurant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantListRequest {

  @Min(value = 0, message = "Page must be >= 0")
  private Integer page;

  @Min(value = 1, message = "Size must be between 1 and 100")
  @Max(value = 100, message = "Size must be between 1 and 100")
  private Integer size;

  @DecimalMin(value = "0.0", message = "minRating must be between 0.0 and 5.0")
  @DecimalMax(value = "5.0", message = "minRating must be between 0.0 and 5.0")
  private BigDecimal minRating;

  @Min(value = 1, message = "maxPriceLevel must be between 1 and 4")
  @Max(value = 4, message = "maxPriceLevel must be between 1 and 4")
  private Integer maxPriceLevel;

  /** true checks each candidate against its opening hours at the current server time (#9). */
  private Boolean openNow;

  /**
   * Case-insensitive substring match against name, description, or an attached, approved,
   * non-deleted dish's name (issue #196). Null or blank is treated as "no keyword filter".
   */
  private String keyword;

  public int getPage() {
    return page == null ? 0 : page;
  }

  public int getSize() {
    return size == null ? 20 : size;
  }

  public boolean isOpenNow() {
    return Boolean.TRUE.equals(openNow);
  }
}
