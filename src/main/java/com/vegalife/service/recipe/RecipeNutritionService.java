package com.vegalife.service.recipe;

import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps {@code recipe} nutrition in sync with the ingredients it uses (ADR-022).
 *
 * <p>Totals are <strong>persisted</strong>, not derived on read, because they are filtered and
 * ranked in SQL: {@code RecipeRecommendationService} narrows by calories and protein, and an
 * in-memory pass over the catalogue would turn a meal plan into a full table scan.
 *
 * <p>The cost of persisting is that an ingredient edit must push the change outwards. Every path
 * that can resolve or change an ingredient's nutrition calls {@link #recomputeForIngredient} in the
 * same transaction as that edit, so a recipe total is never observably behind the ingredient it
 * came from.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeNutritionService {

  private final RecipeRepository recipeRepository;
  private final RecipeIngredientRepository recipeIngredientRepository;

  /**
   * Recomputes one recipe's totals from its current lines and stores them.
   *
   * <p>Called on recipe creation and whenever an ingredient it uses changes. A recipe whose lines
   * are not all resolvable is written as {@code null} across all five columns - never a partial
   * sum.
   *
   * @return true when the recipe ended up with complete nutrition
   */
  @Transactional
  public boolean recomputeForRecipe(UUID recipeId) {
    Recipe recipe = recipeRepository.findById(recipeId).orElse(null);
    if (recipe == null) {
      // Deleted between the lookup and here; nothing to update.
      return false;
    }
    List<RecipeIngredient> lines = recipeIngredientRepository.findByRecipeId(recipeId);
    boolean resolved = apply(recipe, lines);
    recipeRepository.save(recipe);
    return resolved;
  }

  /**
   * Recomputes every recipe that uses this ingredient.
   *
   * <p>This is the write-amplification cost of persisting totals: editing a common ingredient
   * touches every recipe containing it. Batched into one query for the lines and one save per
   * affected recipe, which is acceptable at this catalogue size and avoids a scheduled sweep.
   *
   * @return the number of recipes whose totals changed from unresolved to resolved
   */
  @Transactional
  public int recomputeForIngredient(UUID ingredientId) {
    List<UUID> recipeIds = recipeIngredientRepository.findRecipeIdsByIngredientId(ingredientId);
    if (recipeIds.isEmpty()) {
      return 0;
    }
    return recomputeForRecipes(recipeIds);
  }

  /**
   * Recomputes several recipes, reading all their lines in one query.
   *
   * @return the number of recipes that newly became complete
   */
  @Transactional
  public int recomputeForRecipes(Collection<UUID> recipeIds) {
    if (recipeIds == null || recipeIds.isEmpty()) {
      return 0;
    }
    List<Recipe> recipes = recipeRepository.findByIdInAndDeletedAtIsNull(recipeIds);
    if (recipes.isEmpty()) {
      return 0;
    }

    // One query for every line of every affected recipe, keyed by recipe.
    Map<UUID, List<RecipeIngredient>> linesByRecipe = new HashMap<>();
    for (RecipeIngredient line :
        recipeIngredientRepository.findByRecipeIdInWithIngredient(recipeIds)) {
      linesByRecipe.computeIfAbsent(line.getRecipeId(), key -> new ArrayList<>()).add(line);
    }

    int newlyResolved = 0;
    for (Recipe recipe : recipes) {
      boolean wasResolved = recipe.getCalories() != null;
      boolean resolved = apply(recipe, linesByRecipe.getOrDefault(recipe.getId(), List.of()));
      if (resolved && !wasResolved) {
        newlyResolved++;
      }
      recipeRepository.save(recipe);
    }
    return newlyResolved;
  }

  /**
   * Writes the computed totals onto the entity without saving.
   *
   * @return true when the recipe has complete nutrition
   */
  private boolean apply(Recipe recipe, List<RecipeIngredient> lines) {
    List<RecipeNutritionCalculator.ResolvedLine> resolved = new ArrayList<>(lines.size());
    for (RecipeIngredient line : lines) {
      Ingredient ingredient = line.getIngredient();
      if (ingredient == null) {
        // The association is read-only and can be unset in some fetch paths; treat the line as
        // unknown rather than skipping it, which would silently drop an ingredient from the sum.
        resolved.add(new RecipeNutritionCalculator.ResolvedLine(line.getAmount(), null));
        continue;
      }
      resolved.add(new RecipeNutritionCalculator.ResolvedLine(line.getAmount(), ingredient));
    }

    RecipeNutritionCalculator.NutritionTotals totals = RecipeNutritionCalculator.totals(resolved);
    if (totals == null) {
      recipe.setCalories(null);
      recipe.setProteinG(null);
      recipe.setCarbohydrateG(null);
      recipe.setFatG(null);
      recipe.setFiberG(null);
      return false;
    }

    recipe.setCalories(totals.calories());
    recipe.setProteinG(totals.proteinG());
    recipe.setCarbohydrateG(totals.carbohydrateG());
    recipe.setFatG(totals.fatG());
    recipe.setFiberG(totals.fiberG());
    return true;
  }

  /**
   * Recomputes every recipe that uses any of the given ingredients.
   *
   * <p>Used when a dish-wide or catalogue-wide change lands, so callers never loop per ingredient
   * and re-read the same recipes repeatedly.
   */
  @Transactional
  public int recomputeForIngredients(Collection<UUID> ingredientIds) {
    if (ingredientIds == null || ingredientIds.isEmpty()) {
      return 0;
    }
    List<UUID> recipeIds = new ArrayList<>();
    for (UUID ingredientId : ingredientIds) {
      recipeIds.addAll(recipeIngredientRepository.findRecipeIdsByIngredientId(ingredientId));
    }
    return recomputeForRecipes(recipeIds.stream().distinct().toList());
  }
}
