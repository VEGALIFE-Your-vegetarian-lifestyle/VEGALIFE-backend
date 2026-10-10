package com.vegalife.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.post.Post;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.service.outbound.OutboundChannelAdapter;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CONTENT_FILTER channel adapter (ADR-005): loads the queued post, runs the filter pipeline (FR-005
 * embedding scoring, FR-006 bands) and applies the verdict. {@code PASSED} publishes the post —
 * every queued message was enqueued by an explicit publish request (BR-FILTER-005); {@code
 * REJECTED} / {@code NEEDS_REVIEW} flag the post, move it to {@code unpublished} and WARN (FR-009).
 * Any failure propagates so the queue retries; permanent failure is the existing FAILED@24h
 * handling of the outbound job. No {@code prev_status} tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentFilterOutboundAdapter implements OutboundChannelAdapter {

  private final PostRepository postRepository;
  private final ContentFilterService contentFilterService;
  private final ObjectMapper objectMapper;

  @Override
  public OutboundChannel channel() {
    return OutboundChannel.CONTENT_FILTER;
  }

  @Override
  @Transactional
  public void deliver(OutboundMessage message) {
    ContentFilterPayload payload = parse(message);
    Post post =
        postRepository
            .findById(payload.postId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Outbound message "
                            + message.getId()
                            + " references missing post "
                            + payload.postId()));
    FilterVerdict verdict = contentFilterService.filter(filterBody(post));
    apply(post, verdict);
    postRepository.save(post);
  }

  private static void apply(Post post, FilterVerdict verdict) {
    post.setFlag(verdict.flag());
    switch (verdict.flag()) {
      case PASSED -> {
        post.setStatus(Post.Status.published);
        if (post.getPublishedAt() == null) {
          post.setPublishedAt(Instant.now());
        }
        log.info("Content filter passed post {} (score {})", post.getId(), verdict.score());
      }
      case REJECTED -> {
        post.setStatus(Post.Status.unpublished);
        log.warn("Content filter rejected post {}: {}", post.getId(), verdict.reasons());
      }
      case NEEDS_REVIEW -> {
        post.setStatus(Post.Status.unpublished);
        log.warn(
            "Content filter needs review for post {} (score between bands: {})",
            post.getId(),
            verdict.score());
      }
      default -> throw new IllegalStateException("Unexpected filter verdict: " + verdict.flag());
    }
  }

  private ContentFilterPayload parse(OutboundMessage message) {
    String json = message.getPayload();
    if (json == null || json.isBlank()) {
      throw new IllegalStateException(
          "Outbound message " + message.getId() + " has no payload to deliver");
    }
    try {
      ContentFilterPayload payload = objectMapper.readValue(json, ContentFilterPayload.class);
      if (payload.postId() == null) {
        throw new IllegalStateException(
            "Outbound message " + message.getId() + " payload is missing a postId");
      }
      return payload;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Outbound message " + message.getId() + " has an unreadable payload", e);
    }
  }

  private static String filterBody(Post post) {
    return post.getTitle() + "\n" + post.getContent();
  }
}
