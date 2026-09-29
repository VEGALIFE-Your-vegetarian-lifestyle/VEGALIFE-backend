package com.vegalife.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic hard-reject gate for post content: minimum length (BR-FILTER-001), link-spam ratio
 * (BR-FILTER-002) and Vietnamese/English profanity wordlists (BR-FILTER-003). Runs before any
 * embedding call; a violation short-circuits the filter run.
 */
@Component
public class StaticRulesScorer {

  public static final String REASON_TOO_SHORT = "TOO_SHORT";
  public static final String REASON_LINK_SPAM = "LINK_SPAM";
  public static final String REASON_PROFANITY = "PROFANITY";

  public static final int MIN_CONTENT_CHARS = 20;
  public static final int MIN_LINK_COUNT = 3;
  public static final int LINK_RATIO_DENOMINATOR = 4;
  public static final int MIN_PROFANITY_PREFIX = 4;

  private static final String[] WORDLISTS = {
    "/filter/profanity-vn.txt", "/filter/profanity-en.txt"
  };

  private static final Pattern WHITESPACE =
      Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

  private static final Pattern EDGE_PUNCTUATION =
      Pattern.compile("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$");

  private static final Pattern BARE_DOMAIN =
      Pattern.compile(
          "^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*"
              + "\\.(?:com|net|org|vn|edu|io|info|co|gov|biz|uk|de|fr|jp|cn|au|ca|in|ru|xyz)"
              + "(?:[/?#].*)?$");

  private final Set<String> profanityEntries;

  public StaticRulesScorer() {
    Set<String> entries = new LinkedHashSet<>();
    for (String path : WORDLISTS) {
      try (InputStream in = StaticRulesScorer.class.getResourceAsStream(path)) {
        if (in == null) {
          throw new IllegalStateException("Missing filter wordlist: " + path);
        }
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
          reader
              .lines()
              .map(String::trim)
              .filter(line -> !line.isEmpty() && !line.startsWith("#"))
              .map(line -> Normalizer.normalize(line, Normalizer.Form.NFC).toLowerCase(Locale.ROOT))
              .forEach(entries::add);
        }
      } catch (IOException | UncheckedIOException e) {
        throw new IllegalStateException("Unreadable filter wordlist: " + path, e);
      }
    }
    this.profanityEntries = entries;
  }

  public StaticRulesResult evaluate(String content) {
    String trimmed = content == null ? "" : content.trim();
    if (countNonWhitespace(trimmed) < MIN_CONTENT_CHARS) {
      return StaticRulesResult.fail(List.of(REASON_TOO_SHORT));
    }

    List<String> reasons = new ArrayList<>();
    String[] tokens = WHITESPACE.split(trimmed);
    long links = 0;
    for (String token : tokens) {
      if (isLink(token)) {
        links++;
      }
    }
    if (links >= MIN_LINK_COUNT
        || (links >= 1 && links * LINK_RATIO_DENOMINATOR >= tokens.length)) {
      reasons.add(REASON_LINK_SPAM);
    }
    findProfanity(trimmed).ifPresent(entry -> reasons.add(REASON_PROFANITY + ": " + entry));

    return reasons.isEmpty() ? StaticRulesResult.pass() : StaticRulesResult.fail(reasons);
  }

  private static long countNonWhitespace(String text) {
    long count = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
        count++;
      }
    }
    return count;
  }

  private static boolean isLink(String token) {
    String candidate = EDGE_PUNCTUATION.matcher(token).replaceAll("").toLowerCase(Locale.ROOT);
    if (candidate.startsWith("http://")
        || candidate.startsWith("https://")
        || candidate.startsWith("www.")) {
      return true;
    }
    return BARE_DOMAIN.matcher(candidate).matches();
  }

  private Optional<String> findProfanity(String content) {
    String normalized = Normalizer.normalize(content, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    for (String rawToken : WHITESPACE.split(normalized)) {
      String word = EDGE_PUNCTUATION.matcher(rawToken).replaceAll("");
      if (word.isEmpty()) {
        continue;
      }
      for (String entry : profanityEntries) {
        if (word.equals(entry)
            || (entry.length() >= MIN_PROFANITY_PREFIX && word.startsWith(entry))) {
          return Optional.of(entry);
        }
      }
    }
    return Optional.empty();
  }
}
