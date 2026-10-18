package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.filter.ActiveEmbeddingModel;
import com.vegalife.filter.ContentFilterOutboundAdapter;
import com.vegalife.filter.ContentFilterPayload;
import com.vegalife.filter.ContentFilterService;
import com.vegalife.filter.ContentFilterService.FilterResult;
import com.vegalife.filter.DuplicateDetector;
import com.vegalife.filter.FilterVerdict;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.post.Post;
import com.vegalife.repository.post.PostEmbeddingRepository;
import com.vegalife.repository.post.PostEmbeddingRepository.NearestMatch;
import com.vegalife.repository.post.PostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

/**
 * Verdict-matrix tests for the CONTENT_FILTER queue adapter: PASSED publishes unconditionally
 * (every queued message came from a publish request or a content change on a published/unpublished
 * post, BR-FILTER-005), REJECTED/NEEDS_REVIEW flag and move to {@code unpublished}, and the verdict
 * persists only in {@code post.flag} / {@code post.status}. Failures propagate so the queue
 * retries.
 *
 * <p>Duplicate detection (ADR-015) adds two obligations these tests pin: a flagged duplicate is
 * never published, and the candidate's vector is never stored when the post does not publish —
 * storing it would make an unpublished post a comparison target for everyone else.
 */
@ExtendWith(MockitoExtension.class)
class ContentFilterOutboundAdapterTest {

  private static final String TITLE = "Công thức nấu chay";
  private static final String CONTENT = "Nội dung bài viết chay lành mạnh cho cả gia đình.";
  private static final String MODEL = "gemini-embedding-001";
  private static final float[] EMBEDDING = {0.5f, 0.5f};

  @Mock private PostRepository postRepository;
  @Mock private ContentFilterService contentFilterService;
  @Mock private PostEmbeddingRepository postEmbeddingRepository;
  @Mock private DuplicateDetector duplicateDetector;
  @Mock private ActiveEmbeddingModel activeEmbeddingModel;

