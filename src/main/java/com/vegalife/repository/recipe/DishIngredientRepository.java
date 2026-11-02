package com.vegalife.repository.recipe;

import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.model.recipe.DishIngredient;
import com.vegalife.model.recipe.DishIngredientId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DishIngredientRepository extends JpaRepository<DishIngredient, DishIngredientId> {

  /**
   * Active entries of one dish, joined to their ingredient for the name and review state. Ordered
   * mandatory-first, then alphabetically, so both the admin list and the member suggestions read
   * the way a recipe does.
   */
  @Query(
      """
      SELECT di FROM DishIngredient di
      JOIN FETCH di.ingredient i
      WHERE di.dishId = :dishId AND di.removedAt IS NULL
      ORDER BY di.required DESC, i.name ASC
      """)
  List<DishIngredient> findActiveWithIngredient(@Param("dishId") UUID dishId);

  /**
   * Active entries whose ingredient has been approved, for the member-facing list (BR-DISH-001).
   *
   * <p>Filtering in the query rather than in Java keeps an unreviewed name from being loaded at
   * all, so it cannot leak through a later refactor that forgets to check the state.
   */
  @Query(
      """
      SELECT di FROM DishIngredient di
      JOIN FETCH di.ingredient i
      WHERE di.dishId = :dishId
        AND di.removedAt IS NULL
        AND i.deletedAt IS NULL
        AND i.moderationStatus = :status
      ORDER BY di.required DESC, i.name ASC
      """)
  List<DishIngredient> findVisibleWithIngredient(
      @Param("dishId") UUID dishId, @Param("status") ModerationStatus status);

  /** Any row for the pair, withdrawn or not — the seeding path must not resurrect a tombstone. */
  Optional<DishIngredient> findByDishIdAndIngredientId(UUID dishId, UUID ingredientId);

  /**
   * The required ingredients of a dish, joined to the ingredient for its name. Used to derive the
   * advisory "missing required ingredients" warning on a recipe.
   */
  @Query(
      """
      SELECT di FROM DishIngredient di
      JOIN FETCH di.ingredient i
      WHERE di.dishId = :dishId AND di.removedAt IS NULL AND di.required = true
      ORDER BY i.name ASC
      """)
  List<DishIngredient> findRequiredWithIngredient(@Param("dishId") UUID dishId);

  /** Seeding does one lookup for the whole recipe instead of one per ingredient. */
  List<DishIngredient> findByDishIdAndIngredientIdIn(UUID dishId, Collection<UUID> ingredientIds);

  /**
   * Active entries for several dishes at once, so the admin dish list can embed its ingredient
   * lists without one query per row. Callers must not pass an empty collection — {@code IN ()} is
   * not valid JPQL.
   */
  @Query(
      """
      SELECT di FROM DishIngredient di
      JOIN FETCH di.ingredient i
      WHERE di.dishId IN :dishIds AND di.removedAt IS NULL
      ORDER BY di.required DESC, i.name ASC
      """)
  List<DishIngredient> findActiveWithIngredientForDishes(
      @Param("dishIds") Collection<UUID> dishIds);
}
