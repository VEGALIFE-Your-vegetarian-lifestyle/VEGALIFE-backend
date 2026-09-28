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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "media")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Media {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "media_url", columnDefinition = "TEXT", nullable = false)
  private String mediaUrl;

  @Column(name = "thumbnail_url", columnDefinition = "TEXT")
  private String thumbnailUrl;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20, nullable = false)
  private Status status;

  @Column(name = "duration_seconds")
  private Integer durationSeconds;

  @Column(name = "file_size_bytes")
  private Long fileSizeBytes;

  @Column(name = "mime_type", length = 100)
  private String mimeType;

  @Column(name = "width")
  private Integer width;

  @Column(name = "height")
  private Integer height;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public enum Status {
    uploading,
    succeed,
    failed
  }
}
