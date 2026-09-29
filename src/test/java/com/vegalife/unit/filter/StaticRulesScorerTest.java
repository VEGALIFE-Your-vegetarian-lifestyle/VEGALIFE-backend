package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.vegalife.filter.StaticRulesResult;
import com.vegalife.filter.StaticRulesScorer;
import java.text.Normalizer;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StaticRulesScorerTest {

  private static final String TOO_SHORT = StaticRulesScorer.REASON_TOO_SHORT;
  private static final String LINK_SPAM = StaticRulesScorer.REASON_LINK_SPAM;
  private static final String PROFANITY = StaticRulesScorer.REASON_PROFANITY + ": ";

  private final StaticRulesScorer scorer = new StaticRulesScorer();

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("lengthCases")
  void appliesLengthRule(String name, String content, List<String> expectedReasons) {
    assertVerdict(content, expectedReasons);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("linkSpamCases")
  void appliesLinkSpamRule(String name, String content, List<String> expectedReasons) {
    assertVerdict(content, expectedReasons);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("profanityCases")
  void appliesProfanityRule(String name, String content, List<String> expectedReasons) {
    assertVerdict(content, expectedReasons);
  }

  @Test
  void collectsReasonsInRuleOrder() {
    assertVerdict("https://a.com fuck everything now", List.of(LINK_SPAM, PROFANITY + "fuck"));
  }

  @Test
  void reasonsAreImmutable() {
    StaticRulesResult result = scorer.evaluate(null);
    assertEquals(List.of(TOO_SHORT), result.reasons());
    assertThrows(UnsupportedOperationException.class, () -> result.reasons().add("x"));
  }

  @Test
  void failRequiresAtLeastOneReason() {
    assertThrows(IllegalArgumentException.class, () -> StaticRulesResult.fail(List.of()));
  }

  private void assertVerdict(String content, List<String> expectedReasons) {
    StaticRulesResult result = scorer.evaluate(content);
    assertEquals(expectedReasons, result.reasons());
    assertEquals(expectedReasons.isEmpty(), result.passed());
  }

  static Stream<Arguments> lengthCases() {
    return Stream.of(
        Arguments.of("null content", null, List.of(TOO_SHORT)),
        Arguments.of("empty content", "", List.of(TOO_SHORT)),
        Arguments.of("whitespace only", "   \n\t  ", List.of(TOO_SHORT)),
        Arguments.of("19 characters", "aaaaaaaaaaaaaaaaaaa", List.of(TOO_SHORT)),
        Arguments.of("19 non-whitespace with spaces", "1234567890 12345678", List.of(TOO_SHORT)),
        Arguments.of("20 characters", "aaaaaaaaaaaaaaaaaaaa", List.of()),
        Arguments.of("20 non-whitespace with spaces", "1234567890 1234567890", List.of()),
        Arguments.of("natural sentence", "Hôm nay mình nấu món chay thật ngon.", List.of()));
  }

  static Stream<Arguments> linkSpamCases() {
    return Stream.of(
        Arguments.of(
            "two links pass",
            "Hôm nay mình chia sẻ hai công thức hay: https://a.com và https://b.com cho mọi người.",
            List.of()),
        Arguments.of(
            "three links reject",
            "Xem ngay https://a.com https://b.com https://c.com để biết thêm chi tiết bạn nhé.",
            List.of(LINK_SPAM)),
        Arguments.of(
            "one link in four tokens reject", "https://a.com yummy food vegan", List.of(LINK_SPAM)),
        Arguments.of(
            "one link in ten tokens pass",
            "https://a.com một hai ba bốn năm sáu bảy tám chín",
            List.of()),
        Arguments.of(
            "www prefix counts",
            "www.a.com www.b.com www.c.com xem ngay hôm nay mọi người",
            List.of(LINK_SPAM)),
        Arguments.of(
            "bare domain counts",
            "example.com example.org foo.vn xem ngay hôm nay mới",
            List.of(LINK_SPAM)),
        Arguments.of(
            "bare domain with path counts",
            "a.com/x b.com/y c.com/z xem ngay hôm nay mới nhất",
            List.of(LINK_SPAM)),
        Arguments.of(
            "dotted non-domain passes",
            "Nghiên cứu e.g. thị trường làm nông nghiệp hiện nay tại nhà",
            List.of()));
  }

  static Stream<Arguments> profanityCases() {
    String nfdContent =
        Normalizer.normalize(
            "Hôm nay nội dung này nói Địt gì vậy thật chán quá đi", Normalizer.Form.NFD);
    return Stream.of(
        Arguments.of(
            "vietnamese accented",
            "Hôm nay nội dung này nói Địt gì vậy thật chán quá đi",
            List.of(PROFANITY + "địt")),
        Arguments.of("vietnamese nfd form", nfdContent, List.of(PROFANITY + "địt")),
        Arguments.of(
            "vietnamese unaccented variant",
            "Hôm nay mọi người nói dit gì vậy hay là không biết luôn",
            List.of(PROFANITY + "dit")),
        Arguments.of(
            "english case variation",
            "This photo is FuCk and wrong for the community",
            List.of(PROFANITY + "fuck")),
        Arguments.of(
            "entry prefix match",
            "Your comment was fucking awesome, keep posting more recipes",
            List.of(PROFANITY + "fuck")),
        Arguments.of(
            "near misses do not match",
            "The beach ship asset is fine and lovely today for everyone",
            List.of()),
        Arguments.of(
            "short entry does not prefix match",
            "The asset manager will update the dashboard soon",
            List.of()),
        Arguments.of(
            "punctuation stripped from word",
            "Nội dung này thật sự fuck! ai cũng ghét luôn á",
            List.of(PROFANITY + "fuck")),
        Arguments.of(
            "ambiguous unaccented word not flagged",
            "Mình mua lon bia to nhất ở hà nội về nhậu",
            List.of()));
  }
}
