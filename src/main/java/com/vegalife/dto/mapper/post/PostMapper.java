package com.vegalife.dto.mapper.post;

import com.vegalife.dto.request.post.PostCreateRequest;
import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.model.post.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PostMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "viewCount", ignore = true)
  @Mapping(target = "publishedAt", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  Post toEntity(PostCreateRequest request);

  PostListResponse toListResponse(Post post);
}
