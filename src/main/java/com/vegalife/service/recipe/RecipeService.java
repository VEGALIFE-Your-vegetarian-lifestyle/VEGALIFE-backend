package com.vegalife.service.recipe;

import com.vegalife.dto.mapper.recipe.RecipeMapper;
import com.vegalife.dto.request.recipe.RecipeCreateRequest;
import com.vegalife.dto.request.recipe.RecipeIngredientItem;
import com.vegalife.dto.response.recipe.RecipeIngredientResponse;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.ModerationStatus;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.model.user.User;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
   * One of the caller's own recipes.
   *
   * <p>Owner-scoped rather than public: a recipe starts {@link ModerationStatus#PENDING}, and
   * returning someone else's unreviewed recipe here would make the approval step cosmetic. The
   * author still sees their own, with the state attached, so a withheld recipe is not a mystery.
   */
  @Transactional(readOnly = true)
  public RecipeResponse getRecipe(UUID callerId, UUID recipeId) {
    Recipe recipe =
        recipeRepository
            .findByIdAndDeletedAtIsNull(recipeId)
            .filter(found -> found.getUser().getId().equals(callerId))
            .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));

    return recipeMapper.toResponse(
        recipe, recipe.getDish().getName(), ingredientResponsesOf(recipe));
  }

  /** Every non-deleted recipe the caller created, newest first. */
  @Transactional(readOnly = true)
  public List<RecipeResponse> listMyRecipes(UUID callerId) {
    return recipeRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(callerId).stream()
        .map(
            recipe ->
                recipeMapper.toResponse(
                    recipe, recipe.getDish().getName(), ingredientResponsesOf(recipe)))
        .toList();
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
      responses.add(
          RecipeIngredientResponse.builder()
              .ingredientId(ingredient.getId())
              .name(ingredient.getName())
              .amount(line.getAmount())
              .unit(line.getUnit())
              .build());
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
      resolvedIngredients.add(findOrCreateIngredient(item.name()));
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
                .calories(request.getCalories())
                .proteinG(request.getProteinG())
                .carbohydrateG(request.getCarbohydrateG())
                .fatG(request.getFatG())
                .fiberG(request.getFiberG())
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
              .unit(item.unit())
              .build());
      ingredientResponses.add(
          RecipeIngredientResponse.builder()
              .ingredientId(ingredient.getId())
              .name(item.name())
              .amount(item.amount())
              .unit(item.unit())
              .build());
    }

    log.info("Recipe {} created for user {}", recipe.getId(), userId);

    return recipeMapper.toResponse(recipe, dishName, ingredientResponses);
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

  /** Same contract as {@link #findOrCreateDish}, for ingredient names. */
  private Ingredient findOrCreateIngredient(String normalizedName) {
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
            .moderationStatus(ModerationStatus.PENDING)
            .build());
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
      normalized.add(new NormalizedIngredient(name, item.getAmount(), item.getUnit().trim()));
    }
    return normalized;
  }

  private String normalizeName(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  private Recipe.Difficulty parseDifficulty(String difficulty) {
    if (difficulty == null) {
      return null;
    }
    try {
      return Recipe.Difficulty.valueOf(difficulty);
    } catch (IllegalArgumentException ex) {
      throw new ValidationException("Difficulty must be EASY, MEDIUM, or HARD");
    }
  }

  private String normalizeDescription(String description) {
    return description == null || description.isBlank() ? null : description.trim();
  }

  private record NormalizedIngredient(String name, BigDecimal amount, String unit) {}
}
