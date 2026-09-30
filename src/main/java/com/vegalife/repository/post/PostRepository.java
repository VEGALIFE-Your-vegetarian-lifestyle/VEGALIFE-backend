package com.vegalife.repository.post;

import com.vegalife.model.post.Post;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

  Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

  Optional<Post> findByIdAndUser_IdAndDeletedAtIsNull(UUID id, UUID userId);

  Page<Post> findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  Page<Post> findByUser_IdAndStatusAndDeletedAtIsNullOrderByPublishedAtDescCreatedAtDesc(
      UUID userId, Post.Status status, Pageable pageable);
}
