package com.vegalife.repository.recipe;

import com.vegalife.model.recipe.RecipeIngredient;
import com.vegalife.model.recipe.RecipeIngredientId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeIngredientRepository
    extends JpaRepository<RecipeIngredient, RecipeIngredientId> {

  List<RecipeIngredient> findByRecipeId(UUID recipeId);
}
