package com.vegalife.repository.post;

import com.vegalife.enums.post.VoteType;
import com.vegalife.model.post.Vote;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoteRepository extends JpaRepository<Vote, UUID> {

  Optional<Vote> findByUserIdAndPostId(UUID userId, UUID postId);

  long countByPostIdAndVoteType(UUID postId, VoteType voteType);

  /** Admin dashboard: lifetime votes of one polarity, so the total carries sentiment. */
  long countByVoteType(VoteType voteType);
}
