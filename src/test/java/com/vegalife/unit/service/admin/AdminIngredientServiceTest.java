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
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.service.admin.AdminIngredientService;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
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
    assertThat(lastLog().getAction()).isEqualTo("CREATE_INGREDIENT");
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
