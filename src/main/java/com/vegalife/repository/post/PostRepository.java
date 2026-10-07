package com.vegalife.repository.post;

import com.vegalife.model.post.Post;
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

  Optional<Post> findByIdAndStatusAndDeletedAtIsNull(UUID id, Post.Status status);

  Optional<Post> findByIdAndUser_IdAndDeletedAtIsNull(UUID id, UUID userId);

  Page<Post> findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  Page<Post> findByUser_IdAndStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
      UUID userId, Post.Status status, Pageable pageable);

  Page<Post> findByStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
      Post.Status status, Pageable pageable);

  /**
   * Non-deleted posts attached to any of the given media, paired with the media they belong to so a
   * page of admin videos resolves every association in one query.
   */
  @Query(
      "select new com.vegalife.repository.post.MediaPostRow(m.id, p)"
          + " from Post p join p.media m"
          + " where m.id in :mediaIds and p.deletedAt is null")
  List<MediaPostRow> findPostRowsByMediaIdsIn(@Param("mediaIds") Collection<UUID> mediaIds);
}
