package com.vegalife.repository.user;

import com.vegalife.model.user.UserProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

  Optional<UserProfile> findByUserId(UUID userId);

  boolean existsByUserId(UUID userId);
}
