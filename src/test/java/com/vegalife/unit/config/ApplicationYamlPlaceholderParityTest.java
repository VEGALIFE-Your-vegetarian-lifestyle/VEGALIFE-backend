package com.vegalife.unit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every {@code ${VAR:default}} placeholder must be declared in the base {@code application.yml},
 * not only in a profile-specific file.
 *
 * <p>A placeholder is what binds an environment variable to a property. When one exists only in
 * {@code application-dev.yml} and {@code application-prod.yml}, the property silently disappears on
 * any other profile — and when one exists only in {@code application-prod.yml}, a local run can set
 * the variable in the dotenv file and see nothing happen. Both were real: the VNPAY keys were
 * declared only in dev and prod, and MAIL_FROM only in prod, which is what let a local dotenv file
 * look authoritative while four of its keys did nothing.
 *
 * <p>Base declares; a profile may override the default but must not be the only place a key exists.
 * This reads the YAML as text rather than booting a context, so it pins the shipped declarations
 * instead of resolving them from whatever machine runs the test.
 */
class ApplicationYamlPlaceholderParityTest {

  /**
   * Matches the name inside a placeholder without requiring the closing brace, so a nested form
   * yields both names: {@code ${PORT:${SERVER_PORT:8080}}} gives {@code PORT} and {@code
   * SERVER_PORT}.
   */
  private static final Pattern PLACEHOLDER =
      Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\s*[:}]");

  private static final String BASE = "/application.yml";

  /** Profiles that are allowed to lean on the base file for their declarations. */
  private static final String[] PROFILES = {
    "/application-dev.yml",
    "/application-prod.yml",
    "/application-test.yml",
    "/application-integration.yml"
  };

  @Test
  void everyPlaceholderInAProfileIsAlsoDeclaredInTheBaseFile() throws IOException {
    Set<String> baseKeys = placeholdersIn(readResource(BASE));
    assertThat(baseKeys).as("%s must declare placeholders at all", BASE).isNotEmpty();

    Map<String, Set<String>> violations = new LinkedHashMap<>();
    for (String profile : PROFILES) {
      Set<String> orphans = new TreeSet<>(placeholdersIn(readResource(profile)));
      orphans.removeAll(baseKeys);
      if (!orphans.isEmpty()) {
        violations.put(profile, orphans);
      }
    }

    assertThat(violations)
        .as(
            "declare every env key in application.yml and let the profile override only its"
                + " default; left profile-only, the key binds on some profiles and silently"
                + " vanishes on the rest")
        .isEmpty();
  }

  /**
   * The base file must not delegate a placeholder to itself, which resolves as a circular reference
   * and aborts startup with no hint about which line caused it.
   */
  @Test
  void baseFileDoesNotDeclareASelfReferencingPlaceholder() throws IOException {
    Set<String> selfReferences = new TreeSet<>();
    for (String line : readResource(BASE).lines().toList()) {
      Matcher matcher = PLACEHOLDER.matcher(line);
      while (matcher.find()) {
        String key = matcher.group(1);
        String property = propertyNameOf(line);
        if (property != null && property.equalsIgnoreCase(key)) {
          selfReferences.add(property);
        }
      }
    }

    assertThat(selfReferences)
        .as("a key whose value resolves from itself is a circular placeholder reference")
        .isEmpty();
  }

  @Test
  void dotenvExampleOnlyDocumentsKeysTheApplicationActuallyReads() throws IOException {
    Set<String> known = new LinkedHashSet<>();
    known.addAll(placeholdersIn(readResource(BASE)));
    for (String profile : PROFILES) {
      known.addAll(placeholdersIn(readResource(profile)));
    }
    // Profiles are resolved before config data is read, so this one can never be
    // supplied by .env and is intentionally documented as a comment.
    known.add("SPRING_PROFILES_ACTIVE");
    // Render injects PORT itself; a .env entry would be overridden by the platform.
    known.add("PORT");

    Set<String> undocumented = new TreeSet<>();
    for (String key : dotenvExampleAssignments(readDotenvExample())) {
      if (!known.contains(key)) {
        undocumented.add(key);
      }
    }

    assertThat(undocumented)
        .as(".env.example lists a key nothing reads, so setting it would look effective")
        .isEmpty();
  }

  private static Set<String> placeholdersIn(String text) {
    Set<String> keys = new LinkedHashSet<>();
    Matcher matcher = PLACEHOLDER.matcher(text);
    while (matcher.find()) {
      keys.add(matcher.group(1));
    }
    return keys;
  }

  /** The {@code key:} side of a YAML mapping line, or {@code null} for a sequence item. */
  private static String propertyNameOf(String line) {
    String trimmed = line.strip();
    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
      return null;
    }
    int colon = trimmed.lastIndexOf(':');
    if (colon <= 0) {
      return null;
    }
    return trimmed.substring(0, colon).strip();
  }

  /** Assigned (not commented-out) KEY=value lines, which is what a reader would expect to work. */
  private static Set<String> dotenvExampleAssignments(String text) {
    Set<String> keys = new LinkedHashSet<>();
    for (String line : text.lines().toList()) {
      String trimmed = line.strip();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      int equals = trimmed.indexOf('=');
      if (equals > 0) {
        keys.add(trimmed.substring(0, equals).strip());
      }
    }
    return keys;
  }

  /**
   * {@code .env.example} is a committed repo-root template, not a packaged resource, so it is read
   * from the working directory Maven runs the test in.
   */
  private static String readDotenvExample() throws IOException {
    Path path = Path.of(".env.example");
    if (!Files.isRegularFile(path)) {
      throw new AssertionError(".env.example must exist at the repository root");
    }
    return Files.readString(path, StandardCharsets.UTF_8);
  }

  private static String readResource(String path) throws IOException {
    try (InputStream in = ApplicationYamlPlaceholderParityTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new AssertionError(path + " must be on the classpath");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
