package com.vegalife.dto.request.profile;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

  @DecimalMin(value = "0.01", message = "Height must be between 0 and 300 cm")
  @DecimalMax(value = "300.00", message = "Height must be between 0 and 300 cm")
  @Digits(integer = 3, fraction = 2, message = "Height must have at most 2 decimal places")
  private BigDecimal heightCm;

  @DecimalMin(value = "0.01", message = "Weight must be between 0 and 500 kg")
  @DecimalMax(value = "500.00", message = "Weight must be between 0 and 500 kg")
  @Digits(integer = 3, fraction = 2, message = "Weight must have at most 2 decimal places")
  private BigDecimal weightKg;

  @Min(value = 1, message = "Age must be between 1 and 150")
  @Max(value = 150, message = "Age must be between 1 and 150")
  private Integer age;

  @Pattern(regexp = "^(male|female|other)$", message = "Gender must be male, female, or other")
  private String gender;

  @Size(max = 2000, message = "Description must not exceed 2000 characters")
  private String description;

  @Pattern(
      regexp = "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})([/\\w .-]*)*/?$",
      message = "Avatar URL must be a valid URL")
  private String avatarUrl;

  public boolean hasAnyField() {
    return heightCm != null
        || weightKg != null
        || age != null
        || gender != null
        || description != null
        || avatarUrl != null;
  }
}
