package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vegalife.filter.ContentFilterService;
import com.vegalife.filter.EmbeddingRelevanceScorer;
import com.vegalife.filter.FilterVerdict;
import com.vegalife.model.post.Post;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Unit tests for {@link ContentFilterService}: every run embeds its content (FR-005), the three
 * semantic verdict bands with their score and reason payloads, and the PASSED empty-reason
 * contract.
 */
class ContentFilterServiceTest {

  private static final String VALID_CONTENT =
      "Công thức nấu chay từ đậu hũ và rau củ thật ngon lành cho cả gia đình";

  private FakeEmbeddingModel embeddingModel;
  private ContentFilterService service;

  @BeforeEach
  void setUp() {
    embeddingModel = new FakeEmbeddingModel();
    service =
        new ContentFilterService(
            embeddingModel,
            new EmbeddingRelevanceScorer(new float[] {1f, 0f}, new float[] {0f, 1f}, 0.75, 0.45));
  }

  @Test
  void shortContentStillEmbedsAndIsScored() {
    embeddingModel.next = new float[] {0f, 1f};
    FilterVerdict verdict = service.filter("a");
    assertEquals(Post.Flag.REJECTED, verdict.flag());
    assertEquals(0.0, verdict.score(), 1e-9);
    assertEquals(List.of("RELEVANCE_LOW: 0.000"), verdict.reasons());
    assertEquals(1, embeddingModel.calls());
  }

  @Test
  void acceptBandReturnsPassedWithEmptyReasons() {
    embeddingModel.next = new float[] {1f, 0f};
    FilterVerdict verdict = service.filter(VALID_CONTENT);
    assertEquals(Post.Flag.PASSED, verdict.flag());
    assertEquals(1.0, verdict.score(), 1e-9);
    assertEquals(List.of(), verdict.reasons());
    assertEquals(1, embeddingModel.calls());
  }

  @Test
  void rejectBandReturnsRejectedWithLowReason() {
    embeddingModel.next = new float[] {0f, 1f};
    FilterVerdict verdict = service.filter(VALID_CONTENT);
    assertEquals(Post.Flag.REJECTED, verdict.flag());
    assertEquals(0.0, verdict.score(), 1e-9);
    assertEquals(List.of("RELEVANCE_LOW: 0.000"), verdict.reasons());
  }

  @Test
  void reviewBandReturnsNeedsReviewWithReason() {
    embeddingModel.next = new float[] {1f, 1f};
    FilterVerdict verdict = service.filter(VALID_CONTENT);
    assertEquals(Post.Flag.NEEDS_REVIEW, verdict.flag());
    assertEquals(0.5, verdict.score(), 1e-9);
    assertEquals(List.of("RELEVANCE_REVIEW: 0.500"), verdict.reasons());
  }

  /** Counts real embeddings; unsupported operations fail loudly if the pipeline misroutes. */
  private static final class FakeEmbeddingModel implements EmbeddingModel {

    private float[] next = {1f, 0f};
    private int calls;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
      throw new UnsupportedOperationException("call() is not used by the filter pipeline");
    }

    @Override
    public float[] embed(Document document) {
      throw new UnsupportedOperationException("embed(Document) is not used by the filter pipeline");
    }

    @Override
    public float[] embed(String content) {
      calls++;
      return next;
    }

    int calls() {
      return calls;
    }
  }
}
