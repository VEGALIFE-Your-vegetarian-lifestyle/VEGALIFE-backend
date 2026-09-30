package com.vegalife.dto.mapper.admin;

import com.vegalife.dto.response.admin.AdminPostListResponse;
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

  @Mapping(target = "categoryIds", source = "categories")
  @Mapping(target = "mediaIds", source = "media")
  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "username", source = "user.username")
  @Mapping(target = "email", source = "user.email")
  AdminPostListResponse toResponse(Post post);

  List<AdminPostListResponse> toResponseList(List<Post> posts);

  default Set<UUID> categoryIds(Set<Category> categories) {
    return categories.stream().map(Category::getId).collect(Collectors.toSet());
  }

  default Set<UUID> mediaIds(Set<Media> media) {
    return media.stream().map(Media::getId).collect(Collectors.toSet());
  }
}
