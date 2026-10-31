package com.vegalife.unit.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.filter.ActiveEmbeddingModel;
import com.vegalife.filter.ComponentScorer;
import com.vegalife.filter.ContentFilterOutboundAdapter;
import com.vegalife.filter.ContentFilterPayload;
import com.vegalife.filter.ContentFilterService;
import com.vegalife.filter.ContentFilterService.FilterResult;
import com.vegalife.filter.DuplicateDetector;
import com.vegalife.filter.EmbeddingComponentScorer;
import com.vegalife.filter.FilterVerdict;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.repository.post.PostEmbeddingRepository;
import com.vegalife.repository.post.PostEmbeddingRepository.NearestMatch;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

/**
 * Verdict-matrix tests for the CONTENT_FILTER queue adapter: PASSED publishes unconditionally
 * (every queued message came from a publish request or a content change on a published/unpublished
 * post, BR-FILTER-005), REJECTED/NEEDS_REVIEW flag and move to {@code unpublished}, and the verdict
 * persists only in {@code post.flag} / {@code post.status}. Failures propagate so the queue
 * retries.
 *
 * <p>Duplicate detection (ADR-015) adds two obligations these tests pin: a flagged duplicate is
 * never published, and the candidate's vector is never stored when the post does not publish —
 * storing it would make an unpublished post a comparison target for everyone else.
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

  private ContentFilterOutboundAdapter adapter;
  private final ObjectMapper objectMapper = new ObjectMapper();

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
            componentScorerFactory);
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
  void passedPublishesDraftPost() throws Exception {
    Post post = passing(Post.Status.created, 0.91);

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    assertNotNull(post.getPublishedAt());
    verify(postRepository).save(post);
  }

  @Test
  void passedKeepsExistingPublishedAt() throws Exception {
    Post post = post(Post.Status.published);
    post.setPublishedAt(Instant.parse("2026-01-01T00:00:00Z"));
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.PASSED, 0.8, List.of()));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    assertEquals(Instant.parse("2026-01-01T00:00:00Z"), post.getPublishedAt());
  }

  @Test
  void passedOnUnpublishedPostRepublishesIt() throws Exception {
    Post post = passing(Post.Status.unpublished, 0.77);

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    assertNotNull(post.getPublishedAt());
  }

  @Test
  void rejectedMovesPublishedPostToUnpublished() throws Exception {
    Post post = post(Post.Status.published);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.REJECTED, 0.2, List.of("RELEVANCE_LOW: 0.200")));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.REJECTED, post.getFlag());
    assertEquals(Post.Status.unpublished, post.getStatus());
    verify(postRepository).save(post);
  }

  @Test
  void needsReviewMovesToUnpublished() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.NEEDS_REVIEW, 0.58, List.of("RELEVANCE_REVIEW: 0.580")));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.NEEDS_REVIEW, post.getFlag());
    assertEquals(Post.Status.unpublished, post.getStatus());
    verify(postRepository).save(post);
  }

  @Test
  void passingPostStoresItsVectorForFutureComparisons() throws Exception {
    Post post = passing(Post.Status.created, 0.9);

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    verify(postEmbeddingRepository).upsert(eq(post.getId()), eq(EMBEDDING), eq(MODEL));
  }

  @Test
  void nearDuplicateIsFlaggedAndNeverPublished() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.PASSED, 0.95, List.of()));
    UUID originalId = UUID.randomUUID();
    when(duplicateDetector.findDuplicate(any(), eq(post.getId())))
        .thenReturn(Optional.of(new NearestMatch(originalId, 0.93)));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // The topic verdict was PASSED; the duplicate verdict overrides it.
    assertEquals(Post.Flag.DUPLICATE, post.getFlag());
    assertEquals(Post.Status.unpublished, post.getStatus());
    assertEquals(null, post.getPublishedAt());
  }

  @Test
  void nearDuplicateDoesNotStoreItsVector() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.PASSED, 0.95, List.of()));
    when(duplicateDetector.findDuplicate(any(), eq(post.getId())))
        .thenReturn(Optional.of(new NearestMatch(UUID.randomUUID(), 0.93)));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // Only published posts are comparison targets, so an unpublished duplicate must not become
    // one — otherwise a flagged post could start flagging unrelated content later.
    verify(postEmbeddingRepository, never()).upsert(any(), any(), anyString());
  }

  @Test
  void rejectedPostDoesNotStoreItsVector() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.REJECTED, 0.1, List.of("RELEVANCE_LOW: 0.100")));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    verify(postEmbeddingRepository, never()).upsert(any(), any(), anyString());
  }

  @Test
  void duplicateCheckRunsBeforeTheVectorIsStored() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.PASSED, 0.9, List.of()));
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
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.PASSED, 0.9, List.of()));
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
    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    verify(postRepository).save(post);
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
    Post post = post(Post.Status.created);
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
  void recipeThatNeedsReviewQueuesAnOtherwisePassingPost() throws Exception {
    Post post = passing(Post.Status.created, 0.88);
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
    assertEquals(Post.Flag.NEEDS_REVIEW, post.getFlag());
    assertEquals(Post.Status.unpublished, post.getStatus());
    assertEquals(0.88, post.getContentScore());
    assertEquals(0.31, recipe.getScore());
  }

  @Test
  void contentRejectionOutranksARecipeThatNeedsReview() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.REJECTED, 0.12, List.of("OFF_TOPIC")));
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

    // A recipe no worse than a rejection must not soften it: REJECTED is the harder verdict and
    // stays, so the post is never shown to an admin as merely "reviewable".
    assertEquals(Post.Flag.REJECTED, post.getFlag());
  }

  @Test
  void recipeThatPassesDoesNotAffectTheVerdict() throws Exception {
    Post post = passing(Post.Status.created, 0.90);
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
                Post.Flag.PASSED,
                Optional.of(0.84),
                Optional.of(EMBEDDING)));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.PASSED, post.getFlag());
    assertEquals(Post.Status.published, post.getStatus());
    assertEquals(0.90, post.getContentScore());
    assertEquals(0.84, recipe.getScore());
  }

  @Test
  void unscorableRecipeQueuesThePostWithoutFailingThePublish() throws Exception {
    Post post = passing(Post.Status.created, 0.91);
    Recipe recipe = recipe();
    approvedComponents(recipe);
    when(recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId()))
        .thenReturn(List.of(recipe));
    when(componentScorerFactory.forComponent(ComponentScorer.Component.RECIPE))
        .thenReturn(recipeScorer);
    when(recipeScorer.score(any(), anyString()))
        .thenReturn(ComponentScorer.ComponentResult.failed(ComponentScorer.Component.RECIPE));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // The member's publish succeeds and the post lands in the admin queue. The score column stays
    // null, which is how "never scored" is distinguished from "scored badly".
    assertEquals(Post.Flag.NEEDS_REVIEW, post.getFlag());
    assertNull(recipe.getScore());
    verify(postRepository).save(post);
  }

  // --- Dish and ingredient approval (BR-DISH-001): state, not score, decides ---

  @Test
  void pendingDishNameQueuesAnOtherwisePassingPost() throws Exception {
    Post post = passing(Post.Status.created, 0.92);
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
                Post.Flag.PASSED,
                Optional.of(0.88),
                Optional.of(EMBEDDING)));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    // The text passed on every scored component. The post still waits, because it introduces a
    // name no admin has approved: that is the guarantee a score cannot provide.
    assertEquals(Post.Flag.NEEDS_REVIEW, post.getFlag());
    assertEquals(Post.Status.unpublished, post.getStatus());
  }

  @Test
  void pendingIngredientNameQueuesAnOtherwisePassingPost() throws Exception {
    Post post = passing(Post.Status.created, 0.93);
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
                Post.Flag.PASSED,
                Optional.of(0.90),
                Optional.of(EMBEDDING)));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.NEEDS_REVIEW, post.getFlag());
  }

  @Test
  void rejectingContentStillWinsOverAPendingName() throws Exception {
    Post post = post(Post.Status.created);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.REJECTED, 0.11, List.of("OFF_TOPIC")));
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
                Post.Flag.PASSED,
                Optional.of(0.80),
                Optional.of(EMBEDDING)));
    when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

    adapter.deliver(message(new ContentFilterPayload(post.getId())));

    assertEquals(Post.Flag.REJECTED, post.getFlag());
  }

  private static ComponentScorer.ComponentResult needsReview(
      ComponentScorer.Component component, double score) {
    return new ComponentScorer.ComponentResult(
        component, Post.Flag.NEEDS_REVIEW, Optional.of(score), Optional.of(EMBEDDING));
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
  private Post passing(Post.Status status, double score) throws JsonProcessingException {
    Post post = post(status);
    when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
    stubFilter(new FilterVerdict(Post.Flag.PASSED, score, List.of()));
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

  private static Post post(Post.Status status) {
    return Post.builder()
        .id(UUID.randomUUID())
        .title(TITLE)
        .content(CONTENT)
        .status(status)
        .flag(Post.Flag.PENDING)
        .viewCount(0)
        .build();
  }
}
