package com.vegalife.repository.recipe;

import com.vegalife.model.recipe.Ingredient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface IngredientRepository
    extends JpaRepository<Ingredient, UUID>, JpaSpecificationExecutor<Ingredient> {

  Optional<Ingredient> findByNameIgnoreCase(String name);
}
