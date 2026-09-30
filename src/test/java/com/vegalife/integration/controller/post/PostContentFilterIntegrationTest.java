package com.vegalife.integration.controller.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.filter.RelevanceScore;
import com.vegalife.filter.RelevanceScore.Band;
import com.vegalife.filter.RelevanceScorer;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.outbound.OutboundStatus;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.post.PostFilterLog;
import com.vegalife.model.user.User;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.PostFilterLogRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.scheduled.OutboundMessageDrainer;
import com.vegalife.service.token.JwtTokenService;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end acceptance coverage for post semantic content filtering (FR-007 to FR-012, BR-FILTER
 * series): the static-rule gate, the mocked semantic path, outbox delivery via {@code drainOnce},
 * flag exposure in API responses and the post-filter audit rows.
 *
 * <p>Only the two AI-facing collaborators are replaced ({@link EmbeddingModel}, {@link
 * RelevanceScorer}); everything else — static rules, service, adapter, repositories, drainer — runs
 * for real against PostgreSQL.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class PostContentFilterIntegrationTest {

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

  @Autowired private OutboundMessageRepository outboundMessageRepository;

  @Autowired private PostFilterLogRepository postFilterLogRepository;

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private OutboundMessageDrainer outboundMessageDrainer;

  @Autowired private EntityManager entityManager;

  @MockitoBean private EmbeddingModel embeddingModel;

  @MockitoBean private RelevanceScorer relevanceScorer;

  private User user;
  private User otherUser;
  private String accessToken;
  private String otherAccessToken;

  @BeforeEach
  void setUp() {
    user = createUser("filterowner", "filterowner@example.com");
    otherUser = createUser("filterother", "filterother@example.com");
    accessToken = jwtTokenService.generateAccessToken(user);
    otherAccessToken = jwtTokenService.generateAccessToken(otherUser);
    when(embeddingModel.embed(anyString())).thenReturn(new float[1024]);
  }

  @Test
  void createWithPublish_passesFilter_andPublishesPost() throws Exception {
    stubScore(0.9, Band.PASS);

    UUID postId =
        createPostWithPublish(
            "Tofu bowl for lunch", "A simple plant-based lunch with tofu and rice.");

    Post queued = postById(postId);
    assertThat(queued.getFlag()).isEqualTo(Post.Flag.PENDING);
    assertThat(queued.getStatus()).isEqualTo(Post.Status.created);
    assertThat(queued.getPublishedAt()).isNull();
    assertThat(contentFilterQueue()).hasSize(1);

    drainFilterQueue();

    Post filtered = postById(postId);
    assertThat(filtered.getStatus()).isEqualTo(Post.Status.published);
    assertThat(filtered.getFlag()).isEqualTo(Post.Flag.PASSED);
    assertThat(filtered.getPublishedAt()).isNotNull();

    OutboundMessage message = contentFilterQueue().getFirst();
    assertThat(message.getStatus()).isEqualTo(OutboundStatus.COMPLETED);
    assertThat(message.getPayload()).isNull();

    List<PostFilterLog> logs = auditRows(postId);
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getFlag()).isEqualTo(Post.Flag.PASSED);
    assertThat(logs.getFirst().getScore()).isEqualTo(0.9);
    assertThat(logs.getFirst().getReasons()).isEmpty();
  }

  @Test
  void createWithPublish_staticLinkSpam_rejectedWithoutCallingEmbedding() throws Exception {
    clearInvocations(embeddingModel, relevanceScorer);

    UUID postId =
        createPostWithPublish(
            "Link heavy post",
            "https://a.example.org/x https://b.example.org/y https://c.example.org/z");

    drainFilterQueue();

    Post filtered = postById(postId);
    assertThat(filtered.getStatus()).isEqualTo(Post.Status.flagged);
    assertThat(filtered.getFlag()).isEqualTo(Post.Flag.REJECTED);
    assertThat(filtered.getPublishedAt()).isNull();

    List<PostFilterLog> logs = auditRows(postId);
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getFlag()).isEqualTo(Post.Flag.REJECTED);
    assertThat(logs.getFirst().getScore()).isNull();
    assertThat(logs.getFirst().getReasons()).isEqualTo("LINK_SPAM");

    verifyNoInteractions(embeddingModel, relevanceScorer);
  }

  @Test
  void createWithPublish_reviewBand_flagsNeedsReviewForManualReview() throws Exception {
    stubScore(0.6, Band.REVIEW);

    UUID postId =
        createPostWithPublish(
            "Review queue post", "A thoughtful plant-based cooking article for readers.");

    drainFilterQueue();

    Post filtered = postById(postId);
    assertThat(filtered.getStatus()).isEqualTo(Post.Status.flagged);
    assertThat(filtered.getFlag()).isEqualTo(Post.Flag.NEEDS_REVIEW);
    assertThat(filtered.getPublishedAt()).isNull();

    List<PostFilterLog> logs = auditRows(postId);
    assertThat(logs).hasSize(1);
    assertThat(logs.getFirst().getFlag()).isEqualTo(Post.Flag.NEEDS_REVIEW);
    assertThat(logs.getFirst().getScore()).isEqualTo(0.6);
    assertThat(logs.getFirst().getReasons()).isEqualTo("RELEVANCE_REVIEW: 0.600");
  }

  @Test
  void createAsDraft_isNeverFilteredOrQueued() throws Exception {
    clearInvocations(embeddingModel, relevanceScorer);

    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Draft notes post",
                      "type": "blog",
                      "content": "A draft body that stays unpublished for now."
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("created"))
        .andExpect(jsonPath("$.data.flag").doesNotExist());

    UUID postId = findPostId("Draft notes post");
    Post draft = postById(postId);
    assertThat(draft.getFlag()).isNull();
    assertThat(draft.getStatus()).isEqualTo(Post.Status.created);
    assertThat(contentFilterQueue()).isEmpty();

    verifyNoInteractions(embeddingModel, relevanceScorer);
  }

  @Test
  void editPublishedPost_requeuesAndRejectsOnSecondRun() throws Exception {
    stubScore(0.9, Band.PASS);
    UUID postId =
        createPostWithPublish(
            "Editing published post", "The original article body before any edit happens.");

    drainFilterQueue();
    assertThat(postById(postId).getStatus()).isEqualTo(Post.Status.published);
    assertThat(postById(postId).getFlag()).isEqualTo(Post.Flag.PASSED);

    stubScore(0.3, Band.REJECT);
    mockMvc
        .perform(
            patch("/api/posts/" + postId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "content": "An updated article body with plenty of words and no links at all."
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("published"))
        .andExpect(jsonPath("$.data.flag").value("PENDING"));

    assertThat(postById(postId).getStatus()).isEqualTo(Post.Status.published);
    assertThat(postById(postId).getFlag()).isEqualTo(Post.Flag.PENDING);
    assertThat(contentFilterQueue()).hasSize(2);

    drainFilterQueue();

    Post filtered = postById(postId);
    assertThat(filtered.getStatus()).isEqualTo(Post.Status.flagged);
    assertThat(filtered.getFlag()).isEqualTo(Post.Flag.REJECTED);

    List<PostFilterLog> logs = auditRows(postId);
    assertThat(logs).hasSize(2);
    assertThat(logs)
        .extracting(PostFilterLog::getFlag)
        .containsExactlyInAnyOrder(Post.Flag.PASSED, Post.Flag.REJECTED);
    assertThat(logs)
        .extracting(PostFilterLog::getReasons)
        .containsExactlyInAnyOrder("", "RELEVANCE_LOW: 0.300");
    assertThat(logs)
        .filteredOn(entry -> entry.getFlag() == Post.Flag.REJECTED)
        .singleElement()
        .extracting(PostFilterLog::getScore)
        .isEqualTo(0.3);
  }

  @Test
  void editFlaggedPost_requeuesAndRepublishesOnPass() throws Exception {
    stubScore(0.3, Band.REJECT);
    UUID postId =
        createPostWithPublish(
            "Flagged post edit", "A body that initially lands in the reject band.");

    drainFilterQueue();
    assertThat(postById(postId).getStatus()).isEqualTo(Post.Status.flagged);
    assertThat(postById(postId).getFlag()).isEqualTo(Post.Flag.REJECTED);

    stubScore(0.9, Band.PASS);
    mockMvc
        .perform(
            patch("/api/posts/" + postId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "content": "A clean rewritten article about plant-based cooking ideas."
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("flagged"))
        .andExpect(jsonPath("$.data.flag").value("PENDING"));

    assertThat(contentFilterQueue()).hasSize(2);

    drainFilterQueue();

    Post filtered = postById(postId);
    assertThat(filtered.getStatus()).isEqualTo(Post.Status.published);
    assertThat(filtered.getFlag()).isEqualTo(Post.Flag.PASSED);
    assertThat(filtered.getPublishedAt()).isNotNull();
    assertThat(auditRows(postId)).hasSize(2);
  }

  @Test
  void editByNonOwner_returns404WithoutRequeueing() throws Exception {
    UUID postId =
        createPostWithPublish("Protected post", "Original content owned by the author only.");
    assertThat(contentFilterQueue()).hasSize(1);

    mockMvc
        .perform(
            patch("/api/posts/" + postId)
                .header("Authorization", "Bearer " + otherAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "content": "Someone else changed this content body completely."
                    }
                    """))
        .andExpect(status().isNotFound());

    assertThat(contentFilterQueue()).hasSize(1);
    Post unchanged = postById(postId);
    assertThat(unchanged.getContent()).isEqualTo("Original content owned by the author only.");
    assertThat(unchanged.getFlag()).isEqualTo(Post.Flag.PENDING);
    assertThat(unchanged.getStatus()).isEqualTo(Post.Status.created);
    assertThat(auditRows(postId)).isEmpty();
  }

  @Test
  void flagExposedInCreateUpdateAndListResponses() throws Exception {
    stubScore(0.9, Band.PASS);

    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Timeline flag post",
                      "type": "blog",
                      "content": "A published article whose flag must be visible everywhere.",
                      "categoryIds": ["%s"],
                      "publish": true
                    }
                    """
                        .formatted(
                            categoryRepository
                                .saveAndFlush(Category.builder().name("Recipes").build())
                                .getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.flag").value("PENDING"));

    UUID postId = findPostId("Timeline flag post");
    drainFilterQueue();

    mockMvc
        .perform(get("/api/posts").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(postId.toString()))
        .andExpect(jsonPath("$.data.content[0].flag").value("PASSED"));

    mockMvc
        .perform(
            patch("/api/posts/" + postId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Timeline flag post edited"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("published"))
        .andExpect(jsonPath("$.data.flag").value("PENDING"));
  }

  private void drainFilterQueue() {
    entityManager.flush();
    outboundMessageDrainer.drainOnce();
    entityManager.flush();
    entityManager.clear();
  }

  private void stubScore(double score, Band band) {
    when(relevanceScorer.score(any())).thenReturn(new RelevanceScore(score, band));
  }

  private UUID createPostWithPublish(String title, String content) throws Exception {
    Category category = categoryRepository.saveAndFlush(Category.builder().name("Recipes").build());
    mockMvc
        .perform(
            post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "%s",
                      "type": "blog",
                      "content": "%s",
                      "categoryIds": ["%s"],
                      "publish": true
                    }
                    """
                        .formatted(title, content, category.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("created"))
        .andExpect(jsonPath("$.data.flag").value("PENDING"));
    return findPostId(title);
  }

  private User createUser(String username, String email) {
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

  private UUID findPostId(String title) {
    return postRepository.findAll().stream()
        .filter(p -> title.equals(p.getTitle()))
        .map(Post::getId)
        .findFirst()
        .orElseThrow();
  }

  private Post postById(UUID postId) {
    return postRepository.findById(postId).orElseThrow();
  }

  private List<OutboundMessage> contentFilterQueue() {
    return outboundMessageRepository.findAll().stream()
        .filter(message -> message.getChannel() == OutboundChannel.CONTENT_FILTER)
        .toList();
  }

  private List<PostFilterLog> auditRows(UUID postId) {
    return postFilterLogRepository.findAll().stream()
        .filter(log -> postId.equals(log.getPostId()))
        .toList();
  }
}
