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

  @NotBlank(message = "Code is required")
  @Pattern(
      regexp = "^[A-Z0-9_]{1,20}$",
      message = "Code must be 1-20 uppercase letters/digits/underscores")
  private String code;

  @NotBlank(message = "Name is required")
  @Size(max = 50, message = "Name must be at most 50 characters")
  private String name;

  @NotNull(message = "monthlyRequestLimit is required")
  @Min(value = 0, message = "monthlyRequestLimit must be >= 0")
  private Integer monthlyRequestLimit;

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
