package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vegalife.filter.FilterProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;

/**
 * Pins the shipped filter defaults. {@code app.filter.enabled} decides whether a published post is
 * ever filtered, so flipping it is a behaviour change that must be deliberate and visible in review
 * rather than a side effect of an unrelated edit.
 *
 * <p>The YAML is read as text rather than through a Spring context: the value sits behind a {@code
 * ${FILTER_ENABLED:...}} placeholder, so booting a context would resolve it from the ambient
 * environment — the very thing being pinned here.
 */
class FilterDefaultsTest {

  @Test
  void filterIsEnabledByDefault() {
    assertTrue(
        new FilterProperties().isEnabled(),
        "app.filter.enabled must default to true: the semantic filter is the normal publish path");
  }

  @Test
  void applicationYmlEnablesTheFilterWithAFalseOptOut() throws IOException {
    String yml = readConfig();
    // A literal `true` would remove the operator's ability to switch the filter off without a
    // redeploy; an absent placeholder would silently pin it on with no opt-out at all.
    assertEquals(
        "${FILTER_ENABLED:true}",
        filterEnabledValue(yml),
        "app.filter.enabled must be FILTER_ENABLED with a true default");
  }

  @Test
  void applicationYmlIsParseable() throws IOException {
    // Reads the file through the same loader Spring uses at boot. A malformed comment or a stray
    // tab here fails every context-loading test in the suite (88 of them, when this was added),
    // which buries the one-line cause; this pins it to a single obvious failure instead.
    List<PropertySource<?>> sources =
        new YamlPropertySourceLoader().load("application", new ByteArrayResource(readBytes()));
    assertFalse(sources.isEmpty(), "application.yml must yield at least one property source");
  }

  private static byte[] readBytes() throws IOException {
    try (InputStream in = FilterDefaultsTest.class.getResourceAsStream("/application.yml")) {
      if (in == null) {
        throw new AssertionError("application.yml must be on the classpath");
      }
      return in.readAllBytes();
    }
  }

  private static String readConfig() throws IOException {
    return new String(readBytes(), StandardCharsets.UTF_8);
  }

  /** The value of the {@code enabled:} key nested under the top-level {@code filter:} block. */
  private static String filterEnabledValue(String yml) {
    List<String> lines = yml.lines().toList();
    int blockIndent = -1;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.isBlank() || line.stripLeading().startsWith("#")) {
        continue;
      }
      if (blockIndent < 0) {
        if (line.strip().equals("filter:")) {
          blockIndent = indentOf(line);
        }
        continue;
      }
      if (indentOf(line) <= blockIndent) {
        break; // left the filter block without finding the key
      }
      String trimmed = line.strip();
      if (trimmed.startsWith("enabled:")) {
        return trimmed.substring("enabled:".length()).strip();
      }
    }
    throw new AssertionError("No 'enabled:' key found under the top-level 'filter:' block");
  }

  private static int indentOf(String line) {
    return line.length() - line.stripLeading().length();
  }
}
