package com.vegalife.model.saved;

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

/**
 * One saved post or saved recipe for a member (BR-SAVED-001).
 *
 * <p>A row targets exactly one thing: {@code postId} or {@code recipeId}, never both and never
 * neither. That is a {@code CHECK} constraint in the database, not a convention — a row with no
 * target would be invisible to every query and impossible to delete.
 *
 * <p>Targets are real foreign keys with {@code ON DELETE CASCADE}, so hard-deleting a post or
 * recipe removes its saves. Soft deletion is a different case: the target row survives, so the save
 * survives too and the read paths drop it by filtering on visibility instead. That keeps a save
 * intact across an unpublish/re-approve cycle, which is what a member expects when they return.
 *
 * <p>The user is stored as a plain {@code UUID} column, like {@link com.vegalife.model.post.Vote}:
 * a save is a membership row with no behaviour, and nothing here needs to traverse to the account.
 */
@Entity
@Table(name = "saved_item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  /** The saved post, or null when this row saves a recipe. */
  @Column(name = "post_id", updatable = false)
  private UUID postId;

  /** The saved recipe, or null when this row saves a post. */
  @Column(name = "recipe_id", updatable = false)
  private UUID recipeId;

  @CreationTimestamp
  @Column(name = "saved_at", nullable = false, updatable = false)
  private Instant savedAt;
}
