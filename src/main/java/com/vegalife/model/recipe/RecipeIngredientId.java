package com.vegalife.model.recipe;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class RecipeIngredientId implements Serializable {

  private UUID recipeId;
  private UUID ingredientId;

  public RecipeIngredientId() {}

  public RecipeIngredientId(UUID recipeId, UUID ingredientId) {
    this.recipeId = recipeId;
    this.ingredientId = ingredientId;
  }

  public UUID getRecipeId() {
    return recipeId;
  }

  public void setRecipeId(UUID recipeId) {
    this.recipeId = recipeId;
  }

  public UUID getIngredientId() {
    return ingredientId;
  }

  public void setIngredientId(UUID ingredientId) {
    this.ingredientId = ingredientId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RecipeIngredientId that = (RecipeIngredientId) o;
    return Objects.equals(recipeId, that.recipeId)
        && Objects.equals(ingredientId, that.ingredientId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(recipeId, ingredientId);
  }
}
