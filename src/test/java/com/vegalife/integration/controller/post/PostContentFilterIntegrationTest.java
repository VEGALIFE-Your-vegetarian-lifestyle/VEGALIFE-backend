package com.vegalife.integration.controller.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vegalife.enums.outbound.OutboundChannel;
import com.vegalife.enums.outbound.OutboundStatus;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.outbound.OutboundMessageRepository;
import com.vegalife.repository.post.CategoryRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.scheduled.OutboundMessageDrainer;
import com.vegalife.service.filter.RelevanceScore;
import com.vegalife.service.filter.RelevanceScore.Band;
import com.vegalife.service.filter.RelevanceScorer;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end acceptance coverage for post semantic content filtering (FR-007 to FR-012, BR-FILTER
 * series): the mocked semantic path, outbox delivery via {@code drainOnce}, status/flag transitions
 * on the post itself and their exposure in API responses.
 *
 * <p>Under ADR-023 a submitted post is {@code processing} with no flag while the verdict is
 * outstanding, and the adapter only ever writes {@code published} (pass) or {@code processing} with
 * one of the three blockers. The filter never unpublishes.
 *
 * <p>Only the two AI-facing collaborators are replaced ({@link EmbeddingModel}, {@link
 * RelevanceScorer}); everything else — service, adapter, repositories, drainer — runs for real
 * against PostgreSQL.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@TestPropertySource(properties = "app.filter.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class PostContentFilterIntegrationTest {

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

  @Autowired private PostRepository postRepository;

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private OutboundMessageRepository outboundMessageRepository;

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
    // 768 dimensions, matching the VECTOR(768) column the publish path now writes to (ADR-015).
    // A different width fails at insert time, so this also pins the EMBED_DIMENSIONS coupling.
    when(embeddingModel.embed(anyString())).thenReturn(unitVector(768));
  }

  @Test
  void createWithPublish_passesFilter_andPublishesPost() throws Exception {
    stubScore(0.9, Band.PASS);

    UUID postId =
        createPostWithPublish(
            "Tofu bowl for lunch", "A simple plant-based lunch with tofu and rice.");

    Post queued = postById(postId);
    // Submitted and in flight: status carries that, not the flag (ADR-023).
    assertThat(queued.getStatus()).isEqualTo(PostStatus.processing);
    assertThat(queued.getFlag()).isNull();
    assertThat(queued.getPublishedAt()).isNull();
    assertThat(contentFilterQueue()).hasSize(1);

    drainFilterQueue();

    Post filtered = postById(postId);
    assertThat(filtered.getStatus()).isEqualTo(PostStatus.published);
    // Publication and the verdict are separate facts; the row keeps both (ADR-025).
    assertThat(filtered.getFlag()).isEqualTo(PostFlag.FILTER_PASSED);
    assertThat(filtered.getFlagDetail().path("kind").asText()).isEqualTo("FILTER_PASSED");
    assertThat(filtered.getFlagDetail().path("score").asDouble()).isEqualTo(0.9);
    assertThat(filtered.getFlagDetail().path("threshold").asDouble()).isEqualTo(0.7);
    assertThat(filtered.getPublishedAt()).isNotNull();

    OutboundMessage message = contentFilterQueue().getFirst();
    assertThat(message.getStatus()).isEqualTo(OutboundStatus.COMPLETED);
    assertThat(message.getPayload()).isNull();
  }

  @Test
  void createWithPublish_linkHeavyStillEmbedsAndRejectsOnLowScore() throws Exception {
    clearInvocations(embeddingModel, relevanceScorer);
    stubScore(0.3, Band.REJECT);

    UUID postId =
        createPostWithPublish(
            "Link heavy post",
            "https://a.example.org/x https://b.example.org/y https://c.example.org/z");

    drainFilterQueue();

    Post filtered = postById(postId);
    // Held, not unpublished: only an admin may unpublish (ADR-023).
    assertThat(filtered.getStatus()).isEqualTo(PostStatus.processing);
    assertThat(filtered.getFlag()).isEqualTo(PostFlag.FILTER_FAILED);
    assertThat(filtered.getFlagDetail().path("kind").asText()).isEqualTo("FILTER_FAILED");
    assertThat(filtered.getFlagDetail().path("outcome").asText()).isEqualTo("FAIL");
    assertThat(filtered.getFlagDetail().path("score").asDouble()).isEqualTo(0.3);
    assertThat(filtered.getFlagDetail().path("threshold").asDouble()).isEqualTo(0.45);
    assertThat(filtered.getPublishedAt()).isNull();

    verify(embeddingModel).embed(anyString());
    verify(relevanceScorer).score(any());
  }

  @Test
  void createWithPublish_reviewBand_holdsForManualReview() throws Exception {
    stubScore(0.6, Band.REVIEW);

    UUID postId =
        createPostWithPublish(
            "Review queue post", "A thoughtful plant-based cooking article for readers.");

    drainFilterQueue();

    Post filtered = postById(postId);
    assertThat(filtered.getStatus()).isEqualTo(PostStatus.processing);
    assertThat(filtered.getFlag()).isEqualTo(PostFlag.FILTER_FAILED);
    assertThat(filtered.getPublishedAt()).isNull();
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
                      "content": "A draft body that stays unpublished for now.",
                      "rawContent": {}
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("drafted"))
        .andExpect(jsonPath("$.data.flag").doesNotExist());

    UUID postId = findPostId("Draft notes post");
    Post draft = postById(postId);
    assertThat(draft.getFlag()).isNull();
    assertThat(draft.getStatus()).isEqualTo(PostStatus.drafted);
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
    assertThat(postById(postId).getStatus()).isEqualTo(PostStatus.published);
    assertThat(postById(postId).getFlag()).isEqualTo(PostFlag.FILTER_PASSED);

    stubScore(0.3, Band.REJECT);
    mockMvc
        .perform(
            patch("/api/posts/" + postId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "content": "An updated article body with plenty of words and no links at all.",
                      "rawContent": {}
                    }
                    """))
        .andExpect(status().isOk())
        // A re-check takes the post out of the feed until the new verdict lands.
        .andExpect(jsonPath("$.data.status").value("processing"))
        .andExpect(jsonPath("$.data.flag").doesNotExist());

    assertThat(postById(postId).getStatus()).isEqualTo(PostStatus.processing);
    assertThat(postById(postId).getFlag()).isNull();
    assertThat(contentFilterQueue()).hasSize(2);

    drainFilterQueue();

    Post filtered = postById(postId);
    assertThat(filtered.getStatus()).isEqualTo(PostStatus.processing);
    assertThat(filtered.getFlag()).isEqualTo(PostFlag.FILTER_FAILED);
  }

  @Test
  void editHeldPost_requeuesAndPublishesOnPass() throws Exception {
    stubScore(0.3, Band.REJECT);
    UUID postId =
        createPostWithPublish("Held post edit", "A body that initially lands in the reject band.");

    drainFilterQueue();
    assertThat(postById(postId).getStatus()).isEqualTo(PostStatus.processing);
    assertThat(postById(postId).getFlag()).isEqualTo(PostFlag.FILTER_FAILED);

    stubScore(0.9, Band.PASS);
    mockMvc
        .perform(
            patch("/api/posts/" + postId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "content": "A clean rewritten article about plant-based cooking ideas.",
                      "rawContent": {}
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("processing"))
        .andExpect(jsonPath("$.data.flag").doesNotExist());

    assertThat(contentFilterQueue()).hasSize(2);

    drainFilterQueue();

    Post filtered = postById(postId);
    assertThat(filtered.getStatus()).isEqualTo(PostStatus.published);
    // Re-filtering overwrites the previous blocker instead of stacking a second one (ADR-025).
    assertThat(filtered.getFlag()).isEqualTo(PostFlag.FILTER_PASSED);
    assertThat(filtered.getFlagDetail().path("kind").asText()).isEqualTo("FILTER_PASSED");
    assertThat(filtered.getPublishedAt()).isNotNull();
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
                      "content": "Someone else changed this content body completely.",
                      "rawContent": {}
                    }
                    """))
        .andExpect(status().isNotFound());

    assertThat(contentFilterQueue()).hasSize(1);
    Post unchanged = postById(postId);
    assertThat(unchanged.getContent()).isEqualTo("Original content owned by the author only.");
    assertThat(unchanged.getStatus()).isEqualTo(PostStatus.processing);
    assertThat(unchanged.getFlag()).isNull();
  }

  @Test
  void publishedFlagIsNotExposedOnThePublicPostSurface() throws Exception {
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
                      "content": "A published article whose flag must stay admin-only.",
                      "rawContent": {},
                      "categoryIds": ["%s"],
                      "publish": true
                    }
                    """
                        .formatted(
                            categoryRepository
                                .saveAndFlush(Category.builder().name("Recipes").build())
                                .getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("processing"))
        .andExpect(jsonPath("$.data.flag").doesNotExist());

    UUID postId = findPostId("Timeline flag post");
    drainFilterQueue();

    // The verdict is stored...
    assertThat(postById(postId).getFlag()).isEqualTo(PostFlag.FILTER_PASSED);

    // ...but the member-facing surface does not carry it: a published post that an admin approved
    // over a FILTER_FAILED blocker must not announce that to readers (ADR-025).
    mockMvc
        .perform(get("/api/posts").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content.length()").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value(postId.toString()))
        .andExpect(jsonPath("$.data.content[0].status").value("published"))
        .andExpect(jsonPath("$.data.content[0].flag").doesNotExist())
        .andExpect(jsonPath("$.data.content[0].flagDetail").doesNotExist());

    mockMvc
        .perform(
            patch("/api/posts/" + postId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Timeline flag post edited",
                      "content": "A published article whose flag must stay admin-only.",
                      "rawContent": {}
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("processing"))
        .andExpect(jsonPath("$.data.flag").doesNotExist());
  }

  private void drainFilterQueue() {
    entityManager.flush();
    outboundMessageDrainer.drainOnce();
    entityManager.flush();
    entityManager.clear();
  }

  /**
   * Stubs the scorer with the boundary the real one would report for that band: the accept
   * threshold for PASS/REVIEW, the reject threshold for REJECT (ADR-024 snapshots it into {@code
   * flag_detail}).
   */
  private void stubScore(double score, Band band) {
    double threshold = band == Band.REJECT ? 0.45 : 0.70;
    when(relevanceScorer.score(any())).thenReturn(new RelevanceScore(score, band, threshold));
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
                      "content": "%s",
                      "rawContent": {},
                      "categoryIds": ["%s"],
                      "publish": true
                    }
                    """
                        .formatted(title, content, category.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("processing"))
        .andExpect(jsonPath("$.data.flag").doesNotExist());
    return findPostId(title);
  }

  private static float[] unitVector(int dimensions) {
    float[] v = new float[dimensions];
    v[0] = 1f;
    return v;
  }

  private User createUser(String displayName, String email) {
    return userRepository.save(
        User.builder()
            .displayName(displayName)
            .email(email)
            .passwordHash("$2a$10$test")
            .role(UserRole.USER)
            .status(UserStatus.activated)
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
}
