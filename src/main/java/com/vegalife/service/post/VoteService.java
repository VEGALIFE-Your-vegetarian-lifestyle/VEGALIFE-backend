package com.vegalife.service.post;

import com.vegalife.dto.response.post.VoteResponse;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.post.VoteType;
import com.vegalife.model.post.Vote;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.VoteRepository;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoteService {

  private final VoteRepository voteRepository;
  private final PostRepository postRepository;

  /**
   * BR-VOTE-001: one vote per user per post. Casting the same vote type again removes it; casting
   * the other type switches it. Both outcomes return the post's current counts and the caller's
   * resulting vote (null once removed).
   */
  @Transactional
  public VoteResponse castVote(UUID userId, UUID postId, VoteType voteType) {
    requirePublishedPost(postId);

    Optional<Vote> existing = voteRepository.findByUserIdAndPostId(userId, postId);
    VoteType resultingVote;
    if (existing.isEmpty()) {
      voteRepository.saveAndFlush(
          Vote.builder().userId(userId).postId(postId).voteType(voteType).build());
      resultingVote = voteType;
    } else if (existing.get().getVoteType() == voteType) {
      voteRepository.delete(existing.get());
      resultingVote = null;
    } else {
      Vote vote = existing.get();
      vote.setVoteType(voteType);
      voteRepository.saveAndFlush(vote);
      resultingVote = voteType;
    }

    return buildResponse(postId, resultingVote);
  }

  @Transactional(readOnly = true)
  public VoteResponse getVotes(UUID postId, UUID viewerId) {
    requirePublishedPost(postId);

    VoteType userVote =
        viewerId == null
            ? null
            : voteRepository
                .findByUserIdAndPostId(viewerId, postId)
                .map(Vote::getVoteType)
                .orElse(null);

    return buildResponse(postId, userVote);
  }

  private void requirePublishedPost(UUID postId) {
    postRepository
        .findByIdAndStatusAndDeletedAtIsNull(postId, PostStatus.published)
        .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
  }

  private VoteResponse buildResponse(UUID postId, VoteType userVote) {
    long likeCount = voteRepository.countByPostIdAndVoteType(postId, VoteType.UPVOTE);
    long dislikeCount = voteRepository.countByPostIdAndVoteType(postId, VoteType.DOWNVOTE);
    return VoteResponse.builder()
        .postId(postId)
        .likeCount(likeCount)
        .dislikeCount(dislikeCount)
        .userVote(userVote)
        .build();
  }
}
