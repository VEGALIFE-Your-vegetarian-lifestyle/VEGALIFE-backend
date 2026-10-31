package com.vegalife.repository.post;

import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.model.post.Post;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID>, JpaSpecificationExecutor<Post> {

  Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

  /**
   * Single-post detail lookup: any status is returned, only soft-deleted rows are excluded
   * (BR-POST-011). The status-scoped variant is retained for callers that gate on publication
   * (comment creation).
   */
  @Query("select p from Post p where p.id = :id and p.deletedAt is null")
  Optional<Post> findDetailById(@Param("id") UUID id);

  Optional<Post> findByIdAndStatusAndDeletedAtIsNull(UUID id, PostStatus status);

  Optional<Post> findByIdAndUser_IdAndDeletedAtIsNull(UUID id, UUID userId);

  Page<Post> findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  Page<Post> findByUser_IdAndStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
      UUID userId, PostStatus status, Pageable pageable);

  Page<Post> findByStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
      PostStatus status, Pageable pageable);

  /**
   * Non-deleted posts attached to any of the given media, paired with the media they belong to so a
   * page of admin videos resolves every association in one query.
   */
  @Query(
      "select new com.vegalife.repository.post.MediaPostRow(m.id, p)"
          + " from Post p join p.media m"
          + " where m.id in :mediaIds and p.deletedAt is null")
  List<MediaPostRow> findPostRowsByMediaIdsIn(@Param("mediaIds") Collection<UUID> mediaIds);

  /** Admin dashboard (issue #12): total non-deleted posts. */
  long countByDeletedAtIsNull();

  /** Admin dashboard (issue #12): posts created within a time range. */
  long countByCreatedAtBetweenAndDeletedAtIsNull(Instant from, Instant to);

  /** Admin dashboard (issue #12): posts awaiting moderation review. */
  long countByFlagAndDeletedAtIsNull(PostFlag flag);

  /** Admin dashboard (issue #12): distinct authors who posted within a time range. */
  @Query(
      "select distinct p.user.id from Post p"
          + " where p.createdAt between :from and :to and p.deletedAt is null")
  List<UUID> findDistinctAuthorIdsInRange(@Param("from") Instant from, @Param("to") Instant to);
}
