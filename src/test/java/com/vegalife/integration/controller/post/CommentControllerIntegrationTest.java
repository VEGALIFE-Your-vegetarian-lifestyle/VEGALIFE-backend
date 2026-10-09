package com.vegalife.integration.controller.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.model.post.Comment;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
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

  @Autowired private JwtTokenService jwtTokenService;

  private User user;
  private User otherUser;
  private String accessToken;

  @BeforeEach
  void setUp() {
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

  private User saveUser(String username, String email) {
    return userRepository.save(
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(User.Role.USER)
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
