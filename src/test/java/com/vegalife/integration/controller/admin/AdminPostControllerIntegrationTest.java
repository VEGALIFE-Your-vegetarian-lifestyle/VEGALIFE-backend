package com.vegalife.integration.controller.admin;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.token.JwtTokenService;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class AdminPostControllerIntegrationTest {

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

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private MediaRepository mediaRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private User admin;
  private User member;
  private User author2;
  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    postRepository.deleteAll();
    mediaRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();

    admin = createUser("adminposter", "admin.poster@example.com", User.Role.ADMIN);
    member = createUser("memberposter", "member.poster@example.com", User.Role.USER);
    author2 = createUser("secondauthor", "second.author@example.com", User.Role.USER);
    adminToken = jwtTokenService.generateAccessToken(admin);
    userToken = jwtTokenService.generateAccessToken(member);
  }

  @Test
  void listPosts_asAdmin_returns200WithFullItemFieldsAndExcludesSoftDeleted() throws Exception {
    Category recipes = categoryRepository.saveAndFlush(Category.builder().name("Recipes").build());
    postRepository.save(
        Post.builder()
            .user(author2)
            .title("Aardvark tofu bowl")
            .content("Rich post content")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .featuredImageUrl("https://example.com/tofu.jpg")
            .categories(new HashSet<>(Set.of(recipes)))
            .status(Post.Status.published)
            .flag(Post.Flag.PASSED)
            .viewCount(7)
            .publishedAt(Instant.now())
            .build());
    createPost(member, "Zebra draft", Post.Status.created, null, null);
    createPost(member, "Soft deleted", Post.Status.published, null, Instant.now());

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("sort", "title,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Posts retrieved successfully"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(20))
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.totalPages").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(jsonPath("$.data.content[0].title").value("Aardvark tofu bowl"))
        .andExpect(jsonPath("$.data.content[0].id").exists())
        .andExpect(jsonPath("$.data.content[0].content").value("Rich post content"))
        .andExpect(
            jsonPath("$.data.content[0].featuredImageUrl").value("https://example.com/tofu.jpg"))
        .andExpect(jsonPath("$.data.content[0].categoryIds[0]").value(recipes.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].mediaIds").isArray())
        .andExpect(jsonPath("$.data.content[0].status").value("published"))
        .andExpect(jsonPath("$.data.content[0].flag").value("PASSED"))
        .andExpect(jsonPath("$.data.content[0].viewCount").value(7))
        .andExpect(jsonPath("$.data.content[0].publishedAt").exists())
        .andExpect(jsonPath("$.data.content[0].createdAt").exists())
        .andExpect(jsonPath("$.data.content[0].userId").value(author2.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].username").value("secondauthor"))
        .andExpect(jsonPath("$.data.content[0].email").value("second.author@example.com"))
        .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.content[1].title").value("Zebra draft"))
        .andExpect(jsonPath("$.data.content[1].status").value("created"))
        .andExpect(jsonPath("$.data.content[1].flag").doesNotExist());
  }

  @Test
  void listPosts_returnsAllAuthorsAndAllStatuses() throws Exception {
    createPost(member, "P1", Post.Status.created, null, null);
    createPost(member, "P2", Post.Status.processed, null, null);
    createPost(member, "P3", Post.Status.published, null, null);
    createPost(author2, "P4", Post.Status.unpublished, null, null);
    createPost(author2, "P5", Post.Status.hidden, null, null);
    createPost(admin, "P6", Post.Status.flagged, Post.Flag.REJECTED, null);

    mockMvc
        .perform(get("/api/admin/posts").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(6))
        .andExpect(jsonPath("$.data.content.length()").value(6))
        .andExpect(
            jsonPath("$.data.content[*].status")
                .value(
                    containsInAnyOrder(
                        "created", "processed", "published", "unpublished", "hidden", "flagged")))
        .andExpect(
            jsonPath("$.data.content[*].username")
                .value(
                    containsInAnyOrder(
                        "memberposter",
                        "memberposter",
                        "memberposter",
                        "secondauthor",
                        "secondauthor",
                        "adminposter")));
  }

  @Test
  void listPosts_filterByStatus_returnsOnlyMatchingPosts() throws Exception {
    createPost(member, "Draft", Post.Status.created, null, null);
    createPost(member, "Rejected", Post.Status.flagged, Post.Flag.REJECTED, null);
    createPost(member, "Live", Post.Status.published, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "flagged"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Rejected"))
        .andExpect(jsonPath("$.data.content[0].status").value("flagged"))
        .andExpect(jsonPath("$.data.content[0].flag").value("REJECTED"));
  }

  @Test
  void listPosts_filterByUserId_returnsOnlyThatAuthorsPosts() throws Exception {
    createPost(member, "Member post", Post.Status.published, null, null);
    createPost(author2, "Author post", Post.Status.created, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("userId", author2.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Author post"))
        .andExpect(jsonPath("$.data.content[0].username").value("secondauthor"))
        .andExpect(jsonPath("$.data.content[0].userId").value(author2.getId().toString()));
  }

  @Test
  void listPosts_filterByCategoryId_returnsEachMatchingPostExactlyOnce() throws Exception {
    Category recipes = categoryRepository.saveAndFlush(Category.builder().name("Recipes").build());
    Category snacks = categoryRepository.saveAndFlush(Category.builder().name("Snacks").build());

    postRepository.save(
        Post.builder()
            .user(author2)
            .title("In both categories")
            .content("Content")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .categories(new HashSet<>(Set.of(recipes, snacks)))
            .status(Post.Status.published)
            .viewCount(0)
            .build());
    postRepository.save(
        Post.builder()
            .user(member)
            .title("In recipes only")
            .content("Content")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .categories(new HashSet<>(Set.of(recipes)))
            .status(Post.Status.published)
            .viewCount(0)
            .build());
    postRepository.save(
        Post.builder()
            .user(member)
            .title("In snacks only")
            .content("Content")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .categories(new HashSet<>(Set.of(snacks)))
            .status(Post.Status.published)
            .viewCount(0)
            .build());
    postRepository.save(
        Post.builder()
            .user(member)
            .title("Deleted from recipes")
            .content("Content")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .categories(new HashSet<>(Set.of(recipes)))
            .status(Post.Status.published)
            .viewCount(0)
            .deletedAt(Instant.now())
            .build());

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("categoryId", recipes.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(
            jsonPath("$.data.content[*].title")
                .value(containsInAnyOrder("In both categories", "In recipes only")));
  }

  @Test
  void listPosts_filterByCreatedDateRange_returnsOnlyMatchingPosts() throws Exception {
    createPost(member, "First", Post.Status.published, null, null);
    createPost(author2, "Second", Post.Status.published, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "2000-01-01T00:00:00Z")
                .param("createdTo", "2100-01-01T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(2));

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "2100-01-01T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.content.length()").value(0));
  }

  @Test
  void listPosts_mediaIds_excludesSoftDeletedMedia() throws Exception {
    Media live =
        mediaRepository.save(
            Media.builder().status(Media.Status.succeed).uploadedBy(member).build());
    Media gone =
        mediaRepository.save(
            Media.builder()
                .status(Media.Status.succeed)
                .uploadedBy(member)
                .deletedAt(Instant.now())
                .build());
    postRepository.save(
        Post.builder()
            .user(member)
            .title("Post with media")
            .content("Post content")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .media(new HashSet<>(Set.of(live, gone)))
            .status(Post.Status.published)
            .viewCount(0)
            .build());

    mockMvc
        .perform(get("/api/admin/posts").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].mediaIds").isArray())
        .andExpect(jsonPath("$.data.content[0].mediaIds.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].mediaIds[0]").value(live.getId().toString()));
  }

  @Test
  void listPosts_withoutJwt_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/posts")).andExpect(status().isUnauthorized());
  }

  @Test
  void listPosts_withNonAdminJwt_returns403() throws Exception {
    mockMvc
        .perform(get("/api/admin/posts").header("Authorization", "Bearer " + userToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void listPosts_withPagination_returnsSinglePageItem() throws Exception {
    createPost(member, "Aardvark post", Post.Status.published, null, null);
    createPost(member, "Mid post", Post.Status.published, null, null);
    createPost(member, "Zebra post", Post.Status.published, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "1")
                .param("sort", "title,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(1))
        .andExpect(jsonPath("$.data.totalElements").value(3))
        .andExpect(jsonPath("$.data.totalPages").value(3))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Aardvark post"));
  }

  @Test
  void listPosts_withDisallowedSortProperty_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("sort", "passwordHash,asc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Sort property must be one of: createdAt, publishedAt, updatedAt, viewCount,"
                        + " title"));
  }

  @Test
  void listPosts_withMalformedSort_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("sort", "createdAt,asc,extra"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("Sort must be in the form property,asc|desc"));
  }

  @Test
  void listPosts_withInvalidStatus_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "bogus"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listPosts_withInvalidPaging_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("size", "101"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listPosts_createdFromAfterCreatedTo_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "2100-01-01T00:00:00Z")
                .param("createdTo", "2000-01-01T00:00:00Z"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("createdFrom must be before createdTo"));
  }

  @Test
  void listPosts_withMalformedDate_returns400() throws Exception {
    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "not-a-date"))
        .andExpect(status().isBadRequest());
  }

  private User createUser(String username, String email, User.Role role) {
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

  private Post createPost(
      User owner, String title, Post.Status status, Post.Flag flag, Instant deletedAt) {
    return postRepository.save(
        Post.builder()
            .user(owner)
            .title(title)
            .content("Post content for " + title)
            .rawContent(JsonNodeFactory.instance.objectNode())
            .status(status)
            .flag(flag)
            .viewCount(0)
            .deletedAt(deletedAt)
            .build());
  }
}
