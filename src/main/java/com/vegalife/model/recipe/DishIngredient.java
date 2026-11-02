package com.vegalife.model.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Curated association between a dish and an ingredient that normally belongs to it (BR-DISH-002).
 *
 * <p>Written by an administrator, or seeded from a recipe at approval time (BR-DISH-003). The
 * {@code required} flag is advisory: a recipe omitting it is still valid, it is just reported as a
 * warning. {@code removedAt} is a tombstone so a withdrawn entry is not reinstated by a later
 * recipe approval.
 */
@Entity
@Table(name = "dish_ingredient")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(DishIngredientId.class)
public class DishIngredient {

  @Id
  @Column(name = "dish_id", nullable = false)
  private UUID dishId;

  @Id
  @Column(name = "ingredient_id", nullable = false)
  private UUID ingredientId;

  /**
   * Read-only association: the composite key already holds both ids, and the row is addressed by
   * them. {@code insertable = false} mirrors {@link RecipeIngredient}.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dish_id", insertable = false, updatable = false)
  private Dish dish;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ingredient_id", insertable = false, updatable = false)
  private Ingredient ingredient;

  @Column(name = "is_required", nullable = false)
  @Builder.Default
  private boolean required = false;

  /** Soft delete. Non-null means an admin withdrew this entry; the auto-seed path skips it. */
  @Column(name = "removed_at")
  private Instant removedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
