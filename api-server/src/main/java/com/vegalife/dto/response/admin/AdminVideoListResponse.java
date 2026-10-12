package com.vegalife.dto.response.admin;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminVideoListResponse {

  private UUID id;
  private String mediaUrl;
  private String thumbnailUrl;
  private String description;
  private String status;
  private Integer durationSeconds;
  private Long fileSizeBytes;
  private String mimeType;
  private Integer width;
  private Integer height;
  private String externalId;
  private Instant createdAt;
  private Instant updatedAt;

  private UUID userId;
  private String username;
  private String email;

  private List<AdminVideoPostResponse> posts;
}
