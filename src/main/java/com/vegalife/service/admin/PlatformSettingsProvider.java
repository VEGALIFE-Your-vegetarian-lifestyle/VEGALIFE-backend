package com.vegalife.service.admin;

import com.vegalife.model.admin.PlatformSetting;
import com.vegalife.repository.admin.PlatformSettingRepository;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Live, per-call read access to the eight settings in {@link SettingKey} (issue #13 follow-up).
 * Every getter re-reads {@code platform_setting} on each call — no cache, no snapshot at bean
 * construction — so a PATCH takes effect on the very next request without a restart. A missing row,
 * a blank value, or a value that fails {@link SettingKey#validate(String)} falls back to the
 * registry default with a WARN log; this provider never throws for a bad stored value; only {@code
 * AdminSettingsService} rejects bad values, on write.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformSettingsProvider {

  private final PlatformSettingRepository platformSettingRepository;

  public List<String> getAllowedImageTypes() {
    return getStringList(SettingKey.UPLOAD_ALLOWED_IMAGE_TYPES);
  }

  public List<String> getAllowedVideoTypes() {
    return getStringList(SettingKey.UPLOAD_ALLOWED_VIDEO_TYPES);
  }

  public List<String> getAllowedUploadTypes() {
    return Stream.concat(getAllowedImageTypes().stream(), getAllowedVideoTypes().stream()).toList();
  }

  public boolean isImageContentType(String contentType) {
    return getAllowedImageTypes().contains(contentType);
  }

  public long getMaxImageUploadBytes() {
    return megabytesToBytes(getInt(SettingKey.MAX_IMAGE_UPLOAD_MB));
  }

  public long getMaxVideoUploadBytes() {
    return megabytesToBytes(getInt(SettingKey.MAX_VIDEO_UPLOAD_MB));
  }

  public long maxUploadBytesFor(String contentType) {
    return getAllowedVideoTypes().contains(contentType)
        ? getMaxVideoUploadBytes()
        : getMaxImageUploadBytes();
  }

  public boolean isContentFilterEnabled() {
    return getBoolean(SettingKey.CONTENT_FILTER_ENABLED);
  }

  /**
   * Both thresholds together, since the pair must satisfy {@code reject < accept}
   * (`EmbeddingRelevanceScorer`'s invariant). Each value is individually bounds-checked first; if
   * the pair still violates the invariant (e.g. two individually valid but contradictory admin
   * writes), both fall back to their registry defaults rather than letting a bad pair reach the
   * scorer.
   */
  public ContentFilterThresholds getContentFilterThresholds() {
    double accept = getDecimal(SettingKey.CONTENT_FILTER_ACCEPT_THRESHOLD);
    double reject = getDecimal(SettingKey.CONTENT_FILTER_REJECT_THRESHOLD);
    if (!(reject < accept)) {
      log.warn(
          "content filter thresholds invalid (reject={}, accept={}); falling back to defaults",
          reject,
          accept);
      accept =
          SettingKey.CONTENT_FILTER_ACCEPT_THRESHOLD.parseDecimal(
              SettingKey.CONTENT_FILTER_ACCEPT_THRESHOLD.getDefaultValue());
      reject =
          SettingKey.CONTENT_FILTER_REJECT_THRESHOLD.parseDecimal(
              SettingKey.CONTENT_FILTER_REJECT_THRESHOLD.getDefaultValue());
    }
    return new ContentFilterThresholds(accept, reject);
  }

  public boolean isAiChatEnabled() {
    return getBoolean(SettingKey.AI_CHAT_ENABLED);
  }

  /** Accept/reject pair for the content filter, guaranteed to satisfy {@code reject < accept}. */
  public record ContentFilterThresholds(double acceptThreshold, double rejectThreshold) {}

  private List<String> getStringList(SettingKey settingKey) {
    String raw = rawValue(settingKey);
    if (raw == null) {
      return settingKey.parseStringList(settingKey.getDefaultValue());
    }
    try {
      return settingKey.parseStringList(raw);
    } catch (IllegalArgumentException ex) {
      return fallbackStringList(settingKey, ex);
    }
  }

  private int getInt(SettingKey settingKey) {
    String raw = rawValue(settingKey);
    if (raw == null) {
      return settingKey.parseInt(settingKey.getDefaultValue());
    }
    try {
      return settingKey.parseInt(raw);
    } catch (IllegalArgumentException ex) {
      return fallbackInt(settingKey, ex);
    }
  }

  private double getDecimal(SettingKey settingKey) {
    String raw = rawValue(settingKey);
    if (raw == null) {
      return settingKey.parseDecimal(settingKey.getDefaultValue());
    }
    try {
      return settingKey.parseDecimal(raw);
    } catch (IllegalArgumentException ex) {
      return fallbackDecimal(settingKey, ex);
    }
  }

  private boolean getBoolean(SettingKey settingKey) {
    String raw = rawValue(settingKey);
    if (raw == null) {
      return settingKey.parseBoolean(settingKey.getDefaultValue());
    }
    try {
      return settingKey.parseBoolean(raw);
    } catch (IllegalArgumentException ex) {
      return fallbackBoolean(settingKey, ex);
    }
  }

  private List<String> fallbackStringList(SettingKey settingKey, IllegalArgumentException ex) {
    warnInvalid(settingKey, ex);
    return settingKey.parseStringList(settingKey.getDefaultValue());
  }

  private int fallbackInt(SettingKey settingKey, IllegalArgumentException ex) {
    warnInvalid(settingKey, ex);
    return settingKey.parseInt(settingKey.getDefaultValue());
  }

  private double fallbackDecimal(SettingKey settingKey, IllegalArgumentException ex) {
    warnInvalid(settingKey, ex);
    return settingKey.parseDecimal(settingKey.getDefaultValue());
  }

  private boolean fallbackBoolean(SettingKey settingKey, IllegalArgumentException ex) {
    warnInvalid(settingKey, ex);
    return settingKey.parseBoolean(settingKey.getDefaultValue());
  }

  private void warnInvalid(SettingKey settingKey, IllegalArgumentException ex) {
    log.warn(
        "Invalid stored value for setting '{}': {} — falling back to default '{}'",
        settingKey.getKey(),
        ex.getMessage(),
        settingKey.getDefaultValue());
  }

  /**
   * The stored value, or {@code null} when the row is missing or blank (both mean "use default").
   */
  private String rawValue(SettingKey settingKey) {
    return platformSettingRepository
        .findByKey(settingKey.getKey())
        .map(PlatformSetting::getValue)
        .filter(value -> value != null && !value.isBlank())
        .orElse(null);
  }

  private static long megabytesToBytes(int megabytes) {
    return megabytes * 1024L * 1024L;
  }
}
