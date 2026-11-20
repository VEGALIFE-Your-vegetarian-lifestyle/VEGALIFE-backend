package com.vegalife.unit.service.cache;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins which service methods are allowed to clear a dashboard cache.
 *
 * <p>The rule this guards is not "evict everywhere" — it is "evict where an admin acts on the
 * number". Adding an eviction to a hot member write such as a vote or a save would keep the
 * 26-query summary permanently cold, which is strictly worse than the TTL it replaces. That is an
 * easy mistake to make deliberately ("more correct is better"), so it is asserted rather than left
 * to review memory.
 *
 * <p>Reads source text rather than bytecode on purpose: the property under test is "this method
 * body does not mention the invalidator", which is exactly what a reviewer would check by eye.
 */
class DashboardEvictionCallSitesTest {

  /** Every method expected to call the invalidator, and which intent it must use. */
  private static final Map<String, List<String>> EXPECTED = expected();

  /** Methods that must never evict, each with the reason it is exempt. */
  private static final Map<String, String> FORBIDDEN =
      Map.of(
          "VoteService.java", "votes are the highest-volume write and totalVotes is not actionable",
          "SavedItemService.java", "saves are high-volume and are a redisplay-only metric",
          "AuthService.java", "signup and verification volume must not cold-start the summary");

  @Test
  void theExpectedCallSitesAllEvict() throws IOException {
    for (Map.Entry<String, List<String>> entry : EXPECTED.entrySet()) {
      String source = read(entry.getKey());
      for (String intent : entry.getValue()) {
        assertThatCode(() -> assertThat(source).contains("dashboardCache." + intent))
            .as("%s should call dashboardCache.%s()", entry.getKey(), intent)
            .doesNotThrowAnyException();
      }
    }
  }

  @Test
  void theDeliberateOmissionsDoNotEvict() throws IOException {
    for (Map.Entry<String, String> entry : FORBIDDEN.entrySet()) {
      String source = read(entry.getKey());
      // The field may be injected; what must not appear is a call through it.
      assertThatCode(() -> assertThat(source).doesNotContain("dashboardCache."))
          .as("%s must not evict: %s", entry.getKey(), entry.getValue())
          .doesNotThrowAnyException();
    }
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private static Map<String, List<String>> expected() {
    return Map.ofEntries(
        Map.entry("AdminPostService.java", List.of("all()")),
        Map.entry("AdminService.java", List.of("summary()")),
        Map.entry("AdminRecipeService.java", List.of("summary()")),
        Map.entry("PostService.java", List.of("summary()", "all()")),
        Map.entry("CommentService.java", List.of("summary()")),
        Map.entry("RecipeService.java", List.of("summary()")),
        Map.entry("PaymentWebhookService.java", List.of("summary()", "all()")),
        Map.entry("PlanClaimService.java", List.of("summary()")),
        Map.entry("AdminPlanService.java", List.of("charts()")),
        Map.entry("ContentFilterOutboundAdapter.java", List.of("summary()", "all()")),
        Map.entry("PendingFilterSweepJob.java", List.of("summary()")));
  }

  private static String read(String fileName) throws IOException {
    Path root = Path.of("src", "main", "java", "com", "vegalife");
    try (var paths = Files.walk(root)) {
      Path match =
          paths
              .filter(p -> p.getFileName().toString().equals(fileName))
              .findFirst()
              .orElseThrow(() -> new IllegalStateException("source not found: " + fileName));
      return Files.readString(match, StandardCharsets.UTF_8);
    }
  }

  // JUnit's assertThat on String, without pulling in AssertJ's static import ambiguity above.
  private static org.assertj.core.api.AbstractStringAssert<?> assertThat(String actual) {
    return org.assertj.core.api.Assertions.assertThat(actual);
  }
}
