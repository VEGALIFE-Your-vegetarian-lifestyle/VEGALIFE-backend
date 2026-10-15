package com.vegalife.dto.mapper.post;

import com.vegalife.dto.request.post.PostCreateRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PostMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "categories", ignore = true)
  @Mapping(target = "media", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "flag", ignore = true)
  @Mapping(target = "viewCount", ignore = true)
  @Mapping(target = "publishedAt", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  Post toEntity(PostCreateRequest request);

  /**
   * {@code recipeIds} defaults to empty: {@code Post} carries no recipe field ({@code post_recipe}
   * is owned by {@code Recipe.posts}), and a freshly created or edited post never has recipes
   * attached through these endpoints (that's done separately via {@code POST
   * /api/posts/{postId}/recipes}).
   */
  @Mapping(target = "categoryIds", source = "categories")
  @Mapping(target = "mediaIds", source = "media")
  @Mapping(target = "recipeIds", expression = "java(new java.util.HashSet<>())")
  PostListResponse toListResponse(Post post);

  @Mapping(target = "categoryIds", source = "post.categories")
  @Mapping(target = "mediaIds", source = "post.media")
  @Mapping(target = "recipeIds", source = "recipeIds")
  PostListResponse toListResponse(Post post, Set<UUID> recipeIds);

  default Set<UUID> categoryIds(Set<Category> categories) {
    return categories.stream().map(Category::getId).collect(Collectors.toSet());
  }

  default Set<UUID> mediaIds(Set<Media> media) {
    return media.stream()
        .filter(item -> item.getDeletedAt() == null)
        .map(Media::getId)
        .collect(Collectors.toSet());
  }
}
