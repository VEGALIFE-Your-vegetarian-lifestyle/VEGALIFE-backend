package com.vegalife.filter;

import java.util.List;

/**
 * Outcome of the static content rules: whether the content passed, plus the concrete violated
 * reasons when it did not (BR-FILTER-001..003).
 *
 * @param passed whether every static rule passed
 * @param reasons violated reason codes, empty when passed
 */
public record StaticRulesResult(boolean passed, List<String> reasons) {

  public StaticRulesResult {
    reasons = List.copyOf(reasons);
  }

  public static StaticRulesResult pass() {
    return new StaticRulesResult(true, List.of());
  }

  public static StaticRulesResult fail(List<String> reasons) {
    if (reasons.isEmpty()) {
      throw new IllegalArgumentException("A failed result requires at least one reason");
    }
    return new StaticRulesResult(false, reasons);
  }
}
