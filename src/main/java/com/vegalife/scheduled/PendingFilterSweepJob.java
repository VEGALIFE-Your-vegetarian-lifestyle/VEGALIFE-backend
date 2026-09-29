package com.vegalife.scheduled;

import com.vegalife.filter.FilterProperties;
import com.vegalife.model.post.Post;
import com.vegalife.model.post.PostFilterLog;
import com.vegalife.repository.post.PostFilterLogRepository;
import com.vegalife.repository.post.PostRepository;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stale-pending sweep (FR-011, BR-FILTER-009): any post still {@code flag=PENDING} after {@code
 * app.filter.sweep-max-age} moves to {@code NEEDS_REVIEW} / {@code status=flagged} with an ERROR
 * log and exactly one audit row (BR-FILTER-010), covering queue messages that were lost or
 * exhausted their retries. Fresh {@code PENDING} and NULL-flag posts are never touched.
 */
@Component
@Slf4j
public class PendingFilterSweepJob {

  private final PostRepository postRepository;
  private final PostFilterLogRepository postFilterLogRepository;
  private final FilterProperties filterProperties;
  private final boolean schedulingEnabled;

  public PendingFilterSweepJob(
      PostRepository postRepository,
      PostFilterLogRepository postFilterLogRepository,
      FilterProperties filterProperties,
      @Value("${app.scheduling.enabled:true}") boolean schedulingEnabled) {
    this.postRepository = postRepository;
    this.postFilterLogRepository = postFilterLogRepository;
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
    List<Post> stalePosts =
        postRepository.findByFlagAndFilterQueuedAtBeforeAndDeletedAtIsNull(
            Post.Flag.PENDING, cutoff);
    for (Post post : stalePosts) {
      post.setFlag(Post.Flag.NEEDS_REVIEW);
      post.setStatus(Post.Status.flagged);
      postRepository.save(post);
      postFilterLogRepository.save(
          PostFilterLog.builder()
              .postId(post.getId())
              .flag(Post.Flag.NEEDS_REVIEW)
              .reasons("stale-pending: queued " + post.getFilterQueuedAt())
              .build());
      log.error(
          "Post {} still pending since {} (older than {}) — flagged NEEDS_REVIEW for manual review",
          post.getId(),
          post.getFilterQueuedAt(),
          filterProperties.getSweepMaxAge());
    }
    if (!stalePosts.isEmpty()) {
      log.info(
          "Pending filter sweep flagged {} post(s) stuck pending before {}",
          stalePosts.size(),
          cutoff);
    }
  }
}
