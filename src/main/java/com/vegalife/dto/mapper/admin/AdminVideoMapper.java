package com.vegalife.dto.mapper.admin;

import com.vegalife.dto.response.admin.AdminVideoListResponse;
import com.vegalife.dto.response.admin.AdminVideoPostResponse;
import com.vegalife.model.post.Media;
import com.vegalife.model.post.Post;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminVideoMapper {

  @Mapping(target = "userId", source = "uploadedBy.id")
  @Mapping(target = "username", source = "uploadedBy.username")
  @Mapping(target = "email", source = "uploadedBy.email")
  @Mapping(target = "posts", ignore = true)
  AdminVideoListResponse toResponse(Media media);

  List<AdminVideoListResponse> toResponseList(List<Media> media);

  AdminVideoPostResponse toPostResponse(Post post);
}
