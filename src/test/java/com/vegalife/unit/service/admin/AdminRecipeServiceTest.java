package com.vegalife.unit.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vegalife.dto.mapper.admin.AdminRecipeMapper;
import com.vegalife.dto.request.admin.AdminRecipeUpdateRequest;
import com.vegalife.dto.request.admin.RecipeListRequest;
import com.vegalife.dto.response.admin.AdminRecipeListResponse;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.ModerationStatus;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.user.User;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.service.admin.AdminRecipeService;
import com.vegalife.shared.dto.PageResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AdminRecipeServiceTest {

  @Mock private RecipeRepository recipeRepository;

  @Mock private AdminRecipeMapper adminRecipeMapper;

  @Mock private ModerationLogRepository moderationLogRepository;

  @InjectMocks private AdminRecipeService adminRecipeService;

  private User author;
  private Recipe recipe;
  private AdminRecipeListResponse recipeResponse;
  private Pageable expectedPageable;

  @BeforeEach
  void setUp() {
    author =
        User.builder()
            .id(UUID.randomUUID())
            .username("janedoe")
            .email("jane@example.com")
            .passwordHash("$2a$10$secret")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    Dish dish = Dish.builder().id(UUID.randomUUID()).name("Bowl").build();
    recipe =
        Recipe.builder()
            .id(UUID.randomUUID())
            .dish(dish)
            .user(author)
            .name("Vegan chili")
            .instructions("Simmer everything")
            .servings(4)
            .difficulty(Recipe.Difficulty.MEDIUM)
            .createdAt(Instant.parse("2026-09-21T10:00:00Z"))
            .build();
    recipeResponse =
        AdminRecipeListResponse.builder()
            .id(recipe.getId())
            .name("Vegan chili")
            .difficulty("MEDIUM")
            .status("ACTIVE")
            .dishId(dish.getId())
            .dishName("Bowl")
            .createdAt(recipe.getCreatedAt())
            .userId(author.getId())
            .username("janedoe")
            .email("jane@example.com")
            .build();
    expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  void listRecipes_withDefaults_usesDefaultPaginationAndSort() {
    Page<Recipe> page = new PageImpl<>(List.of(recipe), expectedPageable, 1);
    when(recipeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    when(adminRecipeMapper.toResponse(recipe)).thenReturn(recipeResponse);

    PageResponse<AdminRecipeListResponse> result =
        adminRecipeService.listRecipes(new RecipeListRequest());

    assertThat(result.getContent()).containsExactly(recipeResponse);
    assertThat(result.getPage()).isZero();
    assertThat(result.getSize()).isEqualTo(20);
    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getTotalPages()).isEqualTo(1);
    assertThat(result.isFirst()).isTrue();
    assertThat(result.isLast()).isTrue();

    verify(recipeRepository).findAll(any(Specification.class), eq(expectedPageable));
  }

  @Test
  void listRecipes_withFilters_parsesFiltersAndSort() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-12-31T23:59:59Z");
    RecipeListRequest request =
        RecipeListRequest.builder()
            .page(1)
            .size(10)
            .sort("name,asc")
            .userId(author.getId())
            .categoryId(UUID.randomUUID())
            .createdFrom(from)
            .createdTo(to)
            .build();
    Pageable pageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.ASC, "name"));
    Page<Recipe> page = new PageImpl<>(List.of(recipe), pageable, 1);
    when(recipeRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
    when(adminRecipeMapper.toResponse(recipe)).thenReturn(recipeResponse);

    PageResponse<AdminRecipeListResponse> result = adminRecipeService.listRecipes(request);

    assertThat(result.getContent()).containsExactly(recipeResponse);
    assertThat(result.getPage()).isEqualTo(1);
    assertThat(result.getSize()).isEqualTo(10);
    verify(recipeRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  void listRecipes_whenCreatedFromAfterCreatedTo_throwsValidationException() {
    RecipeListRequest request =
        RecipeListRequest.builder()
            .createdFrom(Instant.parse("2026-12-01T00:00:00Z"))
            .createdTo(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    assertThatThrownBy(() -> adminRecipeService.listRecipes(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("createdFrom must be before createdTo");
  }

  @Test
  void listRecipes_withMalformedSort_throwsValidationException() {
    RecipeListRequest request = RecipeListRequest.builder().sort("createdAt,asc,extra").build();

    assertThatThrownBy(() -> adminRecipeService.listRecipes(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Sort must be in the form property,asc|desc");
  }

  @Test
  void listRecipes_withDisallowedSortProperty_throwsValidationException() {
    RecipeListRequest request = RecipeListRequest.builder().sort("passwordHash,asc").build();

    assertThatThrownBy(() -> adminRecipeService.listRecipes(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage(
            "Sort property must be one of: createdAt, updatedAt, name, moderationStatus, score");
  }

  @Test
  void listRecipes_withEmptyPage_returnsEmptyContent() {
    Page<Recipe> page = Page.empty(expectedPageable);
    when(recipeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

    PageResponse<AdminRecipeListResponse> result =
        adminRecipeService.listRecipes(new RecipeListRequest());

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
    assertThat(result.isFirst()).isTrue();
    assertThat(result.isLast()).isTrue();
  }

  @Test
  void listRecipes_withModerationStatusFilter_acceptsKnownState() {
    Page<Recipe> page = new PageImpl<>(List.of(recipe), expectedPageable, 1);
    when(recipeRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    when(adminRecipeMapper.toResponse(recipe)).thenReturn(recipeResponse);

    // The review queue is this filter; it must be reachable from the existing list endpoint.
    PageResponse<AdminRecipeListResponse> result =
        adminRecipeService.listRecipes(
            RecipeListRequest.builder().moderationStatus("pending").build());

    assertThat(result.getContent()).containsExactly(recipeResponse);
  }

  @Test
  void listRecipes_withUnknownModerationStatus_throwsValidationException() {
    RecipeListRequest request = RecipeListRequest.builder().moderationStatus("MAYBE").build();

    assertThatThrownBy(() -> adminRecipeService.listRecipes(request))
        .isInstanceOf(ValidationException.class)
        .hasMessage("moderationStatus must be PENDING, APPROVED, or REJECTED");
  }

  @Test
  void getRecipe_returnsMappedRecipe() {
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipe.getId()))
        .thenReturn(Optional.of(recipe));
    when(adminRecipeMapper.toResponse(recipe)).thenReturn(recipeResponse);

    assertThat(adminRecipeService.getRecipe(recipe.getId())).isEqualTo(recipeResponse);
  }

  @Test
  void getRecipe_missingOrSoftDeleted_throwsResourceNotFound() {
    UUID recipeId = UUID.randomUUID();
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipeId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminRecipeService.getRecipe(recipeId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Recipe not found");
  }

  @Test
  void updateRecipe_changesFieldsButLeavesModerationStateAndScore() {
    recipe.setModerationStatus(ModerationStatus.PENDING);
    recipe.setScore(0.42);
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipe.getId()))
        .thenReturn(Optional.of(recipe));
    when(recipeRepository.saveAndFlush(recipe)).thenReturn(recipe);
    when(adminRecipeMapper.toResponse(recipe)).thenReturn(recipeResponse);

    adminRecipeService.updateRecipe(
        UUID.randomUUID(),
        recipe.getId(),
        AdminRecipeUpdateRequest.builder()
            .name("  Vegan chilli  ")
            .instructions("Simmer longer")
            .servings(6)
            .difficulty("hard")
            .build());

    assertThat(recipe.getName()).isEqualTo("Vegan chilli");
    assertThat(recipe.getInstructions()).isEqualTo("Simmer longer");
    assertThat(recipe.getServings()).isEqualTo(6);
    assertThat(recipe.getDifficulty()).isEqualTo(Recipe.Difficulty.HARD);
    // The admin is the approver, so an edit is not a reason to re-queue the recipe...
    assertThat(recipe.getModerationStatus()).isEqualTo(ModerationStatus.PENDING);
    // ...and the score records why it was queued, so editing must not rewrite it.
    assertThat(recipe.getScore()).isEqualTo(0.42);
    // An omitted field keeps its value.
    assertThat(recipe.getDescription()).isNull();
  }

  @Test
  void updateRecipe_blankInstructions_throwsValidationException() {
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipe.getId()))
        .thenReturn(Optional.of(recipe));

    assertThatThrownBy(
            () ->
                adminRecipeService.updateRecipe(
                    UUID.randomUUID(),
                    recipe.getId(),
                    AdminRecipeUpdateRequest.builder().instructions("   ").build()))
        .isInstanceOf(ValidationException.class)
        .hasMessage("instructions must not be blank");

    verify(recipeRepository, never()).saveAndFlush(any(Recipe.class));
  }

  @Test
  void updateRecipe_unknownDifficulty_throwsValidationException() {
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipe.getId()))
        .thenReturn(Optional.of(recipe));

    assertThatThrownBy(
            () ->
                adminRecipeService.updateRecipe(
                    UUID.randomUUID(),
                    recipe.getId(),
                    AdminRecipeUpdateRequest.builder().difficulty("EXTREME").build()))
        .isInstanceOf(ValidationException.class)
        .hasMessage("Difficulty must be EASY, MEDIUM, or HARD");
  }

  @Test
  void deleteRecipe_softDeletesAndLogs() {
    UUID adminId = UUID.randomUUID();
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipe.getId()))
        .thenReturn(Optional.of(recipe));

    adminRecipeService.deleteRecipe(adminId, recipe.getId());

    assertThat(recipe.getDeletedAt()).isNotNull();
    verify(recipeRepository).save(recipe);
    // Soft, never hard: post_recipe rows still point at it.
    verify(recipeRepository, never()).delete(any(Recipe.class));

    ModerationLog entry = lastLog();
    assertThat(entry.getActorId()).isEqualTo(adminId);
    assertThat(entry.getAction()).isEqualTo("DELETE_RECIPE");
    assertThat(entry.getTargetType()).isEqualTo("RECIPE");
    assertThat(entry.getTargetId()).isEqualTo(recipe.getId());
  }

  @Test
  void approveRecipe_pending_becomesApprovedAndLogs() {
    UUID adminId = UUID.randomUUID();
    recipe.setModerationStatus(ModerationStatus.PENDING);
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipe.getId()))
        .thenReturn(Optional.of(recipe));
    when(recipeRepository.saveAndFlush(recipe)).thenReturn(recipe);
    when(adminRecipeMapper.toResponse(recipe)).thenReturn(recipeResponse);

    adminRecipeService.approveRecipe(adminId, recipe.getId(), "fine");

    assertThat(recipe.getModerationStatus()).isEqualTo(ModerationStatus.APPROVED);
    ModerationLog entry = lastLog();
    assertThat(entry.getAction()).isEqualTo("APPROVE_COMPONENT");
    assertThat(entry.getReason()).isEqualTo("fine");
  }

  @Test
  void approveRecipe_alreadyApproved_isNoOpWithoutLog() {
    recipe.setModerationStatus(ModerationStatus.APPROVED);
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipe.getId()))
        .thenReturn(Optional.of(recipe));
    when(adminRecipeMapper.toResponse(recipe)).thenReturn(recipeResponse);

    adminRecipeService.approveRecipe(UUID.randomUUID(), recipe.getId(), null);

    verify(recipeRepository, never()).saveAndFlush(any(Recipe.class));
    verify(moderationLogRepository, never()).save(any(ModerationLog.class));
  }

  @Test
  void rejectRecipe_approved_becomesRejectedAndLogs() {
    UUID adminId = UUID.randomUUID();
    recipe.setModerationStatus(ModerationStatus.APPROVED);
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipe.getId()))
        .thenReturn(Optional.of(recipe));
    when(recipeRepository.saveAndFlush(recipe)).thenReturn(recipe);
    when(adminRecipeMapper.toResponse(recipe)).thenReturn(recipeResponse);

    adminRecipeService.rejectRecipe(adminId, recipe.getId(), "instructions are off-topic");

    // An admin acting on the name itself may withdraw an earlier approval; nothing is deleted.
    assertThat(recipe.getModerationStatus()).isEqualTo(ModerationStatus.REJECTED);
    assertThat(recipe.getDeletedAt()).isNull();
    assertThat(lastLog().getAction()).isEqualTo("REJECT_COMPONENT");
  }

  @Test
  void rejectRecipe_alreadyRejected_isNoOpWithoutLog() {
    recipe.setModerationStatus(ModerationStatus.REJECTED);
    when(recipeRepository.findByIdAndDeletedAtIsNull(recipe.getId()))
        .thenReturn(Optional.of(recipe));
    when(adminRecipeMapper.toResponse(recipe)).thenReturn(recipeResponse);

    adminRecipeService.rejectRecipe(UUID.randomUUID(), recipe.getId(), null);

    verify(recipeRepository, never()).saveAndFlush(any(Recipe.class));
    verify(moderationLogRepository, never()).save(any(ModerationLog.class));
  }

  private ModerationLog lastLog() {
    ArgumentCaptor<ModerationLog> captor = ArgumentCaptor.forClass(ModerationLog.class);
    verify(moderationLogRepository, atLeastOnce()).save(captor.capture());
    return captor.getAllValues().get(captor.getAllValues().size() - 1);
  }
}
