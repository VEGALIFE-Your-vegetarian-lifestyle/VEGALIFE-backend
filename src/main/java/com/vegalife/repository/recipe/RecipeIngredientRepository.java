package com.vegalife.repository.recipe;

import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.model.recipe.RecipeIngredientId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeIngredientRepository
    extends JpaRepository<RecipeIngredient, RecipeIngredientId> {

  List<RecipeIngredient> findByRecipeId(UUID recipeId);

  /**
   * The ingredient lines of several recipes at once, so allergy matching does not issue one query
   * per recipe (issue #163).
   *
   * <p>Joins through {@code ingredientId} rather than {@code ri.ingredient}: the association is
   * read-only ({@code insertable = false}), so filtering on the mapped field keeps the query
   * independent of whether the association has been populated. Callers must not pass an empty
   * collection — {@code IN ()} is not valid JPQL.
   */
  @Query(
      """
      SELECT ri.recipeId, i.name FROM RecipeIngredient ri
      JOIN Ingredient i ON i.id = ri.ingredientId
      WHERE ri.recipeId IN :recipeIds
      """)
  List<Object[]> findIngredientNamesByRecipeIds(@Param("recipeIds") Collection<UUID> recipeIds);

  /**
   * Ids of every recipe that uses this ingredient, so a nutrition edit can recompute exactly the
   * affected totals instead of the whole catalogue (ADR-022).
   *
   * <p>Includes soft-deleted recipes on purpose: they can be restored, and a stale total on a
   * deleted row would resurface unchanged.
   */
  @Query(
      """
      SELECT ri.recipeId FROM RecipeIngredient ri
      WHERE ri.ingredientId = :ingredientId
      """)
  List<UUID> findRecipeIdsByIngredientId(@Param("ingredientId") UUID ingredientId);

  /** Every line of several recipes at once, for the batched nutrition recompute. */
  @Query(
      """
      SELECT ri FROM RecipeIngredient ri
      JOIN FETCH Ingredient i ON i.id = ri.ingredientId
      WHERE ri.recipeId IN :recipeIds
      """)
  List<RecipeIngredient> findByRecipeIdInWithIngredient(
      @Param("recipeIds") Collection<UUID> recipeIds);
}
