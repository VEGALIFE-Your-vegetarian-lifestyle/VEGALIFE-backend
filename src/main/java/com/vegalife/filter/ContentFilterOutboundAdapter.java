package com.vegalife.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.filter.ContentFilterService.FilterResult;
import com.vegalife.model.outbound.OutboundChannel;
import com.vegalife.model.outbound.OutboundMessage;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.ModerationStatus;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.repository.post.PostEmbeddingRepository;
import com.vegalife.repository.post.PostEmbeddingRepository.NearestMatch;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.service.outbound.OutboundChannelAdapter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CONTENT_FILTER channel adapter (ADR-005): loads the queued post, runs the filter pipeline (FR-005
 * embedding scoring, FR-006 bands) and applies the verdict. {@code PASSED} publishes the post —
 * every queued message was enqueued by an explicit publish request (BR-FILTER-005); {@code
 * REJECTED} / {@code NEEDS_REVIEW} flag the post, move it to {@code unpublished} and WARN (FR-009).
 * Any failure propagates so the queue retries; permanent failure is the existing FAILED@24h
 * handling of the outbound job. No {@code prev_status} tracking.
 *
 * <p>Duplicate detection (issue #156, ADR-015) runs before the verdict is applied and before the
 * post's vector is stored. Storing first would make every post match itself at similarity 1.0. This
 * ordering has a consequence worth knowing: a flagged duplicate never publishes, so it never
 * becomes a comparison target itself — chained duplicates are therefore missed by design.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentFilterOutboundAdapter implements OutboundChannelAdapter {

  private final PostRepository postRepository;
  private final ContentFilterService contentFilterService;
  private final RecipeRepository recipeRepository;
  private final RecipeIngredientRepository recipeIngredientRepository;
  private final PostEmbeddingRepository postEmbeddingRepository;
  private final DuplicateDetector duplicateDetector;
  private final ActiveEmbeddingModel activeEmbeddingModel;
  private final ObjectMapper objectMapper;
  private final EmbeddingComponentScorer.Factory componentScorerFactory;

  @Override
  public OutboundChannel channel() {
    return OutboundChannel.CONTENT_FILTER;
  }

  @Override
  @Transactional
  public void deliver(OutboundMessage message) {
    ContentFilterPayload payload = parse(message);
    Post post =
        postRepository
            .findById(payload.postId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Outbound message "
                            + message.getId()
                            + " references missing post "
                            + payload.postId()));
    FilterResult result = contentFilterService.filter(filterBody(post));
    FilterVerdict verdict = result.verdict();
    post.setContentScore(verdict.score());

    // Component-level verification (ADR-016). The content verdict above stays authoritative for
    // duplicate detection and for the REJECT band; the recipe body and the referenced names are
    // additional, independent checks. A component that needs review escalates the post, but never
    // downgrades a REJECTED verdict.
    ComponentOutcome recipe = scoreAttachedRecipes(post);
    if (recipe.needsReview() && verdict.flag() != Post.Flag.REJECTED) {
      verdict = escalateToReview(verdict, recipe);
    }

    // Dish and ingredient names carry no score: an unreviewed name is forwarded on its state
    // alone, because a member introduced it and no admin has approved it yet (BR-DISH-001).
    List<String> pendingNames = pendingComponentNames(post);
    if (!pendingNames.isEmpty() && verdict.flag() != Post.Flag.REJECTED) {
      verdict = escalateToReview(verdict, new ComponentOutcome(true, pendingNames));
    }

    // The vector was computed inside the filter call; returning it keeps one embedding per
    // publish. Only stored when the post was not rejected: a rejected post is never a comparison
    // target, so its vector has no reader.
    float[] embedding = result.embedding();

    Optional<NearestMatch> duplicate =
        verdict.flag() == Post.Flag.REJECTED
            ? Optional.empty()
            : duplicateDetector.findDuplicate(embedding, post.getId());

    if (duplicate.isPresent()) {
      NearestMatch match = duplicate.get();
      applyDuplicate(post, match);
      // Deliberately not stored: this post is unpublished, and only published posts are targets.
    } else {
      apply(post, verdict);
      if (verdict.flag() != Post.Flag.REJECTED) {
        storeEmbedding(post.getId(), embedding);
      }
    }
    postRepository.save(post);
  }

  /**
   * Records the vector so future posts can be compared against this one.
   *
   * <p>The failure is logged and swallowed on purpose. Storing the vector is what makes
   * <em>later</em> duplicate detection work; it is not what makes <em>this</em> post publish.
   * Failing here would turn a missing pgvector extension — a deployment prerequisite, not a code
   * error — into every member's post being stuck in the queue. The blast radius stays where it
   * belongs: detection is silently less effective and the WARN says so.
   */
  private void storeEmbedding(UUID postId, float[] embedding) {
    try {
      postEmbeddingRepository.upsert(postId, embedding, activeEmbeddingModel.name());
    } catch (DataAccessException e) {
      log.warn(
          "Could not store the embedding for post {}; duplicate detection will not see it: {}",
          postId,
          e.getMessage());
    }
  }

  private static void apply(Post post, FilterVerdict verdict) {
    post.setFlag(verdict.flag());
    switch (verdict.flag()) {
      case PASSED -> {
        post.setStatus(Post.Status.published);
        if (post.getPublishedAt() == null) {
          post.setPublishedAt(Instant.now());
        }
        log.info("Content filter passed post {} (score {})", post.getId(), verdict.score());
      }
      case REJECTED -> {
        post.setStatus(Post.Status.unpublished);
        log.warn("Content filter rejected post {}: {}", post.getId(), verdict.reasons());
      }
      case NEEDS_REVIEW -> {
        post.setStatus(Post.Status.unpublished);
        log.warn(
            "Content filter needs review for post {} (score between bands: {})",
            post.getId(),
            verdict.score());
      }
      default -> throw new IllegalStateException("Unexpected filter verdict: " + verdict.flag());
    }
  }

  /**
   * Routes a near-duplicate to admin review (ADR-015). Both ids and the similarity are logged so an
   * admin can see which post it resembles — without them the flag is unactionable.
   */
  private static void applyDuplicate(Post post, NearestMatch match) {
    post.setFlag(Post.Flag.DUPLICATE);
    post.setStatus(Post.Status.unpublished);
    log.warn(
        "Content filter flagged post {} as a near-duplicate of published post {} (similarity {})",
        post.getId(),
        match.postId(),
        match.similarity());
  }

  private ContentFilterPayload parse(OutboundMessage message) {
    String json = message.getPayload();
    if (json == null || json.isBlank()) {
      throw new IllegalStateException(
          "Outbound message " + message.getId() + " has no payload to deliver");
    }
    try {
      ContentFilterPayload payload = objectMapper.readValue(json, ContentFilterPayload.class);
      if (payload.postId() == null) {
        throw new IllegalStateException(
            "Outbound message " + message.getId() + " payload is missing a postId");
      }
      return payload;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Outbound message " + message.getId() + " has an unreadable payload", e);
    }
  }

  private static String filterBody(Post post) {
    return post.getTitle() + "\n" + post.getContent();
  }

  /**
   * Scores every recipe attached to the post and stores each score. A post may attach more than one
   * recipe ({@code post_recipe} is many-to-many); the post needs review if <em>any</em> of them
   * does.
   *
   * <p>No recipe attached means no recipe rows and no embedding call — absence is meaningful, not
   * an error.
   */
  private ComponentOutcome scoreAttachedRecipes(Post post) {
    List<Recipe> recipes = recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId());
    if (recipes.isEmpty()) {
      return ComponentOutcome.none();
    }
    ComponentScorer scorer = componentScorerFactory.forComponent(ComponentScorer.Component.RECIPE);
    boolean needsReview = false;
    List<String> reasons = new ArrayList<>();
    for (Recipe recipe : recipes) {
      ComponentScorer.ComponentResult result =
          scorer.score(ComponentScorer.Component.RECIPE, recipeBody(recipe));
      recipe.setScore(result.score().orElse(null));
      if (result.needsReview()) {
        needsReview = true;
        reasons.add(
            result
                .score()
                .map(score -> formatReason("RECIPE_RELEVANCE_REVIEW", score))
                .orElse("RECIPE_NOT_SCORED"));
      }
    }
    return new ComponentOutcome(needsReview, reasons);
  }

  /**
   * Names the post references that no admin has approved yet, as log-ready reason codes.
   *
   * <p>Dishes and ingredients are global and unowned, so this is a state check, not a score: a
   * {@link ModerationStatus#PENDING} dish name or ingredient name means the post must wait for the
   * same human review a low score would require. Rejected names cannot be introduced in the first
   * place (BR-DISH-001), so only pending ones appear here.
   */
  private List<String> pendingComponentNames(Post post) {
    List<Recipe> recipes = recipeRepository.findByPosts_IdAndDeletedAtIsNull(post.getId());
    if (recipes.isEmpty()) {
      return List.of();
    }

    List<String> reasons = new ArrayList<>();
    for (Recipe recipe : recipes) {
      Dish dish = recipe.getDish();
      if (dish != null && dish.getModerationStatus() == ModerationStatus.PENDING) {
        reasons.add("DISH_PENDING_APPROVAL: " + dish.getName());
      }
      for (RecipeIngredient line : recipeIngredientRepository.findByRecipeId(recipe.getId())) {
        Ingredient ingredient = line.getIngredient();
        if (ingredient != null && ingredient.getModerationStatus() == ModerationStatus.PENDING) {
          reasons.add("INGREDIENT_PENDING_APPROVAL: " + ingredient.getName());
        }
      }
    }
    return reasons;
  }

  /**
   * The recipe body handed to the model: name, description and instructions in one string. Scored
   * as a single unit rather than per field, so one weak description cannot outvote a long set of
   * clean instructions (ADR-016).
   */
  static String recipeBody(Recipe recipe) {
    StringBuilder body = new StringBuilder(recipe.getName());
    if (recipe.getDescription() != null && !recipe.getDescription().isBlank()) {
      body.append('\n').append(recipe.getDescription());
    }
    body.append('\n').append(recipe.getInstructions());
    return body.toString();
  }

  /** Keeps the original reasons and adds the component's, so the log explains every check. */
  private static FilterVerdict escalateToReview(FilterVerdict verdict, ComponentOutcome component) {
    List<String> reasons = new ArrayList<>(verdict.reasons());
    reasons.addAll(component.reasons());
    return new FilterVerdict(Post.Flag.NEEDS_REVIEW, verdict.score(), reasons);
  }

  private static String formatReason(String code, double score) {
    return String.format(Locale.ROOT, "%s: %.3f", code, score);
  }

  /**
   * Outcome of one component check.
   *
   * @param needsReview true when the component scored below its threshold or could not be scored
   * @param reasons reason codes for the log, empty when nothing needs review
   */
  private record ComponentOutcome(boolean needsReview, List<String> reasons) {
    static ComponentOutcome none() {
      return new ComponentOutcome(false, List.of());
    }
  }
}
