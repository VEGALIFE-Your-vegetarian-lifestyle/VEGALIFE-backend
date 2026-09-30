package com.vegalife.model.recipe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recipe_ingredient")
@Data
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

  @Column(name = "amount", precision = 10, scale = 3, nullable = false)
  private BigDecimal amount;

  @Column(name = "unit", length = 30, nullable = false)
  private String unit;
}
