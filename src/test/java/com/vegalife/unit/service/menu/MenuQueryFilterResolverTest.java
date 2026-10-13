package com.vegalife.unit.service.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vegalife.model.menu.MenuStatus;
import com.vegalife.service.menu.MenuQueryFilter;
import com.vegalife.service.menu.MenuQueryFilterResolver;
import com.vegalife.shared.exception.InvalidMenuFilterException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.Test;

class MenuQueryFilterResolverTest {

  private final MenuQueryFilterResolver resolver = new MenuQueryFilterResolver();

  @Test
  void noBoundsResolvesUnboundedFilter() {
    MenuQueryFilter filter = resolver.resolve(null, null, null);

    assertThat(filter.hasWindow()).isFalse();
    assertThat(filter.getStatus()).isNull();
  }

  @Test
  void fromAloneBoundsOnlyFromBelow() {
    MenuQueryFilter filter = resolver.resolve("2026-09-01", null, null);

    assertThat(filter.getFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(filter.getTo()).isNull();
  }

  @Test
  void toAloneBoundsOnlyFromAbove() {
    MenuQueryFilter filter = resolver.resolve(null, "2026-10-15", null);

    assertThat(filter.getFrom()).isNull();
    assertThat(filter.getTo()).isEqualTo(LocalDate.of(2026, 10, 15));
  }

  @Test
  void bothBoundsResolveTheGivenInclusiveRange() {
    MenuQueryFilter filter = resolver.resolve("2026-09-01", "2026-10-15", null);

    assertThat(filter.getFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(filter.getTo()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(filter.hasWindow()).isTrue();
  }

  @Test
  void blankBoundsAreTreatedAsAbsent() {
    MenuQueryFilter filter = resolver.resolve("  ", "", null);

    assertThat(filter.hasWindow()).isFalse();
  }

  @Test
  void aSingleDayWindowIsAccepted() {
    MenuQueryFilter filter = resolver.resolve("2026-10-05", "2026-10-05", null);

    assertThat(filter.getFrom()).isEqualTo(LocalDate.of(2026, 10, 5));
    assertThat(filter.getTo()).isEqualTo(LocalDate.of(2026, 10, 5));
  }

  @Test
  void rangeAtThe366DayLimitIsAccepted() {
    MenuQueryFilter filter = resolver.resolve("2026-01-01", "2027-01-02", null);

    assertThat(filter.getFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(filter.getTo()).isEqualTo(LocalDate.of(2027, 1, 2));
  }

  @Test
  void statusIsParsedAndCombined() {
    MenuQueryFilter filter = resolver.resolve("2026-10-05", "2026-10-11", "scheduled");

    assertThat(filter.getStatus()).isEqualTo(MenuStatus.scheduled);
  }

  @Test
  void fromAfterToIsRejected() {
    assertThatThrownBy(() -> resolver.resolve("2026-10-15", "2026-09-01", null))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("from must be on or before to");
  }

  @Test
  void rangeLongerThan366DaysIsRejected() {
    assertThatThrownBy(() -> resolver.resolve("2026-01-01", "2027-01-03", null))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("must not exceed 366 days");
  }

  @Test
  void oneSidedWindowIsNeverLengthChecked() {
    // An open-ended window has no length, so only order and length of a stated range are checkable.
    MenuQueryFilter filter = resolver.resolve("2020-01-01", null, null);

    assertThat(filter.getFrom()).isEqualTo(LocalDate.of(2020, 1, 1));
    assertThat(filter.getTo()).isNull();
  }

  @Test
  void malformedFromIsRejected() {
    assertThatThrownBy(() -> resolver.resolve("2026-13-01", null, null))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("Invalid from");
  }

  @Test
  void malformedToIsRejected() {
    assertThatThrownBy(() -> resolver.resolve(null, "not-a-date", null))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("Invalid to");
  }

  @Test
  void unknownStatusIsRejected() {
    assertThatThrownBy(() -> resolver.resolve("2026-10-05", "2026-10-11", "active"))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("Unknown status");
  }

  @Test
  void mealPlanWithNoBoundsDefaultsToTheCurrentIsoWeek() {
    MenuQueryFilter filter = resolver.resolveMealPlan(null, null);

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    assertThat(filter.getFrom())
        .isEqualTo(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    assertThat(filter.getTo())
        .isEqualTo(today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
    assertThat(filter.hasWindow()).isTrue();
  }

  @Test
  void mealPlanWithBlankBoundsAlsoDefaultsToTheCurrentIsoWeek() {
    MenuQueryFilter filter = resolver.resolveMealPlan(" ", "");

    assertThat(filter.hasWindow()).isTrue();
  }

  @Test
  void mealPlanWithFromAloneLeavesTheUpperBoundOpen() {
    MenuQueryFilter filter = resolver.resolveMealPlan("2026-09-01", null);

    assertThat(filter.getFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(filter.getTo()).isNull();
  }

  @Test
  void mealPlanWithToAloneLeavesTheLowerBoundOpen() {
    MenuQueryFilter filter = resolver.resolveMealPlan(null, "2026-10-15");

    assertThat(filter.getFrom()).isNull();
    assertThat(filter.getTo()).isEqualTo(LocalDate.of(2026, 10, 15));
  }

  @Test
  void mealPlanKeepsTheListReadsValidation() {
    assertThatThrownBy(() -> resolver.resolveMealPlan("2026-10-15", "2026-09-01"))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("from must be on or before to");

    assertThatThrownBy(() -> resolver.resolveMealPlan("2026-01-01", "2027-01-03"))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("must not exceed 366 days");

    assertThatThrownBy(() -> resolver.resolveMealPlan("not-a-date", null))
        .isInstanceOf(InvalidMenuFilterException.class)
        .hasMessageContaining("Invalid from");
  }

  @Test
  void mealPlanCarriesNoStatusFilter() {
    // /api/menus/details has no status parameter: every menu carries its own status in the payload.
    MenuQueryFilter filter = resolver.resolveMealPlan("2026-10-05", "2026-10-11");

    assertThat(filter.getStatus()).isNull();
  }
}
