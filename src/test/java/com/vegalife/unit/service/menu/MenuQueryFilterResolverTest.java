package com.vegalife.unit.service.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vegalife.model.menu.MenuStatus;
import com.vegalife.service.menu.MenuQueryFilter;
import com.vegalife.service.menu.MenuQueryFilterResolver;
import com.vegalife.shared.exception.InvalidMenuFilterException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MenuQueryFilterResolverTest {

  private final MenuQueryFilterResolver resolver = new MenuQueryFilterResolver();

  @Test
  void noPeriodResolvesUnboundedFilter() {
    MenuQueryFilter filter = resolver.resolve(null, null, null, null, null, null);

    assertThat(filter.hasWindow()).isFalse();
    assertThat(filter.getStatus()).isNull();
  }

  @Test
  void weekResolvesToMondayThroughSundayOfTheGivenDate() {
    MenuQueryFilter filter = resolver.resolve("week", "2026-10-07", null, null, null, null);

    assertThat(filter.getFrom()).isEqualTo(LocalDate.of(2026, 10, 5));
    assertThat(filter.getTo()).isEqualTo(LocalDate.of(2026, 10, 11));
  }

  @Test
  void weekOnAWeekBoundaryStaysWithinThatWeek() {
    MenuQueryFilter monday = resolver.resolve("week", "2026-10-05", null, null, null, null);
    MenuQueryFilter sunday = resolver.resolve("week", "2026-10-11", null, null, null, null);

    assertThat(monday.getFrom()).isEqualTo(LocalDate.of(2026, 10, 5));
    assertThat(monday.getTo()).isEqualTo(LocalDate.of(2026, 10, 11));
    assertThat(sunday.getFrom()).isEqualTo(LocalDate.of(2026, 10, 5));
    assertThat(sunday.getTo()).isEqualTo(LocalDate.of(2026, 10, 11));
  }

  @Test
  void monthResolvesToFirstAndLastDay() {
    MenuQueryFilter filter = resolver.resolve("month", null, "2026-02", null, null, null);

    assertThat(filter.getFrom()).isEqualTo(LocalDate.of(2026, 2, 1));
    assertThat(filter.getTo()).isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  void customResolvesTheGivenInclusiveRange() {
    MenuQueryFilter filter =
        resolver.resolve("custom", null, null, "2026-09-01", "2026-10-15", null);

    assertThat(filter.getFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(filter.getTo()).isEqualTo(LocalDate.of(2026, 10, 15));
  }

  @Test
  void customAtThe366DayLimitIsAccepted() {
    MenuQueryFilter filter =
        resolver.resolve("custom", null, null, "2026-01-01", "2027-01-02", null);

    assertThat(filter.getFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(filter.getTo()).isEqualTo(LocalDate.of(2027, 1, 2));
  }

  @Test
  void statusIsParsedAndCombined() {
    MenuQueryFilter filter = resolver.resolve("week", "2026-10-07", null, null, null, "scheduled");

    assertThat(filter.getStatus()).isEqualTo(MenuStatus.scheduled);
  }

  @Test
  void customWithoutBoundsIsRejected() {
    assertThatThrownBy(() -> resolver.resolve("custom", null, null, "2026-09-01", null, null))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("from and to are required");
  }

  @Test
  void customWithFromAfterToIsRejected() {
    assertThatThrownBy(
            () -> resolver.resolve("custom", null, null, "2026-10-15", "2026-09-01", null))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("from must be on or before to");
  }

  @Test
  void customRangeLongerThan366DaysIsRejected() {
    assertThatThrownBy(
            () -> resolver.resolve("custom", null, null, "2026-01-01", "2027-01-03", null))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("must not exceed 366 days");
  }

  @Test
  void malformedDateIsRejected() {
    assertThatThrownBy(() -> resolver.resolve("week", "2026-13-01", null, null, null, null))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("Invalid date");
  }

  @Test
  void malformedMonthIsRejected() {
    assertThatThrownBy(() -> resolver.resolve("month", null, "2026-13", null, null, null))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("Invalid month");
  }

  @Test
  void unknownPeriodIsRejected() {
    assertThatThrownBy(() -> resolver.resolve("quarter", null, null, null, null, null))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("Unknown period");
  }

  @Test
  void unknownStatusIsRejected() {
    assertThatThrownBy(() -> resolver.resolve("week", "2026-10-07", null, null, null, "active"))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("Unknown status");
  }

  @Test
  void explicitBoundsWithoutCustomPeriodAreRejected() {
    assertThatThrownBy(() -> resolver.resolve(null, null, null, "2026-09-01", "2026-10-15", null))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("only valid with period=custom");
  }
}
