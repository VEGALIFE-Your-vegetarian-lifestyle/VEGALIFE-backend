package com.vegalife.dto.response.media;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The media read model, shared by the confirmation and the read endpoints. For a row still in
 * {@code uploading} every measurement field is null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaResponse {

  private UUID mediaId;
  private String status;
  private String mediaUrl;
  private String thumbnailUrl;
  private String description;
  private String mimeType;
  private Long fileSizeBytes;
  private Integer width;
  private Integer height;
  private Integer durationSeconds;
  private Instant createdAt;
}
