package com.vegalife.unit.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.response.post.VoteResponse;
import com.vegalife.model.post.Post;
import com.vegalife.model.post.Vote;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.VoteRepository;
import com.vegalife.service.post.VoteService;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

  @Mock private VoteRepository voteRepository;

  @Mock private PostRepository postRepository;

  @InjectMocks private VoteService voteService;

  private UUID userId;
  private UUID postId;
  private Post post;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    postId = UUID.randomUUID();
    post = Post.builder().id(postId).status(Post.Status.published).build();
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published))
        .thenReturn(Optional.of(post));
  }

  @Test
  void castVote_firstVoteIsInserted() {
    when(voteRepository.findByUserIdAndPostId(userId, postId)).thenReturn(Optional.empty());
    when(voteRepository.countByPostIdAndVoteType(postId, Vote.VoteType.UPVOTE)).thenReturn(1L);
    when(voteRepository.countByPostIdAndVoteType(postId, Vote.VoteType.DOWNVOTE)).thenReturn(0L);

    VoteResponse response = voteService.castVote(userId, postId, Vote.VoteType.UPVOTE);

    ArgumentCaptor<Vote> captor = ArgumentCaptor.forClass(Vote.class);
    verify(voteRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    assertThat(captor.getValue().getPostId()).isEqualTo(postId);
    assertThat(captor.getValue().getVoteType()).isEqualTo(Vote.VoteType.UPVOTE);
    assertThat(response.getUserVote()).isEqualTo(Vote.VoteType.UPVOTE);
    assertThat(response.getLikeCount()).isEqualTo(1L);
    assertThat(response.getDislikeCount()).isEqualTo(0L);
  }

  @Test
  void castVote_sameTypeAgainRemovesVote() {
    Vote existing =
        Vote.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .postId(postId)
            .voteType(Vote.VoteType.UPVOTE)
            .build();
    when(voteRepository.findByUserIdAndPostId(userId, postId)).thenReturn(Optional.of(existing));
    when(voteRepository.countByPostIdAndVoteType(postId, Vote.VoteType.UPVOTE)).thenReturn(0L);
    when(voteRepository.countByPostIdAndVoteType(postId, Vote.VoteType.DOWNVOTE)).thenReturn(0L);

    VoteResponse response = voteService.castVote(userId, postId, Vote.VoteType.UPVOTE);

    verify(voteRepository).delete(existing);
    verify(voteRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    assertThat(response.getUserVote()).isNull();
  }

  @Test
  void castVote_differentTypeSwitchesVote() {
    Vote existing =
        Vote.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .postId(postId)
            .voteType(Vote.VoteType.UPVOTE)
            .build();
    when(voteRepository.findByUserIdAndPostId(userId, postId)).thenReturn(Optional.of(existing));
    when(voteRepository.saveAndFlush(existing)).thenReturn(existing);
    when(voteRepository.countByPostIdAndVoteType(postId, Vote.VoteType.UPVOTE)).thenReturn(0L);
    when(voteRepository.countByPostIdAndVoteType(postId, Vote.VoteType.DOWNVOTE)).thenReturn(1L);

    VoteResponse response = voteService.castVote(userId, postId, Vote.VoteType.DOWNVOTE);

    assertThat(existing.getVoteType()).isEqualTo(Vote.VoteType.DOWNVOTE);
    verify(voteRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    assertThat(response.getUserVote()).isEqualTo(Vote.VoteType.DOWNVOTE);
    assertThat(response.getDislikeCount()).isEqualTo(1L);
  }

  @Test
  void castVote_missingOrUnpublishedPostReturnsNotFound() {
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> voteService.castVote(userId, postId, Vote.VoteType.UPVOTE))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }

  @Test
  void getVotes_anonymousViewerHasNullUserVote() {
    when(voteRepository.countByPostIdAndVoteType(postId, Vote.VoteType.UPVOTE)).thenReturn(3L);
    when(voteRepository.countByPostIdAndVoteType(postId, Vote.VoteType.DOWNVOTE)).thenReturn(2L);

    VoteResponse response = voteService.getVotes(postId, null);

    assertThat(response.getUserVote()).isNull();
    assertThat(response.getLikeCount()).isEqualTo(3L);
    assertThat(response.getDislikeCount()).isEqualTo(2L);
    verify(voteRepository, never())
        .findByUserIdAndPostId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void getVotes_identifiedViewerSeesOwnVote() {
    Vote existing =
        Vote.builder().userId(userId).postId(postId).voteType(Vote.VoteType.DOWNVOTE).build();
    when(voteRepository.findByUserIdAndPostId(userId, postId)).thenReturn(Optional.of(existing));
    when(voteRepository.countByPostIdAndVoteType(postId, Vote.VoteType.UPVOTE)).thenReturn(0L);
    when(voteRepository.countByPostIdAndVoteType(postId, Vote.VoteType.DOWNVOTE)).thenReturn(1L);

    VoteResponse response = voteService.getVotes(postId, userId);

    assertThat(response.getUserVote()).isEqualTo(Vote.VoteType.DOWNVOTE);
  }

  @Test
  void getVotes_missingOrUnpublishedPostReturnsNotFound() {
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> voteService.getVotes(postId, userId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }
}
