package com.vegalife.service.admin;

import com.vegalife.model.recipe.ModerationStatus;
import com.vegalife.shared.exception.ValidationException;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Sort;

/**
 * Query-parameter parsing shared by the admin dish and ingredient listings.
 *
 * <p>Kept package-private and tiny on purpose: the two listings are otherwise identical, and
 * duplicating the status/sort validation would let one of them drift into accepting a typo'd
 * moderation state and silently returning an empty page.
 */
final class AdminComponentQuery {

  private AdminComponentQuery() {}

  /**
   * Parses a moderation status filter.
   *
   * @return the state, or {@code null} when the parameter is absent or blank (meaning "every
   *     state")
   * @throws ValidationException when the value is not a known state — an empty page would look like
   *     "no names need review", which is the one wrong answer a review queue must never give
   */
  static ModerationStatus parseStatus(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return ModerationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new ValidationException("moderationStatus must be PENDING, APPROVED, or REJECTED");
    }
  }

  /** Rejects a sort property that is not an actual column of the entity. */
  static Sort parseSort(String sort, Set<String> allowed, String message) {
    String[] parts = sort.split(",");
    if (parts.length == 0 || parts.length > 2) {
      throw new ValidationException("Sort must be in the form property,asc|desc");
    }
    String property = parts[0].trim();
    if (property.isBlank()) {
      throw new ValidationException("Sort property must not be blank");
    }
    if (!allowed.contains(property)) {
      throw new ValidationException(message);
    }
    Sort.Direction direction =
        parts.length == 2 && parts[1].trim().equalsIgnoreCase("desc")
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    return Sort.by(direction, property);
  }
}
