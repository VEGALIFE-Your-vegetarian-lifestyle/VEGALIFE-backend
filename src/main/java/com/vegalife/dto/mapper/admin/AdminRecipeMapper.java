package com.vegalife.dto.mapper.admin;

import com.vegalife.dto.response.admin.AdminRecipeListResponse;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Recipe;
import com.vegalife.model.recipe.RecipeIngredient;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminRecipeMapper {

  @Mapping(target = "status", source = "deletedAt")
  @Mapping(target = "dishId", source = "dish.id")
  @Mapping(target = "dishName", source = "dish.name")
  @Mapping(target = "categoryIds", source = "posts")
  @Mapping(target = "ingredients", source = "recipeIngredients")
  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "username", source = "user.username")
  @Mapping(target = "email", source = "user.email")
  AdminRecipeListResponse toResponse(Recipe recipe);

  List<AdminRecipeListResponse> toResponseList(List<Recipe> recipes);

  default String status(Instant deletedAt) {
    return deletedAt == null ? "ACTIVE" : "DELETED";
  }

  default Set<UUID> categoryIds(Set<Post> posts) {
    return posts.stream()
        .flatMap(post -> post.getCategories().stream())
        .map(Category::getId)
        .collect(Collectors.toSet());
  }

  List<AdminRecipeListResponse.IngredientItem> toIngredientItems(
      List<RecipeIngredient> recipeIngredients);

  default AdminRecipeListResponse.IngredientItem toIngredientItem(RecipeIngredient ri) {
    return AdminRecipeListResponse.IngredientItem.builder()
        .ingredientId(ri.getIngredient().getId())
        .name(ri.getIngredient().getName())
        .amount(ri.getAmount())
        .unit(ri.getUnit())
        .build();
  }
}
