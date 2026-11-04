package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPlanCreateRequest {

  /** Normalized to uppercase by {@code AdminPlanService}; accepted case-insensitively here. */
  @NotBlank(message = "Code is required")
  @Pattern(
      regexp = "^[A-Za-z0-9_]{1,20}$",
      message = "Code must be 1-20 letters/digits/underscores")
  private String code;

  @NotBlank(message = "Name is required")
  @Size(max = 50, message = "Name must be at most 50 characters")
  private String name;

  @Size(max = 500, message = "Description must be at most 500 characters")
  private String description;

  @NotNull(message = "requestQuota is required")
  @Min(value = 0, message = "requestQuota must be >= 0")
  private Integer requestQuota;

  @NotNull(message = "priceAmount is required")
  @Min(value = 0, message = "priceAmount must be >= 0")
  private Long priceAmount;

  @NotBlank(message = "priceCurrency is required")
  @Size(min = 3, max = 3, message = "priceCurrency must be a 3-letter ISO currency code")
  private String priceCurrency;

  @NotNull(message = "active is required")
  private Boolean active;

  @NotNull(message = "sortOrder is required")
  private Integer sortOrder;
}
