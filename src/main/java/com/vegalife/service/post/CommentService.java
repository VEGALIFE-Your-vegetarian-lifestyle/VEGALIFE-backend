package com.vegalife.service.post;

import com.vegalife.dto.request.post.CommentCreateRequest;
import com.vegalife.dto.response.post.CommentResponse;
import com.vegalife.model.post.Comment;
import com.vegalife.model.post.Post;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

  private final CommentRepository commentRepository;
  private final PostRepository postRepository;

  @Transactional
  public CommentResponse createComment(UUID userId, UUID postId, CommentCreateRequest request) {
    Post post =
        postRepository
            .findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    UUID parentId = request.getParentId();
    if (parentId != null
        && commentRepository
            .findByIdAndPostIdAndDeletedAtIsNull(parentId, post.getId())
            .isEmpty()) {
      throw new ResourceNotFoundException("Parent comment not found");
    }

    Comment saved =
        commentRepository.saveAndFlush(
            Comment.builder()
                .userId(userId)
                .postId(post.getId())
                .parentId(parentId)
                .content(request.getContent().trim())
                .build());

    return CommentResponse.builder()
        .id(saved.getId())
        .postId(saved.getPostId())
        .parentId(saved.getParentId())
        .userId(saved.getUserId())
        .content(saved.getContent())
        .createdAt(saved.getCreatedAt())
        .updatedAt(saved.getUpdatedAt())
        .build();
  }
}
