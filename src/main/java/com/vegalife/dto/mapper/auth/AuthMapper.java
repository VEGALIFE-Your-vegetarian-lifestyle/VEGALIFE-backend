package com.vegalife.dto.mapper.auth;

import com.vegalife.dto.request.auth.RegisterRequest;
import com.vegalife.dto.response.auth.RegisterResponse;
import com.vegalife.model.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "passwordHash", source = "encodedPassword")
  @Mapping(target = "role", constant = "USER")
  @Mapping(target = "status", constant = "created")
  @Mapping(target = "emailVerified", constant = "false")
  @Mapping(target = "lastLoginAt", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "deletedAt", ignore = true)
  User toEntity(RegisterRequest request, String encodedPassword);

  @Mapping(target = "userId", source = "user.id")
  RegisterResponse toRegisterResponse(User user);
}
