package com.vegalife.dto.mapper.profile;

import com.vegalife.dto.request.profile.UpdateProfileRequest;
import com.vegalife.dto.response.profile.ProfileResponse;
import com.vegalife.model.user.DietaryPreferences;
import com.vegalife.model.user.UserProfile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

/**
 * Maps the profile API's flat dietary fields onto the entity's internal {@link DietaryPreferences}
 * value type and back; the value type itself never appears on the wire. BMI is computed from
 * height/weight at mapping time — never stored.
 */
@Mapper(componentModel = "spring")
public interface ProfileMapper {

  ProfileMapper INSTANCE = Mappers.getMapper(ProfileMapper.class);

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "dietaryPreferences", expression = "java(dietaryFromRequest(request))")
  UserProfile toEntity(UpdateProfileRequest request);

  @Mapping(target = "userId", source = "user.id")
  @Mapping(target = "username", source = "user.username")
  @Mapping(target = "email", source = "user.email")
  @Mapping(target = "preferences", source = "dietaryPreferences.preferences")
  @Mapping(target = "dislikes", source = "dietaryPreferences.dislikes")
  @Mapping(target = "allergies", source = "dietaryPreferences.allergies")
  @Mapping(target = "restrictions", source = "dietaryPreferences.restrictions")
  @Mapping(target = "healthGoal", source = "dietaryPreferences.healthGoal")
  @Mapping(
      target = "bmi",
      expression = "java(computeBmi(profile.getHeightCm(), profile.getWeightKg()))")
  ProfileResponse toResponse(UserProfile profile);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "user", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "dietaryPreferences", expression = "java(mergeDietary(request, profile))")
  @Mapping(target = "heightCm", source = "request.heightCm")
  @Mapping(target = "weightKg", source = "request.weightKg")
  @Mapping(target = "age", source = "request.age")
  @Mapping(target = "gender", source = "request.gender")
  @Mapping(target = "description", source = "request.description")
  @Mapping(target = "avatarUrl", source = "request.avatarUrl")
  UserProfile updateEntityFromRequest(
      UpdateProfileRequest request, @MappingTarget UserProfile profile);

  /** Creation path: builds the value type from the flat request fields, or null when none given. */
  default DietaryPreferences dietaryFromRequest(UpdateProfileRequest request) {
    if (!request.hasDietaryField()) {
      return null;
    }
    return DietaryPreferences.builder()
        .preferences(request.getPreferences())
        .dislikes(request.getDislikes())
        .allergies(request.getAllergies())
        .restrictions(request.getRestrictions())
        .healthGoal(request.getHealthGoal())
        .build();
  }

  /**
   * Partial-update path: a null request member keeps the stored value; a provided member (even an
   * empty list) replaces it. When no dietary field is given at all, the existing value is kept
   * untouched.
   */
  default DietaryPreferences mergeDietary(UpdateProfileRequest request, UserProfile profile) {
    DietaryPreferences existing = profile == null ? null : profile.getDietaryPreferences();
    if (!request.hasDietaryField()) {
      return existing;
    }
    return DietaryPreferences.builder()
        .preferences(
            request.getPreferences() != null
                ? request.getPreferences()
                : existing == null ? null : existing.getPreferences())
        .dislikes(
            request.getDislikes() != null
                ? request.getDislikes()
                : existing == null ? null : existing.getDislikes())
        .allergies(
            request.getAllergies() != null
                ? request.getAllergies()
                : existing == null ? null : existing.getAllergies())
        .restrictions(
            request.getRestrictions() != null
                ? request.getRestrictions()
                : existing == null ? null : existing.getRestrictions())
        .healthGoal(
            request.getHealthGoal() != null
                ? request.getHealthGoal()
                : existing == null ? null : existing.getHealthGoal())
        .build();
  }

  /** BMI = kg / m^2, one decimal, HALF_UP; null when height or weight is unknown. */
  default BigDecimal computeBmi(BigDecimal heightCm, BigDecimal weightKg) {
    if (heightCm == null || weightKg == null || heightCm.signum() <= 0) {
      return null;
    }
    BigDecimal heightMeters = heightCm.movePointLeft(2);
    return weightKg.divide(heightMeters.multiply(heightMeters), 1, RoundingMode.HALF_UP);
  }
}
