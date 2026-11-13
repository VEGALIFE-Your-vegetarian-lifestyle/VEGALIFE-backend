package com.vegalife.unit.scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.enums.outbound.OutboundChannel;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.model.post.Post;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.scheduled.PendingFilterSweepJob;
import com.vegalife.service.filter.FilterProperties;
import com.vegalife.service.filter.PostFlagDetail;
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
  void sweepStalePendingPosts_settlesInFlightPostAsFilterSkipped() {
    Post stalePost = Post.builder().id(UUID.randomUUID()).status(PostStatus.processing).build();
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
    // The filter never formed an opinion, so the post is FILTER_SKIPPED, not FILTER_FAILED, and it
    // stays where it was: processing, waiting for a human.
    assertThat(stalePost.getStatus()).isEqualTo(PostStatus.processing);
    assertThat(stalePost.getFlag()).isEqualTo(PostFlag.FILTER_SKIPPED);
    assertThat(stalePost.getFlagDetail())
        .isEqualTo(PostFlagDetail.skipped(PostFlagDetail.CAUSE_SWEPT));
    verify(postRepository).save(stalePost);
  }

  @Test
  void sweepStalePendingPosts_whenPostIsNotInFlight_savesNothing() {
    UUID publishedId = UUID.randomUUID();
    UUID heldId = UUID.randomUUID();
    UUID draftedId = UUID.randomUUID();
    UUID deletedId = UUID.randomUUID();
    Post alreadyPublished = Post.builder().id(publishedId).status(PostStatus.published).build();
    Post alreadyHeld =
        Post.builder()
            .id(heldId)
            .status(PostStatus.processing)
            .flag(PostFlag.FILTER_FAILED)
            .build();
    Post drafted = Post.builder().id(draftedId).status(PostStatus.drafted).build();
    Post deleted =
        Post.builder().id(deletedId).status(PostStatus.processing).deletedAt(Instant.now()).build();
    when(outboundMessageRepository.findRecipientsOfChannelWithLatestBefore(
            eq(OutboundChannel.CONTENT_FILTER), any(Instant.class)))
        .thenReturn(
            List.of(
                publishedId.toString(),
                heldId.toString(),
                draftedId.toString(),
                deletedId.toString()));
    when(postRepository.findAllById(anyCollection()))
        .thenReturn(List.of(alreadyPublished, alreadyHeld, drafted, deleted));
    PendingFilterSweepJob job = createJob(true);

    job.sweepStalePendingPosts();

    assertThat(alreadyPublished.getFlag()).isNull();
    assertThat(alreadyHeld.getFlag()).isEqualTo(PostFlag.FILTER_FAILED);
    assertThat(drafted.getFlag()).isNull();
    assertThat(deleted.getFlag()).isNull();
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
