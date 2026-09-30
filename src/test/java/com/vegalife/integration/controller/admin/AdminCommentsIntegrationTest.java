package com.vegalife.integration.controller.admin;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.model.post.Comment;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
class AdminCommentsIntegrationTest {

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

  @PersistenceContext private EntityManager entityManager;

  private String adminToken;
  private String userToken;
  private UUID regularId;
  private UUID secondId;
  private UUID postAId;
  private UUID postBId;
  private UUID activeCommentId;
  private UUID removedCommentId;
  private UUID oldCommentId;

  @BeforeEach
  void setUp() {
    commentRepository.deleteAll();
    postRepository.deleteAll();
    userRepository.deleteAll();

    User admin = saveUser("adminuser", "admin@example.com", User.Role.ADMIN);
    User regular = saveUser("regularuser", "user@example.com", User.Role.USER);
    User second = saveUser("seconduser", "second@example.com", User.Role.USER);
    regularId = regular.getId();
    secondId = second.getId();
    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(regular);

    Post postA = savePost(regular, "Vegan tofu bowl");
    Post postB = savePost(second, "Plant-based pasta");
    postAId = postA.getId();
    postBId = postB.getId();

    Comment active =
        commentRepository.saveAndFlush(
            Comment.builder().userId(regularId).postId(postAId).content("Great recipe").build());
    Comment removed =
        commentRepository.saveAndFlush(
            Comment.builder()
                .userId(secondId)
                .postId(postAId)
                .content("Removed comment")
                .deletedAt(Instant.now())
                .build());
    Comment old =
        commentRepository.saveAndFlush(
            Comment.builder().userId(secondId).postId(postBId).content("Old comment").build());
    activeCommentId = active.getId();
    removedCommentId = removed.getId();
    oldCommentId = old.getId();
  }

  @Test
  void listComments_asAdmin_returns200PaginatedAcrossUsersAndStatuses() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/comments")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Comments retrieved successfully"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.content.length()").value(3))
        .andExpect(jsonPath("$.data.content[0].id").exists())
        .andExpect(jsonPath("$.data.content[0].postId").exists())
        .andExpect(jsonPath("$.data.content[0].userId").exists())
        .andExpect(jsonPath("$.data.content[0].content").exists())
        .andExpect(jsonPath("$.data.content[0].createdAt").exists())
        .andExpect(jsonPath("$.data.content[0].updatedAt").exists())
        .andExpect(jsonPath("$.data.content[*].username", hasItems("regularuser", "seconduser")))
        .andExpect(jsonPath("$.data.content[*].status", hasItems("active", "removed")));
  }

  @Test
  void listComments_withSmallPageSize_returnsPagedSubset() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/comments")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(2));
  }

  @Test
  void listComments_filterByStatusActive_returnsOnlyActiveComments() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/comments")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "active"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[*].status").value(everyItem(is("active"))))
        .andExpect(
            jsonPath("$.data.content[*].id")
                .value(hasItems(activeCommentId.toString(), oldCommentId.toString())));
  }

  @Test
  void listComments_filterByStatusRemoved_returnsOnlyRemovedComments() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/comments")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "removed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(removedCommentId.toString()))
        .andExpect(jsonPath("$.data.content[0].status").value("removed"))
        .andExpect(jsonPath("$.data.content[0].username").value("seconduser"));
  }

  @Test
  void listComments_filterByUserId_returnsOnlyThatUsersComments() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/comments")
                .header("Authorization", "Bearer " + adminToken)
                .param("userId", secondId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[*].userId").value(everyItem(is(secondId.toString()))))
        .andExpect(jsonPath("$.data.content[*].username").value(everyItem(is("seconduser"))));
  }

  @Test
  void listComments_filterByPostId_returnsOnlyThatPostsComments() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/comments")
                .header("Authorization", "Bearer " + adminToken)
                .param("postId", postAId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content[*].postId").value(everyItem(is(postAId.toString()))));
  }

  @Test
  void listComments_filterByDateRange_returnsOnlyCommentsInRange() throws Exception {
    Instant backdated = Instant.now().minus(400, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    backdate(oldCommentId, backdated);

    mockMvc
        .perform(
            get("/api/admin/comments")
                .header("Authorization", "Bearer " + adminToken)
                .param(
                    "createdFrom",
                    DateTimeFormatter.ISO_INSTANT.format(backdated.minus(1, ChronoUnit.DAYS)))
                .param(
                    "createdTo",
                    DateTimeFormatter.ISO_INSTANT.format(backdated.plus(1, ChronoUnit.DAYS))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(oldCommentId.toString()));
  }

  @Test
  void listComments_withInvalidStatus_returns400WithValidationMessage() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/comments")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "bogus"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Status must be one of: active, removed"));
  }

  @Test
  void listComments_withNonAllowlistedSortProperty_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/comments")
                .header("Authorization", "Bearer " + adminToken)
                .param("sort", "content,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(
            jsonPath("$.message").value("Sort property must be one of: createdAt, updatedAt"));
  }

  @Test
  void listComments_withMalformedUserId_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/comments")
                .header("Authorization", "Bearer " + adminToken)
                .param("userId", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  void listComments_withCreatedFromAfterCreatedTo_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/comments")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "2100-01-01T00:00:00Z")
                .param("createdTo", "2000-01-01T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("createdFrom must be before createdTo"));
  }

  @Test
  void listComments_asNonAdmin_returns403() throws Exception {
    mockMvc
        .perform(get("/api/admin/comments").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void listComments_withoutJwt_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/comments")).andExpect(status().isUnauthorized());
  }

  @Test
  void listComments_withJwtAndContentType_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/comments")
                .header("Authorization", "Bearer " + adminToken)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(3));
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

  private Post savePost(User owner, String title) {
    return postRepository.saveAndFlush(
        Post.builder()
            .user(owner)
            .title(title)
            .content("Post content")
            .status(Post.Status.created)
            .viewCount(0)
            .build());
  }

  private void backdate(UUID commentId, Instant when) {
    commentRepository.flush();
    entityManager
        .createNativeQuery("UPDATE comment SET created_at = :ts WHERE id = :id")
        .setParameter("ts", Timestamp.from(when))
        .setParameter("id", commentId)
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();
  }
}
