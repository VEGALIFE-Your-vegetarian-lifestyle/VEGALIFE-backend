package com.vegalife.unit.service.location;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.service.location.OpeningHoursEvaluator;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OpeningHoursEvaluatorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  // Monday 2026-10-12.
  private static final LocalDateTime MONDAY_NOON = LocalDateTime.of(2026, 10, 12, 12, 0);

  @Test
  void isOpenNow_trueWhenCurrentTimeFallsInsideTodaysRange() {
    String json = "{\"monday\": [{\"open\": \"08:00\", \"close\": \"22:00\"}]}";

    assertThat(OpeningHoursEvaluator.isOpenNow(json, objectMapper, MONDAY_NOON)).isTrue();
  }

  @Test
  void isOpenNow_falseWhenBeforeOpenOrAfterClose() {
    String json = "{\"monday\": [{\"open\": \"13:00\", \"close\": \"22:00\"}]}";

    assertThat(OpeningHoursEvaluator.isOpenNow(json, objectMapper, MONDAY_NOON)).isFalse();
  }

  @Test
  void isOpenNow_trueAtExactOpenAndCloseBoundaries() {
    String json = "{\"monday\": [{\"open\": \"12:00\", \"close\": \"12:00\"}]}";

    assertThat(OpeningHoursEvaluator.isOpenNow(json, objectMapper, MONDAY_NOON)).isTrue();
  }

  @Test
  void isOpenNow_falseWhenTodayHasNoEntry() {
    String json = "{\"tuesday\": [{\"open\": \"08:00\", \"close\": \"22:00\"}]}";

    assertThat(OpeningHoursEvaluator.isOpenNow(json, objectMapper, MONDAY_NOON)).isFalse();
  }

  @Test
  void isOpenNow_falseWhenTodaysRangeIsEmpty() {
    String json = "{\"monday\": []}";

    assertThat(OpeningHoursEvaluator.isOpenNow(json, objectMapper, MONDAY_NOON)).isFalse();
  }

  @Test
  void isOpenNow_matchesSecondRangeWhenFirstDoesNotApply() {
    String json =
        "{\"monday\": ["
            + "{\"open\": \"06:00\", \"close\": \"09:00\"},"
            + "{\"open\": \"11:00\", \"close\": \"14:00\"}"
            + "]}";

    assertThat(OpeningHoursEvaluator.isOpenNow(json, objectMapper, MONDAY_NOON)).isTrue();
  }

  @Test
  void isOpenNow_handlesOvernightRangeSpanningMidnight() {
    String json = "{\"monday\": [{\"open\": \"22:00\", \"close\": \"02:00\"}]}";
    LocalDateTime mondayLateNight = LocalDateTime.of(2026, 10, 12, 23, 30);
    LocalDateTime mondayEarlyMorning = LocalDateTime.of(2026, 10, 12, 1, 0);
    LocalDateTime mondayAfternoon = LocalDateTime.of(2026, 10, 12, 15, 0);

    assertThat(OpeningHoursEvaluator.isOpenNow(json, objectMapper, mondayLateNight)).isTrue();
    assertThat(OpeningHoursEvaluator.isOpenNow(json, objectMapper, mondayEarlyMorning)).isTrue();
    assertThat(OpeningHoursEvaluator.isOpenNow(json, objectMapper, mondayAfternoon)).isFalse();
  }

  @Test
  void isOpenNow_falseWhenOpeningHoursIsNullOrBlank() {
    assertThat(OpeningHoursEvaluator.isOpenNow(null, objectMapper, MONDAY_NOON)).isFalse();
    assertThat(OpeningHoursEvaluator.isOpenNow("  ", objectMapper, MONDAY_NOON)).isFalse();
  }

  @Test
  void isOpenNow_falseWhenJsonIsMalformed() {
    assertThat(OpeningHoursEvaluator.isOpenNow("{not valid json", objectMapper, MONDAY_NOON))
        .isFalse();
  }

  @Test
  void isOpenNow_falseWhenRangeEntryHasNonTextualTime() {
    String json = "{\"monday\": [{\"open\": 8, \"close\": \"22:00\"}]}";

    assertThat(OpeningHoursEvaluator.isOpenNow(json, objectMapper, MONDAY_NOON)).isFalse();
  }
}
