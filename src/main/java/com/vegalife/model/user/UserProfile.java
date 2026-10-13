package com.vegalife.model.user;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_profile")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
  private User user;

  @Column(name = "height_cm", precision = 5, scale = 2)
  private BigDecimal heightCm;

  @Column(name = "weight_kg", precision = 5, scale = 2)
  private BigDecimal weightKg;

  @Column(name = "age")
  private Integer age;

  @Column(name = "gender", length = 20)
  @Enumerated(EnumType.STRING)
  private Gender gender;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "avatar_url")
  private String avatarUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "dietary_preferences", columnDefinition = "jsonb")
  private DietaryPreferences dietaryPreferences;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public enum Gender {
    male,
    female,
    other
  }
}
