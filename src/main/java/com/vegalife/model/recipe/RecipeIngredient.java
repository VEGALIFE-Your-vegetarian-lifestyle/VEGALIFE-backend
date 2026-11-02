package com.vegalife.model.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "recipe_ingredient")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(RecipeIngredientId.class)
public class RecipeIngredient {

  @Id
  @Column(name = "recipe_id", nullable = false)
  private UUID recipeId;

  @Id
  @Column(name = "ingredient_id", nullable = false)
  private UUID ingredientId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recipe_id", insertable = false, updatable = false)
  private Recipe recipe;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ingredient_id", insertable = false, updatable = false)
  private Ingredient ingredient;

  @Column(name = "amount", nullable = false, precision = 10, scale = 3)
  private BigDecimal amount;

  @Column(name = "unit", nullable = false, length = 30)
  private String unit;
}
