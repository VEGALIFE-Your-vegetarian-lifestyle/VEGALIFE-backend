package com.vegalife.dto.mapper.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.vegalife.dto.request.post.PostCreateRequest;
import com.vegalife.dto.response.post.PostFlagDetailResponse;
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
  @Mapping(target = "flagDetail", ignore = true)
  @Mapping(target = "viewCount", ignore = true)
  @Mapping(target = "publishedAt", ignore = true)
  @Mapping(target = "rejectionReason", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  Post toEntity(PostCreateRequest request);

  /**
   * {@code recipeIds} defaults to empty: {@code Post} carries no recipe field ({@code post_recipe}
   * is owned by {@code Recipe.posts}), and a freshly created or edited post never has recipes
   * attached through these endpoints (that's done separately via {@code POST
   * /api/posts/{postId}/recipes}).
   *
   * <p>{@code rejectionReason} is never mapped here. This response type is shared with public
   * endpoints ({@code GET /api/posts/{postId}}, {@code /feed}, {@code /related}), so populating it
   * in the mapper would publish admin wording to everyone. {@code PostService} sets it explicitly,
   * and only when the caller authored the post.
   */
  @Mapping(target = "categoryIds", source = "categories")
  @Mapping(target = "mediaIds", source = "media")
  @Mapping(target = "recipeIds", expression = "java(new java.util.HashSet<>())")
  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "displayName", source = "user.displayName")
  @Mapping(target = "avatarUrl", ignore = true)
  @Mapping(target = "rejectionReason", ignore = true)
  PostListResponse toListResponse(Post post);

  @Mapping(target = "categoryIds", source = "post.categories")
  @Mapping(target = "mediaIds", source = "post.media")
  @Mapping(target = "recipeIds", source = "recipeIds")
  @Mapping(target = "userId", source = "post.user.id")
  @Mapping(target = "displayName", source = "post.user.displayName")
  @Mapping(target = "avatarUrl", ignore = true)
  @Mapping(target = "rejectionReason", ignore = true)
  PostListResponse toListResponse(Post post, Set<UUID> recipeIds);

  default Set<UUID> categoryIds(Set<Category> categories) {
    return categories.stream().map(Category::getId).collect(Collectors.toSet());
  }

  /** The stored {@code flag_detail} snapshot, exposed as a typed object (ADR-024). */
  default PostFlagDetailResponse flagDetail(JsonNode node) {
    return PostFlagDetailResponse.from(node);
  }

  default Set<UUID> mediaIds(Set<Media> media) {
    return media.stream()
        .filter(item -> item.getDeletedAt() == null)
        .map(Media::getId)
        .collect(Collectors.toSet());
  }
}
