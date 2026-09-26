package com.vegalife.dto.mapper.post;

import com.vegalife.dto.response.post.PostListResponse;
import com.vegalife.model.post.Post;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PostMapper {

  PostListResponse toListResponse(Post post);
}
