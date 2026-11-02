package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.admin.AdminDishIngredientMapper;
import com.vegalife.dto.request.admin.AdminDishIngredientCreateRequest;
import com.vegalife.dto.request.admin.AdminDishIngredientUpdateRequest;
import com.vegalife.dto.response.admin.AdminDishIngredientResponse;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.DishIngredient;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.repository.recipe.DishIngredientRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.service.admin.AdminDishIngredientService;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
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
class AdminDishIngredientServiceTest {

  @Mock private DishIngredientRepository dishIngredientRepository;

  @Mock private DishRepository dishRepository;

  @Mock private IngredientRepository ingredientRepository;

  @Mock private AdminDishIngredientMapper adminDishIngredientMapper;

  @InjectMocks private AdminDishIngredientService adminDishIngredientService;

  private UUID adminId;
  private Dish dish;
  private Ingredient tofu;
  private AdminDishIngredientResponse response;

  @BeforeEach
  void setUp() {
    adminId = UUID.randomUUID();
    dish =
        Dish.builder()
            .id(UUID.randomUUID())
            .name("pho")
            .moderationStatus(ModerationStatus.APPROVED)
            .build();
    tofu =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("tofu")
            .moderationStatus(ModerationStatus.APPROVED)
            .build();
    response =
        AdminDishIngredientResponse.builder()
            .ingredientId(tofu.getId())
            .name("tofu")
            .moderationStatus("APPROVED")
            .required(false)
            .build();
  }

  @Test
  void listDishIngredients_returnsActiveEntries() {
    stubActiveDish();
    DishIngredient entry = entry(null);
    when(dishIngredientRepository.findActiveWithIngredient(dish.getId()))
        .thenReturn(List.of(entry));
    when(adminDishIngredientMapper.toResponse(entry)).thenReturn(response);

    assertThat(adminDishIngredientService.listDishIngredients(dish.getId()))
        .containsExactly(response);
  }

