package com.vegalife.service.recipe;

import com.vegalife.dto.response.recipe.DishIngredientSuggestionResponse;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.DishIngredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.repository.recipe.DishIngredientRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dish-level ingredient knowledge: what normally belongs to a dish, and which of those an
 * administrator marked as required (BR-DISH-002).
 *
 * <p>The list is a suggestion source for the create-recipe form and the input to the advisory
 * "missing required ingredients" warning. It never blocks a write — a recipe omitting a required
 * ingredient is still a valid recipe, because legitimate variants routinely substitute the defining
 * ingredient.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DishIngredientService {

  private final DishIngredientRepository dishIngredientRepository;
  private final DishRepository dishRepository;
  private final RecipeIngredientRepository recipeIngredientRepository;

  /**
   * The active, approved ingredients of one approved dish, for a member writing a recipe.
   *
   * <p>A pending or rejected dish answers 404 rather than returning a flagged entity: this endpoint
   * is member-facing, and "exists but you may not see it" would still confirm the name to whoever
   * guessed the id (BR-DISH-001). Ingredients that are not `APPROVED` are filtered out of the query
   * for the same reason — and so the endpoint cannot be used to enumerate unreviewed names.
   */
  @Transactional(readOnly = true)
  public List<DishIngredientSuggestionResponse> listSuggestions(UUID dishId) {
    Dish dish =
        dishRepository
            .findByIdAndDeletedAtIsNullAndModerationStatus(dishId, ModerationStatus.APPROVED)
            .orElseThrow(() -> new ResourceNotFoundException("Dish not found"));

    return dishIngredientRepository
        .findVisibleWithIngredient(dish.getId(), ModerationStatus.APPROVED)
        .stream()
        .map(
            entry ->
                DishIngredientSuggestionResponse.builder()
                    .ingredientId(entry.getIngredient().getId())
                    .name(entry.getIngredient().getName())
                    .required(entry.isRequired())
                    .build())
        .toList();
  }

  /**
   * Names of the dish's active required ingredients that this recipe does not use (BR-DISH-002).
   *
   * <p>Advisory only: the caller attaches these to a response, it never rejects on them. Derived at
   * read time rather than stored, so an administrator marking an ingredient required later applies
   * to existing recipes on their next read without a backfill.
   */
  @Transactional(readOnly = true)
  public List<String> missingRequiredIngredientNames(UUID dishId, Collection<UUID> presentIds) {
    Set<UUID> present = presentIds == null ? Set.of() : new HashSet<>(presentIds);
    List<String> missing = new ArrayList<>();
    for (DishIngredient entry : dishIngredientRepository.findRequiredWithIngredient(dishId)) {
      if (!present.contains(entry.getIngredientId())) {
        missing.add(entry.getIngredient().getName());
      }
    }
    return missing;
  }

  /**
   * Seeds the dish's ingredient list from a recipe that has just been approved (BR-DISH-003).
   *
   * <p>Adds one non-required entry per ingredient that has no entry at all. An existing entry is
   * never touched: neither its {@code required} flag nor its {@code removedAt} tombstone changes,
   * so an administrator's withdrawal is not undone by the next member submission.
   *
   * <p>Dish and ingredient names are approved separately by the caller; this method only records
   * the association.
   */
  @Transactional
  public void seedFromApprovedRecipe(Recipe recipe) {
    Dish dish = recipe.getDish();
    if (dish == null || dish.getId() == null) {
      return;
    }

    List<RecipeIngredient> lines = recipeIngredientRepository.findByRecipeId(recipe.getId());
    if (lines.isEmpty()) {
      return;
    }

    List<UUID> ingredientIds =
        lines.stream().map(RecipeIngredient::getIngredientId).distinct().toList();
    Set<UUID> known =
        dishIngredientRepository.findByDishIdAndIngredientIdIn(dish.getId(), ingredientIds).stream()
            .map(DishIngredient::getIngredientId)
            .collect(Collectors.toSet());

    int seeded = 0;
    for (UUID ingredientId : ingredientIds) {
      if (known.contains(ingredientId)) {
        continue;
      }
      dishIngredientRepository.save(
          DishIngredient.builder()
              .dishId(dish.getId())
              .ingredientId(ingredientId)
              .required(false)
              .build());
      seeded++;
    }

    if (seeded > 0) {
      log.info(
          "Seeded {} dish ingredient(s) for dish {} from recipe {}",
          seeded,
          dish.getId(),
          recipe.getId());
    }
  }
}
