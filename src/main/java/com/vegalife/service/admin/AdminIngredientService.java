package com.vegalife.service.admin;

import com.vegalife.dto.request.admin.AdminComponentListRequest;
import com.vegalife.dto.request.admin.AdminIngredientCreateRequest;
import com.vegalife.dto.request.admin.AdminIngredientUpdateRequest;
import com.vegalife.dto.response.admin.AdminIngredientResponse;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.recipe.Unit;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.recipe.Ingredient;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.recipe.IngredientRepository;
import com.vegalife.repository.recipe.IngredientSpecifications;
import com.vegalife.service.recipe.RecipeNutritionService;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
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
 * Admin management of ingredient names, their measurement unit and their nutrition (ADR-016,
 * ADR-022).
 *
 * <p>Same contract as {@code AdminDishService}, plus nutrition columns. Nutrition values are edited
 * here but are never a moderation concern: they describe the food, not whether the name is
 * acceptable, and a missing value is not a reason to withhold a name.
 *
 * <p>This is the only place a nutrition fact is authored. Because recipe totals are persisted,
 * every write that changes a unit or a nutrition value recomputes the affected recipes in the same
 * transaction - a recipe can therefore never be observed with a total that disagrees with the
 * ingredients it lists.
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
  private final RecipeNutritionService recipeNutritionService;

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

    Unit unit = isBlank(request.getUnit()) ? null : parseUnit(request.getUnit());
    requireCompleteNutrition(
        request.getCalories(),
        request.getProteinG(),
        request.getCarbohydrateG(),
        request.getFatG(),
        request.getFiberG(),
        "Nutrition must be provided in full or not at all");
    requireUnitForNutrition(unit, request.getCalories());

    Ingredient ingredient =
        ingredientRepository.saveAndFlush(
            Ingredient.builder()
                .name(name)
                .unit(unit)
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
   *
   * <p>Nutrition is merged, not replaced: a request carrying only some values is accepted when the
   * ingredient already has the rest, and refused when it would leave a half-answer. Every recipe
   * using this ingredient is recomputed in this same transaction, so no reader can see a total that
   * disagrees with the ingredient it came from.
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

    Unit previousUnit = ingredient.getUnit();
    Unit newUnit = isBlank(request.getUnit()) ? previousUnit : parseUnit(request.getUnit());

    BigDecimal calories =
        request.getCalories() == null ? ingredient.getCalories() : request.getCalories();
    BigDecimal proteinG =
        request.getProteinG() == null ? ingredient.getProteinG() : request.getProteinG();
    BigDecimal carbohydrateG =
        request.getCarbohydrateG() == null
            ? ingredient.getCarbohydrateG()
            : request.getCarbohydrateG();
    BigDecimal fatG = request.getFatG() == null ? ingredient.getFatG() : request.getFatG();
    BigDecimal fiberG = request.getFiberG() == null ? ingredient.getFiberG() : request.getFiberG();

    requireCompleteNutrition(
        calories, proteinG, carbohydrateG, fatG, fiberG, "Nutrition must be provided in full");
    requireUnitForNutrition(newUnit, calories);

    boolean nutritionChanged =
        !Objects.equals(ingredient.getCalories(), calories)
            || !Objects.equals(ingredient.getProteinG(), proteinG)
            || !Objects.equals(ingredient.getCarbohydrateG(), carbohydrateG)
            || !Objects.equals(ingredient.getFatG(), fatG)
            || !Objects.equals(ingredient.getFiberG(), fiberG)
            || !Objects.equals(previousUnit, newUnit);

    ingredient.setUnit(newUnit);
    ingredient.setCalories(calories);
    ingredient.setProteinG(proteinG);
    ingredient.setCarbohydrateG(carbohydrateG);
    ingredient.setFatG(fatG);
    ingredient.setFiberG(fiberG);

    Ingredient saved = ingredientRepository.saveAndFlush(ingredient);

    if (nutritionChanged) {
      int affected = recipeNutritionService.recomputeForIngredient(saved.getId());
      if (!Objects.equals(previousUnit, newUnit)) {
        // A unit change is silent by design, but it reinterprets every stored amount: 200 (g) of
        // rice becomes 200 (ml) of rice. Recorded so the consequence is traceable afterwards.
        log.warn(
            "Ingredient {} unit changed {} -> {}; recomputed {} recipes whose amounts are now"
                + " interpreted in the new unit",
            saved.getId(),
            previousUnit,
            newUnit,
            affected);
      }
    }

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

  /** True for null or whitespace-only text, so a blank field means "not provided". */
  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /**
   * Maps admin unit text onto the enum.
   *
   * <p>An accepted alias such as {@code grams} is normalised; anything else is refused rather than
   * guessed, because {@code kg} names a quantity the three codes cannot express. Omission is a
   * separate decision left to the caller: creating a bare name is allowed, but recording nutrition
   * without a basis is not.
   */
  private Unit parseUnit(String rawUnit) {
    return Unit.fromInput(rawUnit)
        .orElseThrow(
            () -> new ValidationException("unit must be G, ML, or PIECE: " + rawUnit.trim()));
  }

  /**
   * Refuses nutrition that has no unit to be per.
   *
   * <p>A per-gram figure on an ingredient measured in pieces is not a smaller error than a wrong
   * number: it is a number with no meaning at all. The recipe calculator treats such an ingredient
   * as unresolved, so silently accepting it would store data that can never be used.
   */
  private static void requireUnitForNutrition(Unit unit, BigDecimal calories) {
    if (calories != null && unit == null) {
      throw new ValidationException("unit is required when nutrition is being recorded");
    }
  }

  /** Refuses a half-filled nutrition set; see the class comment for why partial is never stored. */
  private static void requireCompleteNutrition(
      BigDecimal calories,
      BigDecimal proteinG,
      BigDecimal carbohydrateG,
      BigDecimal fatG,
      BigDecimal fiberG,
      String message) {
    long present =
        java.util.stream.Stream.of(calories, proteinG, carbohydrateG, fatG, fiberG)
            .filter(Objects::nonNull)
            .count();
    if (present != 0 && present != 5) {
      throw new ValidationException(message);
    }
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
        .unit(ingredient.getUnit() == null ? null : ingredient.getUnit().name())
        .calories(ingredient.getCalories())
        .proteinG(ingredient.getProteinG())
        .carbohydrateG(ingredient.getCarbohydrateG())
        .fatG(ingredient.getFatG())
        .fiberG(ingredient.getFiberG())
        .nutritionResolved(ingredient.hasResolvedNutrition())
        .createdAt(ingredient.getCreatedAt())
        .updatedAt(ingredient.getUpdatedAt())
        .build();
  }
}
