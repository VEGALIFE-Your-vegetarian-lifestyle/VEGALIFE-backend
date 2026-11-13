package com.vegalife.unit.service.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.PostEmbeddingRepository;
import com.vegalife.repository.post.PostEmbeddingRepository.NearestMatch;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.service.filter.ActiveEmbeddingModel;
import com.vegalife.service.filter.ComponentScorer;
import com.vegalife.service.filter.ContentFilterOutboundAdapter;
import com.vegalife.service.filter.ContentFilterPayload;
import com.vegalife.service.filter.ContentFilterService;
import com.vegalife.service.filter.ContentFilterService.FilterResult;
import com.vegalife.service.filter.DuplicateDetector;
import com.vegalife.service.filter.EmbeddingComponentScorer;
import com.vegalife.service.filter.FilterOutcome;
import com.vegalife.service.filter.FilterProperties;
import com.vegalife.service.filter.FilterVerdict;
import com.vegalife.service.filter.PostFlagDetail;
import com.vegalife.shared.exception.EmbeddingClientException;
import com.vegalife.shared.exception.RetryableDeliveryException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

/**
 * Verdict-matrix tests for the CONTENT_FILTER queue adapter.
 *
 * <p>Under ADR-023 the adapter writes one decision and nothing else: {@code status} plus the
 * verdict in {@code flag} + {@code flag_detail}. A {@code PASS} publishes with {@code
 * FILTER_PASSED}; a {@code FAIL} or {@code UNCERTAIN} holds the post at {@code processing} with
 * {@code FILTER_FAILED}; a near-duplicate holds it with {@code SIMILAR_CONTENT} and the matched
 * post id in {@code flag_detail}. The filter never unpublishes — only an admin does.
 *
 * <p>Two more obligations these tests pin: a verdict is discarded unless the post is still {@code
 * processing} and not deleted (otherwise a withdrawn post would be republished), and the vector is
 * stored for <em>every</em> outcome, because a held post an admin later publishes must be able to
 * act as a comparison target.
 */
@ExtendWith(MockitoExtension.class)
class ContentFilterOutboundAdapterTest {

  private static final String TITLE = "Công thức nấu chay";
  private static final String CONTENT = "Nội dung bài viết chay lành mạnh cho cả gia đình.";
  private static final String MODEL = "gemini-embedding-001";
  private static final float[] EMBEDDING = {0.5f, 0.5f};

  @Mock private PostRepository postRepository;
  @Mock private ContentFilterService contentFilterService;
  @Mock private RecipeRepository recipeRepository;
  @Mock private RecipeIngredientRepository recipeIngredientRepository;
  @Mock private PostEmbeddingRepository postEmbeddingRepository;
  @Mock private DuplicateDetector duplicateDetector;
  @Mock private ActiveEmbeddingModel activeEmbeddingModel;
  @Mock private EmbeddingComponentScorer.Factory componentScorerFactory;
  @Mock private ComponentScorer recipeScorer;
  @Mock private ModerationLogRepository moderationLogRepository;

  private ContentFilterOutboundAdapter adapter;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final FilterProperties filterProperties = new FilterProperties();

  @BeforeEach
  void setUp() {
    adapter =
        new ContentFilterOutboundAdapter(
            postRepository,
            contentFilterService,
            recipeRepository,
            recipeIngredientRepository,
            postEmbeddingRepository,
            duplicateDetector,
            activeEmbeddingModel,
            objectMapper,
            componentScorerFactory,
            moderationLogRepository,
            filterProperties);
    noRecipeAttached();
  }

