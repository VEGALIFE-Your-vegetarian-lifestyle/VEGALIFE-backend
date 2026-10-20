package com.vegalife.unit.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.post.CommentCreateRequest;
import com.vegalife.dto.request.post.CommentListRequest;
import com.vegalife.dto.request.post.CommentUpdateRequest;
import com.vegalife.dto.response.post.CommentResponse;
import com.vegalife.dto.response.post.CommentThreadResponse;
import com.vegalife.model.post.Comment;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.post.CommentService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ForbiddenException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
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

  @Mock private UserRepository userRepository;

  @Mock private UserProfileRepository userProfileRepository;

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

  @Test
  void getComments_returnsNestedTreeWithUserInfo() {
    UUID rootId = UUID.randomUUID();
    UUID replyId = UUID.randomUUID();
    User author = User.builder().id(userId).username("veganchef").build();
    UserProfile profile =
        UserProfile.builder().user(author).avatarUrl("https://example.com/a.png").build();
    Comment root =
        Comment.builder().id(rootId).postId(postId).userId(userId).content("Root").build();
    Comment reply =
        Comment.builder()
            .id(replyId)
            .postId(postId)
            .userId(userId)
            .parentId(rootId)
            .content("Reply")
            .build();
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published))
        .thenReturn(Optional.of(post));
    when(commentRepository.findByPostIdOrderByCreatedAtAsc(postId))
        .thenReturn(List.of(root, reply));
    when(userRepository.findAllById(any())).thenReturn(List.of(author));
    when(userProfileRepository.findByUserIdIn(any())).thenReturn(List.of(profile));

    PageResponse<CommentThreadResponse> response =
        commentService.getComments(postId, CommentListRequest.builder().build());

    assertThat(response.getContent()).hasSize(1);
    CommentThreadResponse rootResponse = response.getContent().get(0);
    assertThat(rootResponse.getId()).isEqualTo(rootId);
    assertThat(rootResponse.getUsername()).isEqualTo("veganchef");
    assertThat(rootResponse.getAvatarUrl()).isEqualTo("https://example.com/a.png");
    assertThat(rootResponse.isDeleted()).isFalse();
    assertThat(rootResponse.getReplies()).hasSize(1);
    assertThat(rootResponse.getReplies().get(0).getId()).isEqualTo(replyId);
  }

  @Test
  void getComments_deletedCommentShowsPlaceholderButKeepsReplies() {
    UUID rootId = UUID.randomUUID();
    UUID replyId = UUID.randomUUID();
    Comment deletedRoot =
        Comment.builder()
            .id(rootId)
            .postId(postId)
            .userId(userId)
            .content("Original content")
            .deletedAt(Instant.now())
            .build();
    Comment reply =
        Comment.builder()
            .id(replyId)
            .postId(postId)
            .userId(userId)
            .parentId(rootId)
            .content("Still here")
            .build();
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published))
        .thenReturn(Optional.of(post));
    when(commentRepository.findByPostIdOrderByCreatedAtAsc(postId))
        .thenReturn(List.of(deletedRoot, reply));
    when(userRepository.findAllById(any())).thenReturn(List.of());
    when(userProfileRepository.findByUserIdIn(any())).thenReturn(List.of());

    PageResponse<CommentThreadResponse> response =
        commentService.getComments(postId, CommentListRequest.builder().build());

    CommentThreadResponse rootResponse = response.getContent().get(0);
    assertThat(rootResponse.isDeleted()).isTrue();
    assertThat(rootResponse.getContent()).isEqualTo("[deleted]");
    assertThat(rootResponse.getUserId()).isNull();
    assertThat(rootResponse.getUsername()).isNull();
    assertThat(rootResponse.getReplies()).hasSize(1);
    assertThat(rootResponse.getReplies().get(0).getContent()).isEqualTo("Still here");
  }

  @Test
  void getComments_paginatesRootCommentsOnly() {
    List<Comment> roots =
        IntStream.range(0, 3)
            .mapToObj(
                i ->
                    Comment.builder()
                        .id(UUID.randomUUID())
                        .postId(postId)
                        .userId(userId)
                        .content("Root " + i)
                        .build())
            .toList();
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published))
        .thenReturn(Optional.of(post));
    when(commentRepository.findByPostIdOrderByCreatedAtAsc(postId)).thenReturn(roots);
    when(userRepository.findAllById(any())).thenReturn(List.of());
    when(userProfileRepository.findByUserIdIn(any())).thenReturn(List.of());

    PageResponse<CommentThreadResponse> response =
        commentService.getComments(postId, CommentListRequest.builder().page(0).size(2).build());

    assertThat(response.getContent()).hasSize(2);
    assertThat(response.getTotalElements()).isEqualTo(3);
    assertThat(response.getTotalPages()).isEqualTo(2);
    assertThat(response.isFirst()).isTrue();
    assertThat(response.isLast()).isFalse();
  }

  @Test
  void getComments_missingOrUnpublishedPostReturnsNotFound() {
    when(postRepository.findByIdAndStatusAndDeletedAtIsNull(postId, Post.Status.published))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> commentService.getComments(postId, CommentListRequest.builder().build()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Post not found");
  }

  @Test
  void reportComment_incrementsReportCount() {
    UUID commentId = UUID.randomUUID();
    Comment comment = Comment.builder().id(commentId).postId(postId).reportCount(2).build();
    when(commentRepository.findByIdAndPostIdAndDeletedAtIsNull(commentId, postId))
        .thenReturn(Optional.of(comment));

    commentService.reportComment(postId, commentId);

    assertThat(comment.getReportCount()).isEqualTo(3);
    verify(commentRepository).save(comment);
  }

  @Test
  void reportComment_repeatedCallsKeepIncrementing() {
    UUID commentId = UUID.randomUUID();
    Comment comment = Comment.builder().id(commentId).postId(postId).build();
    when(commentRepository.findByIdAndPostIdAndDeletedAtIsNull(commentId, postId))
        .thenReturn(Optional.of(comment));

    commentService.reportComment(postId, commentId);
    commentService.reportComment(postId, commentId);

    assertThat(comment.getReportCount()).isEqualTo(2);
  }

  @Test
  void reportComment_unknownOrDeletedCommentThrowsNotFound() {
    UUID commentId = UUID.randomUUID();
    when(commentRepository.findByIdAndPostIdAndDeletedAtIsNull(commentId, postId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> commentService.reportComment(postId, commentId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Comment not found");

    verify(commentRepository, never()).save(any());
  }
}
