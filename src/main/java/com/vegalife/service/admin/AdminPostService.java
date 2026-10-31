package com.vegalife.service.admin;

import com.vegalife.dto.mapper.admin.AdminPostMapper;
import com.vegalife.dto.request.admin.PostListRequest;
import com.vegalife.dto.response.admin.AdminPostDetailResponse;
import com.vegalife.dto.response.admin.AdminPostListResponse;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.PostSpecifications;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
  private final ModerationLogRepository moderationLogRepository;
  private final AdminPostMapper adminPostMapper;
  private final RecipeRepository recipeRepository;
  private final RecipeIngredientRepository recipeIngredientRepository;
  private final DishRepository dishRepository;
  private final IngredientRepository ingredientRepository;

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
        PostSpecifications.allWithFilters(status, flag, userId, categoryId, createdFrom, createdTo);
    Page<Post> page = postRepository.findAll(spec, pageable);

    log.debug(
        "Listed posts page={} size={} total={}",
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());

    Page<AdminPostListResponse> mapped = page.map(adminPostMapper::toResponse);
    return PageResponse.from(mapped);
  }

  /**
   * Admin detail view for one post (ADR-016): the post, its scores, and the state of every dish and
   * ingredient name it references.
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
              .score(recipe.getScore())
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
        .title(post.getTitle())
        .content(post.getContent())
        .status(post.getStatus().name())
        .flag(post.getFlag() == null ? null : post.getFlag().name())
        .contentScore(post.getContentScore())
        .recipes(recipeSummaries)
        .names(nameSummaries)
        .userId(post.getUser().getId())
        .username(post.getUser().getUsername())
        .email(post.getUser().getEmail())
        .build();
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
   * <p>Refuses a hidden post (it is moderated through the visibility endpoint) and a post with no
   * category (BR-CONTENT-003), both carried over from the endpoint this replaces.
   */
  @Transactional
  public AdminPostDetailResponse approvePost(UUID adminId, UUID postId, String reason) {
    Post post = requireModeratablePost(postId);

    if (post.getCategories().isEmpty()) {
      throw new ValidationException(CATEGORY_REQUIRED_TO_PUBLISH);
    }

    post.setStatus(PostStatus.published);
    post.setPublishedAt(post.getPublishedAt() == null ? Instant.now() : post.getPublishedAt());
    postRepository.saveAndFlush(post);

    approveComponents(postId, adminId, reason);

    moderationLogRepository.save(
        ModerationLog.builder()
            .actorId(adminId)
            .action("APPROVE_POST")
            .targetType("POST")
            .targetId(post.getId())
            .reason(reason)
            .build());

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
    postRepository.saveAndFlush(post);

    rejectPendingComponents(postId, adminId, reason);

    moderationLogRepository.save(
        ModerationLog.builder()
            .actorId(adminId)
            .action("REJECT_POST")
            .targetType("POST")
            .targetId(post.getId())
            .reason(reason)
            .build());

    log.info("Admin {} rejected post {}", adminId, post.getId());

    return getPostDetail(postId);
  }

  /**
   * Loads a post that may be approved or rejected.
   *
   * <p>The hidden check is carried over from the endpoint this replaces: a hidden post is removed
   * from public view through a different, admin-only path, and approving it here would silently
   * override that decision.
   */
  private Post requireModeratablePost(UUID postId) {
    Post post =
        postRepository
            .findByIdAndDeletedAtIsNull(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    if (post.getStatus() == PostStatus.hidden) {
      throw new ValidationException(
          "A hidden post is moderated through the visibility endpoint, not approve/reject");
    }
    return post;
  }

  /** Approve every component of the post, leaving REJECTED names refused. */
  private void approveComponents(UUID postId, UUID adminId, String reason) {
    Components components = componentsOf(postId);

    for (Recipe recipe : components.recipes()) {
      if (approveIfPending(
          recipe.getModerationStatus(), "RECIPE", recipe.getId(), adminId, reason)) {
        recipe.setModerationStatus(ModerationStatus.APPROVED);
        recipeRepository.save(recipe);
      }
    }
    for (Dish dish : components.dishes()) {
      if (approveIfPending(dish.getModerationStatus(), "DISH", dish.getId(), adminId, reason)) {
        dish.setModerationStatus(ModerationStatus.APPROVED);
        dishRepository.save(dish);
      }
    }
    for (Ingredient ingredient : components.ingredients()) {
      if (approveIfPending(
          ingredient.getModerationStatus(), "INGREDIENT", ingredient.getId(), adminId, reason)) {
        ingredient.setModerationStatus(ModerationStatus.APPROVED);
        ingredientRepository.save(ingredient);
      }
    }
  }

  /** Reject every component of the post that is still awaiting review. */
  private void rejectPendingComponents(UUID postId, UUID adminId, String reason) {
    Components components = componentsOf(postId);

    for (Recipe recipe : components.recipes()) {
      if (rejectIfPending(
          recipe.getModerationStatus(), "RECIPE", recipe.getId(), adminId, reason)) {
        recipe.setModerationStatus(ModerationStatus.REJECTED);
        recipeRepository.save(recipe);
      }
    }
    for (Dish dish : components.dishes()) {
      if (rejectIfPending(dish.getModerationStatus(), "DISH", dish.getId(), adminId, reason)) {
        dish.setModerationStatus(ModerationStatus.REJECTED);
        dishRepository.save(dish);
      }
    }
    for (Ingredient ingredient : components.ingredients()) {
      if (rejectIfPending(
          ingredient.getModerationStatus(), "INGREDIENT", ingredient.getId(), adminId, reason)) {
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
   * Whether a component may be approved.
   *
   * <p>A REJECTED name is a permanent block (BR-DISH-001), so approving the post that mentions it
   * must not undo that. An already-APPROVED name needs no work and no log entry.
   */
  private boolean approveIfPending(
      ModerationStatus current, String type, UUID id, UUID adminId, String reason) {
    if (current != ModerationStatus.PENDING) {
      return false;
    }
    logComponentDecision(adminId, "APPROVE_COMPONENT", type, id, reason);
    return true;
  }

  /**
   * Whether a component may be rejected: only while it is still awaiting review. An APPROVED name
   * was reviewed on its own merits and survives a bad post.
   */
  private boolean rejectIfPending(
      ModerationStatus current, String type, UUID id, UUID adminId, String reason) {
    if (current != ModerationStatus.PENDING) {
      return false;
    }
    logComponentDecision(adminId, "REJECT_COMPONENT", type, id, reason);
    return true;
  }

  private void logComponentDecision(
      UUID adminId, String action, String type, UUID id, String reason) {
    moderationLogRepository.save(
        ModerationLog.builder()
            .actorId(adminId)
            .action(action)
            .targetType(type)
            .targetId(id)
            .reason(reason)
            .build());
  }

  private PostStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return PostStatus.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw new ValidationException(
          "Status must be one of: created, processed, published, unpublished, hidden");
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
          "Flag must be one of: all, PENDING, PASSED, REJECTED, NEEDS_REVIEW");
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
