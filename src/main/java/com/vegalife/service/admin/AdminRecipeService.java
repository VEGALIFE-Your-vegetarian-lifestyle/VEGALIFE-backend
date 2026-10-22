package com.vegalife.service.admin;

import com.vegalife.dto.mapper.admin.AdminRecipeMapper;
import com.vegalife.dto.request.admin.AdminRecipeUpdateRequest;
import com.vegalife.dto.request.admin.RecipeListRequest;
import com.vegalife.dto.response.admin.AdminRecipeListResponse;
import com.vegalife.model.admin.ModerationLog;
import com.vegalife.model.recipe.ModerationStatus;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.repository.admin.ModerationLogRepository;
import com.vegalife.repository.recipe.RecipeRepository;
import com.vegalife.repository.recipe.RecipeSpecifications;
import com.vegalife.shared.dto.PageResponse;
import com.vegalife.shared.exception.ResourceNotFoundException;
import com.vegalife.shared.exception.ValidationException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin management of recipes (ADR-016).
 *
 * <p>Members can create a recipe and nothing else. Editing or deleting one is admin-only because a
 * member who could edit after approval would have a way around the filter: publish clean text, get
 * it approved, then rewrite it. The cost is that a typo needs an admin, which is the accepted
 * trade-off recorded in the feature spec.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRecipeService {

  private static final Set<String> SORTABLE_PROPERTIES =
      Set.of("createdAt", "updatedAt", "name", "moderationStatus", "score");

  private static final String SORTABLE_PROPERTIES_MESSAGE =
      "Sort property must be one of: createdAt, updatedAt, name, moderationStatus, score";

  private final RecipeRepository recipeRepository;
  private final AdminRecipeMapper adminRecipeMapper;
  private final ModerationLogRepository moderationLogRepository;

  /**
   * Lists recipes, optionally narrowed by author, category, date range and review state. The review
   * state filter is what makes the queue reachable: {@code moderationStatus=PENDING} lists exactly
   * the recipes waiting for a decision.
   */
  @Transactional(readOnly = true)
  public PageResponse<AdminRecipeListResponse> listRecipes(RecipeListRequest request) {
    UUID userId = request.getUserId();
    UUID categoryId = request.getCategoryId();
    Instant createdFrom = request.getCreatedFrom();
    Instant createdTo = request.getCreatedTo();

    if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
      throw new ValidationException("createdFrom must be before createdTo");
    }

    ModerationStatus moderationStatus =
        AdminComponentQuery.parseStatus(request.getModerationStatus());

    Pageable pageable =
        PageRequest.of(request.getPage(), request.getSize(), parseSort(request.getSort()));
    Specification<Recipe> spec =
        RecipeSpecifications.allWithFilters(
            userId, categoryId, createdFrom, createdTo, moderationStatus);
    Page<Recipe> page = recipeRepository.findAll(spec, pageable);

    log.debug(
        "Listed recipes page={} size={} total={}",
        page.getNumber(),
        page.getSize(),
        page.getTotalElements());

    Page<AdminRecipeListResponse> mapped = page.map(adminRecipeMapper::toResponse);
    return PageResponse.from(mapped);
  }

  @Transactional(readOnly = true)
  public AdminRecipeListResponse getRecipe(UUID recipeId) {
    return adminRecipeMapper.toResponse(findActive(recipeId));
  }

  /**
   * Partial update; an omitted field keeps its current value.
   *
   * <p>Neither {@code moderationStatus} nor {@code score} changes. The state is untouched because
   * the admin is the approver, and the score is left as measured at submission — it records why the
   * row was queued, and recomputing it here would quietly rewrite that reason.
   */
  @Transactional
  public AdminRecipeListResponse updateRecipe(
      UUID adminId, UUID recipeId, AdminRecipeUpdateRequest request) {
    Recipe recipe = findActive(recipeId);

    if (request.getName() != null) {
      String name = request.getName().trim();
      if (name.isEmpty()) {
        throw new ValidationException("name must not be blank");
      }
      recipe.setName(name);
    }
    if (request.getDescription() != null) {
      String description = request.getDescription().trim();
      recipe.setDescription(description.isEmpty() ? null : description);
    }
    if (request.getInstructions() != null) {
      String instructions = request.getInstructions().trim();
      if (instructions.isEmpty()) {
        throw new ValidationException("instructions must not be blank");
      }
      recipe.setInstructions(instructions);
    }
    if (request.getPrepTimeMinutes() != null) {
      recipe.setPrepTimeMinutes(request.getPrepTimeMinutes());
    }
    if (request.getCookTimeMinutes() != null) {
      recipe.setCookTimeMinutes(request.getCookTimeMinutes());
    }
    if (request.getServings() != null) {
      recipe.setServings(request.getServings());
    }
    if (request.getDifficulty() != null) {
      recipe.setDifficulty(parseDifficulty(request.getDifficulty()));
    }

    Recipe saved = recipeRepository.saveAndFlush(recipe);
    log.info("Admin {} updated recipe {}", adminId, saved.getId());

    return adminRecipeMapper.toResponse(saved);
  }

  /** Soft delete: post and menu references must keep resolving, they simply stop listing it. */
  @Transactional
  public void deleteRecipe(UUID adminId, UUID recipeId) {
    Recipe recipe = findActive(recipeId);
    recipe.setDeletedAt(Instant.now());
    recipeRepository.save(recipe);

    logDecision(adminId, "DELETE_RECIPE", recipe.getId(), null);
    log.info("Admin {} soft-deleted recipe {}", adminId, recipe.getId());
  }

  /**
   * Approves the recipe row only.
   *
   * <p>Its dish and ingredients are separate shared names with their own endpoints. Approving a
   * post already covers all of them at once; doing it implicitly here would let a single recipe
   * approval publish a name that a different post is still waiting to have reviewed.
   */
  @Transactional
  public AdminRecipeListResponse approveRecipe(UUID adminId, UUID recipeId, String reason) {
    Recipe recipe = findActive(recipeId);
    if (recipe.getModerationStatus() == ModerationStatus.APPROVED) {
      return adminRecipeMapper.toResponse(recipe);
    }

    recipe.setModerationStatus(ModerationStatus.APPROVED);
    Recipe saved = recipeRepository.saveAndFlush(recipe);
    logDecision(adminId, "APPROVE_COMPONENT", saved.getId(), reason);
    log.info("Admin {} approved recipe {}", adminId, saved.getId());

    return adminRecipeMapper.toResponse(saved);
  }

  /** Rejects the recipe row only; nothing is deleted, and it cannot be re-created by a member. */
  @Transactional
  public AdminRecipeListResponse rejectRecipe(UUID adminId, UUID recipeId, String reason) {
    Recipe recipe = findActive(recipeId);
    if (recipe.getModerationStatus() == ModerationStatus.REJECTED) {
      return adminRecipeMapper.toResponse(recipe);
    }

    recipe.setModerationStatus(ModerationStatus.REJECTED);
    Recipe saved = recipeRepository.saveAndFlush(recipe);
    logDecision(adminId, "REJECT_COMPONENT", saved.getId(), reason);
    log.info("Admin {} rejected recipe {}", adminId, saved.getId());

    return adminRecipeMapper.toResponse(saved);
  }

  private Recipe findActive(UUID recipeId) {
    return recipeRepository
        .findByIdAndDeletedAtIsNull(recipeId)
        .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
  }

  private Recipe.Difficulty parseDifficulty(String difficulty) {
    try {
      return Recipe.Difficulty.valueOf(difficulty.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new ValidationException("Difficulty must be EASY, MEDIUM, or HARD");
    }
  }

  private void logDecision(UUID adminId, String action, UUID recipeId, String reason) {
    moderationLogRepository.save(
        ModerationLog.builder()
            .actorId(adminId)
            .action(action)
            .targetType("RECIPE")
            .targetId(recipeId)
            .reason(reason)
            .build());
  }

  private Sort parseSort(String sort) {
    String[] parts = sort.split(",");
    if (parts.length == 0 || parts.length > 2) {
      throw new ValidationException("Sort must be in the form property,asc|desc");
    }
    String property = parts[0].trim();
    if (property.isBlank()) {
      throw new ValidationException("Sort property must not be blank");
    }
    if (!SORTABLE_PROPERTIES.contains(property)) {
      throw new ValidationException(SORTABLE_PROPERTIES_MESSAGE);
    }
    Sort.Direction direction =
        parts.length == 2 && parts[1].trim().equalsIgnoreCase("asc")
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    return Sort.by(direction, property);
  }
}
