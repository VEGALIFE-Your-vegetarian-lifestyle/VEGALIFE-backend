package com.vegalife.service.post;

import com.vegalife.dto.request.post.CommentCreateRequest;
import com.vegalife.dto.request.post.CommentListRequest;
import com.vegalife.dto.request.post.CommentUpdateRequest;
import com.vegalife.dto.response.post.CommentResponse;
import com.vegalife.dto.response.post.CommentThreadResponse;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.post.Comment;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ForbiddenException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

  private final CommentRepository commentRepository;
  private final PostRepository postRepository;
  private final ModerationLogRepository moderationLogRepository;
  private final UserRepository userRepository;
  private final UserProfileRepository userProfileRepository;

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

  /**
   * BR-COMMENT-001: reading comments is public. Top-level comments are paginated; each one carries
   * its full reply subtree unpaginated, since a reply thread for a single root comment is expected
   * to be small. A soft-deleted comment still appears (so its replies keep their place in the
   * thread) but is rendered as a "[deleted]" placeholder with no author identity.
   */
  @Transactional(readOnly = true)
  public PageResponse<CommentThreadResponse> getComments(UUID postId, CommentListRequest request) {
    postRepository
        .findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published)
        .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    List<Comment> all = commentRepository.findByPostIdOrderByCreatedAtAsc(postId);

    Map<UUID, List<Comment>> childrenByParentId = new HashMap<>();
    List<Comment> roots = new ArrayList<>();
    for (Comment comment : all) {
      if (comment.getParentId() == null) {
        roots.add(comment);
      } else {
        childrenByParentId
            .computeIfAbsent(comment.getParentId(), key -> new ArrayList<>())
            .add(comment);
      }
    }

    int page = request.getPage();
    int size = request.getSize();
    int totalElements = roots.size();
    int totalPages = totalElements == 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
    int fromIndex = Math.min(page * size, totalElements);
    int toIndex = Math.min(fromIndex + size, totalElements);
    List<Comment> pageRoots = roots.subList(fromIndex, toIndex);

    Set<UUID> authorIds = new HashSet<>();
    collectAuthorIds(pageRoots, childrenByParentId, authorIds);
    Map<UUID, String> usernames =
        userRepository.findAllById(authorIds).stream()
            .collect(Collectors.toMap(User::getId, User::getUsername));
    Map<UUID, String> avatarUrls =
        userProfileRepository.findByUserIdIn(authorIds).stream()
            .collect(
                Collectors.toMap(profile -> profile.getUser().getId(), UserProfile::getAvatarUrl));

    List<CommentThreadResponse> content =
        pageRoots.stream()
            .map(comment -> toThreadResponse(comment, childrenByParentId, usernames, avatarUrls))
            .toList();

    return PageResponse.<CommentThreadResponse>builder()
        .content(content)
        .page(page)
        .size(size)
        .totalElements(totalElements)
        .totalPages(totalPages)
        .first(page <= 0)
        .last(totalPages == 0 || page >= totalPages - 1)
        .build();
  }

  private void collectAuthorIds(
      List<Comment> comments, Map<UUID, List<Comment>> childrenByParentId, Set<UUID> acc) {
    for (Comment comment : comments) {
      if (comment.getDeletedAt() == null) {
        acc.add(comment.getUserId());
      }
      collectAuthorIds(
          childrenByParentId.getOrDefault(comment.getId(), List.of()), childrenByParentId, acc);
    }
  }

  private CommentThreadResponse toThreadResponse(
      Comment comment,
      Map<UUID, List<Comment>> childrenByParentId,
      Map<UUID, String> usernames,
      Map<UUID, String> avatarUrls) {
    boolean deleted = comment.getDeletedAt() != null;
    List<CommentThreadResponse> replies =
        childrenByParentId.getOrDefault(comment.getId(), List.of()).stream()
            .map(child -> toThreadResponse(child, childrenByParentId, usernames, avatarUrls))
            .toList();

    return CommentThreadResponse.builder()
        .id(comment.getId())
        .userId(deleted ? null : comment.getUserId())
        .username(deleted ? null : usernames.get(comment.getUserId()))
        .avatarUrl(deleted ? null : avatarUrls.get(comment.getUserId()))
        .content(deleted ? "[deleted]" : comment.getContent())
        .edited(comment.isEdited())
        .deleted(deleted)
        .createdAt(comment.getCreatedAt())
        .updatedAt(comment.getUpdatedAt())
        .replies(replies)
        .build();
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
