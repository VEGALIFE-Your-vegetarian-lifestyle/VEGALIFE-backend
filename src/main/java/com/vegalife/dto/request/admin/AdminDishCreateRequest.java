package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin creation of a dish name.
 *
 * <p>No {@code moderationStatus} field: a name an admin creates is {@code APPROVED} by definition —
 * the creator is the approver. State changes afterwards go through the approve/reject endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDishCreateRequest {

  @NotBlank(message = "name is required")
  @Size(max = 255, message = "name must be at most 255 characters")
  private String name;

  @Size(max = 2000, message = "description must be at most 2000 characters")
  private String description;

  @Size(max = 1000, message = "imageUrl must be at most 1000 characters")
  private String imageUrl;

  @Size(max = 50, message = "cuisineType must be at most 50 characters")
  private String cuisineType;
}
