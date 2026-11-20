package com.vegalife.dto.mapper.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.vegalife.dto.response.admin.AdminPostListResponse;
import com.vegalife.dto.response.post.PostFlagDetailResponse;
import com.vegalife.model.post.Category;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminPostMapper {

  /**
   * {@code rawContent} maps by name from {@code Post}, needing no annotation. {@code avatarUrl} and
   * {@code recipeIds} are ignored because neither is reachable from a single {@code Post}: the
   * avatar lives on {@code UserProfile}, and the recipes are read from the owning {@code
   * Recipe.posts} side. {@code AdminPostService} fills both in one batch per page.
   */
  @Mapping(target = "categoryIds", source = "categories")
  @Mapping(target = "mediaIds", source = "media")
  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "displayName", source = "user.displayName")
  @Mapping(target = "email", source = "user.email")
  @Mapping(target = "avatarUrl", ignore = true)
  @Mapping(target = "recipeIds", ignore = true)
  AdminPostListResponse toResponse(Post post);

  List<AdminPostListResponse> toResponseList(List<Post> posts);

  default Set<UUID> categoryIds(Set<Category> categories) {
    return categories.stream().map(Category::getId).collect(Collectors.toSet());
  }

  default Set<UUID> mediaIds(Set<Media> media) {
    return media.stream()
        .filter(item -> item.getDeletedAt() == null)
        .map(Media::getId)
        .collect(Collectors.toSet());
  }

  /** The stored {@code flag_detail} snapshot, exposed as a typed object (ADR-024). */
  default PostFlagDetailResponse flagDetail(JsonNode node) {
    return PostFlagDetailResponse.from(node);
  }
}
