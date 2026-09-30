package com.vegalife.dto.request.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadRequest {

  @NotBlank(message = "Content type is required")
  private String contentType;

  /** Advisory only — never used to derive the stored object key (BR-MEDIA-004). */
  @Size(max = 255, message = "File name must not exceed 255 characters")
  private String fileName;

  /** Declared size; rejected before any row is created when above the class ceiling. */
  private Long sizeBytes;
}
