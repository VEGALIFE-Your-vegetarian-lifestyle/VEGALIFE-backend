package com.vegalife.model.post;

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

/**
 * One audit row per filter run (FR-010, BR-FILTER-010; V18 {@code moderation_log} pattern). {@code
 * score} is {@code null} when a static rule rejected before any embedding was produced.
 */
@Entity
@Table(name = "post_filter_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostFilterLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "post_id", nullable = false)
  private UUID postId;

  @Enumerated(EnumType.STRING)
  @Column(name = "flag", length = 16, nullable = false)
  private Post.Flag flag;

  @Column(name = "score")
  private Double score;

  @Column(name = "reasons", columnDefinition = "TEXT")
  private String reasons;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
