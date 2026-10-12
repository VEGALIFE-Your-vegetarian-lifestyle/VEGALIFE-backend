package com.vegalife.unit.service.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.service.media.CloudinaryUploadProvider;
import com.vegalife.service.media.UploadGrant;
import com.vegalife.shared.config.MediaProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloudinaryUploadProviderTest {

  private static final int IMAGE_CEILING = 5 * 1024 * 1024;
  private static final int VIDEO_CEILING = 50 * 1024 * 1024;
  private static final String API_SECRET = "test-secret";

  private CloudinaryUploadProvider provider;

  @BeforeEach
  void setUp() {
    MediaProperties properties = new MediaProperties();
    properties.getCloudinary().setCloudName("testcloud");
    properties.getCloudinary().setApiKey("test-api-key");
    properties.getCloudinary().setApiSecret(API_SECRET);
    provider = new CloudinaryUploadProvider(properties);
  }

  @Test
  void prepare_imageGrant_returnsSignedLocalGrant() {
    UploadGrant grant = provider.prepare("testcloud/user/media", "image/jpeg");

    assertThat(grant.publicId()).isEqualTo("testcloud/user/media");
    assertThat(grant.maxFileSize()).isEqualTo(IMAGE_CEILING);
    assertThat(grant.allowedFormats()).containsExactly("jpg", "png", "webp");
    assertThat(grant.apiKey()).isEqualTo("test-api-key");
    assertThat(grant.timestamp()).isPositive();
    assertThat(grant.signature()).matches("[0-9a-f]{40}");
    assertThat(grant.uploadUrl()).contains("upload");
  }

  @Test
  void prepare_imageGrant_signatureMatchesCloudinaryVerificationString()
      throws NoSuchAlgorithmException {
    UploadGrant grant = provider.prepare("testcloud/user/media", "image/jpeg");

    String verifiedParams =
        String.join(
            "&",
            "allowed_formats=" + String.join(",", grant.allowedFormats()),
            "public_id=" + grant.publicId(),
            "timestamp=" + grant.timestamp());

    assertThat(grant.signature())
        .as("cloudinary verifies only allowed_formats, public_id and timestamp")
        .isEqualTo(sha1Hex(verifiedParams + API_SECRET));
  }

  @Test
  void prepare_videoGrant_usesVideoCeilingAndFormats() {
    UploadGrant grant = provider.prepare("testcloud/user/media", "video/mp4");

    assertThat(grant.publicId()).isEqualTo("testcloud/user/media");
    assertThat(grant.maxFileSize()).isEqualTo(VIDEO_CEILING);
    assertThat(grant.allowedFormats()).containsExactly("mp4", "webm");
    assertThat(grant.apiKey()).isEqualTo("test-api-key");
    assertThat(grant.signature()).matches("[0-9a-f]{40}");
    assertThat(grant.uploadUrl()).contains("upload");
  }

  private static String sha1Hex(String value) throws NoSuchAlgorithmException {
    byte[] digest =
        MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
    return HexFormat.of().formatHex(digest);
  }
}
