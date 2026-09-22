package com.vegalife.dto.mapper.auth;

import com.vegalife.dto.response.auth.LoginResponse;
import com.vegalife.model.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LoginMapper {

  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "accessToken", ignore = true)
  @Mapping(target = "refreshToken", ignore = true)
  @Mapping(target = "tokenType", constant = "Bearer")
  @Mapping(target = "expiresIn", ignore = true)
  LoginResponse toLoginResponse(User user);
}
