package com.vegalife.unit.service.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.response.recipe.DishIngredientSuggestionResponse;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.DishIngredient;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.repository.recipe.DishIngredientRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.service.recipe.DishIngredientService;
import com.vegalife.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DishIngredientServiceTest {

  @Mock private DishIngredientRepository dishIngredientRepository;

  @Mock private DishRepository dishRepository;

  @Mock private RecipeIngredientRepository recipeIngredientRepository;

  @InjectMocks private DishIngredientService dishIngredientService;

  private Dish dish;
  private Ingredient noodles;
  private Ingredient basil;

  @BeforeEach
  void setUp() {
    dish =
        Dish.builder()
            .id(UUID.randomUUID())
            .name("pho")
            .moderationStatus(ModerationStatus.APPROVED)
            .build();
    noodles =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("noodles")
            .moderationStatus(ModerationStatus.APPROVED)
            .build();
    basil =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("basil")
            .moderationStatus(ModerationStatus.APPROVED)
            .build();
  }

  @Test
  void listSuggestions_approvedDish_mapsEntriesInQueryOrder() {
    when(dishRepository.findByIdAndDeletedAtIsNullAndModerationStatus(
            dish.getId(), ModerationStatus.APPROVED))
        .thenReturn(Optional.of(dish));
    when(dishIngredientRepository.findVisibleWithIngredient(
            dish.getId(), ModerationStatus.APPROVED))
        .thenReturn(List.of(entry(noodles, true), entry(basil, false)));

    List<DishIngredientSuggestionResponse> result =
        dishIngredientService.listSuggestions(dish.getId());

    assertThat(result)
        .extracting(DishIngredientSuggestionResponse::getName)
        .containsExactly("noodles", "basil");
    assertThat(result.get(0).isRequired()).isTrue();
    assertThat(result.get(1).isRequired()).isFalse();
    assertThat(result.get(0).getIngredientId()).isEqualTo(noodles.getId());
  }

  @Test
  void listSuggestions_pendingDish_throwsNotFound() {
    when(dishRepository.findByIdAndDeletedAtIsNullAndModerationStatus(
            dish.getId(), ModerationStatus.APPROVED))
        .thenReturn(Optional.empty());

    // A member-facing lookup must not confirm that an unreviewed name exists (BR-DISH-001).
    assertThatThrownBy(() -> dishIngredientService.listSuggestions(dish.getId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Dish not found");
  }

  @Test
  void missingRequiredIngredientNames_skipsIngredientsTheRecipeUses() {
    when(dishIngredientRepository.findRequiredWithIngredient(dish.getId()))
        .thenReturn(List.of(entry(noodles, true), entry(basil, true)));

    List<String> missing =
        dishIngredientService.missingRequiredIngredientNames(
            dish.getId(), List.of(noodles.getId()));

    assertThat(missing).containsExactly("basil");
  }

  @Test
  void missingRequiredIngredientNames_whenNothingMissing_returnsEmpty() {
    when(dishIngredientRepository.findRequiredWithIngredient(dish.getId()))
        .thenReturn(List.of(entry(noodles, true)));

    assertThat(
            dishIngredientService.missingRequiredIngredientNames(
                dish.getId(), List.of(noodles.getId())))
        .isEmpty();
  }

  @Test
  void seedFromApprovedRecipe_addsUnknownIngredientsAsNotRequired() {
    Recipe recipe = recipeFor(dish);
    when(recipeIngredientRepository.findByRecipeId(recipe.getId()))
        .thenReturn(List.of(line(recipe, noodles), line(recipe, basil)));
    when(dishIngredientRepository.findByDishIdAndIngredientIdIn(
            dish.getId(), List.of(noodles.getId(), basil.getId())))
        .thenReturn(List.of(entry(noodles, false)));

    dishIngredientService.seedFromApprovedRecipe(recipe);

    ArgumentCaptor<DishIngredient> captor = ArgumentCaptor.forClass(DishIngredient.class);
    verify(dishIngredientRepository).save(captor.capture());
    DishIngredient seeded = captor.getValue();
    assertThat(seeded.getDishId()).isEqualTo(dish.getId());
    assertThat(seeded.getIngredientId()).isEqualTo(basil.getId());
    // Seeding never asserts a requirement: only an admin may do that.
    assertThat(seeded.isRequired()).isFalse();
  }

  @Test
  void seedFromApprovedRecipe_withdrawnEntryIsNotResurrected() {
    Recipe recipe = recipeFor(dish);
    when(recipeIngredientRepository.findByRecipeId(recipe.getId()))
        .thenReturn(List.of(line(recipe, noodles)));
    DishIngredient withdrawn = entry(noodles, true);
    withdrawn.setRemovedAt(Instant.now());
    when(dishIngredientRepository.findByDishIdAndIngredientIdIn(
            dish.getId(), List.of(noodles.getId())))
        .thenReturn(List.of(withdrawn));

    dishIngredientService.seedFromApprovedRecipe(recipe);

    // The tombstone counts as "known": reinstating it is an explicit admin action (BR-DISH-003).
    verify(dishIngredientRepository, never()).save(any(DishIngredient.class));
  }

  @Test
  void seedFromApprovedRecipe_withoutIngredients_doesNothing() {
    Recipe recipe = recipeFor(dish);
    when(recipeIngredientRepository.findByRecipeId(recipe.getId())).thenReturn(List.of());

    dishIngredientService.seedFromApprovedRecipe(recipe);

    verify(dishIngredientRepository, never()).save(any(DishIngredient.class));
    verify(dishIngredientRepository, never()).findByDishIdAndIngredientIdIn(any(), anyCollection());
  }

  private DishIngredient entry(Ingredient ingredient, boolean required) {
    return DishIngredient.builder()
        .dishId(dish.getId())
        .ingredientId(ingredient.getId())
        .ingredient(ingredient)
        .required(required)
        .build();
  }

  private Recipe recipeFor(Dish target) {
    return Recipe.builder().id(UUID.randomUUID()).dish(target).build();
  }

  private RecipeIngredient line(Recipe recipe, Ingredient ingredient) {
    return RecipeIngredient.builder()
        .recipeId(recipe.getId())
        .ingredientId(ingredient.getId())
        .ingredient(ingredient)
        .build();
  }
}
