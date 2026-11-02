package com.vegalife.service.recipe;

import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.DietaryPreferences;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserProfileRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Selects the recipes that may be recommended for a dish (issue #163).
 *
 * <p>A recipe qualifies when it is approved and not deleted, none of its ingredient names equals
 * one of the caller's stored allergies, and it satisfies the caller's {@link RecipeFilter}. The
 * result is ordered {@code score DESC NULLS LAST, createdAt DESC, id ASC} and capped.
 *
 * <p>Allergy matching is <strong>exact</strong> (case-insensitive, trimmed) on the ingredient name.
 * There is no substring or synonym matching: the stored allergy strings are the member's own words,
 * so {@code peanut} does not cover an ingredient called {@code groundnut}. That limitation is
 * deliberate — see the feature spec.
 *
 * <p>Loads are batched: one query for every recipe of every requested dish and one for their
 * ingredients, so a meal plan never turns into a per-meal query.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeRecommendationService {

  /** Default number of recipes returned per dish when the caller does not say. */
  public static final int DEFAULT_RECIPES_PER_DISH = 3;

  /** Hard ceiling, so a caller cannot ask for the whole catalogue in one meal slot. */
  public static final int MAX_RECIPES_PER_DISH = 10;

  /**
   * Quality signal first (platform relevance, ADR-016), then recency, then id so the same dish
   * never yields a different triple across identical calls. NULL sorts last: an unscored recipe is
   * unvetted, not good.
   */
  private static final Comparator<Recipe> RANKING =
      Comparator.comparing(Recipe::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(Recipe::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(Recipe::getId);

  private final RecipeRepository recipeRepository;
  private final RecipeIngredientRepository recipeIngredientRepository;
  private final UserProfileRepository userProfileRepository;

  /**
   * The recipes worth recommending for each of the given dishes.
   *
   * @param dishIds dishes to resolve; an empty collection short-circuits to an empty map
   * @param userId the caller, whose stored allergies are applied
   * @param filter optional recipe constraints; {@code null} means none
   * @param recipesPerDish cap per dish; clamped to 1..{@link #MAX_RECIPES_PER_DISH}
   * @return qualifying recipes per dish id, ranked and capped; a dish with no qualifying recipe is
   *     absent from the map
   */
  @Transactional(readOnly = true)
  public Map<UUID, List<Recipe>> recommendFor(
      Collection<UUID> dishIds, UUID userId, RecipeFilter filter, int recipesPerDish) {
    if (dishIds == null || dishIds.isEmpty()) {
      return Map.of();
    }
    int cap = Math.max(1, Math.min(MAX_RECIPES_PER_DISH, recipesPerDish));

    List<Recipe> candidates = recipeRepository.findApprovedByDishIdIn(dishIds);
    if (candidates.isEmpty()) {
      return Map.of();
    }

    Set<String> allergies = allergiesOf(userId);
    Set<UUID> allergenicRecipeIds = allergenicRecipeIds(candidates, allergies);
    RecipeFilter effective = filter == null ? RecipeFilter.builder().build() : filter;

    // LinkedHashMap keeps a stable insertion order for callers that merely iterate.
    Map<UUID, List<Recipe>> byDish = new LinkedHashMap<>();
    for (Recipe recipe : candidates) {
      if (allergenicRecipeIds.contains(recipe.getId()) || !matches(recipe, effective)) {
        continue;
      }
      byDish.computeIfAbsent(recipe.getDish().getId(), key -> new ArrayList<>()).add(recipe);
    }

    Map<UUID, List<Recipe>> capped = new LinkedHashMap<>();
    byDish.forEach(
        (dishId, recipes) -> {
          recipes.sort(RANKING);
          capped.put(dishId, List.copyOf(recipes.subList(0, Math.min(cap, recipes.size()))));
        });
    return capped;
  }

  /**
   * The caller's stored allergy names, normalized for comparison. Empty when the member has no
   * profile, no dietary preferences, or no allergies — and an empty set never matches anything.
   */
  @Transactional(readOnly = true)
  public Set<String> allergiesOf(UUID userId) {
    UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
    if (profile == null) {
      return Set.of();
    }
    DietaryPreferences dietary = profile.getDietaryPreferences();
    if (dietary == null || dietary.getAllergies() == null) {
      return Set.of();
    }
    Set<String> normalized = new HashSet<>();
    for (String allergy : dietary.getAllergies()) {
      String value = normalizeName(allergy);
      if (!value.isEmpty()) {
        normalized.add(value);
      }
    }
    return normalized;
  }

  /**
   * Ids of recipes with at least one ingredient whose name exactly matches a stored allergy. One
   * batched ingredient query regardless of how many recipes or allergens there are.
   */
  private Set<UUID> allergenicRecipeIds(List<Recipe> recipes, Set<String> allergies) {
    if (allergies.isEmpty()) {
      return Set.of();
    }
    List<UUID> recipeIds = recipes.stream().map(Recipe::getId).toList();
    Set<UUID> offending = new HashSet<>();
    for (Object[] row : recipeIngredientRepository.findIngredientNamesByRecipeIds(recipeIds)) {
      UUID recipeId = (UUID) row[0];
      String ingredientName = (String) row[1];
      if (allergies.contains(normalizeName(ingredientName))) {
        offending.add(recipeId);
      }
    }
    return offending;
  }

  private boolean matches(Recipe recipe, RecipeFilter filter) {
    if (filter.isEmpty()) {
      return true;
    }
    if (filter.getDifficulty() != null
        && (recipe.getDifficulty() == null
            || !recipe.getDifficulty().name().equalsIgnoreCase(filter.getDifficulty()))) {
      return false;
    }
    if (filter.getMaxPrepTimeMinutes() != null
        && !atMost(recipe.getPrepTimeMinutes(), filter.getMaxPrepTimeMinutes())) {
      return false;
    }
    if (filter.getMaxCookTimeMinutes() != null
        && !atMost(recipe.getCookTimeMinutes(), filter.getMaxCookTimeMinutes())) {
      return false;
    }
    if (filter.getMaxTotalTimeMinutes() != null) {
      int total = orZero(recipe.getPrepTimeMinutes()) + orZero(recipe.getCookTimeMinutes());
      if (total > filter.getMaxTotalTimeMinutes()) {
        return false;
      }
    }
    if (filter.getMinCalories() != null
        && !atLeast(recipe.getCalories(), filter.getMinCalories())) {
      return false;
    }
    if (filter.getMaxCalories() != null
        && !atMostDecimal(recipe.getCalories(), filter.getMaxCalories())) {
      return false;
    }
    return filter.getMinProteinG() == null
        || atLeast(recipe.getProteinG(), filter.getMinProteinG());
  }

  /** A null actual value fails an upper bound: unknown is not known-to-be-small. */
  private static boolean atMost(Integer actual, Integer bound) {
    return actual != null && actual <= bound;
  }

  private static boolean atMostDecimal(java.math.BigDecimal actual, java.math.BigDecimal bound) {
    return actual != null && actual.compareTo(bound) <= 0;
  }

  private static boolean atLeast(java.math.BigDecimal actual, java.math.BigDecimal bound) {
    return actual != null && actual.compareTo(bound) >= 0;
  }

  private static int orZero(Integer value) {
    return value == null ? 0 : value;
  }

  /** Trim and lower-case, so comparison is exact but not case- or whitespace-sensitive. */
  private static String normalizeName(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
  }
}
