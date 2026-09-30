package com.vegalife.dto.mapper.category;

import com.vegalife.dto.response.category.CategoryResponse;
import com.vegalife.model.post.Category;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

  CategoryMapper INSTANCE = Mappers.getMapper(CategoryMapper.class);

  CategoryResponse toResponse(Category category);

  List<CategoryResponse> toResponseList(List<Category> categories);
}
