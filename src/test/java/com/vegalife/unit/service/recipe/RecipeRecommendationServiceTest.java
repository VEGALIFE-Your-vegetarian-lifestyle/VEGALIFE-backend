package com.vegalife.unit.service.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.DietaryPreferences;
import com.vegalife.model.user.UserProfile;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserProfileRepository;
import com.vegalife.service.recipe.RecipeFilter;
import com.vegalife.service.recipe.RecipeRecommendationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeRecommendationServiceTest {

  @Mock private RecipeRepository recipeRepository;
  @Mock private RecipeIngredientRepository recipeIngredientRepository;
  @Mock private UserProfileRepository userProfileRepository;

  private RecipeRecommendationService service;

  private final UUID userId = UUID.randomUUID();
  private final UUID dishId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new RecipeRecommendationService(
            recipeRepository, recipeIngredientRepository, userProfileRepository);
  }

  @Test
  void recommendFor_excludesRecipeWhoseIngredientNameExactlyMatchesAllergy() {
    Recipe safe = recipe("Safe Pad Thai", 0.9, Instant.parse("2026-01-02T00:00:00Z"));
    Recipe unsafe = recipe("Classic Pad Thai", 0.99, Instant.parse("2026-01-03T00:00:00Z"));
    givenProfile(List.of("peanut"));
    when(recipeRepository.findApprovedByDishIdIn(any())).thenReturn(List.of(safe, unsafe));
    when(recipeIngredientRepository.findIngredientNamesByRecipeIds(any()))
        .thenReturn(
            List.of(line(safe, "rice noodles"), line(unsafe, "Peanut"), line(unsafe, "tofu")));

    Map<UUID, List<Recipe>> result = service.recommendFor(List.of(dishId), userId, null, 3);

    assertThat(result.get(dishId)).containsExactly(safe);
  }

  @Test
  void recommendFor_allergyMatchIsCaseAndWhitespaceInsensitiveButNotSubstring() {
    Recipe exact = recipe("Exact", 0.5, Instant.parse("2026-01-01T00:00:00Z"));
    // "Peanut butter" must NOT match the stored "peanut": matching is exact, not substring.
    Recipe substring = recipe("Substring", 0.6, Instant.parse("2026-01-01T00:00:00Z"));
    givenProfile(List.of("  PeAnUt "));
    when(recipeRepository.findApprovedByDishIdIn(any())).thenReturn(List.of(exact, substring));
    when(recipeIngredientRepository.findIngredientNamesByRecipeIds(any()))
        .thenReturn(List.of(line(exact, "peanut"), line(substring, "peanut butter")));

    Map<UUID, List<Recipe>> result = service.recommendFor(List.of(dishId), userId, null, 3);

    assertThat(result.get(dishId)).containsExactly(substring);
  }

  @Test
  void recommendFor_returnsNothingForADishWhoseEveryRecipeIsAllergenic() {
    Recipe one = recipe("One", 0.5, Instant.parse("2026-01-01T00:00:00Z"));
    Recipe two = recipe("Two", 0.6, Instant.parse("2026-01-01T00:00:00Z"));
    givenProfile(List.of("peanut"));
    when(recipeRepository.findApprovedByDishIdIn(any())).thenReturn(List.of(one, two));
    when(recipeIngredientRepository.findIngredientNamesByRecipeIds(any()))
        .thenReturn(List.of(line(one, "peanut"), line(two, "peanut")));

    Map<UUID, List<Recipe>> result = service.recommendFor(List.of(dishId), userId, null, 3);

    assertThat(result).doesNotContainKey(dishId);
  }

  @Test
  void recommendFor_ranksByScoreDescNullsLastThenCreatedAtDesc() {
    Recipe unscored =
        Recipe.builder()
            .id(UUID.randomUUID())
            .name("Unscored")
            .score(null)
            .createdAt(Instant.parse("2026-01-09T00:00:00Z"))
            .moderationStatus(ModerationStatus.APPROVED)
            .dish(dish())
            .build();
    Recipe high = recipe("High", 0.9, Instant.parse("2026-01-01T00:00:00Z"));
    Recipe older = recipe("Older", 0.9, Instant.parse("2026-01-02T00:00:00Z"));
    givenProfile(List.of());
    when(recipeRepository.findApprovedByDishIdIn(any())).thenReturn(List.of(unscored, high, older));

    Map<UUID, List<Recipe>> result = service.recommendFor(List.of(dishId), userId, null, 3);

    // Same score -> newer first; an unscored recipe sorts last even though it is the newest.
    assertThat(result.get(dishId)).containsExactly(older, high, unscored);
  }

  @Test
  void recommendFor_capsAtRecipesPerDish() {
    Recipe a = recipe("A", 0.9, Instant.parse("2026-01-03T00:00:00Z"));
    Recipe b = recipe("B", 0.8, Instant.parse("2026-01-02T00:00:00Z"));
    Recipe c = recipe("C", 0.7, Instant.parse("2026-01-01T00:00:00Z"));
    givenProfile(List.of());
    when(recipeRepository.findApprovedByDishIdIn(any())).thenReturn(List.of(a, b, c));

    Map<UUID, List<Recipe>> result = service.recommendFor(List.of(dishId), userId, null, 2);

    assertThat(result.get(dishId)).containsExactly(a, b);
  }

  @Test
  void recommendFor_unknownNutritionFailsACalorieCeiling() {
    Recipe known = recipe("Known", 0.5, Instant.parse("2026-01-02T00:00:00Z"));
    known.setCalories(new BigDecimal("400"));
    Recipe unknown = recipe("Unknown", 0.6, Instant.parse("2026-01-01T00:00:00Z"));
    unknown.setCalories(null);
    givenProfile(List.of());
    when(recipeRepository.findApprovedByDishIdIn(any())).thenReturn(List.of(known, unknown));

    Map<UUID, List<Recipe>> result =
        service.recommendFor(
            List.of(dishId),
            userId,
            RecipeFilter.builder().maxCalories(new BigDecimal("500")).build(),
            3);

    assertThat(result.get(dishId)).containsExactly(known);
  }

  @Test
  void recommendFor_emptyDishIds_neverHitsTheDatabase() {
    assertThat(service.recommendFor(List.of(), userId, null, 3)).isEmpty();
  }

  private void givenProfile(List<String> allergies) {
    UserProfile profile =
        UserProfile.builder()
            .dietaryPreferences(DietaryPreferences.builder().allergies(allergies).build())
            .build();
    when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
  }

  private Recipe recipe(String name, Double score, Instant createdAt) {
    return Recipe.builder()
        .id(UUID.randomUUID())
        .name(name)
        .score(score)
        .createdAt(createdAt)
        .moderationStatus(ModerationStatus.APPROVED)
        .dish(dish())
        .build();
  }

  private Dish dish() {
    return Dish.builder()
        .id(dishId)
        .name("Pad Thai")
        .moderationStatus(ModerationStatus.APPROVED)
        .build();
  }

  private Object[] line(Recipe recipe, String ingredientName) {
    return new Object[] {recipe.getId(), ingredientName};
  }
}
