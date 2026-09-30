package com.vegalife.model.recipe;

import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "recipe")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recipe {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dish_id", nullable = false)
  private Dish dish;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "name", length = 255, nullable = false)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "instructions", columnDefinition = "TEXT", nullable = false)
  private String instructions;

  @Column(name = "prep_time_minutes")
  private Integer prepTimeMinutes;

  @Column(name = "cook_time_minutes")
  private Integer cookTimeMinutes;

  @Column(name = "servings", nullable = false)
  private Integer servings;

  @Enumerated(EnumType.STRING)
  @Column(name = "difficulty", length = 20)
  private Difficulty difficulty;

  @ManyToMany
  @JoinTable(
      name = "post_recipe",
      joinColumns = @JoinColumn(name = "recipe_id"),
      inverseJoinColumns = @JoinColumn(name = "post_id"))
  @Builder.Default
  private Set<Post> posts = new HashSet<>();

  @OneToMany(mappedBy = "recipe")
  @Builder.Default
  private List<RecipeIngredient> recipeIngredients = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public enum Difficulty {
    EASY,
    MEDIUM,
    HARD
  }
}
