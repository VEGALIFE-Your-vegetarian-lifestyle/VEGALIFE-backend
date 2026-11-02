package com.vegalife.model.recipe;

import com.vegalife.enums.recipe.ModerationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "ingredient")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ingredient {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "name", length = 100, nullable = false)
  private String name;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Soft delete; added with moderation so ingredients delete like recipes and dishes do. */
  @Column(name = "deleted_at")
  private Instant deletedAt;

  /** Review state of this ingredient name (ADR-016); see {@link Dish#getModerationStatus()}. */
  @Enumerated(EnumType.STRING)
  @Column(name = "moderation_status", length = 20, nullable = false)
  @Builder.Default
  private ModerationStatus moderationStatus = ModerationStatus.PENDING;
}
