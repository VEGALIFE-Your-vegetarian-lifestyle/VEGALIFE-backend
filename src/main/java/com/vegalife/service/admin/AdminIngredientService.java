package com.vegalife.service.admin;

import com.vegalife.dto.request.admin.AdminComponentListRequest;
import com.vegalife.dto.request.admin.AdminIngredientCreateRequest;
import com.vegalife.dto.request.admin.AdminIngredientUpdateRequest;
import com.vegalife.dto.response.admin.AdminIngredientResponse;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.ModerationStatus;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.IngredientSpecifications;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin management of ingredient names (ADR-016).
 *
 * <p>Same contract as {@code AdminDishService}, plus nutrition columns. Nutrition values are edited
 * here but are never a moderation concern: they describe the food, not whether the name is
 * acceptable, and a missing value is not a reason to withhold a name.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminIngredientService {

  private static final Set<String> SORTABLE_PROPERTIES =
      Set.of("name", "createdAt", "updatedAt", "moderationStatus");

  private static final String SORTABLE_PROPERTIES_MESSAGE =
      "Sort property must be one of: name, createdAt, updatedAt, moderationStatus";

  private final IngredientRepository ingredientRepository;
  private final ModerationLogRepository moderationLogRepository;

  /** Every state, including pending and rejected, so the review queue is reachable. */
  @Transactional(readOnly = true)
  public PageResponse<AdminIngredientResponse> listIngredients(AdminComponentListRequest request) {
    ModerationStatus status = AdminComponentQuery.parseStatus(request.getModerationStatus());
    Pageable pageable =
        PageRequest.of(
            request.getPage(),
            request.getSize(),
            AdminComponentQuery.parseSort(
                request.getSort(), SORTABLE_PROPERTIES, SORTABLE_PROPERTIES_MESSAGE));

    Specification<Ingredient> spec =
        IngredientSpecifications.anyModerationWithNameFilter(request.getName(), status);
    Page<Ingredient> page = ingredientRepository.findAll(spec, pageable);

    return PageResponse.from(page.map(this::toResponse));
  }

  @Transactional(readOnly = true)
  public AdminIngredientResponse getIngredient(UUID ingredientId) {
    return toResponse(findActive(ingredientId));
  }

  /**
   * Creates a name that is approved on arrival. A rejected name stays blocked and a soft-deleted
   * one still holds its name, so neither is replaced by a fresh approved duplicate.
   */
  @Transactional
  public AdminIngredientResponse createIngredient(
      UUID adminId, AdminIngredientCreateRequest request) {
    String name = request.getName().trim();

    ingredientRepository
        .findByNameIgnoreCase(name)
        .ifPresent(
            existing -> {
              if (existing.getModerationStatus() == ModerationStatus.REJECTED) {
                throw new ValidationException(
                    "Ingredient name was rejected and cannot be re-created: " + name);
              }
              throw new DuplicateResourceException("Ingredient name already exists");
            });

    Ingredient ingredient =
        ingredientRepository.saveAndFlush(
            Ingredient.builder()
                .name(name)
                .calories(request.getCalories())
                .proteinG(request.getProteinG())
                .carbohydrateG(request.getCarbohydrateG())
                .fatG(request.getFatG())
                .fiberG(request.getFiberG())
                .moderationStatus(ModerationStatus.APPROVED)
                .build());

    logDecision(adminId, "CREATE_INGREDIENT", ingredient.getId(), null);
    log.info("Admin {} created ingredient {}", adminId, ingredient.getId());

    return toResponse(ingredient);
  }

  /**
   * Partial update; an omitted field keeps its current value and the moderation state is untouched
   * (BR-DISH-001). Renaming a rejected row is refused for the same reason as for dishes: it would
   * free the blocked name for re-creation.
   */
  @Transactional
  public AdminIngredientResponse updateIngredient(
      UUID adminId, UUID ingredientId, AdminIngredientUpdateRequest request) {
    Ingredient ingredient = findActive(ingredientId);

    if (request.getName() != null) {
      String name = request.getName().trim();
      if (name.isEmpty()) {
        throw new ValidationException("name must not be blank");
      }
      if (!name.equals(ingredient.getName())) {
        if (ingredient.getModerationStatus() == ModerationStatus.REJECTED) {
          throw new ValidationException(
              "A rejected name cannot be renamed; create a new name instead");
        }
        ingredientRepository
            .findByNameIgnoreCase(name)
            .filter(other -> !other.getId().equals(ingredientId))
            .ifPresent(
                other -> {
                  throw new DuplicateResourceException("Ingredient name already exists");
                });
        ingredient.setName(name);
      }
    }
    if (request.getCalories() != null) {
      ingredient.setCalories(request.getCalories());
    }
    if (request.getProteinG() != null) {
      ingredient.setProteinG(request.getProteinG());
    }
    if (request.getCarbohydrateG() != null) {
      ingredient.setCarbohydrateG(request.getCarbohydrateG());
    }
    if (request.getFatG() != null) {
      ingredient.setFatG(request.getFatG());
    }
    if (request.getFiberG() != null) {
      ingredient.setFiberG(request.getFiberG());
    }

    Ingredient saved = ingredientRepository.saveAndFlush(ingredient);
    log.info("Admin {} updated ingredient {}", adminId, saved.getId());

    return toResponse(saved);
  }

  /** Soft delete; existing recipe lines must keep resolving, so the row is never removed. */
  @Transactional
  public void deleteIngredient(UUID adminId, UUID ingredientId) {
    Ingredient ingredient = findActive(ingredientId);
    ingredient.setDeletedAt(Instant.now());
    ingredientRepository.save(ingredient);

    logDecision(adminId, "DELETE_INGREDIENT", ingredient.getId(), null);
    log.info("Admin {} soft-deleted ingredient {}", adminId, ingredient.getId());
  }

  /** Approves a name outright; the only way back from a rejection, see {@code AdminDishService}. */
  @Transactional
  public AdminIngredientResponse approveIngredient(UUID adminId, UUID ingredientId, String reason) {
    Ingredient ingredient = findActive(ingredientId);
    if (ingredient.getModerationStatus() == ModerationStatus.APPROVED) {
      return toResponse(ingredient);
    }

    ingredient.setModerationStatus(ModerationStatus.APPROVED);
    Ingredient saved = ingredientRepository.saveAndFlush(ingredient);
    logDecision(adminId, "APPROVE_COMPONENT", saved.getId(), reason);
    log.info("Admin {} approved ingredient {}", adminId, saved.getId());

    return toResponse(saved);
  }

  /** Rejects a name outright; nothing is deleted, and re-creation is blocked from now on. */
  @Transactional
  public AdminIngredientResponse rejectIngredient(UUID adminId, UUID ingredientId, String reason) {
    Ingredient ingredient = findActive(ingredientId);
    if (ingredient.getModerationStatus() == ModerationStatus.REJECTED) {
      return toResponse(ingredient);
    }

    ingredient.setModerationStatus(ModerationStatus.REJECTED);
    Ingredient saved = ingredientRepository.saveAndFlush(ingredient);
    logDecision(adminId, "REJECT_COMPONENT", saved.getId(), reason);
    log.info("Admin {} rejected ingredient {}", adminId, saved.getId());

    return toResponse(saved);
  }

  private Ingredient findActive(UUID ingredientId) {
    return ingredientRepository
        .findByIdAndDeletedAtIsNull(ingredientId)
        .orElseThrow(() -> new ResourceNotFoundException("Ingredient not found"));
  }

  private void logDecision(UUID adminId, String action, UUID ingredientId, String reason) {
    moderationLogRepository.save(
        ModerationLog.builder()
            .actorId(adminId)
            .action(action)
            .targetType("INGREDIENT")
            .targetId(ingredientId)
            .reason(reason)
            .build());
  }

  private AdminIngredientResponse toResponse(Ingredient ingredient) {
    return AdminIngredientResponse.builder()
        .id(ingredient.getId())
        .name(ingredient.getName())
        .moderationStatus(ingredient.getModerationStatus().name())
        .calories(ingredient.getCalories())
        .proteinG(ingredient.getProteinG())
        .carbohydrateG(ingredient.getCarbohydrateG())
        .fatG(ingredient.getFatG())
        .fiberG(ingredient.getFiberG())
        .createdAt(ingredient.getCreatedAt())
        .updatedAt(ingredient.getUpdatedAt())
        .build();
  }
}
