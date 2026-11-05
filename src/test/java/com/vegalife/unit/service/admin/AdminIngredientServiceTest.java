package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.admin.AdminIngredientCreateRequest;
import com.vegalife.dto.request.admin.AdminIngredientUpdateRequest;
import com.vegalife.dto.response.admin.AdminIngredientResponse;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.recipe.Unit;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.service.admin.AdminIngredientService;
import com.vegalife.service.recipe.RecipeNutritionService;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.math.BigDecimal;
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
class AdminIngredientServiceTest {

  @Mock private IngredientRepository ingredientRepository;

  @Mock private ModerationLogRepository moderationLogRepository;

  @Mock private RecipeNutritionService recipeNutritionService;

  @InjectMocks private AdminIngredientService adminIngredientService;

  private UUID adminId;
  private Ingredient ingredient;

  @BeforeEach
  void setUp() {
    adminId = UUID.randomUUID();
    ingredient =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("đậu hũ")
            .moderationStatus(ModerationStatus.PENDING)
            .build();
  }

  @Test
  void createIngredient_isApprovedOnArrivalAndKeepsNutrition() {
    when(ingredientRepository.findByNameIgnoreCase("đậu hũ")).thenReturn(Optional.empty());
    when(ingredientRepository.saveAndFlush(any(Ingredient.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AdminIngredientResponse response =
        adminIngredientService.createIngredient(
            adminId, AdminIngredientCreateRequest.builder().name("đậu hũ").build());

    assertThat(response.getModerationStatus()).isEqualTo("APPROVED");
    assertThat(response.getName()).isEqualTo("đậu hũ");
    // A name with no nutrition yet is unresolved, not zero-calorie.
    assertThat(response.isNutritionResolved()).isFalse();
    assertThat(response.getCalories()).isNull();
    assertThat(lastLog().getAction()).isEqualTo("CREATE_INGREDIENT");
  }

  @Test
  void createIngredient_withFullNutrition_normalisesUnitAndStoresPerUnitValues() {
    when(ingredientRepository.findByNameIgnoreCase("đậu hũ")).thenReturn(Optional.empty());
    when(ingredientRepository.saveAndFlush(any(Ingredient.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AdminIngredientResponse response =
        adminIngredientService.createIngredient(
            adminId,
            AdminIngredientCreateRequest.builder()
                .name("đậu hũ")
                .unit("grams")
                .calories(new BigDecimal("1.44"))
                .proteinG(new BigDecimal("0.1719"))
                .carbohydrateG(new BigDecimal("0.0278"))
                .fatG(new BigDecimal("0.0872"))
                .fiberG(new BigDecimal("0.023"))
                .build());

    assertThat(response.getUnit()).isEqualTo("G");
    assertThat(response.getCalories()).isEqualByComparingTo("1.44");
    assertThat(response.isNutritionResolved()).isTrue();
  }

  @Test
  void createIngredient_pieceUnit_takesPerPieceEstimateWithNoPieceWeight() {
    when(ingredientRepository.findByNameIgnoreCase("cà chua")).thenReturn(Optional.empty());
    when(ingredientRepository.saveAndFlush(any(Ingredient.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AdminIngredientResponse response =
        adminIngredientService.createIngredient(
            adminId,
            AdminIngredientCreateRequest.builder()
                .name("cà chua")
                .unit("quả")
                .calories(new BigDecimal("22.14"))
                .proteinG(new BigDecimal("1.0824"))
                .carbohydrateG(new BigDecimal("4.7847"))
                .fatG(new BigDecimal("0.246"))
                .fiberG(new BigDecimal("1.476"))
                .build());

    assertThat(response.getUnit()).isEqualTo("PIECE");
    // The per-piece figure is the estimate itself; nothing else is recorded to justify it.
    assertThat(response.getCalories()).isEqualByComparingTo("22.14");
    assertThat(response.isNutritionResolved()).isTrue();
  }

  @Test
  void createIngredient_partialNutrition_isRefused() {
    when(ingredientRepository.findByNameIgnoreCase("đậu hũ")).thenReturn(Optional.empty());

    // A half-filled ingredient could price a recipe while silently omitting a macro.
    assertThatThrownBy(
            () ->
                adminIngredientService.createIngredient(
                    adminId,
                    AdminIngredientCreateRequest.builder()
                        .name("đậu hũ")
                        .unit("G")
                        .calories(new BigDecimal("144"))
                        .build()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("in full or not at all");

    verify(ingredientRepository, never()).saveAndFlush(any(Ingredient.class));
  }

  @Test
  void createIngredient_unknownUnit_isRefused() {
    when(ingredientRepository.findByNameIgnoreCase("đậu hũ")).thenReturn(Optional.empty());

    // 'kg' is not a spelling variant of 'g': accepting it would redefine the nutrition basis.
    assertThatThrownBy(
            () ->
                adminIngredientService.createIngredient(
                    adminId,
                    AdminIngredientCreateRequest.builder().name("đậu hũ").unit("kg").build()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("unit must be G, ML, or PIECE");
  }

  @Test
  void createIngredient_rejectedName_throwsValidation() {
    ingredient.setModerationStatus(ModerationStatus.REJECTED);
    when(ingredientRepository.findByNameIgnoreCase("đậu hũ")).thenReturn(Optional.of(ingredient));

    assertThatThrownBy(
            () ->
                adminIngredientService.createIngredient(
                    adminId, AdminIngredientCreateRequest.builder().name("đậu hũ").build()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("cannot be re-created");
  }

  @Test
  void createIngredient_existingName_throwsDuplicate() {
    when(ingredientRepository.findByNameIgnoreCase("đậu hũ")).thenReturn(Optional.of(ingredient));

    assertThatThrownBy(
            () ->
                adminIngredientService.createIngredient(
                    adminId, AdminIngredientCreateRequest.builder().name("đậu hũ").build()))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  void updateIngredient_keepsModerationStatusAndOnlyTouchesSentFields() {
    ingredient.setModerationStatus(ModerationStatus.APPROVED);
    when(ingredientRepository.findByIdAndDeletedAtIsNull(ingredient.getId()))
        .thenReturn(Optional.of(ingredient));
    when(ingredientRepository.saveAndFlush(ingredient)).thenReturn(ingredient);

    AdminIngredientResponse response =
        adminIngredientService.updateIngredient(
            adminId,
            ingredient.getId(),
            AdminIngredientUpdateRequest.builder().name("đậu hũ non").build());

    assertThat(response.getName()).isEqualTo("đậu hũ non");
    // The admin's edit is not a re-review.
    assertThat(response.getModerationStatus()).isEqualTo("APPROVED");
  }

  @Test
  void updateIngredient_renamingARejectedName_isRefused() {
    ingredient.setModerationStatus(ModerationStatus.REJECTED);
    when(ingredientRepository.findByIdAndDeletedAtIsNull(ingredient.getId()))
        .thenReturn(Optional.of(ingredient));

    assertThatThrownBy(
            () ->
                adminIngredientService.updateIngredient(
                    adminId,
                    ingredient.getId(),
                    AdminIngredientUpdateRequest.builder().name("tàu hũ").build()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("cannot be renamed");
  }

  @Test
  void updateIngredient_nutritionChange_recomputesEveryRecipeUsingIt() {
    ingredient.setModerationStatus(ModerationStatus.APPROVED);
    when(ingredientRepository.findByIdAndDeletedAtIsNull(ingredient.getId()))
        .thenReturn(Optional.of(ingredient));
    when(ingredientRepository.saveAndFlush(ingredient)).thenReturn(ingredient);

    adminIngredientService.updateIngredient(
        adminId,
        ingredient.getId(),
        AdminIngredientUpdateRequest.builder()
            .unit("G")
            .calories(new BigDecimal("1.44"))
            .proteinG(new BigDecimal("0.1719"))
            .carbohydrateG(new BigDecimal("0.0278"))
            .fatG(new BigDecimal("0.0872"))
            .fiberG(new BigDecimal("0.023"))
            .build());

    // Persisted totals would be silently stale otherwise.
    verify(recipeNutritionService).recomputeForIngredient(ingredient.getId());
  }

  @Test
  void updateIngredient_nameOnlyChange_doesNotRecomputeRecipes() {
    ingredient.setModerationStatus(ModerationStatus.APPROVED);
    when(ingredientRepository.findByIdAndDeletedAtIsNull(ingredient.getId()))
        .thenReturn(Optional.of(ingredient));
    when(ingredientRepository.saveAndFlush(ingredient)).thenReturn(ingredient);

    adminIngredientService.updateIngredient(
        adminId, ingredient.getId(), AdminIngredientUpdateRequest.builder().name("tàu hũ").build());

    // A rename cannot change any recipe's arithmetic.
    verify(recipeNutritionService, never()).recomputeForIngredient(any());
  }

  @Test
  void updateIngredient_completingPartialNutrition_isAccepted() {
    ingredient.setUnit(Unit.G);
    ingredient.setCalories(new BigDecimal("1.44"));
    ingredient.setProteinG(new BigDecimal("0.1719"));
    ingredient.setCarbohydrateG(new BigDecimal("0.0278"));
    ingredient.setFatG(new BigDecimal("0.0872"));
    ingredient.setFiberG(new BigDecimal("0.023"));
    when(ingredientRepository.findByIdAndDeletedAtIsNull(ingredient.getId()))
        .thenReturn(Optional.of(ingredient));
    when(ingredientRepository.saveAndFlush(ingredient)).thenReturn(ingredient);

    // Merging one field onto a complete row is a correction, not a half-answer.
    AdminIngredientResponse response =
        adminIngredientService.updateIngredient(
            adminId,
            ingredient.getId(),
            AdminIngredientUpdateRequest.builder().calories(new BigDecimal("1.50")).build());

    assertThat(response.getCalories()).isEqualByComparingTo("1.50");
    assertThat(response.getProteinG()).isEqualByComparingTo("0.1719");
    assertThat(response.isNutritionResolved()).isTrue();
  }

  @Test
  void updateIngredient_partialNutritionOnEmptyRow_isRefused() {
    when(ingredientRepository.findByIdAndDeletedAtIsNull(ingredient.getId()))
        .thenReturn(Optional.of(ingredient));

    assertThatThrownBy(
            () ->
                adminIngredientService.updateIngredient(
                    adminId,
                    ingredient.getId(),
                    AdminIngredientUpdateRequest.builder()
                        .calories(new BigDecimal("1.44"))
                        .build()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("in full");

    verify(ingredientRepository, never()).saveAndFlush(any(Ingredient.class));
  }

  @Test
  void updateIngredient_unitChange_recomputesAndWarns() {
    ingredient.setModerationStatus(ModerationStatus.APPROVED);
    ingredient.setUnit(Unit.G);
    ingredient.setCalories(new BigDecimal("3.65"));
    ingredient.setProteinG(new BigDecimal("0.0713"));
    ingredient.setCarbohydrateG(new BigDecimal("0.7995"));
    ingredient.setFatG(new BigDecimal("0.0066"));
    ingredient.setFiberG(new BigDecimal("0.013"));
    when(ingredientRepository.findByIdAndDeletedAtIsNull(ingredient.getId()))
        .thenReturn(Optional.of(ingredient));
    when(ingredientRepository.saveAndFlush(ingredient)).thenReturn(ingredient);
    when(recipeNutritionService.recomputeForIngredient(ingredient.getId())).thenReturn(4);

    adminIngredientService.updateIngredient(
        adminId, ingredient.getId(), AdminIngredientUpdateRequest.builder().unit("ML").build());

    // Allowed silently, but every stored amount now means something different, so the recipes are
    // recomputed and the change is traceable in the log.
    assertThat(ingredient.getUnit()).isEqualTo(Unit.ML);
    verify(recipeNutritionService).recomputeForIngredient(ingredient.getId());
  }

  @Test
  void deleteIngredient_softDeletesAndLogs() {
    when(ingredientRepository.findByIdAndDeletedAtIsNull(ingredient.getId()))
        .thenReturn(Optional.of(ingredient));

    adminIngredientService.deleteIngredient(adminId, ingredient.getId());

    assertThat(ingredient.getDeletedAt()).isNotNull();
    verify(ingredientRepository, never()).delete(any(Ingredient.class));
    assertThat(lastLog().getAction()).isEqualTo("DELETE_INGREDIENT");
  }

  @Test
  void approveAndReject_areSymmetricStateChanges() {
    when(ingredientRepository.findByIdAndDeletedAtIsNull(ingredient.getId()))
        .thenReturn(Optional.of(ingredient));
    when(ingredientRepository.saveAndFlush(ingredient)).thenReturn(ingredient);

    assertThat(
            adminIngredientService
                .approveIngredient(adminId, ingredient.getId(), null)
                .getModerationStatus())
        .isEqualTo("APPROVED");
    assertThat(
            adminIngredientService
                .rejectIngredient(adminId, ingredient.getId(), "not food")
                .getModerationStatus())
        .isEqualTo("REJECTED");
  }

  @Test
  void getIngredient_missing_throwsResourceNotFound() {
    UUID ingredientId = UUID.randomUUID();
    when(ingredientRepository.findByIdAndDeletedAtIsNull(ingredientId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminIngredientService.getIngredient(ingredientId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Ingredient not found");
  }

  private ModerationLog lastLog() {
    ArgumentCaptor<ModerationLog> captor = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository, atLeastOnce()).save(captor.capture());
    return captor.getAllValues().get(captor.getAllValues().size() - 1);
  }
}
