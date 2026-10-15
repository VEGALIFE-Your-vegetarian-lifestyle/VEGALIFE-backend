package com.vegalife.repository.recipe;

import com.vegalife.model.recipe.Recipe;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository
    extends JpaRepository<Recipe, UUID>, JpaSpecificationExecutor<Recipe> {

  List<Recipe> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

  /** Recipes currently attached to a post, read from the owning side ({@code Recipe.posts}). */
  List<Recipe> findByPosts_IdAndDeletedAtIsNull(UUID postId);
}
