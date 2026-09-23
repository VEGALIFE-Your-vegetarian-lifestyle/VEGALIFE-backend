package com.vegalife.repository.user;

import com.vegalife.model.user.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

  Optional<User> findByEmail(String email);

  Optional<User> findByUsername(String username);

  boolean existsByEmail(String email);

  boolean existsByUsername(String username);

  @Query(
      """
      select u.id as id, u.status as status, u.role as role, u.deletedAt as deletedAt
      from User u
      where u.id = :id
      """)
  Optional<UserAccountAuthState> findAccountAuthStateById(@Param("id") UUID id);
}
