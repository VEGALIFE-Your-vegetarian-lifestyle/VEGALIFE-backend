package com.vegalife.model.post;

import com.fasterxml.jackson.databind.JsonNode;
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
  private Status status;

  @Enumerated(EnumType.STRING)
  @Column(name = "flag", length = 16)
  private Flag flag;

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

  public enum Status {
    created,
    processed,
    published,
    unpublished,
    hidden
  }

  public enum Flag {
    PENDING,
    PASSED,
    REJECTED,
    NEEDS_REVIEW
  }
}
