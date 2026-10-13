package com.vegalife.unit.service.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.recipe.RecipeMapper;
import com.vegalife.dto.request.recipe.RecipeCreateRequest;
import com.vegalife.dto.request.recipe.RecipeIngredientItem;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.model.user.User;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.RecipeIngredientRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.service.recipe.RecipeService;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.math.BigDecimal;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

  @Mock private RecipeRepository recipeRepository;

  @Mock private DishRepository dishRepository;

  @Mock private IngredientRepository ingredientRepository;

  @Mock private RecipeIngredientRepository recipeIngredientRepository;

  @Mock private UserRepository userRepository;

  @Mock private RecipeMapper recipeMapper;

  @Mock private TransactionTemplate transactionTemplate;

  @InjectMocks private RecipeService recipeService;

  private final UUID userId = UUID.randomUUID();
  private final UUID dishId = UUID.randomUUID();
  private final UUID ingredientId = UUID.randomUUID();
  private final UUID recipeId = UUID.randomUUID();
  private User user;

  @BeforeEach
  void setUp() {
    user = User.builder().id(userId).username("alice").build();
    lenient()
        .when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              TransactionCallback<RecipeResponse> callback = invocation.getArgument(0);
              return callback.doInTransaction(mock(TransactionStatus.class));
            });
  }

  @Test
  void createRecipe_newDishAndIngredient_savesNormalizedRowsAndLinks() {
    stubUserFound();
    when(dishRepository.findByNameIgnoreCaseAndDeletedAtIsNull("tofu stir-fry"))
        .thenReturn(Optional.empty());
    when(dishRepository.saveAndFlush(any(Dish.class)))
        .thenAnswer(invocation -> Dish.builder().id(dishId).name("tofu stir-fry").build());
    when(ingredientRepository.findByNameIgnoreCase("tofu")).thenReturn(Optional.empty());
    when(ingredientRepository.saveAndFlush(any(Ingredient.class)))
        .thenAnswer(invocation -> Ingredient.builder().id(ingredientId).name("tofu").build());
    when(recipeRepository.save(any(Recipe.class)))
        .thenAnswer(
            invocation -> {
              Recipe recipe = invocation.getArgument(0);
              recipe.setId(recipeId);
              return recipe;
            });
    RecipeResponse expected = RecipeResponse.builder().id(recipeId).build();
    when(recipeMapper.toResponse(any(Recipe.class), eq("tofu stir-fry"), anyList()))
        .thenReturn(expected);

    RecipeResponse result =
        recipeService.createRecipe(userId, createRequest("  TOFU STIR-FRY  ", "EASY"));

    assertThat(result).isSameAs(expected);

    ArgumentCaptor<Dish> dishCaptor = ArgumentCaptor.forClass(Dish.class);
    verify(dishRepository).saveAndFlush(dishCaptor.capture());
    assertThat(dishCaptor.getValue().getName()).isEqualTo("tofu stir-fry");

    ArgumentCaptor<Ingredient> ingredientCaptor = ArgumentCaptor.forClass(Ingredient.class);
    verify(ingredientRepository).saveAndFlush(ingredientCaptor.capture());
    assertThat(ingredientCaptor.getValue().getName()).isEqualTo("tofu");

    ArgumentCaptor<Recipe> recipeCaptor = ArgumentCaptor.forClass(Recipe.class);
    verify(recipeRepository).save(recipeCaptor.capture());
    Recipe saved = recipeCaptor.getValue();
    assertThat(saved.getName()).isEqualTo("Rainbow Bowl");
    assertThat(saved.getDescription()).isNull();
    assertThat(saved.getDifficulty()).isEqualTo(Recipe.Difficulty.EASY);
    assertThat(saved.getServings()).isEqualTo(2);
    assertThat(saved.getDish()).isNotNull();
    assertThat(saved.getUser()).isSameAs(user);
    verify(recipeRepository).flush();

    ArgumentCaptor<RecipeIngredient> linkCaptor = ArgumentCaptor.forClass(RecipeIngredient.class);
    verify(recipeIngredientRepository).save(linkCaptor.capture());
    RecipeIngredient link = linkCaptor.getValue();
    assertThat(link.getRecipeId()).isEqualTo(recipeId);
    assertThat(link.getIngredientId()).isEqualTo(ingredientId);
    assertThat(link.getAmount()).isEqualByComparingTo("150");
    assertThat(link.getUnit()).isEqualTo("g");
  }

  @Test
  void createRecipe_existingDishAndIngredient_reusesRowsWithoutSaving() {
    stubUserFound();
    Dish existingDish = Dish.builder().id(dishId).name("tofu stir-fry").build();
    when(dishRepository.findByNameIgnoreCaseAndDeletedAtIsNull("tofu stir-fry"))
        .thenReturn(Optional.of(existingDish));
    Ingredient existingIngredient = Ingredient.builder().id(ingredientId).name("tofu").build();
    when(ingredientRepository.findByNameIgnoreCase("tofu"))
        .thenReturn(Optional.of(existingIngredient));
    when(recipeRepository.save(any(Recipe.class)))
        .thenAnswer(
            invocation -> {
              Recipe recipe = invocation.getArgument(0);
              recipe.setId(recipeId);
              return recipe;
            });
    RecipeResponse expected = RecipeResponse.builder().id(recipeId).build();
    when(recipeMapper.toResponse(any(Recipe.class), eq("tofu stir-fry"), anyList()))
        .thenReturn(expected);

    RecipeResponse result =
        recipeService.createRecipe(userId, createRequest("tofu stir-fry", null));

    assertThat(result).isSameAs(expected);
    verify(dishRepository, never()).saveAndFlush(any(Dish.class));
    verify(ingredientRepository, never()).saveAndFlush(any(Ingredient.class));
  }

  @Test
  void createRecipe_duplicateIngredientNamesCaseInsensitive_throwsBeforeTouchingDatabase() {
    RecipeCreateRequest request =
        RecipeCreateRequest.builder()
            .name("Rainbow Bowl")
            .dishName("tofu stir-fry")
            .instructions("Chop and cook")
            .servings(2)
            .ingredients(
                List.of(ingredientItem("Tofu", "150", "g"), ingredientItem("  tofu ", "50", "g")))
            .build();

    assertThatThrownBy(() -> recipeService.createRecipe(userId, request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Duplicate ingredient name: tofu");

    verifyNoInteractions(
        transactionTemplate,
        userRepository,
        dishRepository,
        ingredientRepository,
        recipeRepository,
        recipeIngredientRepository,
        recipeMapper);
  }

  @Test
  void createRecipe_unknownUser_throwsResourceNotFound() {
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> recipeService.createRecipe(userId, createRequest("tofu stir-fry", "EASY")))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("User not found");

    verify(recipeRepository, never()).save(any(Recipe.class));
  }

  @Test
  void createRecipe_concurrentConflict_retriesOnceAndSucceeds() {
    stubUserFound();
    when(dishRepository.findByNameIgnoreCaseAndDeletedAtIsNull("tofu stir-fry"))
        .thenReturn(Optional.empty());
    when(dishRepository.saveAndFlush(any(Dish.class)))
        .thenAnswer(invocation -> Dish.builder().id(dishId).name("tofu stir-fry").build());
    when(ingredientRepository.findByNameIgnoreCase("tofu")).thenReturn(Optional.empty());
    when(ingredientRepository.saveAndFlush(any(Ingredient.class)))
        .thenAnswer(invocation -> Ingredient.builder().id(ingredientId).name("tofu").build());
    when(recipeRepository.save(any(Recipe.class)))
        .thenAnswer(
            invocation -> {
              Recipe recipe = invocation.getArgument(0);
              recipe.setId(recipeId);
              return recipe;
            });
    RecipeResponse expected = RecipeResponse.builder().id(recipeId).build();
    when(recipeMapper.toResponse(any(Recipe.class), eq("tofu stir-fry"), anyList()))
        .thenReturn(expected);
    doThrow(new DataIntegrityViolationException("duplicate key value"))
        .doAnswer(
            invocation -> {
              TransactionCallback<RecipeResponse> callback = invocation.getArgument(0);
              return callback.doInTransaction(mock(TransactionStatus.class));
            })
        .when(transactionTemplate)
        .execute(any());

    RecipeResponse result =
        recipeService.createRecipe(userId, createRequest("tofu stir-fry", "EASY"));

    assertThat(result).isSameAs(expected);
    verify(transactionTemplate, times(2)).execute(any());
  }

  @Test
  void createRecipe_invalidDifficultyValue_throwsValidationException() {
    stubUserFound();
    when(dishRepository.findByNameIgnoreCaseAndDeletedAtIsNull("tofu stir-fry"))
        .thenReturn(Optional.of(Dish.builder().id(dishId).name("tofu stir-fry").build()));
    when(ingredientRepository.findByNameIgnoreCase("tofu"))
        .thenReturn(Optional.of(Ingredient.builder().id(ingredientId).name("tofu").build()));

    assertThatThrownBy(
            () -> recipeService.createRecipe(userId, createRequest("tofu stir-fry", "SPICY")))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Difficulty must be EASY, MEDIUM, or HARD");

    verify(recipeRepository, never()).save(any(Recipe.class));
  }

  private void stubUserFound() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
  }

  private RecipeCreateRequest createRequest(String dishName, String difficulty) {
    return RecipeCreateRequest.builder()
        .name("Rainbow Bowl")
        .dishName(dishName)
        .description("   ")
        .instructions("Chop and cook")
        .prepTimeMinutes(10)
        .cookTimeMinutes(20)
        .servings(2)
        .difficulty(difficulty)
        .ingredients(List.of(ingredientItem(" Tofu ", "150", "g")))
        .build();
  }

  private RecipeIngredientItem ingredientItem(String name, String amount, String unit) {
    return RecipeIngredientItem.builder()
        .name(name)
        .amount(new BigDecimal(amount))
        .unit(unit)
        .build();
  }
}
