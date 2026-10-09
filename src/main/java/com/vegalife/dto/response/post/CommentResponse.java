package com.vegalife.dto.response.post;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CommentResponse {

  UUID id;
  UUID postId;
  UUID parentId;
  UUID userId;
  String content;
  Instant createdAt;
  Instant updatedAt;
}
