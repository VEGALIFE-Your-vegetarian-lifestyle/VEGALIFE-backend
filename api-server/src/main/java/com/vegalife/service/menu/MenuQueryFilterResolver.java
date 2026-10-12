package com.vegalife.service.menu;

import com.vegalife.model.menu.MenuStatus;
import com.vegalife.shared.exception.InvalidMenuFilterException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Turns raw list-query parameters into a {@link MenuQueryFilter}, or fails with a 400. All parsing
 * and window resolution happens here so the service layer works only with resolved dates and the
 * controller stays a thin mapping layer (NFR-MAINT-001).
 *
 * <p>BR-MENU-004: weeks are Monday–Sunday and months are calendar months, both resolved in server
 * (UTC) time; {@code date} defaults to today and {@code month} to the current month.
 */
@Component
@Slf4j
public class MenuQueryFilterResolver {

  private static final long MAX_RANGE_DAYS = 366;

  public MenuQueryFilter resolve(
      String period, String date, String month, String from, String to, String status) {
    MenuStatus parsedStatus = parseStatus(status);

    if (period == null || period.isBlank()) {
      rejectExplicitBounds(period, from, to);
      return MenuQueryFilter.builder().status(parsedStatus).build();
    }

    return switch (period.toLowerCase()) {
      case "week" -> resolveWeek(date, parsedStatus);
      case "month" -> resolveMonth(month, parsedStatus);
      case "custom" -> resolveCustom(from, to, parsedStatus);
      default ->
          throw new InvalidMenuFilterException(
              "Unknown period '" + period + "'. Use week, month or custom.");
    };
  }

  private MenuQueryFilter resolveWeek(String date, MenuStatus status) {
    LocalDate anchor = date == null || date.isBlank() ? today() : parseDate(date, "date");
    LocalDate monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate sunday = anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    return MenuQueryFilter.builder().from(monday).to(sunday).status(status).build();
  }

  private MenuQueryFilter resolveMonth(String month, MenuStatus status) {
    YearMonth target;
    if (month == null || month.isBlank()) {
      target = YearMonth.now(ZoneOffset.UTC);
    } else {
      try {
        target = YearMonth.parse(month);
      } catch (DateTimeParseException e) {
        throw new InvalidMenuFilterException("Invalid month '" + month + "'. Use YYYY-MM.");
      }
    }
    return MenuQueryFilter.builder()
        .from(target.atDay(1))
        .to(target.atEndOfMonth())
        .status(status)
        .build();
  }

  private MenuQueryFilter resolveCustom(String from, String to, MenuStatus status) {
    if (from == null || from.isBlank() || to == null || to.isBlank()) {
      throw new InvalidMenuFilterException("from and to are required when period=custom");
    }
    LocalDate start = parseDate(from, "from");
    LocalDate end = parseDate(to, "to");
    if (start.isAfter(end)) {
      throw new InvalidMenuFilterException("from must be on or before to");
    }
    if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
      throw new InvalidMenuFilterException(
          "Date range must not exceed " + MAX_RANGE_DAYS + " days");
    }
    return MenuQueryFilter.builder().from(start).to(end).status(status).build();
  }

  private void rejectExplicitBounds(String period, String from, String to) {
    if ((from != null && !from.isBlank()) || (to != null && !to.isBlank())) {
      throw new InvalidMenuFilterException("from and to are only valid with period=custom");
    }
  }

  private MenuStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return MenuStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
      throw new InvalidMenuFilterException(
          "Unknown status '" + status + "'. Use drafted, scheduled, cancelled or completed.");
    }
  }

  private LocalDate parseDate(String value, String field) {
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new InvalidMenuFilterException(
          "Invalid " + field + " '" + value + "'. Use YYYY-MM-DD.");
    }
  }

  private LocalDate today() {
    return LocalDate.now(ZoneOffset.UTC);
  }
}
