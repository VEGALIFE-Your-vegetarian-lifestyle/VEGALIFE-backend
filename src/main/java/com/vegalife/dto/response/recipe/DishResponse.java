package com.vegalife.dto.response.recipe;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishResponse {

  private UUID id;
  private String name;
  private String description;
  private String imageUrl;
  private String cuisineType;
  private Instant createdAt;
}
