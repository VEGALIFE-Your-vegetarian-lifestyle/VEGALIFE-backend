package com.vegalife.repository.post;

import com.vegalife.model.post.Comment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository
    extends JpaRepository<Comment, UUID>, JpaSpecificationExecutor<Comment> {

  Optional<Comment> findByIdAndDeletedAtIsNull(UUID id);

  Optional<Comment> findByIdAndPostIdAndDeletedAtIsNull(UUID id, UUID postId);

  Optional<Comment> findByIdAndPostIdAndUserIdAndDeletedAtIsNull(UUID id, UUID postId, UUID userId);

  List<Comment> findByPostIdOrderByCreatedAtAsc(UUID postId);
}
