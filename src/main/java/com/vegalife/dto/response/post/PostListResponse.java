package com.vegalife.dto.response.post;

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
public class PostListResponse {

  private UUID id;
  private String title;
  private String content;
  private String featuredImageUrl;
  private String status;
  private Integer viewCount;
  private Instant publishedAt;
  private Instant createdAt;
}
