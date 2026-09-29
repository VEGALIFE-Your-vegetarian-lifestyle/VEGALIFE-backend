package com.vegalife.unit.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.filter.FilterProperties;
import com.vegalife.model.post.Post;
import com.vegalife.model.post.PostFilterLog;
import com.vegalife.repository.post.PostFilterLogRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.scheduled.PendingFilterSweepJob;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingFilterSweepJobTest {

  @Mock private PostRepository postRepository;

  @Mock private PostFilterLogRepository postFilterLogRepository;

  private FilterProperties properties;

  @BeforeEach
  void setUp() {
    properties = new FilterProperties();
  }

  @Test
  void sweepStalePendingPosts_flagsStalePendingPostAndWritesAuditRow() {
    Post stalePost =
        Post.builder()
            .id(UUID.randomUUID())
            .flag(Post.Flag.PENDING)
            .status(Post.Status.created)
            .filterQueuedAt(Instant.now().minusSeconds(25 * 3600))
            .build();
    when(postRepository.findByFlagAndFilterQueuedAtBeforeAndDeletedAtIsNull(
            any(Post.Flag.class), any(Instant.class)))
        .thenReturn(List.of(stalePost));
    PendingFilterSweepJob job =
        new PendingFilterSweepJob(postRepository, postFilterLogRepository, properties, true);

    job.sweepStalePendingPosts();

    ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(postRepository)
        .findByFlagAndFilterQueuedAtBeforeAndDeletedAtIsNull(
            eq(Post.Flag.PENDING), cutoffCaptor.capture());
    Instant expectedCutoff = Instant.now().minus(properties.getSweepMaxAge());
    assertThat(cutoffCaptor.getValue())
        .isBetween(expectedCutoff.minusSeconds(5), expectedCutoff.plusSeconds(5));
    assertThat(stalePost.getFlag()).isEqualTo(Post.Flag.NEEDS_REVIEW);
    assertThat(stalePost.getStatus()).isEqualTo(Post.Status.flagged);

    ArgumentCaptor<PostFilterLog> logCaptor = ArgumentCaptor.forClass(PostFilterLog.class);
    verify(postFilterLogRepository).save(logCaptor.capture());
    PostFilterLog auditRow = logCaptor.getValue();
    assertThat(auditRow.getPostId()).isEqualTo(stalePost.getId());
    assertThat(auditRow.getFlag()).isEqualTo(Post.Flag.NEEDS_REVIEW);
    assertThat(auditRow.getScore()).isNull();
    assertThat(auditRow.getReasons()).contains("stale-pending");
  }

  @Test
  void sweepStalePendingPosts_whenNoStalePosts_touchesNothing() {
    when(postRepository.findByFlagAndFilterQueuedAtBeforeAndDeletedAtIsNull(
            any(Post.Flag.class), any(Instant.class)))
        .thenReturn(List.of());
    PendingFilterSweepJob job =
        new PendingFilterSweepJob(postRepository, postFilterLogRepository, properties, true);

    job.sweepStalePendingPosts();

    verify(postRepository, never()).save(any(Post.class));
    verifyNoInteractions(postFilterLogRepository);
  }

  @Test
  void sweepStalePendingPosts_whenSchedulingDisabled_touchesNothing() {
    PendingFilterSweepJob job =
        new PendingFilterSweepJob(postRepository, postFilterLogRepository, properties, false);

    job.sweepStalePendingPosts();

    verifyNoInteractions(postRepository, postFilterLogRepository);
  }
}
