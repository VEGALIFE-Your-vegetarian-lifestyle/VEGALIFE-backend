package com.vegalife.dto.response.admin;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of a dish's curated ingredient list, as an administrator sees it.
 *
 * <p>Carries {@code moderationStatus} unlike the member-facing suggestion shape: an admin curating
 * the list must be able to see that a name is not approved yet, because such an entry is invisible
 * to members.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDishIngredientResponse {

  private UUID ingredientId;
  private String name;

  /** The shared ingredient name's own review state (BR-DISH-001). */
  private String moderationStatus;

  /** Advisory: normally mandatory for this dish. Never blocks a recipe (BR-DISH-002). */
  private boolean required;

  private Instant createdAt;
  private Instant updatedAt;
}
