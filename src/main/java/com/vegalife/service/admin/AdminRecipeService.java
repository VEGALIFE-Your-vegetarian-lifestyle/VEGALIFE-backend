package com.vegalife.service.admin;

import com.vegalife.dto.mapper.admin.AdminRecipeMapper;
import com.vegalife.dto.request.admin.AdminRecipeUpdateRequest;
import com.vegalife.dto.request.admin.RecipeListRequest;
import com.vegalife.dto.response.admin.AdminRecipeListResponse;
import com.vegalife.enums.recipe.Difficulty;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.recipe.RecipeSpecifications;
import com.vegalife.service.cache.AdminDashboardCacheInvalidator;
import com.vegalife.service.recipe.DishIngredientService;
import com.vegalife.service.recipe.RecipeNutritionCalculator;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

/**
 * Admin management of recipes (ADR-016).
 *
 * <p>Members can create a recipe and nothing else. Editing or deleting one is admin-only because a
 * member who could edit after approval would have a way around the filter: publish clean text, get
 * it approved, then rewrite it. The cost is that a typo needs an admin, which is the accepted
 * trade-off recorded in the feature spec.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRecipeService {

  private static final Set<String> SORTABLE_PROPERTIES =
      Set.of("createdAt", "updatedAt", "name", "moderationStatus");

  private static final String SORTABLE_PROPERTIES_MESSAGE =
      "Sort property must be one of: createdAt, updatedAt, name, moderationStatus";

  private final RecipeRepository recipeRepository;
  private final DishRepository dishRepository;
  private final IngredientRepository ingredientRepository;
  private final RecipeIngredientRepository recipeIngredientRepository;
  private final DishIngredientService dishIngredientService;
  private final AdminRecipeMapper adminRecipeMapper;
  private final AdminDashboardCacheInvalidator dashboardCache;

  /**
   * Lists recipes, optionally narrowed by author, category, date range, review state, deletion
   * state and the catalogue's dish, difficulty, total-time and keyword filters. The review state
   * filter is what makes the queue reachable: {@code moderationStatus=PENDING} lists exactly the
   * recipes waiting for a decision.
   */
  @Transactional(readOnly = true)
  public PageResponse<AdminRecipeListResponse> listRecipes(RecipeListRequest request) {
    UUID userId = request.getUserId();
    UUID categoryId = request.getCategoryId();
    Instant createdFrom = request.getCreatedFrom();
    Instant createdTo = request.getCreatedTo();

    if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
      throw new ValidationException("createdFrom must be before createdTo");
    }

    ModerationStatus moderationStatus =
        AdminComponentQuery.parseStatus(request.getModerationStatus());
    Boolean deleted = parseDeletionStatus(request.getStatus());
    Difficulty difficulty = parseDifficultyFilter(request.getDifficulty());

    Pageable pageable =
        PageRequest.of(request.getPage(), request.getSize(), parseSort(request.getSort()));
    Specification<Recipe> spec =
        RecipeSpecifications.allWithFilters(
            userId,
            categoryId,
            createdFrom,
            createdTo,
            moderationStatus,
            deleted,
            request.getDishId(),
            difficulty,
            request.getMaxTotalTimeMinutes(),
            request.getKeyword());
    Page<Recipe> page = recipeRepository.findAll(spec, pageable);

    log.debug(
        "Listed recipes page={} size={} total={}",
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());

    Page<AdminRecipeListResponse> mapped = page.map(this::toResponse);
    return PageResponse.from(mapped);
  }

  @Transactional(readOnly = true)
  public AdminRecipeListResponse getRecipe(UUID recipeId) {
    return toResponse(findActive(recipeId));
  }

  /**
   * Partial update; an omitted field keeps its current value.
   *
   * <p>{@code moderationStatus} is untouched because the admin is the approver. Recipe bodies are
   * not scored (ADR-026), so there is no score to preserve or recompute.
   */
  @Transactional
  public AdminRecipeListResponse updateRecipe(
      UUID adminId, UUID recipeId, AdminRecipeUpdateRequest request) {
    Recipe recipe = findActive(recipeId);

    if (request.getName() != null) {
      String name = request.getName().trim();
      if (name.isEmpty()) {
        throw new ValidationException("name must not be blank");
      }
      recipe.setName(name);
    }
    if (request.getDescription() != null) {
      String description = request.getDescription().trim();
      recipe.setDescription(description.isEmpty() ? null : description);
    }
    if (request.getInstructions() != null) {
      String instructions = request.getInstructions().trim();
      if (instructions.isEmpty()) {
        throw new ValidationException("instructions must not be blank");
      }
      recipe.setInstructions(instructions);
    }
    if (request.getPrepTimeMinutes() != null) {
      recipe.setPrepTimeMinutes(request.getPrepTimeMinutes());
    }
    if (request.getCookTimeMinutes() != null) {
      recipe.setCookTimeMinutes(request.getCookTimeMinutes());
    }
    if (request.getServings() != null) {
      recipe.setServings(request.getServings());
    }
    if (request.getDifficulty() != null) {
      recipe.setDifficulty(parseDifficulty(request.getDifficulty()));
    }

    Recipe saved = recipeRepository.saveAndFlush(recipe);
    log.info("Admin {} updated recipe {}", adminId, saved.getId());

    return toResponse(saved);
  }

  /** Soft delete: post and menu references must keep resolving, they simply stop listing it. */
  @Transactional
  public void deleteRecipe(UUID adminId, UUID recipeId) {
    Recipe recipe = findActive(recipeId);
    recipe.setDeletedAt(Instant.now());
    recipeRepository.save(recipe);
    // totalRecipes fell; a PENDING recipe also left the moderation queue.
    dashboardCache.summary();

    log.info("Admin {} soft-deleted recipe {}", adminId, recipe.getId());
  }

  /**
   * Approves the recipe, the dish and ingredient names it introduces, and records the recipe's
   * ingredient list as a suggestion for its dish (BR-DISH-003).
   *
   * <p>The names are approved here because this is the moment a human has read the recipe and
   * accepted it: the dish and ingredients it names are, by that decision, acceptable. Only {@code
   * PENDING} names move — a {@code REJECTED} name is a permanent block and stays blocked, so
   * approving one recipe cannot launder a name an admin refused.
   *
   * <p>Seeding only happens on approval, never on create: a rejected or junk recipe must not be
   * able to pollute the shared per-dish list.
   */
  @Transactional
  public AdminRecipeListResponse approveRecipe(UUID adminId, UUID recipeId) {
    Recipe recipe = findActive(recipeId);
    if (recipe.getModerationStatus() == ModerationStatus.APPROVED) {
      return toResponse(recipe);
    }

    recipe.setModerationStatus(ModerationStatus.APPROVED);
    // Current state, not history: a reason left over from an earlier rejection would describe a
    // decision that no longer holds.
    recipe.setRejectionReason(null);
    Recipe saved = recipeRepository.saveAndFlush(recipe);
    log.info("Admin {} approved recipe {}", adminId, saved.getId());

    approvePendingNamesOf(saved);
    dishIngredientService.seedFromApprovedRecipe(saved);

    // pendingRecipes fell. Placed after the component work so the eviction covers the whole write.
    dashboardCache.summary();

    return toResponse(saved);
  }

  /**
   * Approves the recipe's dish and every ingredient it uses, but only where the row is still {@code
   * PENDING}.
   *
   * <p>Mirrors the component handling of post approval: a name referenced twice is one decision.
   */
  private void approvePendingNamesOf(Recipe recipe) {
    Dish dish = recipe.getDish();
    if (dish != null && isAwaitingReview(dish.getModerationStatus())) {
      dish.setModerationStatus(ModerationStatus.APPROVED);
      dishRepository.save(dish);
    }

    Set<UUID> seen = new HashSet<>();
    for (RecipeIngredient line : recipeIngredientRepository.findByRecipeId(recipe.getId())) {
      Ingredient ingredient = resolveIngredient(line);
      if (ingredient == null || !seen.add(ingredient.getId())) {
        continue;
      }
      if (isAwaitingReview(ingredient.getModerationStatus())) {
        ingredient.setModerationStatus(ModerationStatus.APPROVED);
        ingredientRepository.save(ingredient);
      }
    }
  }

  /**
   * Reads the ingredient behind a recipe line, falling back to a lookup because the association is
   * mapped {@code insertable = false} and can still be null inside the creating transaction.
   */
  private Ingredient resolveIngredient(RecipeIngredient line) {
    if (line.getIngredient() != null) {
      return line.getIngredient();
    }
    return ingredientRepository.findById(line.getIngredientId()).orElse(null);
  }

  /**
   * Whether a name may still be decided: only a {@code PENDING} one. An already-reviewed name is
   * left exactly as it is, so approving a recipe never re-opens a refusal or re-approves a pass.
   */
  private boolean isAwaitingReview(ModerationStatus current) {
    return current == ModerationStatus.PENDING;
  }

  /** Rejects the recipe row only; nothing is deleted, and it cannot be re-created by a member. */
  @Transactional
  public AdminRecipeListResponse rejectRecipe(UUID adminId, UUID recipeId, String reason) {
    Recipe recipe = findActive(recipeId);
    if (recipe.getModerationStatus() == ModerationStatus.REJECTED) {
      return toResponse(recipe);
    }

    recipe.setModerationStatus(ModerationStatus.REJECTED);
    recipe.setRejectionReason(reason);
    Recipe saved = recipeRepository.saveAndFlush(recipe);
    // pendingRecipes fell: the recipe left the queue.
    dashboardCache.summary();
    log.info("Admin {} rejected recipe {}", adminId, saved.getId());

    return toResponse(saved);
  }

  private Recipe findActive(UUID recipeId) {
    return recipeRepository
        .findByIdAndDeletedAtIsNull(recipeId)
        .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
  }

  /**
   * Maps a recipe and attaches everything the mapper cannot supply.
   *
   * <p>Three derived parts are filled here rather than in the mapper. The missing-required warning
   * needs a repository lookup; the per-line nutrition needs the calculator; and the {@code
   * nutritionResolved} flag restates the persisted totals' meaning so "not computed yet" and "zero
   * calories" cannot look alike. Both the calculator and the repository sit above {@code
   * dto/mapper} in the package ranking (ADR-018), so the mapper must not reach either.
   *
   * <p>Totals are read from the persisted columns, never recomputed: the suggest-dishes filters
   * read those same columns, so recomputing here could make the admin view disagree with what a
   * member can filter on.
   */
  private AdminRecipeListResponse toResponse(Recipe recipe) {
    AdminRecipeListResponse response = adminRecipeMapper.toResponse(recipe);
    List<AdminRecipeListResponse.IngredientItem> ingredients =
        recipe.getRecipeIngredients().stream().map(this::toIngredientItem).toList();
    response.setIngredients(ingredients);
    response.setNutritionResolved(recipe.getCalories() != null);
    response.setMissingRequiredIngredients(
        dishIngredientService.missingRequiredIngredientNames(
            recipe.getDish().getId(),
            ingredients.stream()
                .map(AdminRecipeListResponse.IngredientItem::getIngredientId)
                .toList()));
    return response;
  }

  /**
   * One recipe line, with the same per-line nutrition shape the member response returns.
   *
   * <p>Computed by the shared calculator so the admin view cannot drift from what a member sees. A
   * line whose ingredient is uncurated reports five nulls and {@code false}, never a partial sum.
   */
  private AdminRecipeListResponse.IngredientItem toIngredientItem(RecipeIngredient line) {
    Ingredient ingredient = line.getIngredient();
    RecipeNutritionCalculator.LineNutrition nutrition =
        RecipeNutritionCalculator.scaledLineNutrition(line.getAmount(), ingredient);
    AdminRecipeListResponse.IngredientItem.IngredientItemBuilder builder =
        AdminRecipeListResponse.IngredientItem.builder()
            // The unit belongs to the ingredient, not the line (ADR-022): a per-line copy could
            // only repeat it or contradict it.
            .ingredientId(ingredient.getId())
            .name(ingredient.getName())
            .moderationStatus(ingredient.getModerationStatus().name())
            .amount(line.getAmount())
            .unit(ingredient.getUnit() == null ? null : ingredient.getUnit().name())
            .nutritionResolved(nutrition != null);
    if (nutrition != null) {
      builder
          .calories(nutrition.calories())
          .proteinG(nutrition.proteinG())
          .carbohydrateG(nutrition.carbohydrateG())
          .fatG(nutrition.fatG())
          .fiberG(nutrition.fiberG());
    }
    return builder.build();
  }

  /**
   * Maps the deletion {@code status} filter onto {@code deletedAt}. {@code null} keeps both active
   * and soft-deleted recipes, which is what the list did before the filter existed.
   */
  private Boolean parseDeletionStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    return switch (status.trim().toUpperCase(Locale.ROOT)) {
      case "ACTIVE" -> Boolean.FALSE;
      case "DELETED" -> Boolean.TRUE;
      default -> throw new ValidationException("Status must be ACTIVE or DELETED");
    };
  }

  private Difficulty parseDifficulty(String difficulty) {
    try {
      return Difficulty.valueOf(difficulty.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new ValidationException("Difficulty must be EASY, MEDIUM, or HARD");
    }
  }

  /**
   * The list filter's difficulty: absent means "no difficulty filter", which is different from the
   * update path, where an absent value means "leave it alone" and a blank one is a mistake.
   */
  private Difficulty parseDifficultyFilter(String difficulty) {
    if (difficulty == null || difficulty.isBlank()) {
      return null;
    }
    return parseDifficulty(difficulty);
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
