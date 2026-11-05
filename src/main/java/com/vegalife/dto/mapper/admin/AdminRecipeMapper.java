package com.vegalife.dto.mapper.admin;

import com.vegalife.dto.response.admin.AdminRecipeListResponse;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.recipe.Recipe;
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
  @Mapping(target = "dishModerationStatus", source = "dish.moderationStatus")
  @Mapping(target = "categoryIds", source = "posts")
  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "displayName", source = "user.displayName")
  @Mapping(target = "email", source = "user.email")
  // Both are filled by the service: the warning needs a repository lookup, and per-line nutrition
  // needs the calculator, which lives in a package this mapper may not reach (ADR-018 ranks dto
  // below service).
  @Mapping(target = "missingRequiredIngredients", ignore = true)
  @Mapping(target = "ingredients", ignore = true)
  @Mapping(target = "nutritionResolved", ignore = true)
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
}
