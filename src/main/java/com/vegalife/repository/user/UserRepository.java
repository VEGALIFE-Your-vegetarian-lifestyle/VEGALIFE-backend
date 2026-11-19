package com.vegalife.repository.user;

import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.user.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      """
      select u.id as id, u.status as status, u.role as role, u.deletedAt as deletedAt
      from User u
      where u.id = :id
      """)
  Optional<UserAccountAuthState> findAccountAuthStateById(@Param("id") UUID id);

  /** Admin dashboard: total registered accounts. */
  long countByDeletedAtIsNull();

  /** Admin dashboard: accounts that have verified their email. Set once, so it never reverts. */
  long countByEmailVerifiedTrueAndDeletedAtIsNull();

  /** Admin dashboard: accounts currently in one lifecycle state (e.g. suspended). */
  long countByStatusAndDeletedAtIsNull(UserStatus status);
}
