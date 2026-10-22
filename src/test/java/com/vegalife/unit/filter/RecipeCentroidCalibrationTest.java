package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vegalife.filter.EmbeddingRelevanceScorer;
import com.vegalife.infrastructure.embedding.GeminiEmbeddingModel;
import com.vegalife.infrastructure.embedding.GeminiEmbeddingProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

/**
 * Measures the RECIPE-BODY centroid pair (ADR-016) and prints the score of every seed under a
 * candidate threshold, so the thresholds in {@code app.filter.recipe.*} are chosen from evidence
 * rather than copied from the content filter.
 *
 * <p>Opt-in: runs only when {@code RECIPE_CALIBRATION} is set, because it makes live embedding
 * calls against a paid provider. Example (Git Bash, key exported from {@code .env}):
 *
 * <pre>
 * RECIPE_CALIBRATION=true EMBED_API_KEY=... ./mvnw test -Dtest=RecipeCentroidCalibrationTest
 * </pre>
 *
 * <p>The numbers are provider-bound in exactly the way {@code
 * docs/feats/filter-calibration-gemini.md} describes: they describe one model at one dimensionality
 * and must be re-measured, not ported, if the embedding model changes.
 */
@EnabledIfEnvironmentVariable(named = "RECIPE_CALIBRATION", matches = ".+")
class RecipeCentroidCalibrationTest {

  private static final Path ON_TOPIC =
      Path.of("src/main/resources/filter/seed-recipe-on-topic.txt");
  private static final Path OFF_TOPIC =
      Path.of("src/main/resources/filter/seed-recipe-off-topic.txt");

  @Test
  void measureRecipeCentroidSeparation() {
    List<String> onSeeds = readSeeds(ON_TOPIC);
    List<String> offSeeds = readSeeds(OFF_TOPIC);
    assertFalse(onSeeds.isEmpty(), "recipe on-topic corpus must not be empty");
    assertFalse(offSeeds.isEmpty(), "recipe off-topic corpus must not be empty");

    GeminiEmbeddingProperties properties = new GeminiEmbeddingProperties();
    properties.setBaseUrl(nonBlankOr(System.getenv("EMBED_BASE_URL"), properties.getBaseUrl()));
    properties.setModel(nonBlankOr(System.getenv("EMBED_MODEL"), properties.getModel()));
    properties.setApiKey(System.getenv("EMBED_API_KEY"));
    GeminiEmbeddingModel model =
        new GeminiEmbeddingModel(
            RestClient.builder().baseUrl(properties.getBaseUrl()).build(), properties);

    List<float[]> onVectors = embed(model, onSeeds);
    List<float[]> offVectors = embed(model, offSeeds);
    assertEquals(onSeeds.size(), onVectors.size());
    assertEquals(offSeeds.size(), offVectors.size());

    float[] onCentroid = centroid(onVectors);
    float[] offCentroid = centroid(offVectors);
    float[] mean = mean(onVectors, offVectors);

    // Same convention as production: the scorer stores centroids already mean-centered.
    float[] centeredOn = subtract(onCentroid, mean);
    float[] centeredOff = subtract(offCentroid, mean);

    List<Sample> onSamples = score(onSeeds, onVectors, centeredOn, centeredOff, mean, "ON_TOPIC");
    List<Sample> offSamples =
        score(offSeeds, offVectors, centeredOn, centeredOff, mean, "OFF_TOPIC");

    onSamples.sort(java.util.Comparator.comparingDouble(Sample::score));
    offSamples.sort(java.util.Comparator.comparingDouble(Sample::score));

    System.out.println(
        "=== recipe centroid calibration (gemini, dims=" + onCentroid.length + ") ===");
    System.out.println("on-topic seeds:  " + onSamples.size());
    System.out.println("off-topic seeds: " + offSamples.size());

    double lowestOn = onSamples.get(0).score();
    double highestOff = offSamples.get(offSamples.size() - 1).score();
    System.out.printf(Locale.ROOT, "lowest  on-topic: %.6f%n", lowestOn);
    System.out.printf(Locale.ROOT, "highest off-topic: %.6f%n", highestOff);

    // The derivation rule used for the content filter: L is the weakest real sample of the class
    // that must pass, H is the strongest sample of the class that must fail, and the thresholds sit
    // in the gap between them.
    double separation = lowestOn - highestOff;
    System.out.printf(Locale.ROOT, "separation (L-H): %.16f%n", separation);

    System.out.println();
    System.out.println("--- on-topic, ascending ---");
    onSamples.forEach(s -> System.out.println(s.render()));
    System.out.println();
    System.out.println("--- off-topic, ascending ---");
    offSamples.forEach(s -> System.out.println(s.render()));

    System.out.println();
    System.out.println("--- candidate thresholds: false positives vs catches ---");
    double[] candidates = {0.40, 0.450, 0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85, 0.90, 0.95};
    for (double accept : candidates) {
      long caught = onSamples.stream().filter(s -> s.score() >= accept).count();
      long falsePositive = offSamples.stream().filter(s -> s.score() >= accept).count();
      System.out.printf(
          Locale.ROOT,
          "accept>=%.3f : on-topic passed %2d/%2d, off-topic wrongly passed %d/%d%n",
          accept,
          caught,
          onSamples.size(),
          falsePositive,
          offSamples.size());
    }

    assertTrue(
        separation > 0,
        "recipe corpora overlap: no threshold separates them without a false positive");
  }

