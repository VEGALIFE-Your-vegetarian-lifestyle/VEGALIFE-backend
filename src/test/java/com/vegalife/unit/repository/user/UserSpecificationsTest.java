package com.vegalife.unit.repository.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserRepository;
import com.vegalife.repository.user.UserSpecifications;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

/**
 * Filter-composition tests for the admin user list.
 *
 * <p>Every assertion runs the query, so a predicate that compiles but never matches (or matches
 * everything) fails here rather than in production.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserSpecificationsTest {

  private static final Instant CREATED_MARCH = Instant.parse("2026-03-15T12:00:00Z");
  private static final Instant CREATED_JUNE = Instant.parse("2026-06-15T12:00:00Z");

  @Autowired private UserRepository userRepository;

  @Autowired private EntityManager entityManager;

  private User jane;
  private User john;
  private User admin;
  private User deactivated;
  private User deleted;

  @BeforeEach
  void seed() {
    jane = createUser("jane", "jane@example.com", UserRole.USER, UserStatus.activated, true, null);
    john = createUser("john", "john@other.org", UserRole.USER, UserStatus.activated, false, null);
    admin =
        createUser("root", "admin@example.com", UserRole.ADMIN, UserStatus.activated, true, null);
    deactivated =
        createUser("gone", "gone@example.com", UserRole.USER, UserStatus.deactivated, true, null);
    deleted =
        createUser(
            "ghost", "ghost@example.com", UserRole.USER, UserStatus.activated, true, Instant.now());

    entityManager.flush();
    setCreatedAt(jane, CREATED_MARCH);
    setCreatedAt(john, CREATED_JUNE);
    setCreatedAt(admin, CREATED_MARCH);
    setCreatedAt(deactivated, CREATED_JUNE);
    setCreatedAt(deleted, CREATED_MARCH);
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void activeWithFilters_withoutFilters_returnsEveryNonDeletedAccount() {
    Page<User> result = findAll(null, null, null);

    assertThat(result.getContent())
        .extracting(User::getDisplayName)
        .containsExactlyInAnyOrder("jane", "john", "root", "gone");
    assertThat(result.getTotalElements()).isEqualTo(4);
  }

  @Test
  void activeWithFilters_neverReturnsSoftDeletedAccounts() {
    Page<User> result = findAll(null, null, null);

    assertThat(result.getContent()).extracting(User::getDisplayName).doesNotContain("ghost");
  }

  @Test
  void activeWithFilters_byKeywordOnDisplayName_matchesCaseInsensitively() {
    Page<User> result = findAll(null, "JA", null);

    assertThat(result.getContent()).extracting(User::getDisplayName).containsExactly("jane");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void activeWithFilters_byKeywordOnEmail_matchesSubstring() {
    Page<User> result = findAll(null, "example.com", null);

    assertThat(result.getContent())
        .extracting(User::getDisplayName)
        .containsExactlyInAnyOrder("jane", "root", "gone");
    assertThat(result.getTotalElements()).isEqualTo(3);
  }

  @Test
  void activeWithFilters_byKeywordMatchingDisplayNameAndEmail_returnsTheUserOnce() {
    Page<User> result = findAll(null, "jane", null);

    assertThat(result.getContent()).extracting(User::getDisplayName).containsExactly("jane");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void activeWithFilters_byKeywordExcludingSoftDeletedAccount_returnsNothing() {
    Page<User> result = findAll(null, "ghost", null);

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
  }

  @Test
  void activeWithFilters_byKeywordWithWildcardCharacter_matchesLiterally() {
    Page<User> result = findAll(null, "%@example.com", null);

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
  }

  @Test
  void activeWithFilters_byBlankKeyword_appliesNoKeywordPredicate() {
    Page<User> result = findAll(null, "   ", null);

    assertThat(result.getTotalElements()).isEqualTo(4);
  }

  @Test
  void activeWithFilters_byEmailVerifiedTrue_returnsOnlyVerifiedAccounts() {
    Page<User> result = findAll(Boolean.TRUE, null, null);

    assertThat(result.getContent())
        .extracting(User::getDisplayName)
        .containsExactlyInAnyOrder("jane", "root", "gone");
    assertThat(result.getTotalElements()).isEqualTo(3);
  }

  @Test
  void activeWithFilters_byEmailVerifiedFalse_returnsOnlyUnverifiedAccounts() {
    Page<User> result = findAll(Boolean.FALSE, null, null);

    assertThat(result.getContent()).extracting(User::getDisplayName).containsExactly("john");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void activeWithFilters_byKeywordAndEmailVerified_combinesBoth() {
    Page<User> result = findAll(Boolean.TRUE, "example.com", null);

    assertThat(result.getContent())
        .extracting(User::getDisplayName)
        .containsExactlyInAnyOrder("jane", "root", "gone");
    assertThat(result.getTotalElements()).isEqualTo(3);
  }

  @Test
  void activeWithFilters_byKeywordAndStatus_combinesBoth() {
    Page<User> result = findAll(null, "example", UserStatus.deactivated);

    assertThat(result.getContent()).extracting(User::getDisplayName).containsExactly("gone");
    assertThat(result.getTotalElements()).isOne();
  }

  private Page<User> findAll(Boolean emailVerified, String keyword, UserStatus status) {
    Specification<User> spec =
        UserSpecifications.activeWithFilters(status, null, null, null, keyword, emailVerified);
    return userRepository.findAll(
        spec, PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "createdAt")));
  }

  private User createUser(
      String displayName,
      String email,
      UserRole role,
      UserStatus status,
      boolean emailVerified,
      Instant deletedAt) {
    User user =
        User.builder()
            .displayName(displayName)
            .email(email)
            .passwordHash("password-hash")
            .role(role)
            .status(status)
            .emailVerified(emailVerified)
            .deletedAt(deletedAt)
            .build();
    entityManager.persist(user);
    return user;
  }

  private void setCreatedAt(User user, Instant createdAt) {
    entityManager
        .createNativeQuery("UPDATE \"user\" SET created_at = :createdAt WHERE id = :id")
        .setParameter("createdAt", java.sql.Timestamp.from(createdAt))
        .setParameter("id", user.getId())
        .executeUpdate();
  }
}
