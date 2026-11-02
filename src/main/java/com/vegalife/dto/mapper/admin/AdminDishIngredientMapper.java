package com.vegalife.dto.mapper.admin;

import com.vegalife.dto.response.admin.AdminDishIngredientResponse;
import com.vegalife.model.recipe.DishIngredient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps a curated dish/ingredient entry to its admin shape.
 *
 * <p>Shared by the dish-ingredient controller and the dish detail response so the embedded list and
 * the standalone list cannot drift apart.
 */
@Mapper(componentModel = "spring")
public interface AdminDishIngredientMapper {

  @Mapping(target = "ingredientId", source = "ingredientId")
  @Mapping(target = "name", source = "ingredient.name")
  @Mapping(target = "moderationStatus", source = "ingredient.moderationStatus")
  @Mapping(target = "required", source = "required")
  AdminDishIngredientResponse toResponse(DishIngredient entry);
}
