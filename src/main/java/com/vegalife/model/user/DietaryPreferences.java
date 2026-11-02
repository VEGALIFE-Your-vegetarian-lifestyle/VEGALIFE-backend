package com.vegalife.model.user;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A member's dietary profile, stored as JSONB on {@code user_profile.dietary_preferences}. This is
 * an entity/mapper-internal value type only — the API exposes the five members as separate flat
 * fields, never as this object (decision: flat wire contract). All members are free-text and
 * optional; {@code null} on the owning column means "never set" and reads as all-empty.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DietaryPreferences {

  private List<String> preferences;

  private List<String> dislikes;

  private List<String> allergies;

  private List<String> restrictions;

  private String healthGoal;
}
