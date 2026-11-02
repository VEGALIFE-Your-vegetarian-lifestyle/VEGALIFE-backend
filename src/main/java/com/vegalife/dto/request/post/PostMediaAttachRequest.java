package com.vegalife.dto.request.post;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Replaces the post's whole media set; an empty set detaches every attached media. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostMediaAttachRequest {

  @NotNull(message = "mediaIds is required")
  private Set<UUID> mediaIds;
}
