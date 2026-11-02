package com.vegalife.repository.post;

import com.vegalife.model.post.Comment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository
    extends JpaRepository<Comment, UUID>, JpaSpecificationExecutor<Comment> {

  Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);

  Optional<Comment> findByIdAndPostIdAndDeletedAtIsNull(UUID id, UUID postId);

  Optional<Comment> findByIdAndPostIdAndUserIdAndDeletedAtIsNull(UUID id, UUID postId, UUID userId);

  List<Comment> findByPostIdOrderByCreatedAtAsc(UUID postId);

  /** Admin dashboard (issue #12): total non-deleted comments. */
  long countByDeletedAtIsNull();

  /** Admin dashboard (issue #12): comments created within a time range. */
  long countByCreatedAtBetweenAndDeletedAtIsNull(Instant from, Instant to);

  /** Admin dashboard (issue #12): reported comments awaiting moderation. */
  long countByReportCountGreaterThanAndDeletedAtIsNull(int reportCount);

  /** Admin dashboard (issue #12): distinct commenters within a time range. */
  @Query(
      "select distinct c.userId from Comment c"
          + " where c.createdAt between :from and :to and c.deletedAt is null")
  List<UUID> findDistinctCommenterIdsInRange(@Param("from") Instant from, @Param("to") Instant to);
}
