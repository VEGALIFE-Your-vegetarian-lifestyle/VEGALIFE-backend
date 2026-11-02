package com.vegalife.service.location;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Evaluates a {@code location.opening_hours} document against a point in time (issue #9).
 *
 * <p>Expected shape: an object keyed by lowercase English weekday name ({@code monday}..{@code
 * sunday}), each holding an array of {@code {"open": "HH:mm", "close": "HH:mm"}} ranges. A day with
 * no entry, an empty array, or a malformed range means closed that day. A range whose {@code close}
 * is not after its {@code open} (e.g. {@code 22:00}-{@code 02:00}) is treated as spanning midnight.
 */
public final class OpeningHoursEvaluator {

  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

  private OpeningHoursEvaluator() {}

  /** Missing or unparseable opening hours are never "open now" (issue #9 acceptance criteria). */
  public static boolean isOpenNow(
      String openingHoursJson, ObjectMapper objectMapper, LocalDateTime now) {
    if (openingHoursJson == null || openingHoursJson.isBlank()) {
      return false;
    }

    JsonNode root;
    try {
      root = objectMapper.readTree(openingHoursJson);
    } catch (JsonProcessingException e) {
      return false;
    }

    String day = now.getDayOfWeek().toString().toLowerCase(Locale.ROOT);
    JsonNode ranges = root.path(day);
    if (!ranges.isArray()) {
      return false;
    }

    LocalTime currentTime = now.toLocalTime();
    for (JsonNode range : ranges) {
      LocalTime open = parseTime(range.path("open"));
      LocalTime close = parseTime(range.path("close"));
      if (open != null && close != null && isWithin(currentTime, open, close)) {
        return true;
      }
    }
    return false;
  }

  private static LocalTime parseTime(JsonNode node) {
    if (!node.isTextual()) {
      return null;
    }
    try {
      return LocalTime.parse(node.asText(), TIME_FORMAT);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static boolean isWithin(LocalTime current, LocalTime open, LocalTime close) {
    if (open.isBefore(close)) {
      return !current.isBefore(open) && !current.isAfter(close);
    }
    // close <= open: the range spans midnight (e.g. 22:00-02:00).
    return !current.isBefore(open) || !current.isAfter(close);
  }
}
