package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

/**
 * Verdict-matrix tests for the CONTENT_FILTER queue adapter: PASSED publishes unconditionally
 * (every queued message came from a publish request or a content change on a published/flagged
 * post, BR-FILTER-005), REJECTED/NEEDS_REVIEW flag and move to {@code flagged}, and the verdict
 * persists only in {@code post.flag} / {@code post.status}. Failures propagate so the queue
 * retries.
 */
@ExtendWith(MockitoExtension.class)
class ContentFilterOutboundAdapterTest {

  private static final String TITLE = "Công thức nấu chay";
  private static final String CONTENT = "Nội dung bài viết chay lành mạnh cho cả gia đình.";

  @Mock private PostRepository postRepository;
  @Mock private ContentFilterService contentFilterService;

  private ContentFilterOutboundAdapter adapter;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    adapter = new ContentFilterOutboundAdapter(postRepository, contentFilterService, objectMapper);
  }

  @Test
  void passedPublishesDraftPost() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenReturn(new FilterVerdict(Post.Flag.PASSED, 0.91, List.of()));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

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
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenReturn(new FilterVerdict(Post.Flag.PASSED, 0.8, List.of()));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    assertEquals(Instant.parse("2026-01-01T00:00:00Z"), post.getPublishedAt());
  }

  @Test
  void passedOnFlaggedPostRepublishesIt() throws Exception {
    Post post = post(Post.Status.flagged);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenReturn(new FilterVerdict(Post.Flag.PASSED, 0.77, List.of()));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    assertNotNull(post.getPublishedAt());
  }

  @Test
  void rejectedMovesPublishedPostToFlagged() throws Exception {
    Post post = post(Post.Status.published);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenReturn(new FilterVerdict(Post.Flag.REJECTED, 0.2, List.of("RELEVANCE_LOW: 0.200")));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.REJECTED, post.getFlag());
    assertEquals(Post.Status.flagged, post.getStatus());
    verify(postRepository).save(post);
  }

  @Test
  void needsReviewMovesToFlagged() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenReturn(
            new FilterVerdict(Post.Flag.NEEDS_REVIEW, 0.58, List.of("RELEVANCE_REVIEW: 0.580")));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.NEEDS_REVIEW, post.getFlag());
    assertEquals(Post.Status.flagged, post.getStatus());
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
