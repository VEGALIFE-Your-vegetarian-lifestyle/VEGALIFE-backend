package com.vegalife.repository.post;

import com.vegalife.model.post.Comment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository
    extends JpaRepository<Comment, UUID>, JpaSpecificationExecutor<Comment> {

  Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);

  Optional<Comment> findByIdAndPostIdAndDeletedAtIsNull(UUID id, UUID postId);

  Optional<Comment> findByIdAndPostIdAndUserIdAndDeletedAtIsNull(UUID id, UUID postId, UUID userId);

  List<Comment> findByPostIdOrderByCreatedAtAsc(UUID postId);

  /** Admin dashboard: total non-deleted comments. */
  long countByDeletedAtIsNull();

  /** Admin dashboard: reported comments awaiting moderation. */
  long countByReportCountGreaterThanAndDeletedAtIsNull(int reportCount);

  /**
   * Admin dashboard: when the oldest reported comment was created, so the queue can report age
   * rather than only size. Empty when nothing is waiting.
   */
  @Query("select min(c.createdAt) from Comment c where c.reportCount > 0 and c.deletedAt is null")
  Optional<Instant> findOldestReportedCreatedAt();
}
