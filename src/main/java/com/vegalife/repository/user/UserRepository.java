package com.vegalife.repository.user;

import com.vegalife.model.user.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  Optional<User> findByUsername(String username);

  boolean existsByEmail(String email);

  boolean existsByUsername(String username);

  @Query(
      """
      select u from User u
      where u.deletedAt is null
        and (:status is null or u.status = :status)
        and (:role is null or u.role = :role)
        and (:createdFrom is null or u.createdAt >= :createdFrom)
        and (:createdTo is null or u.createdAt <= :createdTo)
      """)
  Page<User> findAllActive(
      @Param("status") User.Status status,
      @Param("role") User.Role role,
      @Param("createdFrom") Instant createdFrom,
      @Param("createdTo") Instant createdTo,
      Pageable pageable);
}
