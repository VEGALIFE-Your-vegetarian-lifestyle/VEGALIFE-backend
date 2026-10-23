package com.vegalife.model.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Admin-configurable platform setting, grouped by {@code category} (issue #13). */
@Entity
@Table(name = "platform_setting")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSetting {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "category", length = 30, nullable = false)
  private String category;

  @Column(name = "key", length = 100, nullable = false, unique = true)
  private String key;

  @Column(name = "value", columnDefinition = "TEXT")
  private String value;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