  @Test
  void listDishIngredients_missingDish_throwsNotFound() {
    when(dishRepository.findByIdAndDeletedAtIsNull(dish.getId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminDishIngredientService.listDishIngredients(dish.getId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Dish not found");
  }

  @Test
  void addDishIngredient_newName_createsApprovedIngredientAndEntry() {
    stubActiveDish();
    when(ingredientRepository.findByNameIgnoreCase("tofu")).thenReturn(Optional.empty());
    when(ingredientRepository.saveAndFlush(any(Ingredient.class)))
        .thenAnswer(
            invocation -> {
              Ingredient saved = invocation.getArgument(0);
              saved.setId(tofu.getId());
              return saved;
            });
    when(dishIngredientRepository.findByDishIdAndIngredientId(dish.getId(), tofu.getId()))
        .thenReturn(Optional.empty());
    when(dishIngredientRepository.saveAndFlush(any(DishIngredient.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(ingredientRepository.findById(tofu.getId())).thenReturn(Optional.of(tofu));
    when(adminDishIngredientMapper.toResponse(any(DishIngredient.class))).thenReturn(response);

    AdminDishIngredientService.AddResult result =
        adminDishIngredientService.addDishIngredient(
            adminId,
            dish.getId(),
            AdminDishIngredientCreateRequest.builder()
                .ingredientName("  Tofu  ")
                .required(true)
                .build());

    assertThat(result.created()).isTrue();

    // The name is normalized the same way recipe creation does (BR-RECP-002) and, because an admin
    // introduced it, arrives already approved.
    ArgumentCaptor<Ingredient> ingredientCaptor = ArgumentCaptor.forClass(Ingredient.class);
    verify(ingredientRepository).saveAndFlush(ingredientCaptor.capture());
    assertThat(ingredientCaptor.getValue().getName()).isEqualTo("tofu");
    assertThat(ingredientCaptor.getValue().getModerationStatus())
        .isEqualTo(ModerationStatus.APPROVED);

    ArgumentCaptor<DishIngredient> entryCaptor = ArgumentCaptor.forClass(DishIngredient.class);
    verify(dishIngredientRepository).saveAndFlush(entryCaptor.capture());
    assertThat(entryCaptor.getValue().getDishId()).isEqualTo(dish.getId());
    assertThat(entryCaptor.getValue().getIngredientId()).isEqualTo(tofu.getId());
    assertThat(entryCaptor.getValue().isRequired()).isTrue();

    // The freshly built entry carries no association, so the mapping step must resolve the name —
    // otherwise the create response would come back with name: null.
    ArgumentCaptor<DishIngredient> mappedCaptor = ArgumentCaptor.forClass(DishIngredient.class);
    verify(adminDishIngredientMapper).toResponse(mappedCaptor.capture());
    assertThat(mappedCaptor.getValue().getIngredient()).isEqualTo(tofu);
  }

  @Test
  void addDishIngredient_newNameWithoutRequiredFlag_defaultsToSuggestion() {
    stubActiveDish();
    when(ingredientRepository.findByNameIgnoreCase("tofu")).thenReturn(Optional.of(tofu));
    when(dishIngredientRepository.findByDishIdAndIngredientId(dish.getId(), tofu.getId()))
        .thenReturn(Optional.empty());
    when(dishIngredientRepository.saveAndFlush(any(DishIngredient.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(adminDishIngredientMapper.toResponse(any(DishIngredient.class))).thenReturn(response);

    adminDishIngredientService.addDishIngredient(
        adminId,
        dish.getId(),
        AdminDishIngredientCreateRequest.builder().ingredientName("tofu").build());

    ArgumentCaptor<DishIngredient> captor = ArgumentCaptor.forClass(DishIngredient.class);
    verify(dishIngredientRepository).saveAndFlush(captor.capture());
    assertThat(captor.getValue().isRequired()).isFalse();
  }

  @Test
  void addDishIngredient_existingActiveEntry_throwsDuplicate() {
    stubActiveDish();
    when(ingredientRepository.findByNameIgnoreCase("tofu")).thenReturn(Optional.of(tofu));
    when(dishIngredientRepository.findByDishIdAndIngredientId(dish.getId(), tofu.getId()))
        .thenReturn(Optional.of(entry(null)));

    assertThatThrownBy(
            () ->
                adminDishIngredientService.addDishIngredient(
                    adminId,
                    dish.getId(),
                    AdminDishIngredientCreateRequest.builder().ingredientName("tofu").build()))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessage("Ingredient is already associated with this dish");

    verify(dishIngredientRepository, never()).saveAndFlush(any(DishIngredient.class));
  }

  @Test
  void addDishIngredient_withdrawnEntry_reinstatesInsteadOfFailing() {
    stubActiveDish();
    DishIngredient withdrawn = entry(Instant.now());
    when(ingredientRepository.findByNameIgnoreCase("tofu")).thenReturn(Optional.of(tofu));
    when(dishIngredientRepository.findByDishIdAndIngredientId(dish.getId(), tofu.getId()))
        .thenReturn(Optional.of(withdrawn));
    when(dishIngredientRepository.saveAndFlush(withdrawn)).thenReturn(withdrawn);
    when(adminDishIngredientMapper.toResponse(withdrawn)).thenReturn(response);

    AdminDishIngredientService.AddResult result =
        adminDishIngredientService.addDishIngredient(
            adminId,
            dish.getId(),
            AdminDishIngredientCreateRequest.builder()
                .ingredientName("tofu")
                .required(true)
                .build());

    // Reinstatement is 200, not 201: the row existed all along.
    assertThat(result.created()).isFalse();
    assertThat(withdrawn.getRemovedAt()).isNull();
    assertThat(withdrawn.isRequired()).isTrue();
  }

  @Test
  void addDishIngredient_rejectedName_throwsValidation() {
    stubActiveDish();
    Ingredient rejected =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("tofu")
            .moderationStatus(ModerationStatus.REJECTED)
            .build();
    when(ingredientRepository.findByNameIgnoreCase("tofu")).thenReturn(Optional.of(rejected));

    assertThatThrownBy(
            () ->
                adminDishIngredientService.addDishIngredient(
                    adminId,
                    dish.getId(),
                    AdminDishIngredientCreateRequest.builder().ingredientName("tofu").build()))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Ingredient is not available: tofu");
  }

  @Test
  void addDishIngredient_softDeletedName_throwsValidation() {
    stubActiveDish();
    Ingredient deleted =
        Ingredient.builder()
            .id(UUID.randomUUID())
            .name("tofu")
            .moderationStatus(ModerationStatus.APPROVED)
            .deletedAt(Instant.now())
            .build();
    when(ingredientRepository.findByNameIgnoreCase("tofu")).thenReturn(Optional.of(deleted));

    assertThatThrownBy(
            () ->
                adminDishIngredientService.addDishIngredient(
                    adminId,
                    dish.getId(),
                    AdminDishIngredientCreateRequest.builder().ingredientName("tofu").build()))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Ingredient is not available: tofu");
  }

  @Test
  void updateDishIngredient_flipsTheAdvisoryFlag() {
    stubActiveDish();
    DishIngredient entry = entry(null);
    when(dishIngredientRepository.findByDishIdAndIngredientId(dish.getId(), tofu.getId()))
        .thenReturn(Optional.of(entry));
    when(dishIngredientRepository.saveAndFlush(entry)).thenReturn(entry);
    when(adminDishIngredientMapper.toResponse(entry)).thenReturn(response);

    adminDishIngredientService.updateDishIngredient(
        adminId,
        dish.getId(),
        tofu.getId(),
        AdminDishIngredientUpdateRequest.builder().required(true).build());

    assertThat(entry.isRequired()).isTrue();
  }

  @Test
  void updateDishIngredient_withdrawnEntry_throwsNotFound() {
    stubActiveDish();
    when(dishIngredientRepository.findByDishIdAndIngredientId(dish.getId(), tofu.getId()))
        .thenReturn(Optional.of(entry(Instant.now())));

    assertThatThrownBy(
            () ->
                adminDishIngredientService.updateDishIngredient(
                    adminId,
                    dish.getId(),
                    tofu.getId(),
                    AdminDishIngredientUpdateRequest.builder().required(true).build()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Dish ingredient not found");
  }

  @Test
  void removeDishIngredient_stampsTheTombstone() {
    stubActiveDish();
    DishIngredient entry = entry(null);
    when(dishIngredientRepository.findByDishIdAndIngredientId(dish.getId(), tofu.getId()))
        .thenReturn(Optional.of(entry));
    when(dishIngredientRepository.saveAndFlush(entry)).thenReturn(entry);

    adminDishIngredientService.removeDishIngredient(adminId, dish.getId(), tofu.getId());

    // Soft delete, never a row delete: the tombstone is what stops auto-seeding from re-adding it.
    assertThat(entry.getRemovedAt()).isNotNull();
    verify(dishIngredientRepository, never()).delete(any(DishIngredient.class));
  }

  @Test
  void removeDishIngredient_unknownEntry_throwsNotFound() {
    stubActiveDish();
    when(dishIngredientRepository.findByDishIdAndIngredientId(dish.getId(), tofu.getId()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                adminDishIngredientService.removeDishIngredient(
                    adminId, dish.getId(), tofu.getId()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Dish ingredient not found");
  }

  @Test
  void removeDishIngredient_alreadyWithdrawn_isNoOp() {
    stubActiveDish();
    DishIngredient withdrawn = entry(Instant.parse("2026-10-01T00:00:00Z"));
    when(dishIngredientRepository.findByDishIdAndIngredientId(dish.getId(), tofu.getId()))
        .thenReturn(Optional.of(withdrawn));

    adminDishIngredientService.removeDishIngredient(adminId, dish.getId(), tofu.getId());

    assertThat(withdrawn.getRemovedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    verify(dishIngredientRepository, never()).saveAndFlush(any(DishIngredient.class));
  }

  private void stubActiveDish() {
    when(dishRepository.findByIdAndDeletedAtIsNull(dish.getId())).thenReturn(Optional.of(dish));
  }

  private DishIngredient entry(Instant removedAt) {
    return DishIngredient.builder()
        .dishId(dish.getId())
        .ingredientId(tofu.getId())
        .ingredient(tofu)
        .required(false)
        .removedAt(removedAt)
        .build();
  }
}
