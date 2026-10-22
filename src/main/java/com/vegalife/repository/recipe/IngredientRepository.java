package com.vegalife.repository.recipe;

import com.vegalife.model.recipe.Ingredient;
import com.vegalife.model.recipe.ModerationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface IngredientRepository
    extends JpaRepository<Ingredient, UUID>, JpaSpecificationExecutor<Ingredient> {

  /**
   * Name lookup without the visibility filter, so a REJECTED or soft-deleted row is found and
   * refused rather than shadowed by a new PENDING duplicate (BR-DISH-001).
   */
  Optional<Ingredient> findByNameIgnoreCase(String name);

  Optional<Ingredient> findByIdAndDeletedAtIsNull(UUID id);

  /** Public listing: only names an admin has approved are visible (ADR-016). */
  List<Ingredient> findByDeletedAtIsNullAndModerationStatusOrderByNameAsc(ModerationStatus status);

  Optional<Ingredient> findByIdAndDeletedAtIsNullAndModerationStatus(
      UUID id, ModerationStatus status);
}
