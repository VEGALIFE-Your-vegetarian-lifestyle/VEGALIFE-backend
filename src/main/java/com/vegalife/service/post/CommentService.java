package com.vegalife.service.post;

import com.vegalife.dto.request.post.CommentCreateRequest;
import com.vegalife.dto.request.post.CommentUpdateRequest;
import com.vegalife.dto.response.post.CommentResponse;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.post.Comment;
import com.vegalife.model.post.Post;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.shared.exception.ForbiddenException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

  private final CommentRepository commentRepository;
  private final PostRepository postRepository;
  private final ModerationLogRepository moderationLogRepository;

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

    return toResponse(saved);
  }

  /**
   * BR-COMMENT-002: only the comment's owner may edit it, with no time limit. A non-owner
   * (including an Administrator) is forbidden, not merely hidden, since the comment is already
   * known to the caller via its id.
   */
  @Transactional
  public CommentResponse updateComment(
      UUID userId, UUID postId, UUID commentId, CommentUpdateRequest request) {
    Comment comment =
        commentRepository
            .findByIdAndPostIdAndDeletedAtIsNull(commentId, postId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

    if (!comment.getUserId().equals(userId)) {
      throw new ForbiddenException("Forbidden");
    }

    comment.setContent(request.getContent().trim());
    comment.setEdited(true);
    Comment saved = commentRepository.saveAndFlush(comment);

    return toResponse(saved);
  }

  /**
   * BR-COMMENT-003: the owner may soft-delete their own comment; an Administrator may soft-delete
   * any comment and that override is recorded in the moderation log (BR-ADMIN-002). Deletion only
   * sets {@code deletedAt}; replies keep their {@code parentId}, so the thread shape survives.
   */
  @Transactional
  public void deleteComment(UUID actorId, boolean isAdmin, UUID postId, UUID commentId) {
    Comment comment =
        (isAdmin
                ? commentRepository.findByIdAndPostIdAndDeletedAtIsNull(commentId, postId)
                : commentRepository.findByIdAndPostIdAndUserIdAndDeletedAtIsNull(
                    commentId, postId, actorId))
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

    comment.setDeletedAt(Instant.now());
    Comment saved = commentRepository.saveAndFlush(comment);

    if (isAdmin && !saved.getUserId().equals(actorId)) {
      moderationLogRepository.save(
          ModerationLog.builder()
              .actorId(actorId)
              .action("DELETE_COMMENT")
              .targetType("COMMENT")
              .targetId(saved.getId())
              .build());
    }
  }

  private CommentResponse toResponse(Comment comment) {
    return CommentResponse.builder()
        .id(comment.getId())
        .postId(comment.getPostId())
        .parentId(comment.getParentId())
        .userId(comment.getUserId())
        .content(comment.getContent())
        .edited(comment.isEdited())
        .createdAt(comment.getCreatedAt())
        .updatedAt(comment.getUpdatedAt())
        .build();
  }
}
