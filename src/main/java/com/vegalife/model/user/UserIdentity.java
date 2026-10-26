package com.vegalife.model.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Binds one external provider subject to one {@link User} (ADR-017, BR-AUTH-022).
 *
 * <p>Repeat sign-ins match on {@code (provider, providerSubject)}, never on the email: the subject
 * is stable for the life of the provider account, while the address can change or be reassigned.
 * {@code emailAtProvider} is the value seen at link time, kept for audit only.
 */
@Entity
@Table(
    name = "user_identity",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_user_identity_provider_subject",
          columnNames = {"provider", "provider_subject"}),
      @UniqueConstraint(
          name = "uq_user_identity_user_provider",
          columnNames = {"user_id", "provider"})
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "provider", length = 20, nullable = false)
  @Enumerated(EnumType.STRING)
  private AuthProvider provider;

  @Column(name = "provider_subject", length = 255, nullable = false)
  private String providerSubject;

  @Column(name = "email_at_provider", length = 255)
  private String emailAtProvider;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
