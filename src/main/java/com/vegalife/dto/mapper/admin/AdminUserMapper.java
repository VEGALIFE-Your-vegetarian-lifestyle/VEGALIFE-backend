package com.vegalife.dto.mapper.admin;

import com.vegalife.dto.response.admin.UserListResponse;
import com.vegalife.model.user.User;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface AdminUserMapper {

  AdminUserMapper INSTANCE = Mappers.getMapper(AdminUserMapper.class);

  UserListResponse toResponse(User user);

  List<UserListResponse> toResponseList(List<User> users);
}
