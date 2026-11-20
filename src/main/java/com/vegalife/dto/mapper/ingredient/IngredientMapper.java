package com.vegalife.dto.mapper.ingredient;

import com.vegalife.dto.response.ingredient.IngredientResponse;
import com.vegalife.model.recipe.Ingredient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface IngredientMapper {

  IngredientMapper INSTANCE = Mappers.getMapper(IngredientMapper.class);

  /**
   * The entity stores {@code unit} as an enum, which MapStruct would map to the same enum type; the
   * wire form is its name, so it is mapped explicitly for the same reason every other unit column
   * is (ADR-022).
   *
   * <p>{@code nutritionResolved} is derived rather than mapped: the entity exposes {@code
   * hasResolvedNutrition()}, which is not a bean getter, and the rule must not be re-expressed
   * here.
   */
  @Mapping(target = "unit", expression = "java(unitName(ingredient))")
  @Mapping(target = "nutritionResolved", expression = "java(ingredient.hasResolvedNutrition())")
  IngredientResponse toResponse(Ingredient ingredient);

  /** Null stays null rather than becoming a literal "null". */
  default String unitName(Ingredient ingredient) {
    return ingredient.getUnit() == null ? null : ingredient.getUnit().name();
  }
}
