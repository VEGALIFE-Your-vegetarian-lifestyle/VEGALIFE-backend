package com.vegalife.scheduled;

import com.vegalife.filter.FilterProperties;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.post.Post;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.post.PostRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stale-pending sweep (FR-011, BR-FILTER-009): any post still {@code flag=PENDING} whose latest
 * CONTENT_FILTER queue row is older than {@code app.filter.sweep-max-age} moves to {@code
 * NEEDS_REVIEW} / {@code status=flagged} with an ERROR log, covering queue messages that were lost
 * or exhausted their retries. The queue row's creation time is the enqueue clock — the message and
 * the PENDING flag are written in one transaction (ADR-005) — and terminal rows outlive the sweep
 * window, so a stuck post is always observable. Fresh {@code PENDING} and NULL-flag posts are never
 * touched.
 */
@Component
@Slf4j
public class PendingFilterSweepJob {

  private final PostRepository postRepository;
  private final OutboundMessageRepository outboundMessageRepository;
  private final FilterProperties filterProperties;
  private final boolean schedulingEnabled;

  public PendingFilterSweepJob(
      PostRepository postRepository,
      OutboundMessageRepository outboundMessageRepository,
      FilterProperties filterProperties,
      @Value("${app.scheduling.enabled:true}") boolean schedulingEnabled) {
    this.postRepository = postRepository;
    this.outboundMessageRepository = outboundMessageRepository;
    this.filterProperties = filterProperties;
    this.schedulingEnabled = schedulingEnabled;
  }

  @Scheduled(cron = "0 20 * * * *") // Hourly — the staleness threshold itself is 24h
  @Transactional
  public void sweepStalePendingPosts() {
    if (!schedulingEnabled) {
      log.debug("Pending filter sweep skipped: app.scheduling.enabled=false");
      return;
    }
    Instant cutoff = Instant.now().minus(filterProperties.getSweepMaxAge());
    List<UUID> stalePostIds = toPostIds(cutoff);
    if (stalePostIds.isEmpty()) {
      return;
    }
    List<Post> stalePosts =
        postRepository.findAllById(stalePostIds).stream()
            .filter(post -> post.getFlag() == Post.Flag.PENDING)
            .filter(post -> post.getDeletedAt() == null)
            .toList();
    for (Post post : stalePosts) {
      post.setFlag(Post.Flag.NEEDS_REVIEW);
      post.setStatus(Post.Status.flagged);
      postRepository.save(post);
      log.error(
          "Post {} still pending while its content-filter queue row is older than {} — flagged"
              + " NEEDS_REVIEW for manual review",
          post.getId(),
          filterProperties.getSweepMaxAge());
    }
    if (!stalePosts.isEmpty()) {
      log.info(
          "Pending filter sweep flagged {} post(s) with queue rows older than {}",
          stalePosts.size(),
          cutoff);
    }
  }

  /** Recipients of CONTENT_FILTER whose latest message predates the cutoff, parsed as post ids. */
  private List<UUID> toPostIds(Instant cutoff) {
    List<UUID> ids = new ArrayList<>();
    for (String recipient :
        outboundMessageRepository.findRecipientsOfChannelWithLatestBefore(
            OutboundChannel.CONTENT_FILTER, cutoff)) {
      try {
        ids.add(UUID.fromString(recipient));
      } catch (IllegalArgumentException e) {
        log.warn("Skipping non-UUID CONTENT_FILTER recipient '{}'", recipient);
      }
    }
    return ids;
  }
}
