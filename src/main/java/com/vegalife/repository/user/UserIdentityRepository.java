package com.vegalife.repository.user;

import com.vegalife.model.user.AuthProvider;
import com.vegalife.model.user.UserIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

  /** Repeat sign-ins resolve through this: the provider subject is the stable key. */
  Optional<UserIdentity> findByProviderAndProviderSubject(
      AuthProvider provider, String providerSubject);

  boolean existsByUserIdAndProvider(UUID userId, AuthProvider provider);
}