  private ContentFilterOutboundAdapter adapter;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    adapter =
        new ContentFilterOutboundAdapter(
            postRepository,
            contentFilterService,
            postEmbeddingRepository,
            duplicateDetector,
            activeEmbeddingModel,
            objectMapper);
  }

  @Test
  void passedPublishesDraftPost() throws Exception {
    Post post = passing(Post.Status.created, 0.91);

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    assertNotNull(post.getPublishedAt());
    verify(postRepository).save(post);
  }

  @Test
  void passedKeepsExistingPublishedAt() throws Exception {
    Post post = post(Post.Status.published);
    post.setPublishedAt(Instant.parse("2026-01-01T00:00:00Z"));
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.PASSED, 0.8, List.of()));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    assertEquals(Instant.parse("2026-01-01T00:00:00Z"), post.getPublishedAt());
  }

  @Test
  void passedOnUnpublishedPostRepublishesIt() throws Exception {
    Post post = passing(Post.Status.unpublished, 0.77);

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    assertNotNull(post.getPublishedAt());
  }

  @Test
  void rejectedMovesPublishedPostToUnpublished() throws Exception {
    Post post = post(Post.Status.published);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.REJECTED, 0.2, List.of("RELEVANCE_LOW: 0.200")));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.REJECTED, post.getFlag());
    assertEquals(Post.Status.unpublished, post.getStatus());
    verify(postRepository).save(post);
  }

  @Test
  void needsReviewMovesToUnpublished() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.NEEDS_REVIEW, 0.58, List.of("RELEVANCE_REVIEW: 0.580")));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.NEEDS_REVIEW, post.getFlag());
    assertEquals(Post.Status.unpublished, post.getStatus());
    verify(postRepository).save(post);
  }

  @Test
  void passingPostStoresItsVectorForFutureComparisons() throws Exception {
    Post post = passing(Post.Status.created, 0.9);

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    verify(postEmbeddingRepository).upsert(eq(post.getId()), eq(EMBEDDING), eq(MODEL));
  }

  @Test
  void nearDuplicateIsFlaggedAndNeverPublished() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.PASSED, 0.95, List.of()));
    UUID originalId = UUID.randomUUID();
    when(duplicateDetector.findDuplicate(any(), eq(post.getId())))
        .thenReturn(Optional.of(new NearestMatch(originalId, 0.93)));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // The topic verdict was PASSED; the duplicate verdict overrides it.
    assertEquals(Post.Flag.DUPLICATE, post.getFlag());
    assertEquals(Post.Status.unpublished, post.getStatus());
    assertEquals(null, post.getPublishedAt());
  }

  @Test
  void nearDuplicateDoesNotStoreItsVector() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.PASSED, 0.95, List.of()));
    when(duplicateDetector.findDuplicate(any(), eq(post.getId())))
        .thenReturn(Optional.of(new NearestMatch(UUID.randomUUID(), 0.93)));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // Only published posts are comparison targets, so an unpublished duplicate must not become
    // one — otherwise a flagged post could start flagging unrelated content later.
    verify(postEmbeddingRepository, never()).upsert(any(), any(), anyString());
  }

  @Test
  void rejectedPostDoesNotStoreItsVector() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.REJECTED, 0.1, List.of("RELEVANCE_LOW: 0.100")));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    verify(postEmbeddingRepository, never()).upsert(any(), any(), anyString());
  }

  @Test
  void duplicateCheckRunsBeforeTheVectorIsStored() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.PASSED, 0.9, List.of()));
    when(duplicateDetector.findDuplicate(any(), eq(post.getId()))).thenReturn(Optional.empty());
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // Ordering is the whole correctness argument here: storing first would make every post match
    // itself at similarity 1.0. inOrder proves the call sequence rather than assuming it.
    var inOrder = org.mockito.Mockito.inOrder(duplicateDetector, postEmbeddingRepository);
    inOrder.verify(duplicateDetector).findDuplicate(any(), eq(post.getId()));
    inOrder.verify(postEmbeddingRepository).upsert(any(), any(), anyString());
  }

  @Test
  void aFailedVectorStoreStillPublishesThePost() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.PASSED, 0.9, List.of()));
    when(duplicateDetector.findDuplicate(any(), eq(post.getId()))).thenReturn(Optional.empty());
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    // The realistic cause: the pgvector extension is missing on the target database, so the table
    // does not accept the insert. That is a deployment problem, not this post's problem.
    doThrow(new InvalidDataAccessResourceUsageException("relation does not exist"))
        .when(postEmbeddingRepository)
        .upsert(any(), any(), anyString());
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // Storing the vector powers future detection; it is not what publishes this post. Blocking the
    // publish would let one misconfigured database stop the whole platform's posts.
    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    verify(postRepository).save(post);
  }

  @Test
  void missingPostThrowsWithoutFiltering() throws Exception {
    UUID missing = UUID.randomUUID();
    when(postRepository.findById(missing)).thenReturn(Optional.empty());
    OutboundMessage message = message(new ContentFilterPayload(missing));

    assertThrows(IllegalStateException.class, () -> adapter.deliver(message));

    verify(contentFilterService, never()).filter(any());
    verify(postRepository, never()).save(any(Post.class));
  }

  @Test
  void filterFailurePropagatesWithoutSaving() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenThrow(new IllegalStateException("embedding unavailable"));
    OutboundMessage message = message(new ContentFilterPayload(post.getId()));

    assertThrows(IllegalStateException.class, () -> adapter.deliver(message));

    verify(postRepository, never()).save(any(Post.class));
  }

  @Test
  void blankPayloadThrows() {
    OutboundMessage message = OutboundMessage.builder().id(UUID.randomUUID()).payload("  ").build();

    assertThrows(IllegalStateException.class, () -> adapter.deliver(message));
  }

  @Test
  void payloadWithoutPostIdThrows() {
    OutboundMessage message = OutboundMessage.builder().id(UUID.randomUUID()).payload("{}").build();

    assertThrows(IllegalStateException.class, () -> adapter.deliver(message));
  }

  /** Sets up a post that passes the topic filter and is not a duplicate of anything. */
  private Post passing(Post.Status status, double score) throws JsonProcessingException {
    Post post = post(status);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.PASSED, score, List.of()));
    when(duplicateDetector.findDuplicate(any(), eq(post.getId()))).thenReturn(Optional.empty());
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
    return post;
  }

  private void stubFilter(FilterVerdict verdict) {
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenReturn(new FilterResult(verdict, EMBEDDING));
  }

  private OutboundMessage message(ContentFilterPayload payload) throws JsonProcessingException {
    return OutboundMessage.builder()
        .id(UUID.randomUUID())
        .payload(objectMapper.writeValueAsString(payload))
        .build();
  }

  private static Post post(Post.Status status) {
    return Post.builder()
        .id(UUID.randomUUID())
        .title(TITLE)
        .content(CONTENT)
        .status(status)
        .flag(Post.Flag.PENDING)
        .viewCount(0)
        .build();
  }
}
