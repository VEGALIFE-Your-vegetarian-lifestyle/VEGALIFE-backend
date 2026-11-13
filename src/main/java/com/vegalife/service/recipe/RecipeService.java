package com.vegalife.service.recipe;

import com.vegalife.dto.mapper.recipe.RecipeMapper;
import com.vegalife.dto.request.recipe.MyRecipeListRequest;
import com.vegalife.dto.request.recipe.RecipeCreateRequest;
import com.vegalife.dto.request.recipe.RecipeIngredientItem;
import com.vegalife.dto.request.recipe.RecipeListRequest;
import com.vegalife.dto.response.recipe.RecipeIngredientResponse;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.enums.recipe.Difficulty;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.recipe.Unit;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.model.user.User;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.recipe.RecipeSpecifications;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates recipes for the authenticated user (BR-RECP-001). Dish and ingredient names are trimmed
 * and lowercased before resolving against shared rows (BR-RECP-002); required fields are enforced
 * by bean validation on the request plus the duplicate-ingredient check here (BR-RECP-003).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeService {

  private final RecipeRepository recipeRepository;
  private final DishRepository dishRepository;
  private final IngredientRepository ingredientRepository;
  private final RecipeIngredientRepository recipeIngredientRepository;
  private final UserRepository userRepository;
  private final RecipeMapper recipeMapper;
  private final DishIngredientService dishIngredientService;
  private final RecipeNutritionService recipeNutritionService;
  private final TransactionTemplate transactionTemplate;

  /**
   * BR-RECP-002: a concurrent create may win the unique index between our lookup and our insert.
   * The losing attempt's transaction rolls back entirely; we retry once, and by then the winning
   * dish/ingredient row exists so the retry reuses it instead of failing the request.
   */
  public RecipeResponse createRecipe(UUID userId, RecipeCreateRequest request) {
    List<NormalizedIngredient> ingredients = normalizeIngredients(request.getIngredients());

    try {
      return transactionTemplate.execute(status -> doCreate(userId, request, ingredients));
    } catch (DataIntegrityViolationException ex) {
      log.warn("Concurrent find-or-create conflict while creating recipe, retrying once");
      return transactionTemplate.execute(status -> doCreate(userId, request, ingredients));
    }
  }

  /**
   * One recipe, by moderation state plus authorship (BR-RECP-008).
   *
   * <p>An approved, non-deleted recipe is public: anyone, including a guest ({@code callerId =
   * null}), can read it. A {@code PENDING} or {@code REJECTED} recipe still resolves for its
   * author, so a submitted recipe never looks lost to the person who wrote it. Everyone else gets
   * the same generic not-found answer as a missing id, so the endpoint cannot confirm that an
   * unapproved recipe exists.
   */
  @Transactional(readOnly = true)
  public RecipeResponse getRecipe(UUID callerId, UUID recipeId) {
    Recipe recipe =
        recipeRepository
            .findByIdAndDeletedAtIsNull(recipeId)
            .filter(
                found ->
                    found.getModerationStatus() == ModerationStatus.APPROVED
                        || isAuthor(found, callerId))
            .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

    RecipeResponse response =
        toResponse(recipe, recipe.getDish().getName(), ingredientResponsesOf(recipe));
    // The rejection reason is an administrative message to the author; this payload is now also a
    // public read, so a non-author must never receive it.
    if (!isAuthor(recipe, callerId)) {
      clearRejectionReason(response);
    }
    return response;
  }

  /**
   * The public recipe catalogue (BR-RECP-008): approved, non-deleted recipes, newest first.
   *
   * <p>Ingredient lines are resolved per recipe inside the single read transaction, mirroring the
   * saved-recipe read path. The page size ceiling bounds that work.
   */
  @Transactional(readOnly = true)
  public PageResponse<RecipeResponse> listApprovedRecipes(RecipeListRequest request) {
    Pageable pageable =
        PageRequest.of(
            request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<Recipe> recipes =
        recipeRepository.findAll(
            RecipeSpecifications.catalogue(
                null,
                ModerationStatus.APPROVED,
                request.getDishId(),
                parseDifficulty(request.getDifficulty()),
                request.getMaxTotalTimeMinutes(),
                request.getKeyword()),
            pageable);

    Page<RecipeResponse> mapped = recipes.map(this::toPublicResponse);
    return PageResponse.from(mapped);
  }

  /**
   * A recipe line for an audience that may not be its author.
   *
   * <p>Every caller of this helper reads approved recipes — the public catalogue, or another
   * member's saved item — so {@code rejectionReason} must never survive: it is an administrative
   * message to the author (BR-RECP-008).
   */
  private RecipeResponse toPublicResponse(Recipe recipe) {
    RecipeResponse response =
        toResponse(recipe, recipe.getDish().getName(), ingredientResponsesOf(recipe));
    clearRejectionReason(response);
    return response;
  }

  private static void clearRejectionReason(RecipeResponse response) {
    response.setRejectionReason(null);
  }

  private static boolean isAuthor(Recipe recipe, UUID callerId) {
    return callerId != null && recipe.getUser().getId().equals(callerId);
  }

  /** Every non-deleted recipe the caller created, newest first, optionally filtered. */
  @Transactional(readOnly = true)
  public PageResponse<RecipeResponse> listMyRecipes(UUID callerId, MyRecipeListRequest request) {
    Pageable pageable =
        PageRequest.of(
            request.getPage(), request.getSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<Recipe> recipes =
        recipeRepository.findAll(
            RecipeSpecifications.catalogue(
                callerId,
                parseModerationStatus(request.getModerationStatus()),
                request.getDishId(),
                parseDifficulty(request.getDifficulty()),
                request.getMaxTotalTimeMinutes(),
                null),
            pageable);

    return PageResponse.from(
        recipes.map(
            recipe ->
                toResponse(recipe, recipe.getDish().getName(), ingredientResponsesOf(recipe))));
  }

  /**
   * Maps a recipe and attaches the advisory missing-required-ingredient warning.
   *
   * <p>Derived on every read rather than stored: the dish's required set is curated independently
   * of any one recipe and changes without touching it (BR-DISH-002). The warning never blocks a
   * write, so attaching it here — after the recipe is already saved — is the whole enforcement.
   */
  /**
   * Builds one recipe line for a response.
   *
   * <p>Shared by the read path and the create path so the two cannot disagree about how a line's
   * nutrition is derived. The display name is passed in because the two paths legitimately differ:
   * a read echoes the stored row's name, while the create response echoes what the caller typed.
   *
   * <p>Derived here rather than in the mapper because {@code dto/mapper} ranks below {@code
   * service} (ADR-018) and may not reach the calculator.
   */
  private RecipeIngredientResponse ingredientResponseOf(
      BigDecimal amount, String name, Ingredient ingredient) {
    RecipeNutritionCalculator.LineNutrition nutrition =
        RecipeNutritionCalculator.scaledLineNutrition(amount, ingredient);
    RecipeIngredientResponse.RecipeIngredientResponseBuilder builder =
        RecipeIngredientResponse.builder()
            .ingredientId(ingredient.getId())
            .name(name)
            .amount(amount)
            .unit(unitName(ingredient.getUnit()))
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
   * Shapes the recipes behind a saved-items page (BR-SAVED-002).
   *
   * <p>Only approved, non-deleted recipes are returned: a saved recipe that is awaiting review or
   * was rejected is invisible everywhere else, so it must not reappear through this list. Same
   * mapping as {@code GET /api/recipes/me} and {@code GET /api/recipes}, so a saved recipe and an
   * authored or browsed one cannot describe themselves differently.
   *
   * <p>One batched ingredient lookup per recipe is unavoidable here — the response carries each
   * line — but the ingredient rows are resolved inside the single read transaction, so a page of N
   * saved recipes costs a bounded number of queries, not a per-row round trip to the database.
   */
  @Transactional(readOnly = true)
  public Map<UUID, RecipeResponse> readApprovedRecipesByIds(Collection<UUID> recipeIds) {
    if (recipeIds == null || recipeIds.isEmpty()) {
      return Map.of();
    }
    List<Recipe> recipes =
        recipeRepository.findByIdInAndDeletedAtIsNull(recipeIds).stream()
            .filter(recipe -> recipe.getModerationStatus() == ModerationStatus.APPROVED)
            .toList();
    Map<UUID, RecipeResponse> byId = new HashMap<>();
    for (Recipe recipe : recipes) {
      byId.put(recipe.getId(), toPublicResponse(recipe));
    }
    return byId;
  }

  private RecipeResponse toResponse(
      Recipe recipe, String dishName, List<RecipeIngredientResponse> ingredients) {
    RecipeResponse response = recipeMapper.toResponse(recipe, dishName, ingredients);
    List<UUID> presentIds =
        ingredients.stream().map(RecipeIngredientResponse::getIngredientId).toList();
    response.setMissingRequiredIngredients(
        dishIngredientService.missingRequiredIngredientNames(recipe.getDish().getId(), presentIds));
    // Stated explicitly rather than derived in the mapper: "unknown nutrition" and "no nutrition
    // field" must not look the same to a client.
    response.setNutritionResolved(recipe.getCalories() != null);
    return response;
  }

  private List<RecipeIngredientResponse> ingredientResponsesOf(Recipe recipe) {
    List<RecipeIngredientResponse> responses = new ArrayList<>();
    for (RecipeIngredient line : recipeIngredientRepository.findByRecipeId(recipe.getId())) {
      Ingredient ingredient = line.getIngredient();
      if (ingredient == null) {
        // insertable = false on the association: inside the transaction that created the line it
        // can
        // still be unset, so fall back to the row itself.
        ingredient = ingredientRepository.findById(line.getIngredientId()).orElse(null);
      }
      if (ingredient == null) {
        continue;
      }
      responses.add(ingredientResponseOf(line.getAmount(), ingredient.getName(), ingredient));
    }
    return responses;
  }

  private RecipeResponse doCreate(
      UUID userId, RecipeCreateRequest request, List<NormalizedIngredient> ingredients) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    String dishName = normalizeName(request.getDishName());
    Dish dish = findOrCreateDish(dishName);

    List<Ingredient> resolvedIngredients = new ArrayList<>();
    for (NormalizedIngredient item : ingredients) {
      resolvedIngredients.add(findOrCreateIngredient(item.name(), item.unit()));
    }

    Recipe recipe =
        recipeRepository.save(
            Recipe.builder()
                .dish(dish)
                .user(user)
                .name(request.getName().trim())
                .description(normalizeDescription(request.getDescription()))
                .instructions(request.getInstructions().trim())
                .prepTimeMinutes(request.getPrepTimeMinutes())
                .cookTimeMinutes(request.getCookTimeMinutes())
                .servings(request.getServings())
                .difficulty(parseDifficulty(request.getDifficulty()))
                .build());
    recipeRepository.flush();

    List<RecipeIngredientResponse> ingredientResponses = new ArrayList<>();
    for (int i = 0; i < ingredients.size(); i++) {
      NormalizedIngredient item = ingredients.get(i);
      Ingredient ingredient = resolvedIngredients.get(i);
      recipeIngredientRepository.save(
          RecipeIngredient.builder()
              .recipeId(recipe.getId())
              .ingredientId(ingredient.getId())
              .amount(item.amount())
              .build());
      ingredientResponses.add(ingredientResponseOf(item.amount(), item.name(), ingredient));
    }

    // Totals are derived from the lines just written, never taken from the request (ADR-022). A
    // recipe whose ingredients are all unresolved is stored with null nutrition rather than a
    // partial sum, and fills in later when an admin completes the ingredients.
    recipeNutritionService.recomputeForRecipe(recipe.getId());

    log.info("Recipe {} created for user {}", recipe.getId(), userId);

    return toResponse(recipe, dishName, ingredientResponses);
  }

  /**
   * Resolves a dish name to an existing visible row or creates it as {@link
   * ModerationStatus#PENDING}.
   *
   * <p>A name created here is not approved: the member who typed it is not the reviewer, and the
   * post carrying it is queued for an admin (BR-DISH-001). An existing row keeps whatever state it
   * already has — reusing an approved name must not resurface it, and reusing a pending one must
   * not quietly approve it.
   *
   * <p>A {@link ModerationStatus#REJECTED} name is a permanent block: it is refused rather than
   * re-created, whichever lookup path finds it.
   */
  private Dish findOrCreateDish(String normalizedDishName) {
    Optional<Dish> existing = dishRepository.findByNameIgnoreCase(normalizedDishName);
    if (existing.isPresent()) {
      Dish dish = existing.get();
      requireNotRejected(dish.getModerationStatus(), "Dish", normalizedDishName);
      // Soft-deleted rows keep their name; the admin hid this dish deliberately.
      if (dish.getDeletedAt() != null) {
        throw new ValidationException("Dish is not available: " + normalizedDishName);
      }
      return dish;
    }
    return dishRepository.saveAndFlush(
        Dish.builder().name(normalizedDishName).moderationStatus(ModerationStatus.PENDING).build());
  }

  /**
   * Same contract as {@link #findOrCreateDish}, for ingredient names.
   *
   * <p>{@code rawUnit} is applied <strong>only when the name is new</strong>. For an existing
   * ingredient the row's own unit already governs every recipe that references it, so honouring the
   * caller's text would silently re-denominate a shared ingredient and change the quantity in other
   * people's recipes (ADR-022).
   */
  private Ingredient findOrCreateIngredient(String normalizedName, String rawUnit) {
    Optional<Ingredient> existing = ingredientRepository.findByNameIgnoreCase(normalizedName);
    if (existing.isPresent()) {
      Ingredient ingredient = existing.get();
      requireNotRejected(ingredient.getModerationStatus(), "Ingredient", normalizedName);
      if (ingredient.getDeletedAt() != null) {
        throw new ValidationException("Ingredient is not available: " + normalizedName);
      }
      return ingredient;
    }
    return ingredientRepository.saveAndFlush(
        Ingredient.builder()
            .name(normalizedName)
            .unit(parseUnit(rawUnit))
            .moderationStatus(ModerationStatus.PENDING)
            .build());
  }

  /**
   * Maps the optional member-supplied unit onto the three-code enum.
   *
   * <p>Omitted stays {@code null}: a member who writes {@code "2 cà chua"} should not be forced to
   * spell out a unit they do not think in, and an admin resolves it later. Present-but-unmappable
   * is refused rather than dropped, because a caller who wrote {@code kg} means a different
   * quantity than {@code g} and must not have that silently discarded.
   */
  private Unit parseUnit(String rawUnit) {
    if (rawUnit == null || rawUnit.isBlank()) {
      return null;
    }
    return Unit.fromInput(rawUnit)
        .orElseThrow(
            () -> new ValidationException("Unit must be G, ML, or PIECE: " + rawUnit.trim()));
  }

  /** The wire form of a unit; null stays null rather than becoming a literal "null". */
  private static String unitName(Unit unit) {
    return unit == null ? null : unit.name();
  }

  private static void requireNotRejected(ModerationStatus status, String kind, String name) {
    if (status == ModerationStatus.REJECTED) {
      throw new ValidationException(kind + " is not available: " + name);
    }
  }

  /**
   * BR-RECP-002: ingredient names must be unique within one request; comparison happens after the
   * same trim + lowercase normalization the persistence path uses.
   */
  private List<NormalizedIngredient> normalizeIngredients(List<RecipeIngredientItem> items) {
    List<NormalizedIngredient> normalized = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (RecipeIngredientItem item : items) {
      String name = normalizeName(item.getName());
      if (!seen.add(name)) {
        throw new ValidationException("Duplicate ingredient name: " + name);
      }
      normalized.add(new NormalizedIngredient(name, item.getAmount(), item.getUnit()));
    }
    return normalized;
  }

  private String normalizeName(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private Difficulty parseDifficulty(String difficulty) {
    if (difficulty == null) {
      return null;
    }
    try {
      return Difficulty.valueOf(difficulty);
    } catch (IllegalArgumentException ex) {
      throw new ValidationException("Difficulty must be EASY, MEDIUM, or HARD");
    }
  }

  /** Absent or blank means "every state"; the caller's own list may span all of them. */
  private ModerationStatus parseModerationStatus(String moderationStatus) {
    if (moderationStatus == null || moderationStatus.isBlank()) {
      return null;
    }
    try {
      return ModerationStatus.valueOf(moderationStatus.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new ValidationException("moderationStatus must be PENDING, APPROVED, or REJECTED");
    }
  }

  private String normalizeDescription(String description) {
    return description == null || description.isBlank() ? null : description.trim();
  }

  private record NormalizedIngredient(String name, BigDecimal amount, String unit) {}
}
