package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vegalife.model.admin.PlatformSetting;
import com.vegalife.repository.admin.PlatformSettingRepository;
import com.vegalife.service.admin.PlatformSettingsProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers the issue #13 follow-up wiring contract: every getter reads per call (no snapshot), and a
 * missing, blank, or invalid stored value falls back to the {@code SettingKey} default rather than
 * throwing.
 */
@ExtendWith(MockitoExtension.class)
class PlatformSettingsProviderTest {

  @Mock private PlatformSettingRepository platformSettingRepository;

  private PlatformSettingsProvider provider;

  @BeforeEach
  void setUp() {
    provider = new PlatformSettingsProvider(platformSettingRepository);
  }

  @Test
  void missingRowFallsBackToDefault() {
    when(platformSettingRepository.findByKey("max_image_upload_mb")).thenReturn(Optional.empty());

    assertThat(provider.getMaxImageUploadBytes()).isEqualTo(5L * 1024 * 1024);
  }

  @Test
  void blankValueFallsBackToDefault() {
    stub("ai_chat_enabled", "  ");

    assertThat(provider.isAiChatEnabled()).isTrue();
  }

  @Test
  void invalidValueFallsBackToDefaultInsteadOfThrowing() {
    stub("max_image_upload_mb", "not-a-number");

    assertThat(provider.getMaxImageUploadBytes()).isEqualTo(5L * 1024 * 1024);
  }

  @Test
  void outOfBoundsValueFallsBackToDefault() {
    stub("max_video_upload_mb", "99999");

    assertThat(provider.getMaxVideoUploadBytes()).isEqualTo(50L * 1024 * 1024);
  }

  @Test
  void validStoredValueIsUsedAsIs() {
    stub("max_image_upload_mb", "20");

    assertThat(provider.getMaxImageUploadBytes()).isEqualTo(20L * 1024 * 1024);
  }

  @Test
  void allowedUploadTypesConcatenatesImageAndVideoLists() {
    stub("upload_allowed_image_types", "image/jpeg");
    stub("upload_allowed_video_types", "video/mp4");

    assertThat(provider.getAllowedUploadTypes()).containsExactly("image/jpeg", "video/mp4");
    assertThat(provider.isImageContentType("image/jpeg")).isTrue();
    assertThat(provider.isImageContentType("video/mp4")).isFalse();
  }

  @Test
  void maxUploadBytesForPicksCeilingByContentType() {
    stub("upload_allowed_video_types", "video/mp4");
    stub("max_image_upload_mb", "5");
    stub("max_video_upload_mb", "50");

    assertThat(provider.maxUploadBytesFor("video/mp4")).isEqualTo(50L * 1024 * 1024);
    assertThat(provider.maxUploadBytesFor("image/jpeg")).isEqualTo(5L * 1024 * 1024);
  }

  @Test
  void contentFilterThresholdsValidPairIsUsedAsIs() {
    stub("content_filter_accept_threshold", "0.80");
    stub("content_filter_reject_threshold", "0.20");

    var thresholds = provider.getContentFilterThresholds();

    assertThat(thresholds.acceptThreshold()).isEqualTo(0.80);
    assertThat(thresholds.rejectThreshold()).isEqualTo(0.20);
  }

  @Test
  void contentFilterThresholdsInvalidPairFallsBackToBothDefaults() {
    // Individually in-bounds (both within [0, 1]) but violate reject < accept.
    stub("content_filter_accept_threshold", "0.30");
    stub("content_filter_reject_threshold", "0.60");

    var thresholds = provider.getContentFilterThresholds();

    assertThat(thresholds.acceptThreshold()).isEqualTo(0.70);
    assertThat(thresholds.rejectThreshold()).isEqualTo(0.45);
  }

  private void stub(String key, String value) {
    when(platformSettingRepository.findByKey(key))
        .thenReturn(
            Optional.of(PlatformSetting.builder().category("x").key(key).value(value).build()));
  }
}
