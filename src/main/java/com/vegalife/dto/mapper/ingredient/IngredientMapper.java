package com.vegalife.dto.mapper.ingredient;

import com.vegalife.dto.response.ingredient.IngredientResponse;
import com.vegalife.model.recipe.Ingredient;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface IngredientMapper {

  IngredientMapper INSTANCE = Mappers.getMapper(IngredientMapper.class);

  IngredientResponse toResponse(Ingredient ingredient);
}