  private record Sample(String label, String text, double score) {
    String render() {
      String shortText = text.length() > 58 ? text.substring(0, 58) + "..." : text;
      return String.format(Locale.ROOT, "%.6f  %-9s %s", score, label, shortText);
    }
  }

  private static List<Sample> score(
      List<String> seeds,
      List<float[]> vectors,
      float[] centeredOn,
      float[] centeredOff,
      float[] mean,
      String label) {
    List<Sample> samples = new ArrayList<>();
    for (int i = 0; i < seeds.size(); i++) {
      samples.add(
          new Sample(
              label,
              seeds.get(i),
              EmbeddingRelevanceScorer.relevanceScore(
                  vectors.get(i), centeredOn, centeredOff, mean)));
    }
    return samples;
  }

  private static float[] subtract(float[] vector, float[] reference) {
    float[] result = new float[vector.length];
    for (int i = 0; i < vector.length; i++) {
      result[i] = vector[i] - reference[i];
    }
    return result;
  }

  private static List<float[]> embed(GeminiEmbeddingModel model, List<String> seeds) {
    List<float[]> vectors = new ArrayList<>(seeds.size());
    for (String seed : seeds) {
      vectors.add(model.embed(seed));
    }
    return vectors;
  }

  private static float[] centroid(List<float[]> vectors) {
    float[] sum = new float[vectors.get(0).length];
    for (float[] vector : vectors) {
      for (int i = 0; i < sum.length; i++) {
        sum[i] += vector[i];
      }
    }
    return normalize(sum);
  }

  private static float[] mean(List<float[]> a, List<float[]> b) {
    float[] sum = new float[a.get(0).length];
    int count = a.size() + b.size();
    for (List<float[]> group : List.of(a, b)) {
      for (float[] vector : group) {
        for (int i = 0; i < sum.length; i++) {
          sum[i] += vector[i];
        }
      }
    }
    for (int i = 0; i < sum.length; i++) {
      sum[i] /= count;
    }
    return sum;
  }

  private static float[] normalize(float[] vector) {
    double norm = 0;
    for (float v : vector) {
      norm += (double) v * v;
    }
    float length = (float) Math.sqrt(norm);
    float[] out = new float[vector.length];
    for (int i = 0; i < vector.length; i++) {
      out[i] = vector[i] / length;
    }
    return out;
  }

  private static List<String> readSeeds(Path path) {
    try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
      return lines
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read seed corpus " + path, e);
    }
  }

  private static String nonBlankOr(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
