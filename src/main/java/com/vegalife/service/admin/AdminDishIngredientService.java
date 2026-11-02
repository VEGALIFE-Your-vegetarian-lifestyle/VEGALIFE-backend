package com.vegalife.service.admin;

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
import com.vegalife.shared.exception.DuplicateResourceException;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin curation of a dish's ingredient list (BR-DISH-002).
 *
 * <p>This is the only path that can mark an entry {@code required}, and the only path that can
 * reinstate an entry the auto-seed path is forbidden from resurrecting (BR-DISH-003).
 *
 * <p>These writes are not moderation decisions — they neither approve nor reject a name — so unlike
 * the dish and ingredient approve/reject endpoints they do not write a {@code moderation_log} row.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDishIngredientService {

  private final DishIngredientRepository dishIngredientRepository;
  private final DishRepository dishRepository;
  private final IngredientRepository ingredientRepository;
  private final AdminDishIngredientMapper adminDishIngredientMapper;

  /** Active entries of one dish, including names that are not approved yet. */
  @Transactional(readOnly = true)
  public List<AdminDishIngredientResponse> listDishIngredients(UUID dishId) {
    Dish dish = findActiveDish(dishId);
    return toResponses(dishIngredientRepository.findActiveWithIngredient(dish.getId()));
  }

  /**
   * Adds an entry, or reinstates a withdrawn one.
   *
   * <p>Reinstatement is deliberate: the seeding path must never restore an entry an admin withdrew
   * (BR-DISH-003), so this explicit call is the only way back. {@link AddResult#created()} tells
   * the controller whether to answer 201 or 200.
   */
  @Transactional
  public AddResult addDishIngredient(
      UUID adminId, UUID dishId, AdminDishIngredientCreateRequest request) {
    Dish dish = findActiveDish(dishId);
    Ingredient ingredient = findOrCreateIngredient(request.getIngredientName());
    boolean required = Boolean.TRUE.equals(request.getRequired());

    DishIngredient existing =
        dishIngredientRepository
            .findByDishIdAndIngredientId(dish.getId(), ingredient.getId())
            .orElse(null);

    if (existing != null) {
      if (existing.getRemovedAt() == null) {
        throw new DuplicateResourceException("Ingredient is already associated with this dish");
      }
      existing.setRemovedAt(null);
      existing.setRequired(required);
      DishIngredient saved = dishIngredientRepository.saveAndFlush(existing);
      log.info(
          "Admin {} reinstated ingredient {} for dish {}",
          adminId,
          ingredient.getId(),
          dish.getId());
      return new AddResult(toResponse(saved), false);
    }

    DishIngredient saved =
        dishIngredientRepository.saveAndFlush(
            DishIngredient.builder()
                .dishId(dish.getId())
                .ingredientId(ingredient.getId())
                .required(required)
                .build());

    log.info("Admin {} added ingredient {} to dish {}", adminId, ingredient.getId(), dish.getId());

    return new AddResult(toResponse(saved), true);
  }

  /** Flips the advisory {@code required} flag. The ingredient identity cannot change here. */
  @Transactional
  public AdminDishIngredientResponse updateDishIngredient(
      UUID adminId, UUID dishId, UUID ingredientId, AdminDishIngredientUpdateRequest request) {
    Dish dish = findActiveDish(dishId);
    DishIngredient entry = findActiveEntry(dish.getId(), ingredientId);

    entry.setRequired(Boolean.TRUE.equals(request.getRequired()));
    DishIngredient saved = dishIngredientRepository.saveAndFlush(entry);

    log.info(
        "Admin {} set required={} on ingredient {} for dish {}",
        adminId,
        saved.isRequired(),
        ingredientId,
        dish.getId());

    return toResponse(saved);
  }

  /**
   * Withdraws an entry by stamping {@code removedAt}. The row is kept so the seeding path can tell
   * "never known" from "an admin removed this" (BR-DISH-003).
   *
   * <p>Idempotent: an already-withdrawn entry answers 200 without a second write, matching how the
   * other soft deletes in the codebase behave.
   */
  @Transactional
  public void removeDishIngredient(UUID adminId, UUID dishId, UUID ingredientId) {
    Dish dish = findActiveDish(dishId);

    DishIngredient entry =
        dishIngredientRepository
            .findByDishIdAndIngredientId(dish.getId(), ingredientId)
            .orElseThrow(() -> new ResourceNotFoundException("Dish ingredient not found"));

    if (entry.getRemovedAt() != null) {
      return;
    }

    entry.setRemovedAt(Instant.now());
    dishIngredientRepository.saveAndFlush(entry);

    log.info("Admin {} removed ingredient {} from dish {}", adminId, ingredientId, dish.getId());
  }

  private Dish findActiveDish(UUID dishId) {
    return dishRepository
        .findByIdAndDeletedAtIsNull(dishId)
        .orElseThrow(() -> new ResourceNotFoundException("Dish not found"));
  }

  private DishIngredient findActiveEntry(UUID dishId, UUID ingredientId) {
    return dishIngredientRepository
        .findByDishIdAndIngredientId(dishId, ingredientId)
        .filter(found -> found.getRemovedAt() == null)
        .orElseThrow(() -> new ResourceNotFoundException("Dish ingredient not found"));
  }

  /**
   * Resolves an ingredient name the way recipe creation does (BR-RECP-002): trimmed and lowercased,
   * existing row reused, a rejected or soft-deleted name refused rather than shadowed.
   *
   * <p>A name an admin introduces here is created {@link ModerationStatus#APPROVED} — the creator
   * is the approver. An existing row keeps whatever state it already has, so reusing a pending name
   * does not quietly approve it.
   */
  private Ingredient findOrCreateIngredient(String rawName) {
    String name = rawName.trim().toLowerCase(Locale.ROOT);

    Ingredient existing = ingredientRepository.findByNameIgnoreCase(name).orElse(null);
    if (existing != null) {
      if (existing.getModerationStatus() == ModerationStatus.REJECTED
          || existing.getDeletedAt() != null) {
        throw new ValidationException("Ingredient is not available: " + name);
      }
      return existing;
    }

    return ingredientRepository.saveAndFlush(
        Ingredient.builder().name(name).moderationStatus(ModerationStatus.APPROVED).build());
  }

  private List<AdminDishIngredientResponse> toResponses(List<DishIngredient> entries) {
    return entries.stream().map(this::toResponse).toList();
  }

  /**
   * Maps an entry, resolving its ingredient first.
   *
   * <p>The association is mapped read-only, so it can be unset on an entry this service just built
   * (or on the managed copy {@code merge} returns) even though the id is known. Without this the
   * response would carry a null name after a create or a flag update.
   */
  private AdminDishIngredientResponse toResponse(DishIngredient entry) {
    if (entry.getIngredient() == null) {
      entry.setIngredient(ingredientRepository.findById(entry.getIngredientId()).orElse(null));
    }
    return adminDishIngredientMapper.toResponse(entry);
  }

  /**
   * Whether the entry was newly created ({@code true}) or reinstated ({@code false}), so the
   * controller can answer 201 or 200 without a second lookup.
   */
  public record AddResult(AdminDishIngredientResponse response, boolean created) {}
}
