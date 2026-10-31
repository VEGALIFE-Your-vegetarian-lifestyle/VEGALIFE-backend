package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.request.admin.AdminDishCreateRequest;
import com.vegalife.dto.request.admin.AdminDishUpdateRequest;
import com.vegalife.dto.response.admin.AdminDishResponse;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.recipe.Dish;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.service.admin.AdminDishService;
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
class AdminDishServiceTest {

  @Mock private DishRepository dishRepository;

  @Mock private ModerationLogRepository moderationLogRepository;

  @InjectMocks private AdminDishService adminDishService;

  private UUID adminId;
  private Dish dish;

  @BeforeEach
  void setUp() {
    adminId = UUID.randomUUID();
    dish =
        Dish.builder()
            .id(UUID.randomUUID())
            .name("phở chay")
            .moderationStatus(ModerationStatus.PENDING)
            .build();
  }

  @Test
  void createDish_isApprovedOnArrivalAndLogged() {
    when(dishRepository.findByNameIgnoreCase("bún chả chay")).thenReturn(Optional.empty());
    when(dishRepository.saveAndFlush(any(Dish.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AdminDishResponse response =
        adminDishService.createDish(
            adminId, AdminDishCreateRequest.builder().name("  bún chả chay  ").build());

    assertThat(response.getName()).isEqualTo("bún chả chay");
    // The creator is the approver: an admin-created name needs no second review.
    assertThat(response.getModerationStatus()).isEqualTo("APPROVED");
    assertThat(lastLog().getAction()).isEqualTo("CREATE_DISH");
  }

  @Test
  void createDish_existingName_throwsDuplicate() {
    when(dishRepository.findByNameIgnoreCase("phở chay")).thenReturn(Optional.of(dish));

    assertThatThrownBy(
            () ->
                adminDishService.createDish(
                    adminId, AdminDishCreateRequest.builder().name("phở chay").build()))
        .isInstanceOf(DuplicateResourceException.class);

    verify(dishRepository, never()).saveAndFlush(any(Dish.class));
  }

  @Test
  void createDish_rejectedName_throwsValidationBecauseTheBlockIsPermanent() {
    dish.setModerationStatus(ModerationStatus.REJECTED);
    when(dishRepository.findByNameIgnoreCase("phở chay")).thenReturn(Optional.of(dish));

    // Re-creating a rejected name would launder the block by replacing the refused row.
    assertThatThrownBy(
            () ->
                adminDishService.createDish(
                    adminId, AdminDishCreateRequest.builder().name("phở chay").build()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("cannot be re-created");
  }

  @Test
  void updateDish_keepsModerationStatus() {
    dish.setModerationStatus(ModerationStatus.APPROVED);
    when(dishRepository.findByIdAndDeletedAtIsNull(dish.getId())).thenReturn(Optional.of(dish));
    when(dishRepository.saveAndFlush(dish)).thenReturn(dish);

    AdminDishResponse response =
        adminDishService.updateDish(
            adminId,
            dish.getId(),
            AdminDishUpdateRequest.builder().description("  noodle soup ").build());

    assertThat(response.getDescription()).isEqualTo("noodle soup");
    // Re-queueing an approved name would withhold it from every recipe that already uses it.
    assertThat(response.getModerationStatus()).isEqualTo("APPROVED");
  }

  @Test
  void updateDish_renamingARejectedName_isRefused() {
    dish.setModerationStatus(ModerationStatus.REJECTED);
    when(dishRepository.findByIdAndDeletedAtIsNull(dish.getId())).thenReturn(Optional.of(dish));

    // Renaming would free the blocked name for a member to create again.
    assertThatThrownBy(
            () ->
                adminDishService.updateDish(
                    adminId, dish.getId(), AdminDishUpdateRequest.builder().name("phở bò").build()))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("cannot be renamed");
  }

  @Test
  void updateDish_nameAlreadyTaken_throwsDuplicate() {
    Dish other =
        Dish.builder()
            .id(UUID.randomUUID())
            .name("bún riêu")
            .moderationStatus(ModerationStatus.APPROVED)
            .build();
    when(dishRepository.findByIdAndDeletedAtIsNull(dish.getId())).thenReturn(Optional.of(dish));
    when(dishRepository.findByNameIgnoreCase("bún riêu")).thenReturn(Optional.of(other));

    assertThatThrownBy(
            () ->
                adminDishService.updateDish(
                    adminId,
                    dish.getId(),
                    AdminDishUpdateRequest.builder().name("bún riêu").build()))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  void updateDish_caseOnlyRename_isAppliedBecauseTheOnlyMatchIsItself() {
    when(dishRepository.findByIdAndDeletedAtIsNull(dish.getId())).thenReturn(Optional.of(dish));
    // A case-insensitive lookup for the new name finds this very row.
    when(dishRepository.findByNameIgnoreCase("PHỞ CHAY")).thenReturn(Optional.of(dish));
    when(dishRepository.saveAndFlush(dish)).thenReturn(dish);

    AdminDishResponse response =
        adminDishService.updateDish(
            adminId, dish.getId(), AdminDishUpdateRequest.builder().name("PHỞ CHAY").build());

    assertThat(response.getName()).isEqualTo("PHỞ CHAY");
  }

  @Test
  void deleteDish_softDeletesAndLogs() {
    when(dishRepository.findByIdAndDeletedAtIsNull(dish.getId())).thenReturn(Optional.of(dish));

    adminDishService.deleteDish(adminId, dish.getId());

    assertThat(dish.getDeletedAt()).isNotNull();
    verify(dishRepository).save(dish);
    // Never hard: recipes and menu_detail rows still reference it.
    verify(dishRepository, never()).delete(any(Dish.class));
    assertThat(lastLog().getAction()).isEqualTo("DELETE_DISH");
  }

  @Test
  void approveDish_pending_becomesApprovedAndLogged() {
    when(dishRepository.findByIdAndDeletedAtIsNull(dish.getId())).thenReturn(Optional.of(dish));
    when(dishRepository.saveAndFlush(dish)).thenReturn(dish);

    AdminDishResponse response = adminDishService.approveDish(adminId, dish.getId(), "valid food");

    assertThat(response.getModerationStatus()).isEqualTo("APPROVED");
    assertThat(lastLog().getAction()).isEqualTo("APPROVE_COMPONENT");
    assertThat(lastLog().getReason()).isEqualTo("valid food");
  }

  @Test
  void approveDish_rejected_isTheCorrectionPath() {
    dish.setModerationStatus(ModerationStatus.REJECTED);
    when(dishRepository.findByIdAndDeletedAtIsNull(dish.getId())).thenReturn(Optional.of(dish));
    when(dishRepository.saveAndFlush(dish)).thenReturn(dish);

    AdminDishResponse response = adminDishService.approveDish(adminId, dish.getId(), "mistyped");

    // Post approval must never do this, but an admin naming the row by id is making exactly this
    // decision, and without it a mistyped rejection would poison a real name forever.
    assertThat(response.getModerationStatus()).isEqualTo("APPROVED");
  }

  @Test
  void approveDish_alreadyApproved_isNoOpWithoutLog() {
    dish.setModerationStatus(ModerationStatus.APPROVED);
    when(dishRepository.findByIdAndDeletedAtIsNull(dish.getId())).thenReturn(Optional.of(dish));

    adminDishService.approveDish(adminId, dish.getId(), null);

    verify(dishRepository, never()).saveAndFlush(any(Dish.class));
    verify(moderationLogRepository, never()).save(any(ModerationLog.class));
  }

  @Test
  void rejectDish_approved_becomesRejected() {
    dish.setModerationStatus(ModerationStatus.APPROVED);
    when(dishRepository.findByIdAndDeletedAtIsNull(dish.getId())).thenReturn(Optional.of(dish));
    when(dishRepository.saveAndFlush(dish)).thenReturn(dish);

    AdminDishResponse response = adminDishService.rejectDish(adminId, dish.getId(), "not food");

    assertThat(response.getModerationStatus()).isEqualTo("REJECTED");
    assertThat(dish.getDeletedAt()).isNull();
    assertThat(lastLog().getAction()).isEqualTo("REJECT_COMPONENT");
  }

  @Test
  void getDish_missingOrSoftDeleted_throwsResourceNotFound() {
    UUID dishId = UUID.randomUUID();
    when(dishRepository.findByIdAndDeletedAtIsNull(dishId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminDishService.getDish(dishId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Dish not found");
  }

  private ModerationLog lastLog() {
    ArgumentCaptor<ModerationLog> captor = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
    return captor.getAllValues().get(captor.getAllValues().size() - 1);
  }
}
