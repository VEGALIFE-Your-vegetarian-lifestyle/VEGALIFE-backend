package com.vegalife.dto.request.profile;

import jakarta.validation.constraints.*;
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

  @Size(max = 50, message = "Preferences must contain at most 50 entries")
  private List<@Size(max = 100, message = "Each preference must not exceed 100 characters") String>
      preferences;

  @Size(max = 50, message = "Dislikes must contain at most 50 entries")
  private List<@Size(max = 100, message = "Each dislike must not exceed 100 characters") String>
      dislikes;

  @Size(max = 50, message = "Allergies must contain at most 50 entries")
  private List<@Size(max = 100, message = "Each allergy must not exceed 100 characters") String>
      allergies;

  @Size(max = 50, message = "Restrictions must contain at most 50 entries")
  private List<@Size(max = 100, message = "Each restriction must not exceed 100 characters") String>
      restrictions;

  @Size(max = 200, message = "Health goal must not exceed 200 characters")
  private String healthGoal;

  public boolean hasAnyField() {
    return heightCm != null
        || weightKg != null
        || age != null
        || gender != null
        || description != null
        || avatarUrl != null
        || hasDietaryField();
  }

  public boolean hasDietaryField() {
    return preferences != null
        || dislikes != null
        || allergies != null
        || restrictions != null
        || healthGoal != null;
  }
}
