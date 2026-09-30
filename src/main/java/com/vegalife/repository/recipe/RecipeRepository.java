package com.vegalife.repository.recipe;

import com.vegalife.model.recipe.Recipe;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {}
