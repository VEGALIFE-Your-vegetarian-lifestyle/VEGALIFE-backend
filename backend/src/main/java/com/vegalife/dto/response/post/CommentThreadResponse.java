package com.vegalife.dto.response.post;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CommentThreadResponse {

  UUID id;
  UUID userId;
  String username;
  String avatarUrl;
  String content;
  boolean edited;
  boolean deleted;
  Instant createdAt;
  Instant updatedAt;
  List<CommentThreadResponse> replies;
}
