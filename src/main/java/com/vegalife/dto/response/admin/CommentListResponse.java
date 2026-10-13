package com.vegalife.dto.response.admin;

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
public class CommentListResponse {

  private UUID id;
  private UUID postId;
  private UUID parentId;
  private UUID userId;
  private String username;
  private String content;
  private String status;
  private Instant createdAt;
  private Instant updatedAt;
}
