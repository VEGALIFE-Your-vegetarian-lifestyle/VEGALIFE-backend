package com.vegalife.dto.mapper.recipe;

import com.vegalife.dto.response.recipe.DishResponse;
import com.vegalife.model.recipe.Dish;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface DishMapper {

  DishMapper INSTANCE = Mappers.getMapper(DishMapper.class);

  DishResponse toResponse(Dish dish);
}
