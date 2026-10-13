package com.vegalife.dto.response.media;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Uniform upload instruction: the client posts {@code file} to {@code url} together with {@code
 * headers} and {@code fields} in one multipart request. The shape is provider-agnostic so the
 * frontend has a single code path.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadGrantResponse {

  private UUID mediaId;
  private String status;
  private Instant expiresAt;
  private UploadInstruction upload;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UploadInstruction {

    private String method;
    private String url;
    private Map<String, String> headers;
    private UploadFields fields;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UploadFields {

    @JsonProperty("api_key")
    private String apiKey;

    private long timestamp;

    private String signature;

    /** Server-chosen from the media ID and owner user ID; the client cannot alter it. */
    @JsonProperty("public_id")
    private String publicId;

    @JsonProperty("max_file_size")
    private long maxFileSize;

    @JsonProperty("allowed_formats")
    private List<String> allowedFormats;
  }
}
