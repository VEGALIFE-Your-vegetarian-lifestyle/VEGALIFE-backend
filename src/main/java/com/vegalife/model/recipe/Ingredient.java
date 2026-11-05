package com.vegalife.model.recipe;

import com.vegalife.enums.recipe.ModerationStatus;
import com.vegalife.enums.recipe.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * A shared ingredient name, its measurement unit, and its nutrition per one unit (ADR-022).
 *
 * <p>Nutrition lives here rather than on {@link Recipe}: a recipe's totals are the sum of its
 * ingredient lines, so this is the single place a nutrition fact is authored. Storing a total on
 * the recipe as well would create two sources of truth that can disagree.
 */
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

  /**
   * The one unit every {@code recipe_ingredient.amount} for this ingredient is expressed in, and
   * the basis the nutrition values below are per.
   *
   * <p>{@code null} means the unit is not yet decided: either the legacy backfill could not map
   * every existing line to one dimension, or a member created the name without one. Any recipe
   * using this ingredient is then uncomputable rather than wrong.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "unit", length = 10)
  private Unit unit;

  /**
   * Nutrition per <em>one</em> {@link #unit} of this ingredient, never per 100 anything and never
   * per serving. {@code null} on all five means "not provided", which is deliberately distinct from
   * 0 (water is legitimately 0 kcal).
   *
   * <p>Scale is 6 because a per-gram figure is small: cooked rice is ~0.0130 g protein per gram, so
   * a two-decimal column would round most macros to zero.
   *
   * <p>All five are null or all five are set, enforced by {@code
   * chk_ingredient_nutrition_complete}. A partially filled ingredient could produce a recipe total
   * that looks authoritative while silently omitting a macro.
   */
  @Column(name = "calories", precision = 12, scale = 6)
  private BigDecimal calories;

  @Column(name = "protein_g", precision = 12, scale = 6)
  private BigDecimal proteinG;

  @Column(name = "carbohydrate_g", precision = 12, scale = 6)
  private BigDecimal carbohydrateG;

  @Column(name = "fat_g", precision = 12, scale = 6)
  private BigDecimal fatG;

  @Column(name = "fiber_g", precision = 12, scale = 6)
  private BigDecimal fiberG;

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

  /**
   * True when a recipe line can be priced: the unit and all five nutrition values are present.
   *
   * <p>This is the single definition of "resolved" used by the calculator, so the rule cannot drift
   * between the write path and the read path.
   */
  public boolean hasResolvedNutrition() {
    return unit != null
        && calories != null
        && proteinG != null
        && carbohydrateG != null
        && fatG != null
        && fiberG != null;
  }
}
