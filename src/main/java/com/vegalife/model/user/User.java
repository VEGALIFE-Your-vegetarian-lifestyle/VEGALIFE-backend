package com.vegalife.model.user;

import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "\"user\"")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @Column(name = "username", length = 50, nullable = false, unique = true)
  private String username;

  @Column(name = "email", length = 100, nullable = false, unique = true)
  private String email;

  /**
   * BCrypt hash of the local password, or {@code null} when the account was created through an
   * external provider and has never had one (BR-AUTH-023). Every caller that reads this must treat
   * null as "no password to match", not as a malformed hash.
   */
  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "role", length = 20, nullable = false)
  @Enumerated(EnumType.STRING)
  private UserRole role;

  @Column(name = "status", length = 20, nullable = false)
  @Enumerated(EnumType.STRING)
  private UserStatus status;

  @Column(name = "email_verified", nullable = false)
  private Boolean emailVerified;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
