package com.vegalife.integration.controller.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.post.Comment;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class CommentControllerIntegrationTest {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("vegalife_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(false);

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private PostRepository postRepository;

  @Autowired private CommentRepository commentRepository;

  @Autowired private ModerationLogRepository moderationLogRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private User user;
  private User otherUser;
  private String accessToken;

  @BeforeEach
  void setUp() {
    moderationLogRepository.deleteAll();
    commentRepository.deleteAll();
    postRepository.deleteAll();
    userRepository.deleteAll();

    user = saveUser("commenter", "commenter@example.com");
    otherUser = saveUser("postauthor", "postauthor@example.com");
    accessToken = jwtTokenService.generateAccessToken(user);
  }

  @Test
  void createComment_authenticatedUserCreatesTopLevelComment() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);

    mockMvc
        .perform(
            post("/api/posts/{postId}/comments", post.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"  Great post!  \"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Comment created successfully"))
        .andExpect(jsonPath("$.data.id").isNotEmpty())
        .andExpect(jsonPath("$.data.postId").value(post.getId().toString()))
        .andExpect(jsonPath("$.data.userId").value(user.getId().toString()))
        .andExpect(jsonPath("$.data.parentId").doesNotExist())
        .andExpect(jsonPath("$.data.content").value("Great post!"));

    assertThat(commentRepository.findAll()).hasSize(1);
    assertThat(commentRepository.findAll().getFirst().getPostId()).isEqualTo(post.getId());
    assertThat(commentRepository.findAll().getFirst().getUserId()).isEqualTo(user.getId());
  }

  @Test
  void createComment_withParentIdCreatesNestedReply() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment parent =
        commentRepository.saveAndFlush(
            Comment.builder()
                .postId(post.getId())
                .userId(otherUser.getId())
                .content("Original comment")
                .build());

    mockMvc
        .perform(
            post("/api/posts/{postId}/comments", post.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Reply\",\"parentId\":\"%s\"}".formatted(parent.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.postId").value(post.getId().toString()))
        .andExpect(jsonPath("$.data.parentId").value(parent.getId().toString()))
        .andExpect(jsonPath("$.data.userId").value(user.getId().toString()));

    assertThat(commentRepository.findAll())
        .anySatisfy(comment -> assertThat(comment.getParentId()).isEqualTo(parent.getId()));
  }

  @Test
  void createComment_withoutAuthenticationReturns401() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);

    mockMvc
        .perform(
            post("/api/posts/{postId}/comments", post.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Hello\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void createComment_blankContentReturns400() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);

    mockMvc
        .perform(
            post("/api/posts/{postId}/comments", post.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void createComment_unpublishedPostReturns404() throws Exception {
    Post post = savePost(otherUser, Post.Status.created);

    mockMvc
        .perform(
            post("/api/posts/{postId}/comments", post.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Hello\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Post not found"));
  }

  @Test
  void createComment_parentFromAnotherPostReturns404() throws Exception {
    Post target = savePost(otherUser, Post.Status.published);
    Post differentPost = savePost(user, Post.Status.published);
    Comment parent =
        commentRepository.saveAndFlush(
            Comment.builder()
                .postId(differentPost.getId())
                .userId(user.getId())
                .content("Different thread")
                .build());

    mockMvc
        .perform(
            post("/api/posts/{postId}/comments", target.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Reply\",\"parentId\":\"%s\"}".formatted(parent.getId())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Parent comment not found"));
  }

  @Test
  void updateComment_ownerEditsContentAndMarksEdited() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment comment =
        commentRepository.saveAndFlush(
            Comment.builder().postId(post.getId()).userId(user.getId()).content("Typo").build());

    mockMvc
        .perform(
            patch("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"  Fixed  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Comment updated successfully"))
        .andExpect(jsonPath("$.data.content").value("Fixed"))
        .andExpect(jsonPath("$.data.edited").value(true));

    assertThat(commentRepository.findById(comment.getId()).orElseThrow().getContent())
        .isEqualTo("Fixed");
  }

  @Test
  void updateComment_secondEditStaysMarkedEdited() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment comment =
        commentRepository.saveAndFlush(
            Comment.builder()
                .postId(post.getId())
                .userId(user.getId())
                .content("Already edited")
                .isEdited(true)
                .build());

    mockMvc
        .perform(
            patch("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Edited again\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.edited").value(true));
  }

  @Test
  void updateComment_nonOwnerReturns403() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment comment =
        commentRepository.saveAndFlush(
            Comment.builder()
                .postId(post.getId())
                .userId(otherUser.getId())
                .content("Not yours")
                .build());

    mockMvc
        .perform(
            patch("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Hijack\"}"))
        .andExpect(status().isForbidden());

    assertThat(commentRepository.findById(comment.getId()).orElseThrow().getContent())
        .isEqualTo("Not yours");
  }

  @Test
  void updateComment_withoutAuthenticationReturns401() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment comment =
        commentRepository.saveAndFlush(
            Comment.builder().postId(post.getId()).userId(user.getId()).content("Mine").build());

    mockMvc
        .perform(
            patch("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Edited\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateComment_blankContentReturns400() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment comment =
        commentRepository.saveAndFlush(
            Comment.builder().postId(post.getId()).userId(user.getId()).content("Mine").build());

    mockMvc
        .perform(
            patch("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  @Test
  void updateComment_missingCommentReturns404() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);

    mockMvc
        .perform(
            patch("/api/posts/{postId}/comments/{commentId}", post.getId(), UUID.randomUUID())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Hello\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Comment not found"));
  }

  @Test
  void updateComment_commentFromAnotherPostReturns404() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Post differentPost = savePost(otherUser, Post.Status.published);
    Comment comment =
        commentRepository.saveAndFlush(
            Comment.builder()
                .postId(differentPost.getId())
                .userId(user.getId())
                .content("Wrong thread")
                .build());

    mockMvc
        .perform(
            patch("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"Hello\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Comment not found"));
  }

  @Test
  void deleteComment_ownerSoftDeletesOwnComment() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment comment =
        commentRepository.saveAndFlush(
            Comment.builder().postId(post.getId()).userId(user.getId()).content("Mine").build());

    mockMvc
        .perform(
            delete("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Comment deleted successfully"));

    assertThat(commentRepository.findById(comment.getId()).orElseThrow().getDeletedAt())
        .isNotNull();
    assertThat(moderationLogRepository.findAll()).isEmpty();
  }

  @Test
  void deleteComment_nonOwnerNonAdminReturns404() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment comment =
        commentRepository.saveAndFlush(
            Comment.builder()
                .postId(post.getId())
                .userId(otherUser.getId())
                .content("Not yours")
                .build());

    mockMvc
        .perform(
            delete("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Comment not found"));

    assertThat(commentRepository.findById(comment.getId()).orElseThrow().getDeletedAt()).isNull();
  }

  @Test
  void deleteComment_adminDeletesAnotherUsersCommentAndLogsModeration() throws Exception {
    User admin = saveUser("adminuser", "admin@example.com", User.Role.ADMIN);
    String adminToken = jwtTokenService.generateAccessToken(admin);
    Post post = savePost(otherUser, Post.Status.published);
    Comment comment =
        commentRepository.saveAndFlush(
            Comment.builder()
                .postId(post.getId())
                .userId(user.getId())
                .content("Needs moderation")
                .build());

    mockMvc
        .perform(
            delete("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    assertThat(commentRepository.findById(comment.getId()).orElseThrow().getDeletedAt())
        .isNotNull();
    List<ModerationLog> logs = moderationLogRepository.findAll();
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getActorId()).isEqualTo(admin.getId());
    assertThat(logs.getFirst().getTargetId()).isEqualTo(comment.getId());
    assertThat(logs.getFirst().getAction()).isEqualTo("DELETE_COMMENT");
    assertThat(logs.getFirst().getTargetType()).isEqualTo("COMMENT");
  }

  @Test
  void deleteComment_adminDeletingOwnCommentIsNotLogged() throws Exception {
    User admin = saveUser("adminuser", "admin@example.com", User.Role.ADMIN);
    String adminToken = jwtTokenService.generateAccessToken(admin);
    Post post = savePost(otherUser, Post.Status.published);
    Comment comment =
        commentRepository.saveAndFlush(
            Comment.builder().postId(post.getId()).userId(admin.getId()).content("Mine").build());

    mockMvc
        .perform(
            delete("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk());

    assertThat(moderationLogRepository.findAll()).isEmpty();
  }

  @Test
  void deleteComment_withoutAuthenticationReturns401() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment comment =
        commentRepository.saveAndFlush(
            Comment.builder().postId(post.getId()).userId(user.getId()).content("Mine").build());

    mockMvc
        .perform(delete("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteComment_alreadyDeletedReturns404() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment comment =
        commentRepository.saveAndFlush(
            Comment.builder()
                .postId(post.getId())
                .userId(user.getId())
                .content("Mine")
                .deletedAt(Instant.now())
                .build());

    mockMvc
        .perform(
            delete("/api/posts/{postId}/comments/{commentId}", post.getId(), comment.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteComment_replyKeepsParentIdAfterParentIsDeleted() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment parent =
        commentRepository.saveAndFlush(
            Comment.builder().postId(post.getId()).userId(user.getId()).content("Parent").build());
    Comment reply =
        commentRepository.saveAndFlush(
            Comment.builder()
                .postId(post.getId())
                .userId(otherUser.getId())
                .parentId(parent.getId())
                .content("Reply")
                .build());

    mockMvc
        .perform(
            delete("/api/posts/{postId}/comments/{commentId}", post.getId(), parent.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());

    Comment reloadedReply = commentRepository.findById(reply.getId()).orElseThrow();
    assertThat(reloadedReply.getDeletedAt()).isNull();
    assertThat(reloadedReply.getParentId()).isEqualTo(parent.getId());
  }

  @Test
  void listComments_returnsNestedThreadWithoutAuthentication() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment root =
        commentRepository.saveAndFlush(
            Comment.builder().postId(post.getId()).userId(user.getId()).content("Root").build());
    commentRepository.saveAndFlush(
        Comment.builder()
            .postId(post.getId())
            .userId(otherUser.getId())
            .parentId(root.getId())
            .content("Reply")
            .build());

    mockMvc
        .perform(get("/api/posts/{postId}/comments", post.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(root.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].username").value("commenter"))
        .andExpect(jsonPath("$.data.content[0].deleted").value(false))
        .andExpect(jsonPath("$.data.content[0].replies.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].replies[0].content").value("Reply"))
        .andExpect(jsonPath("$.data.content[0].replies[0].username").value("postauthor"));
  }

  @Test
  void listComments_deletedCommentShowsPlaceholderAndKeepsReplies() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    Comment root =
        commentRepository.saveAndFlush(
            Comment.builder()
                .postId(post.getId())
                .userId(user.getId())
                .content("Will be deleted")
                .build());
    commentRepository.saveAndFlush(
        Comment.builder()
            .postId(post.getId())
            .userId(otherUser.getId())
            .parentId(root.getId())
            .content("Still visible")
            .build());

    mockMvc
        .perform(
            delete("/api/posts/{postId}/comments/{commentId}", post.getId(), root.getId())
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/posts/{postId}/comments", post.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content[0].deleted").value(true))
        .andExpect(jsonPath("$.data.content[0].content").value("[deleted]"))
        .andExpect(jsonPath("$.data.content[0].userId").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].username").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].replies[0].content").value("Still visible"));
  }

  @Test
  void listComments_paginatesRootComments() throws Exception {
    Post post = savePost(otherUser, Post.Status.published);
    for (int i = 0; i < 3; i++) {
      commentRepository.saveAndFlush(
          Comment.builder().postId(post.getId()).userId(user.getId()).content("Root " + i).build());
    }

    mockMvc
        .perform(
            get("/api/posts/{postId}/comments", post.getId()).param("page", "0").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.first").value(true))
        .andExpect(jsonPath("$.data.last").value(false));
  }

  @Test
  void listComments_unpublishedPostReturns404() throws Exception {
    Post post = savePost(otherUser, Post.Status.created);

    mockMvc
        .perform(get("/api/posts/{postId}/comments", post.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Post not found"));
  }

  @Test
  void listComments_missingPostReturns404() throws Exception {
    mockMvc
        .perform(get("/api/posts/{postId}/comments", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Post not found"));
  }

  private User saveUser(String username, String email) {
    return saveUser(username, email, User.Role.USER);
  }

  private User saveUser(String username, String email, User.Role role) {
    return userRepository.save(
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(role)
            .status(User.Status.activated)
            .emailVerified(true)
            .build());
  }

  private Post savePost(User owner, Post.Status status) {
    return postRepository.saveAndFlush(
        Post.builder()
            .user(owner)
            .title("Published post")
            .content("Post body")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .status(status)
            .viewCount(0)
            .build());
  }
}
