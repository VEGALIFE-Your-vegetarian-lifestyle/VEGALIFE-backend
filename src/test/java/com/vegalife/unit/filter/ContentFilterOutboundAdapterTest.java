package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.filter.ContentFilterOutboundAdapter;
import com.vegalife.filter.ContentFilterPayload;
import com.vegalife.filter.ContentFilterService;
import com.vegalife.filter.FilterVerdict;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.post.Post;
import com.vegalife.model.post.PostFilterLog;
import com.vegalife.repository.post.PostFilterLogRepository;
import com.vegalife.repository.post.PostRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Verdict-matrix tests for the CONTENT_FILTER queue adapter: PASSED publishes only with publish
 * intent or an already-published post, REJECTED/NEEDS_REVIEW flag and move to {@code flagged}, and
 * every run writes exactly one audit row (FR-010). Failures propagate so the queue retries.
 */
@ExtendWith(MockitoExtension.class)
class ContentFilterOutboundAdapterTest {

  private static final String TITLE = "Công thức nấu chay";
  private static final String CONTENT = "Nội dung bài viết chay lành mạnh cho cả gia đình.";

  @Mock private PostRepository postRepository;
  @Mock private ContentFilterService contentFilterService;
  @Mock private PostFilterLogRepository postFilterLogRepository;

  private ContentFilterOutboundAdapter adapter;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    adapter =
        new ContentFilterOutboundAdapter(
            postRepository, contentFilterService, postFilterLogRepository, objectMapper);
  }

  @Test
  void passedWithPublishIntentPublishesPostAndWritesOneAuditRow() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenReturn(new FilterVerdict(Post.Flag.PASSED, 0.91, List.of()));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId(), true)));

    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    assertNotNull(post.getPublishedAt());
    ArgumentCaptor<PostFilterLog> captor = ArgumentCaptor.forClass(PostFilterLog.class);
    verify(postFilterLogRepository).save(captor.capture());
    PostFilterLog logRow = captor.getValue();
    assertEquals(post.getId(), logRow.getPostId());
    assertEquals(Post.Flag.PASSED, logRow.getFlag());
    assertEquals(0.91, logRow.getScore());
    assertTrue(logRow.getReasons().isEmpty());
  }

  @Test
  void passedStaysPublishedWhenAlreadyPublishedAndNoIntent() throws Exception {
    Post post = post(Post.Status.published);
    post.setPublishedAt(Instant.parse("2026-01-01T00:00:00Z"));
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenReturn(new FilterVerdict(Post.Flag.PASSED, 0.8, List.of()));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId(), false)));

    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    assertEquals(Instant.parse("2026-01-01T00:00:00Z"), post.getPublishedAt());
    verify(postFilterLogRepository).save(any(PostFilterLog.class));
  }

  @Test
  void passedWithoutIntentKeepsCurrentStatus() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenReturn(new FilterVerdict(Post.Flag.PASSED, 0.77, List.of()));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId(), false)));

    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.created, post.getStatus());
    assertNull(post.getPublishedAt());
  }

  @Test
  void rejectedMovesPublishedPostToFlaggedWithReasonsAndNullScore() throws Exception {
    Post post = post(Post.Status.published);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenReturn(
            new FilterVerdict(
                Post.Flag.REJECTED, null, List.of("PROFANITY_VN: chửi thề", "SPAM: link ratio")));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId(), true)));

    assertEquals(Post.Flag.REJECTED, post.getFlag());
    assertEquals(Post.Status.flagged, post.getStatus());
    ArgumentCaptor<PostFilterLog> captor = ArgumentCaptor.forClass(PostFilterLog.class);
    verify(postFilterLogRepository).save(captor.capture());
    PostFilterLog logRow = captor.getValue();
    assertEquals(Post.Flag.REJECTED, logRow.getFlag());
    assertNull(logRow.getScore());
    assertEquals("PROFANITY_VN: chửi thề, SPAM: link ratio", logRow.getReasons());
  }

  @Test
  void needsReviewMovesToFlaggedAndRecordsScore() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenReturn(
            new FilterVerdict(Post.Flag.NEEDS_REVIEW, 0.58, List.of("RELEVANCE_REVIEW: 0.580")));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId(), true)));

    assertEquals(Post.Flag.NEEDS_REVIEW, post.getFlag());
    assertEquals(Post.Status.flagged, post.getStatus());
    ArgumentCaptor<PostFilterLog> captor = ArgumentCaptor.forClass(PostFilterLog.class);
    verify(postFilterLogRepository).save(captor.capture());
    PostFilterLog logRow = captor.getValue();
    assertEquals(0.58, logRow.getScore());
    assertEquals("RELEVANCE_REVIEW: 0.580", logRow.getReasons());
  }

  @Test
  void missingPostThrowsAndWritesNoAuditRow() throws Exception {
    UUID missing = UUID.randomUUID();
    when(postRepository.findById(missing)).thenReturn(Optional.empty());
    OutboundMessage message = message(new ContentFilterPayload(missing, true));

    assertThrows(IllegalStateException.class, () -> adapter.deliver(message));

    verify(contentFilterService, never()).filter(any());
    verify(postFilterLogRepository, never()).save(any());
  }

  @Test
  void filterFailurePropagatesWithoutSaving() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenThrow(new IllegalStateException("embedding unavailable"));
    OutboundMessage message = message(new ContentFilterPayload(post.getId(), true));

    assertThrows(IllegalStateException.class, () -> adapter.deliver(message));

    verify(postRepository, never()).save(any(Post.class));
    verify(postFilterLogRepository, never()).save(any());
  }

  @Test
  void blankPayloadThrows() {
    OutboundMessage message = OutboundMessage.builder().id(UUID.randomUUID()).payload("  ").build();

    assertThrows(IllegalStateException.class, () -> adapter.deliver(message));
  }

  @Test
  void payloadWithoutPostIdThrows() {
    OutboundMessage message =
        OutboundMessage.builder()
            .id(UUID.randomUUID())
            .payload("{\"requestedPublish\":true}")
            .build();

    assertThrows(IllegalStateException.class, () -> adapter.deliver(message));
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
