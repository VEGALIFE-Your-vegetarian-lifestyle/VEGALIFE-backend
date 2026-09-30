package com.vegalife.unit.service.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.service.media.CloudinaryUploadProvider;
import com.vegalife.service.media.UploadGrant;
import com.vegalife.shared.config.MediaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CloudinaryUploadProviderTest {

  private static final int IMAGE_CEILING = 5 * 1024 * 1024;
  private static final int VIDEO_CEILING = 50 * 1024 * 1024;

  private CloudinaryUploadProvider provider;

  @BeforeEach
  void setUp() {
    MediaProperties properties = new MediaProperties();
    properties.getCloudinary().setCloudName("testcloud");
    properties.getCloudinary().setApiKey("test-api-key");
    properties.getCloudinary().setApiSecret("test-secret");
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
  void prepare_videoGrant_usesVideoCeilingAndFormats() {
    UploadGrant grant = provider.prepare("testcloud/user/media", "video/mp4");

    assertThat(grant.publicId()).isEqualTo("testcloud/user/media");
    assertThat(grant.maxFileSize()).isEqualTo(VIDEO_CEILING);
    assertThat(grant.allowedFormats()).containsExactly("mp4", "webm");
    assertThat(grant.apiKey()).isEqualTo("test-api-key");
    assertThat(grant.signature()).matches("[0-9a-f]{40}");
    assertThat(grant.uploadUrl()).contains("upload");
  }
}
