package com.vegalife.service.admin;

import com.vegalife.dto.mapper.admin.AdminPostMapper;
import com.vegalife.dto.request.admin.PostListRequest;
import com.vegalife.dto.response.admin.AdminPostDetailResponse;
import com.vegalife.dto.response.admin.AdminPostListResponse;
import com.vegalife.dto.response.post.PostFlagDetailResponse;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.PostSpecifications;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.service.cache.AdminDashboardCacheInvalidator;
import com.vegalife.service.recipe.DishIngredientService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPostService {

  private static final Set<String> SORTABLE_PROPERTIES =
      Set.of("createdAt", "publishedAt", "updatedAt", "viewCount", "title");

  private static final String SORTABLE_PROPERTIES_MESSAGE =
      "Sort property must be one of: createdAt, publishedAt, updatedAt, viewCount, title";

  private static final String CATEGORY_REQUIRED_TO_PUBLISH =
      "At least one category is required to publish a post";

  private final PostRepository postRepository;
  private final AdminPostMapper adminPostMapper;
  private final RecipeRepository recipeRepository;
  private final RecipeIngredientRepository recipeIngredientRepository;
  private final DishRepository dishRepository;
  private final IngredientRepository ingredientRepository;
  private final DishIngredientService dishIngredientService;
  private final UserProfileRepository userProfileRepository;
  private final AdminDashboardCacheInvalidator dashboardCache;

  @Transactional(readOnly = true)
  public PageResponse<AdminPostListResponse> listPosts(PostListRequest request) {
    PostStatus status = parseStatus(request.getStatus());
    PostFlag flag = parseFlag(request.getFlag());
    UUID userId = request.getUserId();
    UUID categoryId = request.getCategoryId();
    Instant createdFrom = request.getCreatedFrom();
    Instant createdTo = request.getCreatedTo();

    if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
      throw new ValidationException("createdFrom must be before createdTo");
    }

    Pageable pageable =
        PageRequest.of(request.getPage(), request.getSize(), parseSort(request.getSort()));
    Specification<Post> spec =
        PostSpecifications.allWithFilters(
            status, flag, userId, categoryId, createdFrom, createdTo, request.getKeyword());
    Page<Post> page = postRepository.findAll(spec, pageable);

    log.debug(
        "Listed posts page={} size={} total={}",
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());

    return PageResponse.<AdminPostListResponse>builder()
        .content(enrich(page.getContent()))
        .page(page.getNumber())
        .size(page.getSize())
        .totalElements(page.getTotalElements())
        .totalPages(page.getTotalPages())
        .first(page.isFirst())
        .last(page.isLast())
        .build();
  }

  /**
   * Maps one page of posts, then fills the two fields the mapper cannot reach.
   *
   * <p>Both are resolved for the whole page rather than per row: the avatar lives on {@code
   * UserProfile} and the attached recipes are read from the owning {@code Recipe.posts} side, so
   * neither is a property of the {@code Post} being mapped.
   *
   * <p>The result is a strict superset of {@code PostListResponse} — the author's avatar, the
   * editor document and the attached recipe ids are all present, on top of the moderation fields
   * only an admin may see. The contract is asserted by an integration test that compares the two
   * live payloads, so a future member field cannot go missing here unnoticed.
   */
  private List<AdminPostListResponse> enrich(List<Post> posts) {
    List<AdminPostListResponse> responses =
        posts.stream().map(adminPostMapper::toResponse).collect(Collectors.toList());
    if (responses.isEmpty()) {
      return responses;
    }

    applyAuthorAvatars(responses);
    applyRecipeIds(responses);
    return responses;
  }

  /**
   * Resolves every author avatar in the page with a single query. A member with no profile row yet
   * (BR-PROFILE-003) has no avatar, which is null rather than an error.
   */
  private void applyAuthorAvatars(List<AdminPostListResponse> responses) {
    Set<UUID> authorIds =
        responses.stream()
            .map(AdminPostListResponse::getUserId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (authorIds.isEmpty()) {
      return;
    }
    // Built by hand rather than Collectors.toMap: avatars are nullable and toMap rejects nulls.
    Map<UUID, String> avatars = new HashMap<>();
    for (UserProfile profile : userProfileRepository.findByUserIdIn(authorIds)) {
      avatars.put(profile.getUser().getId(), profile.getAvatarUrl());
    }
    responses.forEach(response -> response.setAvatarUrl(avatars.get(response.getUserId())));
  }

  /** Groups the page's (post, recipe) pairs by post so each item lists its own attached recipes. */
  private void applyRecipeIds(List<AdminPostListResponse> responses) {
    Set<UUID> postIds =
        responses.stream()
            .map(AdminPostListResponse::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (postIds.isEmpty()) {
      return;
    }
    Map<UUID, Set<UUID>> recipeIdsByPost = new HashMap<>();
    for (RecipeRepository.PostRecipeId row : recipeRepository.findRecipeIdsByPostIds(postIds)) {
      recipeIdsByPost
          .computeIfAbsent(row.getPostId(), key -> new HashSet<>())
          .add(row.getRecipeId());
    }
    responses.forEach(
        response ->
            response.setRecipeIds(recipeIdsByPost.getOrDefault(response.getId(), new HashSet<>())));
  }

  /**
   * Admin detail view for one post (ADR-016): the post, its content score, and the state of every
   * dish and ingredient name it references.
   *
   * <p>Component information is queried here rather than stored on the post. Storing it would mean
   * a snapshot that goes stale as soon as another post approves the same name, and the only
   * question a reviewer actually asks is "is it approved now?".
   */
  @Transactional(readOnly = true)
  public AdminPostDetailResponse getPostDetail(UUID postId) {
    Post post =
        postRepository
            .findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

    Components components = componentsOf(postId);

    List<AdminPostDetailResponse.ComponentSummary> recipeSummaries = new ArrayList<>();
    for (Recipe recipe : components.recipes()) {
      recipeSummaries.add(
          AdminPostDetailResponse.ComponentSummary.builder()
              .id(recipe.getId())
              .type("RECIPE")
              .name(recipe.getName())
              .moderationStatus(recipe.getModerationStatus().name())
              .build());
    }

    List<AdminPostDetailResponse.ComponentSummary> nameSummaries = new ArrayList<>();
    for (Dish dish : components.dishes()) {
      nameSummaries.add(
          AdminPostDetailResponse.ComponentSummary.builder()
              .id(dish.getId())
              .type("DISH")
              .name(dish.getName())
              .moderationStatus(dish.getModerationStatus().name())
              .build());
    }
    for (Ingredient ingredient : components.ingredients()) {
      nameSummaries.add(
          AdminPostDetailResponse.ComponentSummary.builder()
              .id(ingredient.getId())
              .type("INGREDIENT")
              .name(ingredient.getName())
              .moderationStatus(ingredient.getModerationStatus().name())
              .build());
    }

    return AdminPostDetailResponse.builder()
        .id(post.getId())
        .userId(post.getUser().getId())
        .displayName(post.getUser().getDisplayName())
        .avatarUrl(avatarOf(post.getUser().getId()))
        .title(post.getTitle())
        .content(post.getContent())
        .rawContent(post.getRawContent())
        .featuredImageUrl(post.getFeaturedImageUrl())
        // Same extraction as the list item, reused so the two cannot drift apart.
        .categoryIds(adminPostMapper.categoryIds(post.getCategories()))
        .mediaIds(adminPostMapper.mediaIds(post.getMedia()))
        // Read from the owning Recipe.posts side, like the list route: the same set of recipes,
        // reported as ids here and as reviewable components in `recipes`.
        .recipeIds(components.recipes().stream().map(Recipe::getId).collect(Collectors.toSet()))
        .status(post.getStatus().name())
        .flag(post.getFlag() == null ? null : post.getFlag().name())
        .flagDetail(PostFlagDetailResponse.from(post.getFlagDetail()))
        .rejectionReason(post.getRejectionReason())
        .contentScore(post.getContentScore())
        .recipes(recipeSummaries)
        .names(nameSummaries)
        .email(post.getUser().getEmail())
        .viewCount(post.getViewCount())
        .publishedAt(post.getPublishedAt())
        .createdAt(post.getCreatedAt())
        .build();
  }

  /** The author's avatar, or null when they have no profile row yet (BR-PROFILE-003). */
  private String avatarOf(UUID userId) {
    return userProfileRepository.findByUserIdIn(Set.of(userId)).stream()
        .findFirst()
        .map(UserProfile::getAvatarUrl)
        .orElse(null);
  }

  /**
   * Administrator approves a post (ADR-016): publishes it and approves every component of that
   * post.
   *
   * <p>Component approval is deliberately post-scoped. A dish or ingredient has no owner, so
   * approving "phở chay" because this post used it also makes it available elsewhere — that is
   * intended: the decision is about the name being real food, not about this particular post. Names
   * the post *references* that were already {@link ModerationStatus#APPROVED} are untouched, and so
   * are {@link ModerationStatus#REJECTED} ones: approving a post must never resurrect a name an
   * admin previously refused.
   *
   * <p>Refuses a drafted post (nobody submitted it) and a post with no category (BR-CONTENT-003),
   * both carried over from the endpoint this replaces.
   *
   * <p>The post's flag is left exactly as it was (ADR-025): approving a {@code FILTER_FAILED} post
   * publishes it but does not relabel the verdict, so the row still says the decision was a human
   * override rather than a filter pass.
   */
  @Transactional
  public AdminPostDetailResponse approvePost(UUID adminId, UUID postId) {
    Post post = requireModeratablePost(postId);

    if (post.getCategories().isEmpty()) {
      throw new ValidationException(CATEGORY_REQUIRED_TO_PUBLISH);
    }

    post.setStatus(PostStatus.published);
    post.setPublishedAt(post.getPublishedAt() == null ? Instant.now() : post.getPublishedAt());
    // Current state, not history: once approved again, a stale reason would describe a decision
    // that no longer holds.
    post.setRejectionReason(null);
    // The flag is deliberately kept: published + FILTER_FAILED says a human overrode the filter,
    // which is a different fact from published + FILTER_PASSED. Clearing it here would erase the
    // only place that distinction is stored (ADR-025).
    postRepository.saveAndFlush(post);

    approveComponents(postId);

    // published_at moved and the post left the pending queue: both the summary and the
    // published-posts series are now wrong.
    dashboardCache.all();

    log.info("Admin {} approved post {}", adminId, post.getId());

    return getPostDetail(postId);
  }

  /**
   * Administrator rejects a post (ADR-016): unpublishes it and refuses the components it
   * introduced.
   *
   * <p>Only rows still {@link ModerationStatus#PENDING} are rejected. An {@link
   * ModerationStatus#APPROVED} name stays approved even if this post is refused — it was reviewed
   * on its own merits, possibly by an earlier post, and one bad post must not knock out a name that
   * other members' recipes and menus depend on. Nothing is ever deleted: existing references keep
   * resolving, and a rejected name is blocked from re-creation instead.
   */
  @Transactional
  public AdminPostDetailResponse rejectPost(UUID adminId, UUID postId, String reason) {
    Post post = requireModeratablePost(postId);

    post.setStatus(PostStatus.unpublished);
    post.setPublishedAt(null);
    post.setRejectionReason(reason);
    // The blocker stays: together with rejection_reason it is the full decision record — what the
    // machine saw and what the admin decided on top of it (ADR-025).
    postRepository.saveAndFlush(post);

    rejectPendingComponents(postId, reason);

    // published_at was cleared and the post left the pending queue.
    dashboardCache.all();

    log.info("Admin {} rejected post {}", adminId, post.getId());

    return getPostDetail(postId);
  }

  /**
   * Loads a post that may be approved or rejected.
   *
   * <p>A {@code drafted} post is refused: nobody submitted it, so there is nothing to moderate —
   * approving it would publish content its author never asked to publish (ADR-023). A {@code
   * pending} post is the normal case: a verdict was recorded and a human must decide. A {@code
   * published} post stays moderatable because approve/reject is also how the dish and ingredient
   * names it introduced are decided (ADR-016). A {@code processing} post is allowed too — an admin
   * may decide before the filter finishes, and the late verdict is then discarded.
   */
  private Post requireModeratablePost(UUID postId) {
    Post post =
        postRepository
            .findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    if (post.getStatus() == PostStatus.drafted) {
      throw new ValidationException(
          "A drafted post has not been submitted for publication and cannot be moderated");
    }
    return post;
  }

  /**
   * Approve every component of the post, leaving REJECTED names refused, then record each
   * now-approved recipe's ingredient list as a suggestion for its dish (BR-DISH-003).
   *
   * <p>The seeding half is what keeps the two approval paths from diverging: a recipe approved via
   * its post contributes exactly what one approved via the recipe queue does.
   */
  private void approveComponents(UUID postId) {
    Components components = componentsOf(postId);

    for (Recipe recipe : components.recipes()) {
      if (isAwaitingReview(recipe.getModerationStatus())) {
        recipe.setModerationStatus(ModerationStatus.APPROVED);
        recipeRepository.save(recipe);
      }
    }
    for (Dish dish : components.dishes()) {
      if (isAwaitingReview(dish.getModerationStatus())) {
        dish.setModerationStatus(ModerationStatus.APPROVED);
        dishRepository.save(dish);
      }
    }
    for (Ingredient ingredient : components.ingredients()) {
      if (isAwaitingReview(ingredient.getModerationStatus())) {
        ingredient.setModerationStatus(ModerationStatus.APPROVED);
        ingredientRepository.save(ingredient);
      }
    }

    for (Recipe recipe : components.recipes()) {
      if (recipe.getModerationStatus() == ModerationStatus.APPROVED) {
        dishIngredientService.seedFromApprovedRecipe(recipe);
      }
    }
  }

  /** Reject every component of the post that is still awaiting review. */
  private void rejectPendingComponents(UUID postId, String reason) {
    Components components = componentsOf(postId);

    for (Recipe recipe : components.recipes()) {
      if (isAwaitingReview(recipe.getModerationStatus())) {
        recipe.setModerationStatus(ModerationStatus.REJECTED);
        // The reason is recorded on the recipe as well as the post: the recipe's author reads its
        // own rejection from its own row, and the decision that refused it is the same decision.
        recipe.setRejectionReason(reason);
        recipeRepository.save(recipe);
      }
    }
    for (Dish dish : components.dishes()) {
      if (isAwaitingReview(dish.getModerationStatus())) {
        dish.setModerationStatus(ModerationStatus.REJECTED);
        dishRepository.save(dish);
      }
    }
    for (Ingredient ingredient : components.ingredients()) {
      if (isAwaitingReview(ingredient.getModerationStatus())) {
        ingredient.setModerationStatus(ModerationStatus.REJECTED);
        ingredientRepository.save(ingredient);
      }
    }
  }

  /**
   * Every component of one post: its recipes, and the distinct dishes and ingredients they
   * reference.
   *
   * <p>Shared by approve, reject and the detail view so the three can never disagree about which
   * components a post has. A name referenced twice is returned once — it is one decision.
   */
  private Components componentsOf(UUID postId) {
    List<Recipe> recipes = recipeRepository.findByPosts_IdAndDeletedAtIsNull(postId);
    List<Dish> dishes = new ArrayList<>();
    List<Ingredient> ingredients = new ArrayList<>();
    Set<UUID> seenNames = new HashSet<>();

    for (Recipe recipe : recipes) {
      Dish dish = recipe.getDish();
      if (dish != null && seenNames.add(dish.getId())) {
        dishes.add(dish);
      }

      for (RecipeIngredient line : recipeIngredientRepository.findByRecipeId(recipe.getId())) {
        Ingredient ingredient = resolveIngredient(line);
        if (ingredient != null && seenNames.add(ingredient.getId())) {
          ingredients.add(ingredient);
        }
      }
    }
    return new Components(recipes, dishes, ingredients);
  }

  /**
   * Reads the ingredient behind a recipe line.
   *
   * <p>Falls back to a repository lookup because {@code RecipeIngredient.ingredient} is mapped
   * {@code insertable = false}: inside the transaction that created the line, the association can
   * still be null, and a recipe created and approved in one request must not silently skip its
   * ingredients.
   */
  private Ingredient resolveIngredient(RecipeIngredient line) {
    if (line.getIngredient() != null) {
      return line.getIngredient();
    }
    return ingredientRepository.findById(line.getIngredientId()).orElse(null);
  }

  /**
   * One post's reviewed components.
   *
   * @param recipes attached recipes
   * @param dishes distinct non-null dishes they reference
   * @param ingredients distinct non-null ingredients they reference
   */
  private record Components(
      List<Recipe> recipes, List<Dish> dishes, List<Ingredient> ingredients) {}

  /**
   * Whether a component may still be decided: only while it is {@code PENDING}.
   *
   * <p>A REVIEWED name survives, in both directions. An {@code APPROVED} name was reviewed on its
   * own merits and an approving post must not re-open it; a {@code REJECTED} name is a permanent
   * block (BR-DISH-001), so approving the post that mentions it must not undo either.
   */
  private boolean isAwaitingReview(ModerationStatus current) {
    return current == ModerationStatus.PENDING;
  }

  private PostStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return PostStatus.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new ValidationException(
          "Status must be one of: drafted, processing, pending, published, unpublished");
    }
  }

  private PostFlag parseFlag(String flag) {
    if (flag == null || flag.isBlank() || "all".equalsIgnoreCase(flag)) {
      return null;
    }
    try {
      return PostFlag.valueOf(flag);
    } catch (IllegalArgumentException ex) {
      throw new ValidationException(
          "Flag must be one of: all, FILTER_PASSED, FILTER_FAILED, SIMILAR_CONTENT, FILTER_SKIPPED");
    }
  }

  private Sort parseSort(String sort) {
    String[] parts = sort.split(",");
    if (parts.length == 0 || parts.length > 2) {
      throw new ValidationException("Sort must be in the form property,asc|desc");
    }
    String property = parts[0].trim();
    if (property.isBlank()) {
      throw new ValidationException("Sort property must not be blank");
    }
    if (!SORTABLE_PROPERTIES.contains(property)) {
      throw new ValidationException(SORTABLE_PROPERTIES_MESSAGE);
    }
    Sort.Direction direction =
        parts.length == 2 && parts[1].trim().equalsIgnoreCase("asc")
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    return Sort.by(direction, property);
  }
}
