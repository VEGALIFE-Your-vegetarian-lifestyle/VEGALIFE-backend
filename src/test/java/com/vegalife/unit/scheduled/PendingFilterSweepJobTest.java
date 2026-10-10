package com.vegalife.unit.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.filter.FilterProperties;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.post.Post;
import com.vegalife.repository.outbound.OutboundMessageRepository;
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

  @Mock private OutboundMessageRepository outboundMessageRepository;

  private FilterProperties properties;

  @BeforeEach
  void setUp() {
    properties = new FilterProperties();
  }

  @Test
  void sweepStalePendingPosts_movesPostWhoseLatestQueueRowIsStaleToNeedsReview() {
    Post stalePost =
        Post.builder()
            .id(UUID.randomUUID())
            .flag(Post.Flag.PENDING)
            .status(Post.Status.created)
            .build();
    when(outboundMessageRepository.findRecipientsOfChannelWithLatestBefore(
            eq(OutboundChannel.CONTENT_FILTER), any(Instant.class)))
        .thenReturn(List.of(stalePost.getId().toString()));
    when(postRepository.findAllById(anyCollection())).thenReturn(List.of(stalePost));
    PendingFilterSweepJob job = createJob(true);

    job.sweepStalePendingPosts();

    ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(outboundMessageRepository)
        .findRecipientsOfChannelWithLatestBefore(
            eq(OutboundChannel.CONTENT_FILTER), cutoffCaptor.capture());
    Instant expectedCutoff = Instant.now().minus(properties.getSweepMaxAge());
    assertThat(cutoffCaptor.getValue())
        .isBetween(expectedCutoff.minusSeconds(5), expectedCutoff.plusSeconds(5));
    assertThat(stalePost.getFlag()).isEqualTo(Post.Flag.NEEDS_REVIEW);
    assertThat(stalePost.getStatus()).isEqualTo(Post.Status.unpublished);
    verify(postRepository).save(stalePost);
  }

  @Test
  void sweepStalePendingPosts_whenRecipientStaleButPostNotPending_savesNothing() {
    UUID filteredId = UUID.randomUUID();
    UUID deletedId = UUID.randomUUID();
    Post alreadyFiltered =
        Post.builder().id(filteredId).flag(Post.Flag.PASSED).status(Post.Status.published).build();
    Post deleted =
        Post.builder()
            .id(deletedId)
            .flag(Post.Flag.PENDING)
            .status(Post.Status.created)
            .deletedAt(Instant.now())
            .build();
    when(outboundMessageRepository.findRecipientsOfChannelWithLatestBefore(
            eq(OutboundChannel.CONTENT_FILTER), any(Instant.class)))
        .thenReturn(List.of(filteredId.toString(), deletedId.toString()));
    when(postRepository.findAllById(anyCollection())).thenReturn(List.of(alreadyFiltered, deleted));
    PendingFilterSweepJob job = createJob(true);

    job.sweepStalePendingPosts();

    assertThat(alreadyFiltered.getFlag()).isEqualTo(Post.Flag.PASSED);
    assertThat(deleted.getFlag()).isEqualTo(Post.Flag.PENDING);
    verify(postRepository, never()).save(any(Post.class));
  }

  @Test
  void sweepStalePendingPosts_whenNoStaleRecipients_touchesNothing() {
    when(outboundMessageRepository.findRecipientsOfChannelWithLatestBefore(
            eq(OutboundChannel.CONTENT_FILTER), any(Instant.class)))
        .thenReturn(List.of());
    PendingFilterSweepJob job = createJob(true);

    job.sweepStalePendingPosts();

    verify(postRepository, never()).findAllById(anyCollection());
    verify(postRepository, never()).save(any(Post.class));
  }

  @Test
  void sweepStalePendingPosts_whenRecipientNotAUuid_skipsIt() {
    when(outboundMessageRepository.findRecipientsOfChannelWithLatestBefore(
            eq(OutboundChannel.CONTENT_FILTER), any(Instant.class)))
        .thenReturn(List.of("not-a-uuid"));
    PendingFilterSweepJob job = createJob(true);

    job.sweepStalePendingPosts();

    verify(postRepository, never()).findAllById(anyCollection());
    verify(postRepository, never()).save(any(Post.class));
  }

  @Test
  void sweepStalePendingPosts_whenSchedulingDisabled_touchesNothing() {
    PendingFilterSweepJob job = createJob(false);

    job.sweepStalePendingPosts();

    verifyNoInteractions(postRepository, outboundMessageRepository);
  }

  private PendingFilterSweepJob createJob(boolean schedulingEnabled) {
    return new PendingFilterSweepJob(
        postRepository, outboundMessageRepository, properties, schedulingEnabled);
  }
}
