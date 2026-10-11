package com.vegalife.unit.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.post.CommentCreateRequest;
import com.vegalife.dto.request.post.CommentUpdateRequest;
import com.vegalife.dto.response.post.CommentResponse;
import com.vegalife.model.post.Comment;
import com.vegalife.model.post.Post;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.service.post.CommentService;
import com.vegalife.shared.exception.ForbiddenException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

  @Mock private CommentRepository commentRepository;

  @Mock private PostRepository postRepository;

  @Mock private ModerationLogRepository moderationLogRepository;

  @InjectMocks private CommentService commentService;

  private UUID userId;
  private UUID postId;
  private Post post;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    postId = UUID.randomUUID();
    post = Post.builder().id(postId).status(Post.Status.published).build();
  }

  @Test
  void createComment_savesTopLevelCommentAndReturnsThreadPosition() {
    Comment saved =
        Comment.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .postId(postId)
            .content("Nice post")
            .build();
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published))
        .thenReturn(Optional.of(post));
    when(commentRepository.saveAndFlush(any(Comment.class))).thenReturn(saved);

    CommentResponse response =
        commentService.createComment(
            userId, postId, CommentCreateRequest.builder().content("  Nice post  ").build());

    assertThat(response.getId()).isEqualTo(saved.getId());
    assertThat(response.getPostId()).isEqualTo(postId);
    assertThat(response.getUserId()).isEqualTo(userId);
    assertThat(response.getParentId()).isNull();
    verify(commentRepository).saveAndFlush(any(Comment.class));
  }

  @Test
  void createComment_savesReplyWithParentId() {
    UUID parentId = UUID.randomUUID();
    Comment saved =
        Comment.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .postId(postId)
            .parentId(parentId)
            .content("Reply")
            .build();
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published))
        .thenReturn(Optional.of(post));
    when(commentRepository.findByIdAndPostIdAndDeletedAtIsNull(parentId, postId))
        .thenReturn(Optional.of(Comment.builder().id(parentId).postId(postId).build()));
    when(commentRepository.saveAndFlush(any(Comment.class))).thenReturn(saved);

    CommentResponse response =
        commentService.createComment(
            userId,
            postId,
            CommentCreateRequest.builder().content("Reply").parentId(parentId).build());

    assertThat(response.getParentId()).isEqualTo(parentId);
  }

  @Test
  void createComment_rejectsParentFromAnotherPost() {
    UUID parentId = UUID.randomUUID();
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published))
        .thenReturn(Optional.of(post));
    when(commentRepository.findByIdAndPostIdAndDeletedAtIsNull(parentId, postId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                commentService.createComment(
                    userId,
                    postId,
                    CommentCreateRequest.builder().content("Reply").parentId(parentId).build()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Parent comment not found");
  }

  @Test
  void createComment_rejectsMissingOrUnpublishedPost() {
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                commentService.createComment(
                    userId, postId, CommentCreateRequest.builder().content("Hello").build()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }

  @Test
  void updateComment_ownerUpdatesContentAndMarksEdited() {
    UUID commentId = UUID.randomUUID();
    Comment existing =
        Comment.builder()
            .id(commentId)
            .userId(userId)
            .postId(postId)
            .content("Typo")
            .isEdited(false)
            .build();
    when(commentRepository.findByIdAndPostIdAndDeletedAtIsNull(commentId, postId))
        .thenReturn(Optional.of(existing));
    when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

    CommentResponse response =
        commentService.updateComment(
            userId, postId, commentId, CommentUpdateRequest.builder().content("Fixed").build());

    assertThat(response.getContent()).isEqualTo("Fixed");
    assertThat(response.isEdited()).isTrue();
  }

  @Test
  void updateComment_secondEditStaysMarkedEdited() {
    UUID commentId = UUID.randomUUID();
    Comment existing =
        Comment.builder()
            .id(commentId)
            .userId(userId)
            .postId(postId)
            .content("Already edited once")
            .isEdited(true)
            .build();
    when(commentRepository.findByIdAndPostIdAndDeletedAtIsNull(commentId, postId))
        .thenReturn(Optional.of(existing));
    when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

    CommentResponse response =
        commentService.updateComment(
            userId,
            postId,
            commentId,
            CommentUpdateRequest.builder().content("Edited again").build());

    assertThat(response.isEdited()).isTrue();
  }

  @Test
  void updateComment_nonOwnerIsForbidden() {
    UUID commentId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    Comment existing =
        Comment.builder().id(commentId).userId(ownerId).postId(postId).content("Mine").build();
    when(commentRepository.findByIdAndPostIdAndDeletedAtIsNull(commentId, postId))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                commentService.updateComment(
                    userId,
                    postId,
                    commentId,
                    CommentUpdateRequest.builder().content("Hijack").build()))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void updateComment_missingCommentReturnsNotFound() {
    UUID commentId = UUID.randomUUID();
    when(commentRepository.findByIdAndPostIdAndDeletedAtIsNull(commentId, postId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                commentService.updateComment(
                    userId,
                    postId,
                    commentId,
                    CommentUpdateRequest.builder().content("Hello").build()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Comment not found");
  }

  @Test
  void deleteComment_ownerSoftDeletesOwnCommentWithoutLogging() {
    UUID commentId = UUID.randomUUID();
    Comment existing =
        Comment.builder().id(commentId).userId(userId).postId(postId).content("Mine").build();
    when(commentRepository.findByIdAndPostIdAndUserIdAndDeletedAtIsNull(commentId, postId, userId))
        .thenReturn(Optional.of(existing));
    when(commentRepository.saveAndFlush(existing)).thenReturn(existing);

    commentService.deleteComment(userId, false, postId, commentId);

    assertThat(existing.getDeletedAt()).isNotNull();
    verify(moderationLogRepository, never()).save(any());
  }

  @Test
  void deleteComment_nonOwnerNonAdminReturnsNotFound() {
    UUID commentId = UUID.randomUUID();
    when(commentRepository.findByIdAndPostIdAndUserIdAndDeletedAtIsNull(commentId, postId, userId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.deleteComment(userId, false, postId, commentId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Comment not found");
  }

  @Test
  void deleteComment_adminDeletingAnotherUsersCommentIsLogged() {
    UUID commentId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Comment existing =
        Comment.builder().id(commentId).userId(ownerId).postId(postId).content("Theirs").build();
    when(commentRepository.findByIdAndPostIdAndDeletedAtIsNull(commentId, postId))
        .thenReturn(Optional.of(existing));
    when(commentRepository.saveAndFlush(existing)).thenReturn(existing);

    commentService.deleteComment(adminId, true, postId, commentId);

    assertThat(existing.getDeletedAt()).isNotNull();
    verify(moderationLogRepository)
        .save(
            argThat(
                log ->
                    adminId.equals(log.getActorId())
                        && commentId.equals(log.getTargetId())
                        && "DELETE_COMMENT".equals(log.getAction())
                        && "COMMENT".equals(log.getTargetType())));
  }

  @Test
  void deleteComment_adminDeletingOwnCommentIsNotLogged() {
    UUID commentId = UUID.randomUUID();
    Comment existing =
        Comment.builder().id(commentId).userId(userId).postId(postId).content("Mine").build();
    when(commentRepository.findByIdAndPostIdAndDeletedAtIsNull(commentId, postId))
        .thenReturn(Optional.of(existing));
    when(commentRepository.saveAndFlush(existing)).thenReturn(existing);

    commentService.deleteComment(userId, true, postId, commentId);

    verify(moderationLogRepository, never()).save(any());
  }

  @Test
  void deleteComment_adminMissingCommentReturnsNotFound() {
    UUID commentId = UUID.randomUUID();
    when(commentRepository.findByIdAndPostIdAndDeletedAtIsNull(commentId, postId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.deleteComment(userId, true, postId, commentId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Comment not found");
  }
}
