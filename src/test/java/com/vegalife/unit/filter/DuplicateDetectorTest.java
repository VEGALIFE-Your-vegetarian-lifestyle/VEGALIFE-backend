package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.filter.ActiveEmbeddingModel;
import com.vegalife.filter.DuplicateDetector;
import com.vegalife.filter.FilterProperties;
import com.vegalife.repository.post.PostEmbeddingRepository;
import com.vegalife.repository.post.PostEmbeddingRepository.NearestMatch;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The duplicate rule itself: which similarity counts as a duplicate, and when detection is off.
 *
 * <p>The search is a single SQL query and is covered by the integration test; what belongs here is
 * the threshold comparison, so the boundary is pinned without a database.
 */
@ExtendWith(MockitoExtension.class)
class DuplicateDetectorTest {

  private static final float[] EMBEDDING = {1f, 0f};
  private static final String MODEL = "gemini-embedding-001";

  @Mock private PostEmbeddingRepository postEmbeddingRepository;
  @Mock private ActiveEmbeddingModel activeEmbeddingModel;

  private FilterProperties filterProperties;
  private DuplicateDetector detector;
  private final UUID candidate = UUID.randomUUID();
  private final UUID original = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    filterProperties = new FilterProperties();
    detector =
        new DuplicateDetector(postEmbeddingRepository, activeEmbeddingModel, filterProperties);
  }

  @Test
  void similarityAboveThresholdIsADuplicate() {
    stubNearest(0.91);

    Optional<NearestMatch> match = detector.findDuplicate(EMBEDDING, candidate);

    assertEquals(Optional.of(new NearestMatch(original, 0.91)), match);
  }

  @Test
  void similarityExactlyAtThresholdIsADuplicate() {
    // The band is inclusive: a post exactly at the threshold is flagged rather than published,
    // which is the conservative direction for a rule that only ever sends content to a human.
    stubNearest(0.8);

    assertTrue(detector.findDuplicate(EMBEDDING, candidate).isPresent());
  }

  @Test
  void similarityBelowThresholdIsNotADuplicate() {
    stubNearest(0.79);

    assertTrue(detector.findDuplicate(EMBEDDING, candidate).isEmpty());
  }

  @Test
  void noPublishedPostMeansNoDuplicate() {
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    when(postEmbeddingRepository.findNearestPublished(any(), eq(MODEL), eq(candidate)))
        .thenReturn(Optional.empty());

    assertTrue(detector.findDuplicate(EMBEDDING, candidate).isEmpty());
  }

  @Test
  void disabledDetectionSkipsTheSearchEntirely() {
    filterProperties.setDuplicateEnabled(false);

    assertTrue(detector.findDuplicate(EMBEDDING, candidate).isEmpty());

    // Not merely returning empty: the query must not run, so switching the feature off restores
    // the pre-feature publish path rather than running a pointless query on every publish.
    verifyNoInteractions(postEmbeddingRepository);
  }

  @Test
  void thresholdIsConfigurable() {
    filterProperties.setDuplicateThreshold(0.95);
    stubNearest(0.9);

    assertTrue(detector.findDuplicate(EMBEDDING, candidate).isEmpty());
  }

  @Test
  void searchesUnderTheActiveModelOnly() {
    stubNearest(0.5);

    detector.findDuplicate(EMBEDDING, candidate);

    // Vectors from a different model are not comparable; the query must be scoped to the active
    // one or a provider change would compare vectors from two different spaces.
    org.mockito.Mockito.verify(postEmbeddingRepository)
        .findNearestPublished(EMBEDDING, MODEL, candidate);
  }

  private void stubNearest(double similarity) {
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    when(postEmbeddingRepository.findNearestPublished(any(), eq(MODEL), eq(candidate)))
        .thenReturn(Optional.of(new NearestMatch(original, similarity)));
  }
}
