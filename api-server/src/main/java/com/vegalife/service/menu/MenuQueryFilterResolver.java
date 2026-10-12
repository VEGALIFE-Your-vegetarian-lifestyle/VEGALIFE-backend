package com.vegalife.service.menu;

import com.vegalife.model.menu.MenuStatus;
import com.vegalife.shared.exception.InvalidMenuFilterException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Turns raw list-query parameters into a {@link MenuQueryFilter}, or fails with a 400. All parsing
 * and range validation happens here so the service layer works only with resolved dates and the
 * controller stays a thin mapping layer (NFR-MAINT-001).
 *
 * <p>BR-MENU-002: {@code from} and {@code to} are each independently optional inclusive bounds —
 * either may be supplied alone, and neither leaves that side unbounded. There is no period or month
 * selector: a caller wanting a week or a month states the dates, which keeps the surface flat and
 * makes the most natural request ({@code ?from=&to=}) the valid one.
 */
@Component
@Slf4j
public class MenuQueryFilterResolver {

  private static final long MAX_RANGE_DAYS = 366;

  public MenuQueryFilter resolve(String from, String to, String status) {
    LocalDate start = parseBound(from, "from");
    LocalDate end = parseBound(to, "to");
    validateRange(start, end);
    return MenuQueryFilter.builder().from(start).to(end).status(parseStatus(status)).build();
  }

  /** Parses one inclusive bound, treating null and blank alike as "not supplied". */
  private LocalDate parseBound(String value, String field) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value);
    } catch (DateTimeParseException e) {
      throw new InvalidMenuFilterException(
          "Invalid " + field + " '" + value + "'. Use YYYY-MM-DD.");
    }
  }

  /**
   * BR-MENU-002: only a fully-stated window can be checked for order and length — a one-sided
   * window is unbounded on the open side and therefore always well-formed.
   */
  private void validateRange(LocalDate start, LocalDate end) {
    if (start == null || end == null) {
      return;
    }
    if (start.isAfter(end)) {
      throw new InvalidMenuFilterException("from must be on or before to");
    }
    if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
      throw new InvalidMenuFilterException(
          "Date range must not exceed " + MAX_RANGE_DAYS + " days");
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
}
