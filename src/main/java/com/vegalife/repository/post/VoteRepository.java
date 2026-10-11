package com.vegalife.repository.post;

import com.vegalife.model.post.Vote;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoteRepository extends JpaRepository<Vote, UUID> {

  Optional<Vote> findByUserIdAndPostId(UUID userId, UUID postId);

  long countByPostIdAndVoteType(UUID postId, Vote.VoteType voteType);
}
