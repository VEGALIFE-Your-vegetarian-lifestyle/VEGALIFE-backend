package com.vegalife.dto.request.post;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Replaces the post's whole recipe set; an empty set detaches every attached recipe. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostRecipeAttachRequest {

  @NotNull(message = "recipeIds is required")
  private Set<UUID> recipeIds;
}
