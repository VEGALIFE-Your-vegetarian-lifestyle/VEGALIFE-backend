package com.vegalife.dto.mapper.profile;

import com.vegalife.dto.request.profile.UpdateProfileRequest;
import com.vegalife.dto.response.profile.ProfileResponse;
import com.vegalife.model.user.UserProfile;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface ProfileMapper {

  ProfileMapper INSTANCE = Mappers.getMapper(ProfileMapper.class);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  UserProfile toEntity(UpdateProfileRequest request);

  @Mapping(target = "userId", source = "user.id")
  ProfileResponse toResponse(UserProfile profile);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "heightCm", source = "request.heightCm")
  @Mapping(target = "weightKg", source = "request.weightKg")
  @Mapping(target = "age", source = "request.age")
  @Mapping(target = "gender", source = "request.gender")
  @Mapping(target = "description", source = "request.description")
  @Mapping(target = "avatarUrl", source = "request.avatarUrl")
  UserProfile updateEntityFromRequest(
      UpdateProfileRequest request, @MappingTarget UserProfile profile);
}
