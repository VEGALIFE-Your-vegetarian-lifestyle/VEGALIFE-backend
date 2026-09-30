package com.vegalife.dto.mapper.admin;

import com.vegalife.dto.response.admin.CommentListResponse;
import com.vegalife.model.post.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface AdminCommentMapper {

  AdminCommentMapper INSTANCE = Mappers.getMapper(AdminCommentMapper.class);

  @Mapping(target = "username", ignore = true)
  @Mapping(
      target = "status",
      expression = "java(comment.getDeletedAt() == null ? \"active\" : \"removed\")")
  CommentListResponse toResponse(Comment comment);
}
