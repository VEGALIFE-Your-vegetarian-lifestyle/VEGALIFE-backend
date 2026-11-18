package com.vegalife.unit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/**
 * The dev profile must load the gitignored {@code .env} into the Spring {@code Environment}.
 *
 * <p>Without this, every {@code ${VAR:default}} placeholder in {@code application*.yml} silently
 * falls back to its compiled default: {@code AI_CHAT_API_KEY} becomes the dummy key, and the
 * embedding, VNPay and Cloudinary credentials never reach their clients. The file is ignored
 * because a {@code .env} is in none of Spring's config-data tiers, and nothing in this project
 * bridges it.
 *
 * <p>Scoped to the dev profile on purpose. Tests must stay hermetic — a developer's repo-root
 * {@code .env} must never change a test outcome — and prod keeps its own fail-fast requireds.
 */
class DotenvConfigImportTest {

  /**
   * Reads the YAML as text rather than through a Spring context: the point is to pin the shipped
   * declaration, and booting a context would resolve it from the ambient machine instead.
   */
  @Test
  void devProfileImportsDotenvAsAPropertiesFile() throws IOException {
    String yaml = readResource("/application-dev.yml");

    assertThat(springConfigImportValue(yaml))
        .as(
            "application-dev.yml must load .env so ${VAR:default} placeholders see the configured"
                + " values; [.properties] is required because the filename has no suffix to infer")
        .isEqualTo("optional:file:./.env[.properties]");
  }

  /**
   * Proves the mechanism actually works, with a controlled file: an import in {@code
   * file:<path>/.env[.properties]} form puts the file's keys into the environment, and an OS-level
   * property source still outranks it so a deployed environment stays authoritative.
   */
  @Test
  void importedDotenvValueIsReadableAndYieldsToAnEnvironmentVariable(@TempDir Path tempDir)
      throws IOException {
    Path dotenv = tempDir.resolve(".env");
    Files.writeString(
        dotenv,
        """
        # comment lines and blank lines are skipped by the properties parser

        VEGALIFE_DOTENV_PROBE=from-dotenv
        VEGALIFE_DOTENV_SHADOWED=from-dotenv
        """,
        StandardCharsets.UTF_8);

    MockEnvironment environment = new MockEnvironment();
    environment.setProperty(
        "spring.config.import", "optional:file:" + dotenv.toAbsolutePath() + "[.properties]");
    // Stands in for a real OS variable (Render/CI inject these); config data must rank below it.
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "simulatedOsEnvironment", Map.of("VEGALIFE_DOTENV_SHADOWED", "from-os-env")));

    ConfigDataEnvironmentPostProcessor.applyTo(environment);

    assertThat(environment.getProperty("VEGALIFE_DOTENV_PROBE"))
        .as("the .env file must contribute properties")
        .isEqualTo("from-dotenv");
    assertThat(environment.getProperty("VEGALIFE_DOTENV_SHADOWED"))
        .as("an OS environment variable must outrank .env so deploys stay authoritative")
        .isEqualTo("from-os-env");
  }

  /**
   * Pins what an imported {@code .env} does <em>not</em> do, so the limitation is documented in
   * code rather than only in prose.
   *
   * <p>A {@code .env} entry only takes effect when something asks for that exact property name.
   * {@code SPRING_PROFILES_ACTIVE} is the sharp case: the profile is resolved <em>before</em>
   * config-data is processed, so a {@code .env} entry can never activate a profile — which is also
   * why importing {@code .env} from a profile-scoped file cannot bootstrap itself. Conversely a
   * relaxed-binding style name such as {@code LOGGING_LEVEL_ROOT} is not a property key at all;
   * only a real OS environment variable is translated that way.
   */
  @Test
  void dotenvCannotActivateAProfileOrFeedRelaxedBindingKeys(@TempDir Path tempDir)
      throws IOException {
    Path dotenv = tempDir.resolve(".env");
    Files.writeString(
        dotenv,
        """
        SPRING_PROFILES_ACTIVE=prod
        LOGGING_LEVEL_ROOT=TRACE
        VEGALIFE_DOTENV_CONTROL=from-dotenv
        """,
        StandardCharsets.UTF_8);

    MockEnvironment environment = new MockEnvironment();
    environment.setProperty(
        "spring.config.import", "optional:file:" + dotenv.toAbsolutePath() + "[.properties]");

    String profilesBefore = String.join(",", environment.getActiveProfiles());
    ConfigDataEnvironmentPostProcessor.applyTo(environment);

    assertThat(environment.getProperty("VEGALIFE_DOTENV_CONTROL"))
        .as("control: the file really was imported")
        .isEqualTo("from-dotenv");
    assertThat(environment.getProperty("SPRING_PROFILES_ACTIVE"))
        .as("the value lands as an ordinary property ...")
        .isEqualTo("prod");
    assertThat(String.join(",", environment.getActiveProfiles()))
        .as("... but it must NOT activate a profile: profiles resolve before config data is read")
        .isEqualTo(profilesBefore);
    assertThat(environment.getProperty("logging.level.root"))
        .as("relaxed binding is an environment-variable feature, not a properties-file one")
        .isNull();
  }

  private static String readResource(String path) throws IOException {
    try (InputStream in = DotenvConfigImportTest.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new AssertionError(path + " must be on the classpath");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /**
   * The value of {@code spring.config.import} nested under the top-level {@code spring:} block,
   * joined across a block-sequence form and stripped of quotes.
   */
  private static String springConfigImportValue(String yaml) {
    List<String> lines = yaml.lines().toList();
    int springIndent = -1;
    boolean inImport = false;
    int importIndent = -1;
    StringBuilder collected = new StringBuilder();

    for (String line : lines) {
      if (line.isBlank() || line.stripLeading().startsWith("#")) {
        continue;
      }
      int indent = indentOf(line);
      String trimmed = line.strip();

      if (springIndent < 0) {
        if (trimmed.equals("spring:")) {
          springIndent = indent;
        }
        continue;
      }

      if (inImport) {
        // Block-sequence items ("- value") belong to the import list.
        if (indent > importIndent && trimmed.startsWith("-")) {
          appendToken(collected, trimmed.substring(1));
          continue;
        }
        if (indent > importIndent) {
          // A long scalar wrapped onto the next line.
          appendToken(collected, trimmed);
          continue;
        }
        break;
      }

      if (indent <= springIndent) {
        break; // left the spring block
      }
      if (trimmed.equals("config:")) {
        continue;
      }
      if (trimmed.startsWith("import:")) {
        String inline = trimmed.substring("import:".length()).strip();
        if (!inline.isEmpty()) {
          appendToken(collected, inline);
          break;
        }
        inImport = true;
        importIndent = indent;
      }
    }
    return collected.toString();
  }

  private static void appendToken(StringBuilder target, String rawToken) {
    String token = rawToken.strip();
    if (token.length() >= 2
        && ((token.startsWith("\"") && token.endsWith("\""))
            || (token.startsWith("'") && token.endsWith("'")))) {
      token = token.substring(1, token.length() - 1);
    }
    if (target.length() > 0) {
      target.append(',');
    }
    target.append(token);
  }

  private static int indentOf(String line) {
    return line.length() - line.stripLeading().length();
  }
}
