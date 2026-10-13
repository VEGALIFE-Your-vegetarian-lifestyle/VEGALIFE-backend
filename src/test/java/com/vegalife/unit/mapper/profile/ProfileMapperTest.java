package com.vegalife.unit.mapper.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.dto.mapper.profile.ProfileMapper;
import com.vegalife.dto.request.profile.UpdateProfileRequest;
import com.vegalife.dto.response.profile.ProfileResponse;
import com.vegalife.model.user.DietaryPreferences;
import com.vegalife.model.user.User;
import com.vegalife.model.user.UserProfile;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Whitebox coverage for the profile mapper's default methods: the flat-DTO ↔ DietaryPreferences
 * assembly, the member-wise partial-update merge, and the derived BMI (including the cm → m
 * conversion). Runs the real mapper, no mocks.
 */
class ProfileMapperTest {

  private final ProfileMapper mapper = ProfileMapper.INSTANCE;

  @Test
  void dietaryFromRequest_buildsTheValueTypeFromFlatFields() {
    UpdateProfileRequest request =
        UpdateProfileRequest.builder()
            .preferences(List.of("spicy"))
            .allergies(List.of("peanuts", "shellfish"))
            .healthGoal("weight loss")
            .build();

    DietaryPreferences dietary = mapper.dietaryFromRequest(request);

    assertThat(dietary.getPreferences()).containsExactly("spicy");
    assertThat(dietary.getAllergies()).containsExactly("peanuts", "shellfish");
    assertThat(dietary.getHealthGoal()).isEqualTo("weight loss");
    assertThat(dietary.getDislikes()).isNull();
    assertThat(dietary.getRestrictions()).isNull();
  }

  @Test
  void dietaryFromRequest_isNullWhenNoDietaryFieldGiven() {
    assertThat(mapper.dietaryFromRequest(UpdateProfileRequest.builder().age(30).build())).isNull();
  }

  @Test
  void mergeDietary_keepsExistingMembersWhenRequestOmitsThem() {
    UserProfile profile =
        UserProfile.builder()
            .dietaryPreferences(
                DietaryPreferences.builder()
                    .preferences(List.of("spicy"))
                    .allergies(List.of("peanuts"))
                    .healthGoal("muscle gain")
                    .build())
            .build();
    UpdateProfileRequest request =
        UpdateProfileRequest.builder().restrictions(List.of("gluten free")).build();

    DietaryPreferences merged = mapper.mergeDietary(request, profile);

    assertThat(merged.getRestrictions()).containsExactly("gluten free");
    assertThat(merged.getPreferences()).containsExactly("spicy");
    assertThat(merged.getAllergies()).containsExactly("peanuts");
    assertThat(merged.getHealthGoal()).isEqualTo("muscle gain");
  }

  @Test
  void mergeDietary_replacesAMemberWhenProvidedAndEmptyListClearsIt() {
    UserProfile profile =
        UserProfile.builder()
            .dietaryPreferences(DietaryPreferences.builder().allergies(List.of("peanuts")).build())
            .build();
    UpdateProfileRequest request = UpdateProfileRequest.builder().allergies(List.of()).build();

    DietaryPreferences merged = mapper.mergeDietary(request, profile);

    assertThat(merged.getAllergies()).isEmpty();
  }

  @Test
  void mergeDietary_keepsTheExistingValueUntouchedWhenNoDietaryFieldGiven() {
    DietaryPreferences existing =
        DietaryPreferences.builder().allergies(List.of("peanuts")).build();
    UserProfile profile = UserProfile.builder().dietaryPreferences(existing).build();

    assertThat(mapper.mergeDietary(UpdateProfileRequest.builder().age(30).build(), profile))
        .isSameAs(existing);
  }

  @Test
  void toResponse_flattensTheValueTypeAndComputesBmi() {
    UserProfile profile =
        UserProfile.builder()
            .user(User.builder().id(java.util.UUID.randomUUID()).username("u").build())
            .heightCm(new BigDecimal("170.00"))
            .weightKg(new BigDecimal("70.00"))
            .dietaryPreferences(
                DietaryPreferences.builder()
                    .preferences(List.of("spicy"))
                    .dislikes(List.of("mushrooms"))
                    .allergies(List.of("peanuts"))
                    .restrictions(List.of("gluten free"))
                    .healthGoal("weight loss")
                    .build())
            .build();

    ProfileResponse response = mapper.toResponse(profile);

    assertThat(response.getPreferences()).containsExactly("spicy");
    assertThat(response.getDislikes()).containsExactly("mushrooms");
    assertThat(response.getAllergies()).containsExactly("peanuts");
    assertThat(response.getRestrictions()).containsExactly("gluten free");
    assertThat(response.getHealthGoal()).isEqualTo("weight loss");
    // 70 / 1.70^2 = 24.22... → 24.2 (confirms cm → m before squaring)
    assertThat(response.getBmi()).isEqualByComparingTo("24.2");
  }

  @Test
  void toResponse_nullsEverythingWhenTheProfileHasNoDietaryData() {
    UserProfile profile =
        UserProfile.builder()
            .user(User.builder().id(java.util.UUID.randomUUID()).username("u").build())
            .build();

    ProfileResponse response = mapper.toResponse(profile);

    assertThat(response.getPreferences()).isNull();
    assertThat(response.getDislikes()).isNull();
    assertThat(response.getAllergies()).isNull();
    assertThat(response.getRestrictions()).isNull();
    assertThat(response.getHealthGoal()).isNull();
    assertThat(response.getBmi()).isNull();
  }

  @Test
  void computeBmi_isNullWhenEitherInputIsMissingOrHeightIsDegenerate() {
    assertThat(mapper.computeBmi(null, new BigDecimal("70"))).isNull();
    assertThat(mapper.computeBmi(new BigDecimal("170"), null)).isNull();
    assertThat(mapper.computeBmi(BigDecimal.ZERO, new BigDecimal("70"))).isNull();
    assertThat(mapper.computeBmi(new BigDecimal("-170"), new BigDecimal("70"))).isNull();
  }

  @Test
  void computeBmi_roundsHalfUpToOneDecimal() {
    // 60 / 1.65^2 = 22.038... → 22.0
    assertThat(mapper.computeBmi(new BigDecimal("165.00"), new BigDecimal("60.00")))
        .isEqualByComparingTo("22.0");
    // 54.5 / 1.60^2 = 21.289... → 21.3
    assertThat(mapper.computeBmi(new BigDecimal("160.00"), new BigDecimal("54.50")))
        .isEqualByComparingTo("21.3");
  }
}
