package com.vegalife.model.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "post")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "title", length = 255, nullable = false)
  private String title;

  @Column(name = "content", columnDefinition = "TEXT", nullable = false)
  private String content;

  @Column(name = "featured_image_url", columnDefinition = "TEXT")
  private String featuredImageUrl;

  /** Rich-text document produced by the frontend editor; {@code content} stays the filter input. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_content", nullable = false)
  private JsonNode rawContent;

  @ManyToMany
  @JoinTable(
      name = "post_category",
      joinColumns = @JoinColumn(name = "post_id"),
      inverseJoinColumns = @JoinColumn(name = "category_id"))
  @Builder.Default
  private Set<Category> categories = new HashSet<>();

  @ManyToMany
  @JoinTable(
      name = "post_media",
      joinColumns = @JoinColumn(name = "post_id"),
      inverseJoinColumns = @JoinColumn(name = "media_id"))
  @Builder.Default
  private Set<Media> media = new HashSet<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20, nullable = false)
  private PostStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "flag", length = 32)
  private PostFlag flag;

  /**
   * JSON snapshot for {@link #flag}, never a free-form caption (ADR-024). One object per blocker,
   * always carrying {@code kind} so it cannot describe a different blocker than {@code flag}: the
   * matched post, similarity and threshold for {@code SIMILAR_CONTENT}; the outcome, score and
   * applied threshold for {@code FILTER_FAILED}; a cause code for {@code FILTER_SKIPPED}. Kept out
   * of {@code flag} so the flag stays a closed, exactly-matchable vocabulary the admin filter can
   * rely on (ADR-023).
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "flag_detail")
  private JsonNode flagDetail;

  /**
   * Relevance score of {@code title + content} as produced by the content filter. {@code null}
   * means never scored: either the filter is disabled or the embedding call failed. That is
   * deliberately distinct from a low score (ADR-016).
   */
  @Column(name = "content_score")
  private Double contentScore;

  /**
   * The admin-supplied reason for the most recent rejection, cleared when an admin approves the
   * post again. Current state, not history — {@code moderation_log} keeps the audit trail.
   *
   * <p>Only a human decision writes this; the content filter records its verdict in {@link #flag}
   * instead. Nullable because the reason is optional and because every row rejected before the
   * column existed has its reason recorded nowhere but the log.
   */
  @Column(name = "rejection_reason", columnDefinition = "TEXT")
  private String rejectionReason;

  @Column(name = "view_count", nullable = false)
  private Integer viewCount;

  @Column(name = "published_at")
  private Instant publishedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
