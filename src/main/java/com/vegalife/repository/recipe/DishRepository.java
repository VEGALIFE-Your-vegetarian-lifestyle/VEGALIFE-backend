package com.vegalife.repository.recipe;

import com.vegalife.model.recipe.Dish;
import com.vegalife.model.recipe.ModerationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DishRepository extends JpaRepository<Dish, UUID>, JpaSpecificationExecutor<Dish> {

  Optional<Dish> findByNameIgnoreCaseAndDeletedAtIsNull(String name);

  /**
   * Name lookup without the visibility filter. Used when resolving a name a member typed: a
   * REJECTED row must be found and refused rather than shadowed by a new PENDING duplicate, and a
   * soft-deleted one must be reported as unavailable (BR-DISH-001).
   */
  Optional<Dish> findByNameIgnoreCase(String name);

  /** A dish is usable in a menu only while it exists and is not soft-deleted (BR-MENU-005). */
  Optional<Dish> findByIdAndDeletedAtIsNull(UUID id);

  /** Public listing: only names an admin has approved are visible (ADR-016). */
  List<Dish> findByDeletedAtIsNullAndModerationStatusOrderByNameAsc(ModerationStatus status);

  Optional<Dish> findByIdAndDeletedAtIsNullAndModerationStatus(UUID id, ModerationStatus status);
}