  /**
   * Default for every existing test: the post has no recipe attached, so the recipe component is
   * never consulted. Tests that do attach a recipe override this.
   *
   * <p>Lenient because tests that fail before scoring — a blank payload, a missing post, a filter
   * that throws — never reach the recipe lookup, and that is the behaviour they assert.
   */
  private void noRecipeAttached() {
    lenient().when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(any())).thenReturn(List.of());
  }

  @Test
  void passPublishesThePostAndRecordsTheVerdict() throws Exception {
    Post post = passing(0.91);

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(PostStatus.published, post.getStatus());
    assertEquals(PostFlag.FILTER_PASSED, post.getFlag());
    // The snapshot survives publication: score and the accept boundary it cleared (ADR-025).
    JsonNode detail = post.getFlagDetail();
    assertEquals("FILTER_PASSED", detail.path("kind").asText());
    assertEquals(0.91, detail.path("score").asDouble(), 1e-9);
    assertEquals(0.70, detail.path("threshold").asDouble(), 1e-9);
    // No outcome: the kind already says the run passed.
    assertFalse(detail.has("outcome"));
    assertNotNull(post.getPublishedAt());
    verify(postRepository).save(post);
  }

  @Test
  void passKeepsTheOriginalPublicationTime() throws Exception {
    Post post = post(PostStatus.processing);
    post.setPublishedAt(Instant.parse("2026-01-01T00:00:00Z"));
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(FilterOutcome.PASS, 0.8, List.of()));
    when(duplicateDetector.findDuplicate(any(), eq(post.getId()))).thenReturn(Optional.empty());
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(PostStatus.published, post.getStatus());
    assertEquals(PostFlag.FILTER_PASSED, post.getFlag());
    // The caller did not know which boundary applied, so the snapshot omits it rather than
    // inventing one (ADR-024).
    assertFalse(post.getFlagDetail().has("threshold"));
    assertEquals(Instant.parse("2026-01-01T00:00:00Z"), post.getPublishedAt());
  }

  @Test
  void aRefusalHoldsThePostAtProcessingInsteadOfUnpublishingIt() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(FilterOutcome.FAIL, 0.2, 0.45, List.of("RELEVANCE_LOW: 0.200")));
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // Only an admin may unpublish (ADR-023). The filter's refusal is a blocker, not a decision.
    assertEquals(PostStatus.processing, post.getStatus());
    assertEquals(PostFlag.FILTER_FAILED, post.getFlag());
    // The snapshot keeps the score and the bar it failed, so retuning the filter later cannot
    // rewrite the reason this post was held (ADR-024).
    JsonNode detail = post.getFlagDetail();
    assertEquals("FILTER_FAILED", detail.path("kind").asText());
    assertEquals("FAIL", detail.path("outcome").asText());
    assertEquals(0.2, detail.path("score").asDouble(), 1e-9);
    assertEquals(0.45, detail.path("threshold").asDouble(), 1e-9);
    assertNull(post.getPublishedAt());
    verify(postRepository).save(post);
  }

  @Test
  void anUncertainVerdictHoldsThePostWithTheSameBlocker() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(
        new FilterVerdict(FilterOutcome.UNCERTAIN, 0.58, List.of("RELEVANCE_REVIEW: 0.580")));
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(PostStatus.processing, post.getStatus());
    assertEquals(PostFlag.FILTER_FAILED, post.getFlag());
    verify(postRepository).save(post);
  }

  @Test
  void passStoresTheVectorForFutureComparisons() throws Exception {
    Post post = passing(0.9);

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    verify(postEmbeddingRepository).upsert(eq(post.getId()), eq(EMBEDDING), eq(MODEL));
  }

  @Test
  void nearDuplicateIsHeldAndRecordsTheMatchedPost() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(FilterOutcome.PASS, 0.95, List.of()));
    UUID originalId = UUID.randomUUID();
    when(duplicateDetector.findDuplicate(any(), eq(post.getId())))
        .thenReturn(Optional.of(new NearestMatch(originalId, 0.93)));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // The topic verdict was a pass; the duplicate verdict overrides it and parks the post.
    assertEquals(PostStatus.processing, post.getStatus());
    assertEquals(PostFlag.SIMILAR_CONTENT, post.getFlag());
    // The matched post cannot be recovered later, so it is persisted for the admin to open - with
    // the similarity that triggered the block and the threshold it was measured against.
    JsonNode detail = post.getFlagDetail();
    assertEquals("SIMILAR_CONTENT", detail.path("kind").asText());
    assertEquals(originalId.toString(), detail.path("postId").asText());
    assertEquals(0.93, detail.path("similarity").asDouble(), 1e-9);
    assertEquals(
        filterProperties.getDuplicateThreshold(), detail.path("threshold").asDouble(), 1e-9);
    assertNull(post.getPublishedAt());
  }

  @Test
  void aNearDuplicateStillStoresItsVector() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(FilterOutcome.PASS, 0.95, List.of()));
    when(duplicateDetector.findDuplicate(any(), eq(post.getId())))
        .thenReturn(Optional.of(new NearestMatch(UUID.randomUUID(), 0.93)));
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // The comparison query only reads published posts, so storing a held post's vector is safe -
    // and necessary: an admin may publish this very post after reviewing the duplicate.
    verify(postEmbeddingRepository).upsert(eq(post.getId()), eq(EMBEDDING), eq(MODEL));
  }

  @Test
  void aRefusedPostStillStoresItsVector() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(FilterOutcome.FAIL, 0.1, List.of("RELEVANCE_LOW: 0.100")));
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    verify(postEmbeddingRepository).upsert(eq(post.getId()), eq(EMBEDDING), eq(MODEL));
  }

  @Test
  void duplicateCheckRunsBeforeTheVectorIsStored() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(FilterOutcome.PASS, 0.9, List.of()));
    when(duplicateDetector.findDuplicate(any(), eq(post.getId()))).thenReturn(Optional.empty());
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // Ordering is the whole correctness argument here: storing first would make every post match
    // itself at similarity 1.0. inOrder proves the call sequence rather than assuming it.
    var inOrder = org.mockito.Mockito.inOrder(duplicateDetector, postEmbeddingRepository);
    inOrder.verify(duplicateDetector).findDuplicate(any(), eq(post.getId()));
    inOrder.verify(postEmbeddingRepository).upsert(any(), any(), anyString());
  }

  @Test
  void aFailedVectorStoreStillPublishesThePost() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(FilterOutcome.PASS, 0.9, List.of()));
    when(duplicateDetector.findDuplicate(any(), eq(post.getId()))).thenReturn(Optional.empty());
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    // The realistic cause: the pgvector extension is missing on the target database, so the table
    // does not accept the insert. That is a deployment problem, not this post's problem.
    doThrow(new InvalidDataAccessResourceUsageException("relation does not exist"))
        .when(postEmbeddingRepository)
        .upsert(any(), any(), anyString());
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // Storing the vector powers future detection; it is not what publishes this post. Blocking the
    // publish would let one misconfigured database stop the whole platform's posts.
    assertEquals(PostStatus.published, post.getStatus());
    assertEquals(PostFlag.FILTER_PASSED, post.getFlag());
    verify(postRepository).save(post);
  }

  // --- The verdict only applies to a post that is still waiting for it (ADR-023) ---

  @Test
  void passIsDiscardedWhenTheAuthorWithdrewWhileTheMessageWasInFlight() throws Exception {
    Post post = post(PostStatus.drafted);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // The author took the post down; a late pass must not put it back in the feed.
    assertEquals(PostStatus.drafted, post.getStatus());
    assertNull(post.getFlag());
    verify(contentFilterService, never()).filter(any());
    verify(postRepository, never()).save(any(Post.class));
  }

  @Test
  void refusalIsDiscardedWhenThePostWasAlreadyDecided() throws Exception {
    Post post = post(PostStatus.published);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // An in-flight refusal must not re-open a post that has already been published.
    assertEquals(PostStatus.published, post.getStatus());
    assertNull(post.getFlag());
    verify(contentFilterService, never()).filter(any());
    verify(postRepository, never()).save(any(Post.class));
  }

  @Test
  void verdictIsDiscardedForADeletedPost() throws Exception {
    Post post = post(PostStatus.processing);
    post.setDeletedAt(Instant.now());
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(PostStatus.processing, post.getStatus());
    assertNull(post.getFlag());
    verify(contentFilterService, never()).filter(any());
    verify(postRepository, never()).save(any(Post.class));
  }

  @Test
  void missingPostThrowsWithoutFiltering() throws Exception {
    UUID missing = UUID.randomUUID();
    when(postRepository.findById(missing)).thenReturn(Optional.empty());
    OutboundMessage message = message(new ContentFilterPayload(missing));

    assertThrows(IllegalStateException.class, () -> adapter.deliver(message));

    verify(contentFilterService, never()).filter(any());
    verify(postRepository, never()).save(any(Post.class));
  }

  @Test
  void filterFailurePropagatesWithoutSaving() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenThrow(new IllegalStateException("embedding unavailable"));
    OutboundMessage message = message(new ContentFilterPayload(post.getId()));

    assertThrows(IllegalStateException.class, () -> adapter.deliver(message));

    verify(postRepository, never()).save(any(Post.class));
  }

  @Test
  void blankPayloadThrows() {
    OutboundMessage message = OutboundMessage.builder().id(UUID.randomUUID()).payload("  ").build();

    assertThrows(IllegalStateException.class, () -> adapter.deliver(message));
  }

  @Test
  void payloadWithoutPostIdThrows() {
    OutboundMessage message = OutboundMessage.builder().id(UUID.randomUUID()).payload("{}").build();

    assertThrows(IllegalStateException.class, () -> adapter.deliver(message));
  }

  // --- Component-level verification (ADR-016): the recipe body is a second, independent score ---

  @Test
  void recipeThatNeedsReviewHoldsAnOtherwisePassingPost() throws Exception {
    Post post = passing(0.88);
    Recipe recipe = recipe();
    approvedComponents(recipe);
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId()))
        .thenReturn(List.of(recipe));
    when(componentScorerFactory.forComponent(ComponentScorer.Component.RECIPE))
        .thenReturn(recipeScorer);
    when(recipeScorer.score(any(), anyString()))
        .thenReturn(needsReview(ComponentScorer.Component.RECIPE, 0.31));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // The post's own prose passed. It is withheld because its recipe did not — that is the whole
    // point of scoring the recipe at all.
    assertEquals(PostStatus.processing, post.getStatus());
    assertEquals(PostFlag.FILTER_FAILED, post.getFlag());
    assertEquals(0.88, post.getContentScore());
    assertEquals(0.31, recipe.getScore());
    // The snapshot keeps the content band's own score and accept threshold: a reviewer can see the
    // prose cleared 0.70 and that something else is what held the post (ADR-024).
    JsonNode detail = post.getFlagDetail();
    assertEquals("UNCERTAIN", detail.path("outcome").asText());
    assertEquals(0.88, detail.path("score").asDouble(), 1e-9);
    assertEquals(0.7, detail.path("threshold").asDouble(), 1e-9);
  }

  @Test
  void contentRejectionOutranksARecipeThatNeedsReview() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(FilterOutcome.FAIL, 0.12, List.of("OFF_TOPIC")));
    Recipe recipe = recipe();
    approvedComponents(recipe);
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId()))
        .thenReturn(List.of(recipe));
    when(componentScorerFactory.forComponent(ComponentScorer.Component.RECIPE))
        .thenReturn(recipeScorer);
    when(recipeScorer.score(any(), anyString()))
        .thenReturn(needsReview(ComponentScorer.Component.RECIPE, 0.40));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // A recipe no worse than a rejection must not soften it: the refusal keeps its own score line
    // in flag_detail instead of being replaced by an "uncertain" reading.
    assertEquals(PostFlag.FILTER_FAILED, post.getFlag());
    assertEquals("FAIL", post.getFlagDetail().path("outcome").asText());
    assertEquals(0.12, post.getFlagDetail().path("score").asDouble(), 1e-9);
  }

  @Test
  void recipeThatPassesDoesNotAffectTheVerdict() throws Exception {
    Post post = passing(0.90);
    Recipe recipe = recipe();
    approvedComponents(recipe);
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId()))
        .thenReturn(List.of(recipe));
    when(componentScorerFactory.forComponent(ComponentScorer.Component.RECIPE))
        .thenReturn(recipeScorer);
    when(recipeScorer.score(any(), anyString()))
        .thenReturn(
            new ComponentScorer.ComponentResult(
                ComponentScorer.Component.RECIPE,
                false,
                Optional.of(0.84),
                Optional.of(EMBEDDING)));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(PostStatus.published, post.getStatus());
    assertEquals(PostFlag.FILTER_PASSED, post.getFlag());
    assertEquals(0.90, post.getContentScore());
    assertEquals(0.84, recipe.getScore());
  }

  @Test
  void unscorableRecipeHoldsThePost() throws Exception {
    Post post = passing(0.91);
    Recipe recipe = recipe();
    approvedComponents(recipe);
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId()))
        .thenReturn(List.of(recipe));
    when(componentScorerFactory.forComponent(ComponentScorer.Component.RECIPE))
        .thenReturn(recipeScorer);
    when(recipeScorer.score(any(), anyString()))
        .thenReturn(ComponentScorer.ComponentResult.failed(ComponentScorer.Component.RECIPE));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // The post is held for review. The score column stays null, which is how "never scored" is
    // distinguished from "scored badly".
    assertEquals(PostStatus.processing, post.getStatus());
    assertEquals(PostFlag.FILTER_FAILED, post.getFlag());
    assertNull(recipe.getScore());
    verify(postRepository).save(post);
  }

  // --- Dish and ingredient approval (BR-DISH-001): state, not score, decides ---

  @Test
  void pendingDishNameHoldsAnOtherwisePassingPost() throws Exception {
    Post post = passing(0.92);
    Recipe recipe = recipe();
    recipe.setDish(Dish.builder().id(UUID.randomUUID()).name("bún chả nấm").build());
    lenient().when(recipeIngredientRepository.findByRecipeId(recipe.getId())).thenReturn(List.of());
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId()))
        .thenReturn(List.of(recipe));
    when(componentScorerFactory.forComponent(ComponentScorer.Component.RECIPE))
        .thenReturn(recipeScorer);
    when(recipeScorer.score(any(), anyString()))
        .thenReturn(
            new ComponentScorer.ComponentResult(
                ComponentScorer.Component.RECIPE,
                false,
                Optional.of(0.88),
                Optional.of(EMBEDDING)));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // The text passed on every scored component. The post still waits, because it introduces a
    // name no admin has approved: that is the guarantee a score cannot provide.
    assertEquals(PostStatus.processing, post.getStatus());
    assertEquals(PostFlag.FILTER_FAILED, post.getFlag());
  }

  @Test
  void pendingIngredientNameHoldsAnOtherwisePassingPost() throws Exception {
    Post post = passing(0.93);
    Recipe recipe = recipe();
    recipe.setDish(approvedDish("Phở chay"));
    Ingredient pending =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("bột ngọt giả")
            .moderationStatus(ModerationStatus.PENDING)
            .build();
    when(recipeIngredientRepository.findByRecipeId(recipe.getId()))
        .thenReturn(
            List.of(
                RecipeIngredient.builder()
                    .recipeId(recipe.getId())
                    .ingredientId(pending.getId())
                    .ingredient(pending)
                    .build()));
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId()))
        .thenReturn(List.of(recipe));
    when(componentScorerFactory.forComponent(ComponentScorer.Component.RECIPE))
        .thenReturn(recipeScorer);
    when(recipeScorer.score(any(), anyString()))
        .thenReturn(
            new ComponentScorer.ComponentResult(
                ComponentScorer.Component.RECIPE,
                false,
                Optional.of(0.90),
                Optional.of(EMBEDDING)));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(PostStatus.processing, post.getStatus());
    assertEquals(PostFlag.FILTER_FAILED, post.getFlag());
  }

  @Test
  void rejectingContentStillWinsOverAPendingName() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(FilterOutcome.FAIL, 0.11, List.of("OFF_TOPIC")));
    Recipe recipe = recipe();
    recipe.setDish(Dish.builder().id(UUID.randomUUID()).name("món lạ").build());
    lenient().when(recipeIngredientRepository.findByRecipeId(recipe.getId())).thenReturn(List.of());
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId()))
        .thenReturn(List.of(recipe));
    when(componentScorerFactory.forComponent(ComponentScorer.Component.RECIPE))
        .thenReturn(recipeScorer);
    when(recipeScorer.score(any(), anyString()))
        .thenReturn(
            new ComponentScorer.ComponentResult(
                ComponentScorer.Component.RECIPE,
                false,
                Optional.of(0.80),
                Optional.of(EMBEDDING)));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(PostFlag.FILTER_FAILED, post.getFlag());
  }

  /**
   * ADR-020, the case that caused the outage: the provider rejected the credential permanently, so
   * retrying the message 116 times could only waste ten hours. The post must leave the in-flight
   * state now and the reason must be readable afterwards.
   */
  @Test
  void permanentEmbeddingRejectionQueuesThePostForReviewInsteadOfRetrying() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenThrow(
            new EmbeddingClientException(
                "Gemini embedding call failed for model gemini-embedding-001: the provider"
                    + " rejected the credential (HTTP 400) [API_KEY_INVALID]",
                EmbeddingClientException.Reason.AUTH,
                400,
                "API_KEY_INVALID",
                null,
                null));

    // No exception: the message's purpose is discharged by the review queue, not by a retry.
    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // The filter never formed an opinion, so this is FILTER_SKIPPED, not FILTER_FAILED.
    assertEquals(PostStatus.processing, post.getStatus());
    assertEquals(PostFlag.FILTER_SKIPPED, post.getFlag());
    // The row carries a cause code only; the provider message lives on the moderation log.
    assertEquals("FILTER_SKIPPED", post.getFlagDetail().path("kind").asText());
    assertEquals("UNAVAILABLE", post.getFlagDetail().path("cause").asText());
    verify(postRepository).save(post);

    ArgumentCaptor<ModerationLog> log = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository).save(log.capture());
    assertEquals("FILTER_UNAVAILABLE", log.getValue().getAction());
    assertEquals("POST", log.getValue().getTargetType());
    assertEquals(post.getId(), log.getValue().getTargetId());
    // An operator must be able to tell a lapsed key from a bad request without the raw body.
    assertTrue(log.getValue().getReason().contains("AUTH"));
    assertTrue(log.getValue().getReason().contains("HTTP 400"));
    assertTrue(log.getValue().getReason().contains("API_KEY_INVALID"));
  }

  /** A quota is transient: the post must stay untouched and the provider's hint must survive. */
  @Test
  void transientEmbeddingFailureIsRethrownRetryableWithTheProviderHint() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenThrow(
            new EmbeddingClientException(
                "rate limited",
                EmbeddingClientException.Reason.RATE_LIMITED,
                429,
                "RESOURCE_EXHAUSTED",
                Duration.ofSeconds(40),
                null));

    RetryableDeliveryException thrown =
        assertThrows(
            RetryableDeliveryException.class,
            () -> adapter.deliver(message(new ContentFilterPayload(post.getId()))));

    assertEquals(Duration.ofSeconds(40), thrown.getRetryAfter());
    // Still in flight: processing with no blocker.
    assertEquals(PostStatus.processing, post.getStatus());
    assertNull(post.getFlag());
    verify(postRepository, never()).save(any(Post.class));
    verify(moderationLogRepository, never()).save(any(ModerationLog.class));
  }

  /** A message abandoned before delivery still owes the post a terminal state. */
  @Test
  void abandonedMessageSettlesAProcessingPost() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.onDeliveryAbandoned(
        message(new ContentFilterPayload(post.getId())), new IllegalStateException("unavailable"));

    assertEquals(PostStatus.processing, post.getStatus());
    assertEquals(PostFlag.FILTER_SKIPPED, post.getFlag());
    assertEquals("UNAVAILABLE", post.getFlagDetail().path("cause").asText());
    ArgumentCaptor<ModerationLog> log = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository).save(log.capture());
    assertEquals("FILTER_UNAVAILABLE", log.getValue().getAction());
  }

  /** Never let an abandonment overwrite a verdict that already ran. */
  @Test
  void abandonedMessageNeverOverwritesAnExistingBlocker() throws Exception {
    Post post = post(PostStatus.processing);
    JsonNode existing = PostFlagDetail.skipped(PostFlagDetail.CAUSE_DISABLED);
    post.setFlag(PostFlag.FILTER_FAILED);
    post.setFlagDetail(existing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

    adapter.onDeliveryAbandoned(
        message(new ContentFilterPayload(post.getId())), new IllegalStateException("unavailable"));

    assertEquals(PostFlag.FILTER_FAILED, post.getFlag());
    assertEquals(existing, post.getFlagDetail());
    verify(postRepository, never()).save(any(Post.class));
    verify(moderationLogRepository, never()).save(any(ModerationLog.class));
  }

  /** Never let an abandonment resurrect a post the author already took down. */
  @Test
  void abandonedMessageIgnoresAPostThatIsNoLongerProcessing() throws Exception {
    Post post = post(PostStatus.drafted);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

    adapter.onDeliveryAbandoned(
        message(new ContentFilterPayload(post.getId())), new IllegalStateException("unavailable"));

    assertEquals(PostStatus.drafted, post.getStatus());
    assertNull(post.getFlag());
    verify(postRepository, never()).save(any(Post.class));
  }

  /** An unreadable payload has no post to settle; the hook must not throw into the drainer. */
  @Test
  void abandonedMessageWithUnreadablePayloadIsIgnored() {
    OutboundMessage broken =
        OutboundMessage.builder().id(UUID.randomUUID()).payload("not json").build();

    adapter.onDeliveryAbandoned(broken, new IllegalStateException("unavailable"));

    verify(postRepository, never()).findById(any());
  }

  /**
   * A long provider code no longer reaches the row at all, so it can no longer be truncated: the
   * snapshot is a fixed cause code and the prose stays in {@code moderation_log} (ADR-024).
   */
  @Test
  void unavailableCauseIsACodeNotProviderProse() throws Exception {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
    String providerCode = "A_VERY_LONG_PROVIDER_ERROR_CODE_THAT_WOULD_NOT_FIT_IN_THE_COLUMN_AT_ALL";
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenThrow(
            new EmbeddingClientException(
                "rejected",
                EmbeddingClientException.Reason.INVALID_REQUEST,
                400,
                providerCode,
                null,
                null));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals("UNAVAILABLE", post.getFlagDetail().path("cause").asText());
    ArgumentCaptor<ModerationLog> log = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository).save(log.capture());
    // Nothing is lost: the full code is still readable on the append-only log.
    assertTrue(log.getValue().getReason().contains(providerCode));
  }

  private static ComponentScorer.ComponentResult needsReview(
      ComponentScorer.Component component, double score) {
    return new ComponentScorer.ComponentResult(
        component, true, Optional.of(score), Optional.of(EMBEDDING));
  }

  private static Recipe recipe() {
    return Recipe.builder()
        .id(UUID.randomUUID())
        .name("Phở chay")
        .description("Nước dùng thanh, nấm hương")
        .instructions("Nấu nước dùng, trụng bánh phở, thêm rau thơm")
        .servings(2)
        .moderationStatus(ModerationStatus.APPROVED)
        .build();
  }

  /**
   * An approved dish and no ingredient lines: the default component state for tests that are about
   * the recipe score rather than about approval.
   */
  private void approvedComponents(Recipe recipe) {
    recipe.setDish(approvedDish("Phở chay"));
    lenient().when(recipeIngredientRepository.findByRecipeId(recipe.getId())).thenReturn(List.of());
  }

  private static Dish approvedDish(String name) {
    return Dish.builder()
        .id(UUID.randomUUID())
        .name(name)
        .moderationStatus(ModerationStatus.APPROVED)
        .build();
  }

  /** Sets up a post that passes the topic filter and is not a duplicate of anything. */
  private Post passing(double score) throws JsonProcessingException {
    Post post = post(PostStatus.processing);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(FilterOutcome.PASS, score, 0.70, List.of()));
    when(duplicateDetector.findDuplicate(any(), eq(post.getId()))).thenReturn(Optional.empty());
    when(activeEmbeddingModel.name()).thenReturn(MODEL);
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));
    return post;
  }

  private void stubFilter(FilterVerdict verdict) {
    when(contentFilterService.filter(TITLE + "\n" + CONTENT))
        .thenReturn(new FilterResult(verdict, EMBEDDING));
  }

  private OutboundMessage message(ContentFilterPayload payload) throws JsonProcessingException {
    return OutboundMessage.builder()
        .id(UUID.randomUUID())
        .payload(objectMapper.writeValueAsString(payload))
        .build();
  }

  /** A submitted post waiting for a verdict: {@code processing} with no blocker yet. */
  private static Post post(PostStatus status) {
    return Post.builder()
        .id(UUID.randomUUID())
        .title(TITLE)
        .content(CONTENT)
        .status(status)
        .viewCount(0)
        .build();
  }
}
