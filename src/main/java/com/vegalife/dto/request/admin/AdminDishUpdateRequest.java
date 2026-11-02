package com.vegalife.dto.request.admin;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin partial update of a dish name: an omitted field keeps its current value.
 *
 * <p>Editing does not reset the name's moderation state (BR-DISH-001). The admin is the approver,
 * so their edit is itself an approval decision; re-queueing an approved name because someone fixed
 * a typo would withhold it from every recipe that uses it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDishUpdateRequest {

  @Size(max = 255, message = "name must be at most 255 characters")
  private String name;

  @Size(max = 2000, message = "description must be at most 2000 characters")
  private String description;

  @Size(max = 1000, message = "imageUrl must be at most 1000 characters")
  private String imageUrl;

  @Size(max = 50, message = "cuisineType must be at most 50 characters")
  private String cuisineType;
}
