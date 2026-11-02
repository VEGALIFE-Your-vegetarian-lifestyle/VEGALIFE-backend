package com.vegalife.model.recipe;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class DishIngredientId implements Serializable {

  private UUID dishId;
  private UUID ingredientId;

  public DishIngredientId() {}

  public DishIngredientId(UUID dishId, UUID ingredientId) {
    this.dishId = dishId;
    this.ingredientId = ingredientId;
  }

  public UUID getDishId() {
    return dishId;
  }

  public void setDishId(UUID dishId) {
    this.dishId = dishId;
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
    DishIngredientId that = (DishIngredientId) o;
    return Objects.equals(dishId, that.dishId) && Objects.equals(ingredientId, that.ingredientId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(dishId, ingredientId);
  }
}
