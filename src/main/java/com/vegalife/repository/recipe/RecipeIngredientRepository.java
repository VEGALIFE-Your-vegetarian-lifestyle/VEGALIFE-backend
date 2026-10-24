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
   * per recipe (issue #163). Callers must not pass an empty collection — {@code IN ()} is not valid
   * JPQL.
   */
  @Query(
      """
      SELECT ri FROM RecipeIngredient ri
      JOIN FETCH ri.ingredient i
      WHERE ri.recipeId IN :recipeIds
      """)
  List<RecipeIngredient> findByRecipeIdInWithIngredient(
      @Param("recipeIds") Collection<UUID> recipeIds);
}
