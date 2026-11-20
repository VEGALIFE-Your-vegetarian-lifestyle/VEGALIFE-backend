package com.vegalife.service.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegalife.enums.outbound.OutboundChannel;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.recipe.ModerationStatus;
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
import com.vegalife.service.cache.AdminDashboardCacheInvalidator;
import com.vegalife.service.filter.ContentFilterService.FilterResult;
import com.vegalife.service.outbound.OutboundChannelAdapter;
import com.vegalife.shared.exception.EmbeddingClientException;
import com.vegalife.shared.exception.RetryableDeliveryException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CONTENT_FILTER channel adapter (ADR-005): loads the queued post, runs the filter pipeline (FR-005
 * embedding scoring, FR-006 bands) and applies the outcome under ADR-023.
 *
 * <p>A {@code PASS} publishes the post and records {@code FILTER_PASSED}: publication and the
 * verdict are two facts, and the row keeps both (ADR-025). A {@code FAIL} or {@code UNCERTAIN}
 * leaves it at {@code pending} with {@code flag = FILTER_FAILED}; the post never leaves the public
 * feed on the filter's authority alone — only an admin can unpublish. A duplicate leaves it at
 * {@code pending} with {@code flag = SIMILAR_CONTENT} and a JSON snapshot of the matched post, its
 * similarity and the threshold applied in {@code flag_detail} (ADR-024). Every one of those states
 * is visible on the admin review queue ({@code status=pending}), which is what replaces the old
 * terminal {@code unpublished} the filter used to write itself.
 *
 * <p>The filter only reads text (ADR-026), so a pass does not publish a post that carries media: a
 * non-blank featured image or any attached media is unreviewed content the run never saw, and the
 * post waits at {@code pending} with {@code FILTER_PASSED} for a human (ADR-029). The score and the
 * snapshot are recorded exactly as for an ordinary pass.
 *
 * <p>A transient failure is rethrown as retryable so the queue waits the provider out; a permanent
 * rejection cannot be retried into success, so the post is settled at {@code pending} with {@code
 * FILTER_SKIPPED} and the cause written to the application log (ADR-020).
 *
 * <p>The verdict is applied only while the post is still {@code processing} and not soft-deleted. A
 * post withdrawn or deleted while its message was in flight keeps its new state; the late result is
 * discarded rather than republishing content its author already took down.
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
  private final FilterProperties filterProperties;
  private final AdminDashboardCacheInvalidator dashboardCache;

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

    // The author may have withdrawn or deleted the post after the message was queued. Applying the
    // verdict would publish content they already took down, so the run is discarded - before the
    // embedding call, which is the expensive part.
    if (post.getDeletedAt() != null || post.getStatus() != PostStatus.processing) {
      log.info(
          "Discarding content-filter result for post {}: it is now {} (deleted={})",
          post.getId(),
          post.getStatus(),
          post.getDeletedAt() != null);
      return;
    }

    FilterResult result;
    try {
      result = contentFilterService.filter(filterBody(post));
    } catch (EmbeddingClientException e) {
      if (!e.isTerminal()) {
        throw new RetryableDeliveryException(
            "Content filter could not embed post " + post.getId() + " (" + e.getMessage() + ")",
            e.getRetryAfter(),
            e);
      }
      // A permanent rejection cannot be retried into success: settle the post now and let the
      // message complete, because its purpose - "get this post judged" - is discharged by the
      // review queue. Marking it FAILED would only misreport it (ADR-020).
      applyFilterUnavailable(post, e);
      postRepository.save(post);
      // Held for manual review: pendingPosts and filterSkippedPosts rose. The post was never
      // published, so no series moved.
      dashboardCache.summary();
      log.error(
          "Content filter could not run for post {} (outbound message {}): {}. The post is queued"
              + " for manual review instead of being retried.",
          post.getId(),
          message.getId(),
          e.getMessage());
      return;
    }

    FilterVerdict verdict = result.verdict();
    post.setContentScore(verdict.score());

    // The post body is the only thing scored (ADR-026). The referenced dish and ingredient names
    // are
    // an additional, independent state check: an unreviewed name is forwarded on its state alone,
    // because a member introduced it and no admin has approved it yet (BR-DISH-001). Either check
    // escalates the post, but never downgrades a refusal.
    List<String> pendingNames = pendingComponentNames(post);
    if (!pendingNames.isEmpty() && verdict.outcome() != FilterOutcome.FAIL) {
      verdict = escalateToReview(verdict, new ComponentOutcome(true, pendingNames));
    }

    // The vector was computed inside the filter call; returning it keeps one embedding per run.
    float[] embedding = result.embedding();

    Optional<NearestMatch> duplicate =
        verdict.outcome() == FilterOutcome.FAIL
            ? Optional.empty()
            : duplicateDetector.findDuplicate(embedding, post.getId());

    if (duplicate.isPresent()) {
      applyDuplicate(post, duplicate.get());
    } else {
      apply(post, verdict);
    }

    // Stored for every outcome, including refusals and duplicates: the lookup only compares
    // published posts, and an admin may still publish this one later - at which point it must be
    // able to act as a comparison target. Duplicate detection runs before this call, so a post can
    // never match itself.
    storeEmbedding(post.getId(), embedding);
    postRepository.save(post);
    // This is the dominant publisher: a PASS publishes the post, while a FAIL, UNCERTAIN, component
    // escalation or duplicate holds it. Either way the queue count moved, and a publication also
    // moved the published-posts series.
    dashboardCache.all();
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

  /**
   * Applies the "filter unavailable" outcome: never visible, never attributed to the model's
   * judgement.
   *
   * <p>The post is held with {@code FILTER_SKIPPED} rather than {@code FILTER_FAILED} or a new flag
   * value, so an operator can tell "the filter could not run" from "the filter judged this" without
   * widening the blocker vocabulary (ADR-020, ADR-023). The row carries only {@code cause =
   * UNAVAILABLE} (ADR-024); the classified cause goes to the application log.
   */
  private void applyFilterUnavailable(Post post, EmbeddingClientException e) {
    block(post, PostFlag.FILTER_SKIPPED, PostFlagDetail.skipped(PostFlagDetail.CAUSE_UNAVAILABLE));
    log.warn("Content filter unavailable for post {}: {}", post.getId(), describeFailure(e));
  }

  /** Stable, log-ready cause: reason code plus upstream status, never the provider's full body. */
  static String describeFailure(EmbeddingClientException e) {
    String status = e.getUpstreamStatus() == null ? "no response" : "HTTP " + e.getUpstreamStatus();
    String code = e.getProviderCode() == null ? "" : " " + e.getProviderCode();
    return "FILTER_UNAVAILABLE: " + e.getReason() + " (" + status + ")" + code;
  }

  /**
   * A message that was abandoned before delivery (permanent rejection or a retry that could not fit
   * inside max age) still owes the post a terminal state — the member's post must not stay {@code
   * processing} with no reason and invisible forever (ADR-020, FR-011).
   */
  @Override
  @Transactional
  public void onDeliveryAbandoned(OutboundMessage message, RuntimeException cause) {
    ContentFilterPayload payload;
    try {
      payload = parse(message);
    } catch (RuntimeException e) {
      // The payload is already unreadable; there is no post to settle.
      return;
    }
    Optional<Post> found = postRepository.findById(payload.postId());
    if (found.isEmpty()) {
      return;
    }
    Post post = found.get();
    if (post.getStatus() != PostStatus.processing || post.getFlag() != null) {
      // Already settled by an earlier attempt, withdrawn, or deleted; never overwrite a real state.
      return;
    }
    String reason =
        cause instanceof EmbeddingClientException embedding
            ? describeFailure(embedding)
            : "FILTER_UNAVAILABLE: " + cause.getMessage();
    block(post, PostFlag.FILTER_SKIPPED, PostFlagDetail.skipped(PostFlagDetail.CAUSE_UNAVAILABLE));
    postRepository.save(post);
    // Settled out of processing into the queue: pendingPosts and filterSkippedPosts rose.
    dashboardCache.summary();
    log.error(
        "Outbound message {} was abandoned; post {} is held as FILTER_SKIPPED: {}",
        message.getId(),
        post.getId(),
        reason);
  }

  /**
   * Applies one outcome: a pass publishes and records the verdict, unless the post carries media —
   * the filter only judged the text, so the media still needs a human. Anything else holds the post
   * for an admin.
   */
  private static void apply(Post post, FilterVerdict verdict) {
    switch (verdict.outcome()) {
      case PASS -> {
        post.setFlag(PostFlag.FILTER_PASSED);
        post.setFlagDetail(PostFlagDetail.passed(verdict));
        if (carriesMedia(post)) {
          post.setStatus(PostStatus.pending);
          log.info(
              "Content filter passed the text of post {} (score {}) but it carries media; held for"
                  + " admin review",
              post.getId(),
              verdict.score());
          return;
        }
        post.setStatus(PostStatus.published);
        if (post.getPublishedAt() == null) {
          post.setPublishedAt(Instant.now());
        }
        log.info("Content filter passed post {} (score {})", post.getId(), verdict.score());
      }
      case FAIL, UNCERTAIN -> {
        block(post, PostFlag.FILTER_FAILED, PostFlagDetail.filterFailed(verdict));
        log.warn(
            "Content filter did not pass post {} (outcome {}, score {}): {}",
            post.getId(),
            verdict.outcome(),
            verdict.score(),
            verdict.reasons());
      }
    }
  }

  /**
   * Whether the post carries content the filter never saw (ADR-029): a featured image, or any
   * attached media that has not been soft-deleted. Soft-deleted rows are ignored because the public
   * post shape already hides them ({@code PostMapper.mediaIds}), so they cannot reach a reader.
   */
  private static boolean carriesMedia(Post post) {
    if (post.getFeaturedImageUrl() != null && !post.getFeaturedImageUrl().isBlank()) {
      return true;
    }
    return post.getMedia().stream().anyMatch(media -> media.getDeletedAt() == null);
  }

  /**
   * Routes a near-duplicate to admin review (ADR-015). The matched post, the similarity and the
   * threshold applied are all snapshotted: the ids alone made the flag actionable but not arguable,
   * because a reviewer could not tell a 0.86 coincidence from a 0.99 repost.
   */
  private void applyDuplicate(Post post, NearestMatch match) {
    block(
        post,
        PostFlag.SIMILAR_CONTENT,
        PostFlagDetail.similarContent(
            match.postId(), match.similarity(), filterProperties.getDuplicateThreshold()));
    log.warn(
        "Content filter flagged post {} as a near-duplicate of published post {} (similarity {})",
        post.getId(),
        match.postId(),
        match.similarity());
  }

  /** Records the blocker and keeps the post in the review queue. */
  private static void block(Post post, PostFlag flag, JsonNode detail) {
    post.setStatus(PostStatus.pending);
    post.setFlag(flag);
    post.setFlagDetail(detail);
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

  /** Keeps the original reasons and adds the component's, so the log explains every check. */
  private static FilterVerdict escalateToReview(FilterVerdict verdict, ComponentOutcome component) {
    List<String> reasons = new ArrayList<>(verdict.reasons());
    reasons.addAll(component.reasons());
    // The threshold belongs to the content band that was applied, so it survives an escalation:
    // a reviewer can compare score with threshold to see that the content itself cleared the bar.
    return new FilterVerdict(
        FilterOutcome.UNCERTAIN, verdict.score(), verdict.threshold(), reasons);
  }

  /**
   * Outcome of the name check.
   *
   * @param needsReview true when a referenced dish or ingredient name is awaiting approval
   * @param reasons reason codes for the log, empty when nothing needs review
   */
  private record ComponentOutcome(boolean needsReview, List<String> reasons) {}
}
