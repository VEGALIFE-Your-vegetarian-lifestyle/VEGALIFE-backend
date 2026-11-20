package com.vegalife.integration.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vegalife.enums.post.MediaStatus;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.User;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.MediaRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserProfileRepository;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
      new PostgreSQLContainer<>("pgvector/pgvector:pg15")
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

  @Autowired private UserProfileRepository userProfileRepository;

  @Autowired private PostRepository postRepository;

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private MediaRepository mediaRepository;

  @Autowired private DishRepository dishRepository;

  @Autowired private RecipeRepository recipeRepository;

  @Autowired private JwtTokenService jwtTokenService;

  private User admin;
  private User member;
  private User author2;
  private String adminToken;
  private String userToken;

  @BeforeEach
  void setUp() {
    recipeRepository.deleteAll();
    postRepository.deleteAll();
    mediaRepository.deleteAll();
    categoryRepository.deleteAll();
    userProfileRepository.deleteAll();
    dishRepository.deleteAll();
    userRepository.deleteAll();

    admin = createUser("adminposter", "admin.poster@example.com", UserRole.ADMIN);
    member = createUser("memberposter", "member.poster@example.com", UserRole.USER);
    author2 = createUser("secondauthor", "second.author@example.com", UserRole.USER);
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
            .status(PostStatus.published)
            .viewCount(7)
            .publishedAt(Instant.now())
            .build());
    createPost(member, "Zebra draft", PostStatus.drafted, null, null);
    createPost(member, "Soft deleted", PostStatus.published, null, Instant.now());

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("flag", "all")
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
        .andExpect(jsonPath("$.data.content[0].flag").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].viewCount").value(7))
        .andExpect(jsonPath("$.data.content[0].publishedAt").exists())
        .andExpect(jsonPath("$.data.content[0].createdAt").exists())
        .andExpect(jsonPath("$.data.content[0].userId").value(author2.getId().toString()))
        .andExpect(jsonPath("$.data.content[0].displayName").value("secondauthor"))
        .andExpect(jsonPath("$.data.content[0].email").value("second.author@example.com"))
        .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist())
        .andExpect(jsonPath("$.data.content[1].title").value("Zebra draft"))
        .andExpect(jsonPath("$.data.content[1].status").value("drafted"))
        .andExpect(jsonPath("$.data.content[1].flag").doesNotExist());
  }

  @Test
  void listPosts_returnsAllAuthorsAndAllStatuses() throws Exception {
    createPost(member, "P1", PostStatus.drafted, null, null);
    createPost(member, "P2", PostStatus.pending, PostFlag.FILTER_FAILED, null);
    createPost(member, "P3", PostStatus.published, null, null);
    createPost(author2, "P4", PostStatus.unpublished, null, null);
    createPost(author2, "P5", PostStatus.processing, null, null);
    createPost(admin, "P6", PostStatus.published, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("flag", "all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(6))
        .andExpect(jsonPath("$.data.content.length()").value(6))
        .andExpect(
            jsonPath("$.data.content[*].status")
                .value(
                    containsInAnyOrder(
                        "drafted",
                        "pending",
                        "published",
                        "unpublished",
                        "processing",
                        "published")))
        .andExpect(
            jsonPath("$.data.content[*].displayName")
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
    createPost(member, "Draft", PostStatus.drafted, null, null);
    createPost(member, "Rejected", PostStatus.unpublished, null, null);
    createPost(member, "Live", PostStatus.published, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "unpublished")
                .param("flag", "all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Rejected"))
        .andExpect(jsonPath("$.data.content[0].status").value("unpublished"))
        .andExpect(jsonPath("$.data.content[0].flag").doesNotExist());
  }

  @Test
  void listPosts_filterByStatusPending_returnsTheReviewQueue() throws Exception {
    createPost(member, "Draft", PostStatus.drafted, null, null);
    createPost(member, "In flight", PostStatus.processing, null, null);
    createPost(member, "Filter failed", PostStatus.pending, PostFlag.FILTER_FAILED, null);
    createPost(member, "Live", PostStatus.published, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "pending")
                .param("flag", "all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Filter failed"));
  }

  @Test
  void listPosts_filterByFlag_returnsOnlyMatchingPosts() throws Exception {
    createPost(member, "Draft", PostStatus.drafted, null, null);
    createPost(member, "Rejected", PostStatus.unpublished, null, null);
    createPost(member, "Failed", PostStatus.pending, PostFlag.FILTER_FAILED, null);
    createPost(member, "Duplicate", PostStatus.pending, PostFlag.SIMILAR_CONTENT, null);
    createPost(member, "Live", PostStatus.published, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("flag", "SIMILAR_CONTENT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Duplicate"))
        .andExpect(jsonPath("$.data.content[0].flag").value("SIMILAR_CONTENT"));
  }

  @Test
  void listPosts_filterByPassedFlag_returnsThePostTheFilterCleared() throws Exception {
    createPost(member, "Live", PostStatus.published, PostFlag.FILTER_PASSED, null);
    createPost(member, "Overridden", PostStatus.published, PostFlag.FILTER_FAILED, null);

    // published + FILTER_PASSED vs published + FILTER_FAILED is exactly the distinction an admin
    // cannot recover any other way: one was cleared by the filter, the other by a human (ADR-025).
    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "published")
                .param("flag", "FILTER_PASSED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Live"))
        .andExpect(jsonPath("$.data.content[0].flag").value("FILTER_PASSED"));
  }

  @Test
  void listPosts_withoutFlagParam_doesNotFilterByFlag() throws Exception {
    createPost(member, "Draft", PostStatus.drafted, null, null);
    createPost(member, "Rejected", PostStatus.unpublished, null, null);
    createPost(member, "Failed", PostStatus.pending, PostFlag.FILTER_FAILED, null);
    createPost(member, "Live", PostStatus.published, null, null);

    // The default is `all`: a post can carry any of the four verdicts, so preselecting one would
    // silently hide the others (ADR-023, ADR-025).
    mockMvc
        .perform(get("/api/admin/posts").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(4))
        .andExpect(jsonPath("$.data.content.length()").value(4));
  }

  @Test
  void listPosts_filterByUserId_returnsOnlyThatAuthorsPosts() throws Exception {
    createPost(member, "Member post", PostStatus.published, null, null);
    createPost(author2, "Author post", PostStatus.drafted, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("userId", author2.getId().toString())
                .param("flag", "all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Author post"))
        .andExpect(jsonPath("$.data.content[0].displayName").value("secondauthor"))
        .andExpect(jsonPath("$.data.content[0].userId").value(author2.getId().toString()));
  }

  @Test
  void listPosts_filterByKeyword_returnsOnlyMatchingPosts() throws Exception {
    createPost(member, "Vegan pho", PostStatus.published, null, null);
    createPost(author2, "Bean burger", PostStatus.drafted, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("keyword", "PHO")
                .param("flag", "all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].title").value("Vegan pho"));
  }

  @Test
  void listPosts_filterByKeywordMatchingContent_returnsThePost() throws Exception {
    createPost(member, "Unrelated title", PostStatus.published, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("keyword", "unrelated title")
                .param("flag", "all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(1));
  }

  @Test
  void listPosts_filterByKeywordWithWildcard_matchesLiterally() throws Exception {
    createPost(member, "Vegan pho", PostStatus.published, null, null);
    createPost(author2, "Bean burger", PostStatus.drafted, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("keyword", "%")
                .param("flag", "all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.content.length()").value(0));
  }

  @Test
  void listPosts_filterByKeywordMatchingNothing_returns200WithEmptyPage() throws Exception {
    createPost(member, "Vegan pho", PostStatus.published, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("keyword", "nothing matches this")
                .param("flag", "all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(0))
        .andExpect(jsonPath("$.data.content.length()").value(0));
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
            .status(PostStatus.published)
            .viewCount(0)
            .build());
    postRepository.save(
        Post.builder()
            .user(member)
            .title("In recipes only")
            .content("Content")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .categories(new HashSet<>(Set.of(recipes)))
            .status(PostStatus.published)
            .viewCount(0)
            .build());
    postRepository.save(
        Post.builder()
            .user(member)
            .title("In snacks only")
            .content("Content")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .categories(new HashSet<>(Set.of(snacks)))
            .status(PostStatus.published)
            .viewCount(0)
            .build());
    postRepository.save(
        Post.builder()
            .user(member)
            .title("Deleted from recipes")
            .content("Content")
            .rawContent(JsonNodeFactory.instance.objectNode())
            .categories(new HashSet<>(Set.of(recipes)))
            .status(PostStatus.published)
            .viewCount(0)
            .deletedAt(Instant.now())
            .build());

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("categoryId", recipes.getId().toString())
                .param("flag", "all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.totalElements").value(2))
        .andExpect(jsonPath("$.data.content.length()").value(2))
        .andExpect(
            jsonPath("$.data.content[*].title")
                .value(containsInAnyOrder("In both categories", "In recipes only")));
  }

  @Test
  void listPosts_filterByCreatedDateRange_returnsOnlyMatchingPosts() throws Exception {
    createPost(member, "First", PostStatus.published, null, null);
    createPost(author2, "Second", PostStatus.published, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("createdFrom", "2000-01-01T00:00:00Z")
                .param("createdTo", "2100-01-01T00:00:00Z")
                .param("flag", "all"))
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
            Media.builder().status(MediaStatus.succeed).uploadedBy(member).build());
    Media gone =
        mediaRepository.save(
            Media.builder()
                .status(MediaStatus.succeed)
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
            .status(PostStatus.published)
            .viewCount(0)
            .build());

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("flag", "all"))
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
    createPost(member, "Aardvark post", PostStatus.published, null, null);
    createPost(member, "Mid post", PostStatus.published, null, null);
    createPost(member, "Zebra post", PostStatus.published, null, null);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("page", "0")
                .param("size", "1")
                .param("flag", "all")
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

  /**
   * The admin list item must carry every field the member feed carries, plus its own. Compared from
   * the two live payloads rather than a hardcoded list, so a field added to {@code
   * PostListResponse} and forgotten here fails this test instead of shipping silently.
   */
  @Test
  void listPosts_itemFields_areASupersetOfTheMemberFeedItemFields() throws Exception {
    createPost(member, "Superset source", PostStatus.published, PostFlag.FILTER_PASSED, null);

    JsonNode feedItem = firstItem(get("/api/posts"), null);
    JsonNode adminItem = firstItem(get("/api/admin/posts"), adminToken);

    Set<String> feedFields = fieldNames(feedItem);
    Set<String> adminFields = fieldNames(adminItem);

    assertThat(adminFields).containsAll(feedFields);
    assertThat(adminFields).contains("flag", "flagDetail", "email");
  }

  @Test
  void listPosts_populatesAuthorAvatarRawContentAndRecipeIds() throws Exception {
    JsonNode rawContent = JsonNodeFactory.instance.objectNode().put("blocks", "editor-document");
    Post post =
        postRepository.saveAndFlush(
            Post.builder()
                .user(member)
                .title("Post with a full payload")
                .content("Plain text body")
                .rawContent(rawContent)
                .status(PostStatus.published)
                .viewCount(3)
                .build());
    userProfileRepository.saveAndFlush(
        UserProfile.builder().user(member).avatarUrl("https://cdn.example.com/avatar.jpg").build());

    Dish dish = dishRepository.saveAndFlush(Dish.builder().name("Avatar pho").build());
    Recipe live = saveRecipe(post, dish, "Live recipe");
    Recipe removed = saveRecipe(post, dish, "Removed recipe");
    removed.setDeletedAt(Instant.now());
    recipeRepository.saveAndFlush(removed);

    mockMvc
        .perform(
            get("/api/admin/posts")
                .header("Authorization", "Bearer " + adminToken)
                .param("flag", "all"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.content[0].avatarUrl").value("https://cdn.example.com/avatar.jpg"))
        .andExpect(jsonPath("$.data.content[0].rawContent.blocks").value("editor-document"))
        .andExpect(jsonPath("$.data.content[0].recipeIds.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].recipeIds[0]").value(live.getId().toString()));
  }

  private JsonNode firstItem(MockHttpServletRequestBuilder request, String token) throws Exception {
    MockHttpServletResponse response =
        mockMvc
            .perform(token == null ? request : request.header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    return new ObjectMapper()
        .readTree(response.getContentAsString())
        .path("data")
        .path("content")
        .get(0);
  }

  private static Set<String> fieldNames(JsonNode node) {
    Set<String> names = new HashSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private Recipe saveRecipe(Post post, Dish dish, String name) {
    return recipeRepository.saveAndFlush(
        Recipe.builder()
            .dish(dish)
            .user(member)
            .name(name)
            .instructions("Simmer until tender")
            .servings(2)
            .moderationStatus(ModerationStatus.APPROVED)
            .posts(new HashSet<>(Set.of(post)))
            .build());
  }

  private User createUser(String displayName, String email, UserRole role) {
    return userRepository.save(
        User.builder()
            .displayName(displayName)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(role)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build());
  }

  private Post createPost(
      User owner, String title, PostStatus status, PostFlag flag, Instant deletedAt) {
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
