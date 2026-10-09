package com.vegalife.unit.service.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.post.CommentCreateRequest;
import com.vegalife.dto.response.post.CommentResponse;
import com.vegalife.model.post.Comment;
import com.vegalife.model.post.Post;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.service.post.CommentService;
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
}
