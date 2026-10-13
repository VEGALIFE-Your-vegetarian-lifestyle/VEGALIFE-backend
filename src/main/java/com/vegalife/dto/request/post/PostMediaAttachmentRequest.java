package com.vegalife.dto.request.post;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostMediaAttachmentRequest {

  @NotNull private Set<UUID> mediaIds;
}
