package com.vegalife.service.menu;

import com.vegalife.model.menu.MenuStatus;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A resolved menu query: an inclusive time window (either bound nullable for unbounded) plus an
 * optional status. Produced by {@link MenuQueryFilterResolver} from raw query parameters so the
 * service layer never parses request strings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuQueryFilter {

  private LocalDate from;
  private LocalDate to;
  private MenuStatus status;

  /** BR-MENU-002: a menu matches when its window intersects {@code [from, to]}. */
  public boolean hasWindow() {
    return from != null && to != null;
  }
}
