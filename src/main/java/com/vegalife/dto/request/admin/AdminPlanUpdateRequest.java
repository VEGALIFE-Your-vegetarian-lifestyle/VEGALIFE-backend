package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Partial update of a plan's mutable fields. {@code code} is never editable here (issue #119). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPlanUpdateRequest {

  @Pattern(regexp = "(?s).*\\S.*", message = "Name must not be blank")
  @Size(max = 50, message = "Name must be at most 50 characters")
  private String name;

  @Size(max = 500, message = "Description must be at most 500 characters")
  private String description;

  @Min(value = 0, message = "requestQuota must be >= 0")
  private Integer requestQuota;

  @Min(value = 0, message = "priceAmount must be >= 0")
  private Long priceAmount;

  @Size(min = 3, max = 3, message = "priceCurrency must be a 3-letter ISO currency code")
  private String priceCurrency;

  private Boolean active;

  private Integer sortOrder;

  @AssertTrue(message = "At least one field must be provided")
  public boolean isUpdateRequestNotEmpty() {
    return name != null
        || description != null
        || requestQuota != null
        || priceAmount != null
        || priceCurrency != null
        || active != null
        || sortOrder != null;
  }
}
