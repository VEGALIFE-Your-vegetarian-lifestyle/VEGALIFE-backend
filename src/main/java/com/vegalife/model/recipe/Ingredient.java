package com.vegalife.model.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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

  @Column(name = "calories", precision = 10, scale = 2)
  private BigDecimal calories;

  @Column(name = "protein_g", precision = 10, scale = 2)
  private BigDecimal proteinG;

  @Column(name = "carbohydrate_g", precision = 10, scale = 2)
  private BigDecimal carbohydrateG;

  @Column(name = "fat_g", precision = 10, scale = 2)
  private BigDecimal fatG;

  @Column(name = "fiber_g", precision = 10, scale = 2)
  private BigDecimal fiberG;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
