package com.vegalife.dto.mapper.recipe;

import com.vegalife.dto.response.recipe.RecipeIngredientResponse;
import com.vegalife.dto.response.recipe.RecipeResponse;
import com.vegalife.model.recipe.Recipe;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface RecipeMapper {

  RecipeMapper INSTANCE = Mappers.getMapper(RecipeMapper.class);

  @Mapping(source = "recipe.id", target = "id")
  @Mapping(source = "recipe.user.id", target = "userId")
  @Mapping(source = "recipe.dish.id", target = "dishId")
  @Mapping(source = "dishName", target = "dishName")
  @Mapping(source = "ingredients", target = "ingredients")
  RecipeResponse toResponse(
      Recipe recipe, String dishName, List<RecipeIngredientResponse> ingredients);
}
