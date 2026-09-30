package com.vegalife.shared.config;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Storage provider credentials and per-class upload ceilings, bound from {@code app.media.*}
 * (BR-MEDIA-001, BR-MEDIA-003). No allowlist entry or byte count is a code constant.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.media")
public class MediaProperties {

  private Cloudinary cloudinary = new Cloudinary();
  private Upload upload = new Upload();

  @Data
  public static class Cloudinary {

    private String cloudName = "";
    private String apiKey = "";
    private String apiSecret = "";
  }

  @Data
  public static class Upload {

    /** How long a grant stays confirmable before it is rejected (BR-MEDIA-005). */
    private Duration grantTtl = Duration.ofMinutes(15);

    private long maxImageBytes = 5L * 1024 * 1024;
    private long maxVideoBytes = 50L * 1024 * 1024;

    private List<String> allowedImageTypes = List.of("image/jpeg", "image/png", "image/webp");
    private List<String> allowedVideoTypes = List.of("video/mp4", "video/webm");

    public List<String> allowedTypes() {
      return Stream.concat(allowedImageTypes.stream(), allowedVideoTypes.stream()).toList();
    }

    public long maxBytesFor(String contentType) {
      return allowedVideoTypes.contains(contentType) ? maxVideoBytes : maxImageBytes;
    }

    public boolean isImage(String contentType) {
      return allowedImageTypes.contains(contentType);
    }
  }
}
