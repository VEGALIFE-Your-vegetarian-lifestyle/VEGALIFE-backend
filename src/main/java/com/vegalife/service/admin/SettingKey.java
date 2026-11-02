package com.vegalife.service.admin;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of every admin-configurable platform setting (issue #13 follow-up): one source of truth
 * for a key's category, type, default value and bounds, so the {@code platform_setting} table never
 * drifts from what {@link PlatformSettingsProvider} and {@code AdminSettingsService} expect.
 * Defaults are byte-identical to the compiled {@code application.yml} values they replace, so a
 * deployment with no row overridden behaves exactly as it did before this setting existed.
 */
public enum SettingKey {
  UPLOAD_ALLOWED_IMAGE_TYPES(
      "media",
      "upload_allowed_image_types",
      Type.STRING_LIST,
      "image/jpeg,image/png,image/webp",
      null,
      null,
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif")),
  UPLOAD_ALLOWED_VIDEO_TYPES(
      "media",
      "upload_allowed_video_types",
      Type.STRING_LIST,
      "video/mp4,video/webm",
      null,
      null,
      Set.of("video/mp4", "video/webm", "video/quicktime")),
  MAX_IMAGE_UPLOAD_MB("media", "max_image_upload_mb", Type.INT, "5", 1d, 100d, null),
  MAX_VIDEO_UPLOAD_MB("media", "max_video_upload_mb", Type.INT, "50", 1d, 1024d, null),
  CONTENT_FILTER_ENABLED(
      "content", "content_filter_enabled", Type.BOOLEAN, "true", null, null, null),
  CONTENT_FILTER_ACCEPT_THRESHOLD(
      "content", "content_filter_accept_threshold", Type.DECIMAL, "0.70", 0d, 1d, null),
  CONTENT_FILTER_REJECT_THRESHOLD(
      "content", "content_filter_reject_threshold", Type.DECIMAL, "0.45", 0d, 1d, null),
  AI_CHAT_ENABLED("ai", "ai_chat_enabled", Type.BOOLEAN, "true", null, null, null);

  /** How a setting's raw string value is parsed and bounds-checked. */
  public enum Type {
    STRING_LIST,
    INT,
    DECIMAL,
    BOOLEAN
  }

  private final String category;
  private final String key;
  private final Type type;
  private final String defaultValue;
  private final Double min;
  private final Double max;
  private final Set<String> knownValues;

  SettingKey(
      String category,
      String key,
      Type type,
      String defaultValue,
      Double min,
      Double max,
      Set<String> knownValues) {
    this.category = category;
    this.key = key;
    this.type = type;
    this.defaultValue = defaultValue;
    this.min = min;
    this.max = max;
    this.knownValues = knownValues;
  }

  public String getCategory() {
    return category;
  }

  public String getKey() {
    return key;
  }

  public Type getType() {
    return type;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public static Optional<SettingKey> fromKey(String key) {
    return Arrays.stream(values()).filter(candidate -> candidate.key.equals(key)).findFirst();
  }

  /**
   * Validates {@code rawValue} against this key's type and bounds, throwing {@link
   * IllegalArgumentException} on failure. Used both to reject an invalid PATCH (write path, strict)
   * and — wrapped in a try/catch — to decide whether a stored value is safe to use (read path,
   * tolerant: callers fall back to {@link #getDefaultValue()} on failure instead of propagating).
   */
  public void validate(String rawValue) {
    switch (type) {
      case STRING_LIST -> parseStringList(rawValue);
      case INT -> parseInt(rawValue);
      case DECIMAL -> parseDecimal(rawValue);
      case BOOLEAN -> parseBoolean(rawValue);
      default -> throw new IllegalStateException("Unhandled setting type: " + type);
    }
  }

  public List<String> parseStringList(String rawValue) {
    List<String> items =
        rawValue == null
            ? List.of()
            : Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    if (items.isEmpty()) {
      throw new IllegalArgumentException(key + " must be a non-empty comma-separated list");
    }
    if (knownValues != null && !knownValues.containsAll(items)) {
      throw new IllegalArgumentException(key + " must only contain known types: " + knownValues);
    }
    return items;
  }

  public int parseInt(String rawValue) {
    int value;
    try {
      value = Integer.parseInt(rawValue.trim());
    } catch (NumberFormatException | NullPointerException ex) {
      throw new IllegalArgumentException(key + " must be an integer", ex);
    }
    checkBounds(value);
    return value;
  }

  public double parseDecimal(String rawValue) {
    double value;
    try {
      value = Double.parseDouble(rawValue.trim());
    } catch (NumberFormatException | NullPointerException ex) {
      throw new IllegalArgumentException(key + " must be a decimal number", ex);
    }
    checkBounds(value);
    return value;
  }

  public boolean parseBoolean(String rawValue) {
    if (rawValue == null) {
      throw new IllegalArgumentException(key + " must be 'true' or 'false'");
    }
    String trimmed = rawValue.trim();
    if ("true".equalsIgnoreCase(trimmed)) {
      return true;
    }
    if ("false".equalsIgnoreCase(trimmed)) {
      return false;
    }
    throw new IllegalArgumentException(key + " must be 'true' or 'false'");
  }

  private void checkBounds(double value) {
    if (min != null && value < min) {
      throw new IllegalArgumentException(key + " must be >= " + min);
    }
    if (max != null && value > max) {
      throw new IllegalArgumentException(key + " must be <= " + max);
    }
  }
}
