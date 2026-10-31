package com.vegalife.service.admin;

import com.vegalife.dto.request.admin.AdminComponentListRequest;
import com.vegalife.dto.request.admin.AdminDishCreateRequest;
import com.vegalife.dto.request.admin.AdminDishUpdateRequest;
import com.vegalife.dto.response.admin.AdminDishResponse;
import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.recipe.Dish;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.recipe.DishRepository;
import com.vegalife.repository.recipe.DishSpecifications;
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
 * Admin management of dish names (ADR-016).
 *
 * <p>Dishes are global and unowned, so this is the only place a name can be created without going
 * through a member's post, and the only place an existing name's state can be changed directly. A
 * name created here is {@link ModerationStatus#APPROVED}: the creator is the approver.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDishService {

  private static final Set<String> SORTABLE_PROPERTIES =
      Set.of("name", "createdAt", "updatedAt", "moderationStatus");

  private static final String SORTABLE_PROPERTIES_MESSAGE =
      "Sort property must be one of: name, createdAt, updatedAt, moderationStatus";

  private final DishRepository dishRepository;
  private final ModerationLogRepository moderationLogRepository;

  /** Every state, including pending and rejected, so the review queue is reachable. */
  @Transactional(readOnly = true)
  public PageResponse<AdminDishResponse> listDishes(AdminComponentListRequest request) {
    ModerationStatus status = AdminComponentQuery.parseStatus(request.getModerationStatus());
    Pageable pageable =
        PageRequest.of(
            request.getPage(),
            request.getSize(),
            AdminComponentQuery.parseSort(
                request.getSort(), SORTABLE_PROPERTIES, SORTABLE_PROPERTIES_MESSAGE));

    Specification<Dish> spec =
        DishSpecifications.anyModerationWithNameFilter(request.getName(), status);
    Page<Dish> page = dishRepository.findAll(spec, pageable);

    return PageResponse.from(page.map(this::toResponse));
  }

  @Transactional(readOnly = true)
  public AdminDishResponse getDish(UUID dishId) {
    return toResponse(findActive(dishId));
  }

  /**
   * Creates a name that is approved on arrival.
   *
   * <p>Refuses to shadow an existing row: a rejected name stays blocked (creating it again would
   * launder the block) and a soft-deleted one still holds its name, so neither is silently replaced
   * by a fresh approved duplicate.
   */
  @Transactional
  public AdminDishResponse createDish(UUID adminId, AdminDishCreateRequest request) {
    String name = request.getName().trim();

    dishRepository
        .findByNameIgnoreCase(name)
        .ifPresent(
            existing -> {
              if (existing.getModerationStatus() == ModerationStatus.REJECTED) {
                throw new ValidationException(
                    "Dish name was rejected and cannot be re-created: " + name);
              }
              throw new DuplicateResourceException("Dish name already exists");
            });

    Dish dish =
        dishRepository.saveAndFlush(
            Dish.builder()
                .name(name)
                .description(normalizeText(request.getDescription()))
                .imageUrl(normalizeText(request.getImageUrl()))
                .cuisineType(normalizeText(request.getCuisineType()))
                .moderationStatus(ModerationStatus.APPROVED)
                .build());

    // Logged because this path approves without review; an audit trail of who did that is the
    // whole point of keeping the decision visible.
    logDecision(adminId, "CREATE_DISH", dish.getId(), null);
    log.info("Admin {} created dish {}", adminId, dish.getId());

    return toResponse(dish);
  }

  /**
   * Partial update; an omitted field keeps its current value.
   *
   * <p>The moderation state is deliberately untouched: the admin is the approver, so their edit is
   * itself an approval, and re-queueing an approved name would withhold it from every recipe and
   * menu that already uses it (BR-DISH-001).
   *
   * <p>Renaming a {@link ModerationStatus#REJECTED} row is refused. The rejection is a block on
   * that name, and renaming the row would free the old name for a member to create again.
   */
  @Transactional
  public AdminDishResponse updateDish(UUID adminId, UUID dishId, AdminDishUpdateRequest request) {
    Dish dish = findActive(dishId);

    if (request.getName() != null) {
      String name = request.getName().trim();
      if (name.isEmpty()) {
        throw new ValidationException("name must not be blank");
      }
      if (!name.equals(dish.getName())) {
        if (dish.getModerationStatus() == ModerationStatus.REJECTED) {
          throw new ValidationException(
              "A rejected name cannot be renamed; create a new name instead");
        }
        // Exact comparison, so a case-only correction ("Phở chay" -> "phở chay") is applied. The
        // self-filter below is what keeps that from looking like a duplicate of itself.
        dishRepository
            .findByNameIgnoreCase(name)
            .filter(other -> !other.getId().equals(dishId))
            .ifPresent(
                other -> {
                  throw new DuplicateResourceException("Dish name already exists");
                });
        dish.setName(name);
      }
    }
    if (request.getDescription() != null) {
      dish.setDescription(normalizeText(request.getDescription()));
    }
    if (request.getImageUrl() != null) {
      dish.setImageUrl(normalizeText(request.getImageUrl()));
    }
    if (request.getCuisineType() != null) {
      dish.setCuisineType(normalizeText(request.getCuisineType()));
    }

    Dish saved = dishRepository.saveAndFlush(dish);
    log.info("Admin {} updated dish {}", adminId, saved.getId());

    return toResponse(saved);
  }

  /**
   * Soft delete. Never a hard delete: existing recipes and {@code menu_detail} rows reference the
   * row and must keep resolving (BR-MENU-005).
   *
   * <p>The name is not freed. A soft-deleted dish is reported unavailable if a member tries to
   * reuse it, which is the same outcome as before it was deleted — resurrection is not on the
   * table.
   */
  @Transactional
  public void deleteDish(UUID adminId, UUID dishId) {
    Dish dish = findActive(dishId);
    dish.setDeletedAt(Instant.now());
    dishRepository.save(dish);

    logDecision(adminId, "DELETE_DISH", dish.getId(), null);
    log.info("Admin {} soft-deleted dish {}", adminId, dish.getId());
  }

  /**
   * Approves a name outright, including one previously rejected.
   *
   * <p>This is the deliberate correction path and the only way back from {@link
   * ModerationStatus#REJECTED}. Post approval deliberately cannot do this — a post is not a review
   * of every name it happens to mention — but an admin naming this row by id is making exactly that
   * decision, and without it a mistyped rejection would poison a legitimate name forever.
   */
  @Transactional
  public AdminDishResponse approveDish(UUID adminId, UUID dishId, String reason) {
    Dish dish = findActive(dishId);
    if (dish.getModerationStatus() == ModerationStatus.APPROVED) {
      return toResponse(dish);
    }

    dish.setModerationStatus(ModerationStatus.APPROVED);
    Dish saved = dishRepository.saveAndFlush(dish);
    logDecision(adminId, "APPROVE_COMPONENT", saved.getId(), reason);
    log.info("Admin {} approved dish {}", adminId, saved.getId());

    return toResponse(saved);
  }

  /**
   * Rejects a name outright, including one currently approved.
   *
   * <p>Nothing is deleted: recipes and menus that reference it keep resolving, they just stop being
   * able to introduce it again. Withdrawing an approved name is allowed here because an admin
   * acting on the name itself is the authority that approved it in the first place.
   */
  @Transactional
  public AdminDishResponse rejectDish(UUID adminId, UUID dishId, String reason) {
    Dish dish = findActive(dishId);
    if (dish.getModerationStatus() == ModerationStatus.REJECTED) {
      return toResponse(dish);
    }

    dish.setModerationStatus(ModerationStatus.REJECTED);
    Dish saved = dishRepository.saveAndFlush(dish);
    logDecision(adminId, "REJECT_COMPONENT", saved.getId(), reason);
    log.info("Admin {} rejected dish {}", adminId, saved.getId());

    return toResponse(saved);
  }

  private Dish findActive(UUID dishId) {
    return dishRepository
        .findByIdAndDeletedAtIsNull(dishId)
        .orElseThrow(() -> new ResourceNotFoundException("Dish not found"));
  }

  private void logDecision(UUID adminId, String action, UUID dishId, String reason) {
    moderationLogRepository.save(
        ModerationLog.builder()
            .actorId(adminId)
            .action(action)
            .targetType("DISH")
            .targetId(dishId)
            .reason(reason)
            .build());
  }

  private String normalizeText(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private AdminDishResponse toResponse(Dish dish) {
    return AdminDishResponse.builder()
        .id(dish.getId())
        .name(dish.getName())
        .description(dish.getDescription())
        .imageUrl(dish.getImageUrl())
        .cuisineType(dish.getCuisineType())
        .moderationStatus(dish.getModerationStatus().name())
        .createdAt(dish.getCreatedAt())
        .updatedAt(dish.getUpdatedAt())
        .build();
  }
}
