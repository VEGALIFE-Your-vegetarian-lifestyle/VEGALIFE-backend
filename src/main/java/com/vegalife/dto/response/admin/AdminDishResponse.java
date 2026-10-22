package com.vegalife.dto.response.admin;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin view of a dish name, including its review state.
 *
 * <p>Members never see this shape: the public {@code DishResponse} omits {@code moderationStatus}
 * because the member listing already excludes anything not approved, so the field would be a
 * constant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDishResponse {

  private UUID id;
  private String name;
  private String description;
  private String imageUrl;
  private String cuisineType;

  /** {@code PENDING}, {@code APPROVED} or {@code REJECTED}. */
  private String moderationStatus;

  private Instant createdAt;
  private Instant updatedAt;
}
