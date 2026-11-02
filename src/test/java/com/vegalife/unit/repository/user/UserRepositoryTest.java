package com.vegalife.unit.repository.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.user.User;
import com.vegalife.repository.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

  @Autowired private UserRepository userRepository;

  @Autowired private TestEntityManager entityManager;

  @Test
  void findByEmail_existingEmail_returnsUser() {
    User user =
        User.builder()
            .displayName("testuser")
            .email("test@example.com")
            .passwordHash("hash")
            .role(UserRole.USER)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build();

    entityManager.persistAndFlush(user);

    Optional<User> found = userRepository.findByEmail("test@example.com");

    assertThat(found).isPresent();
    assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    assertThat(found.get().getDisplayName()).isEqualTo("testuser");
  }

  @Test
  void findByEmail_nonExistingEmail_returnsEmpty() {
    Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

    assertThat(found).isEmpty();
  }

  @Test
  void save_twoUsersWithSameDisplayName_bothPersist() {
    // ADR-019: display_name is not unique — it is a label, not an identifier.
    User first =
        User.builder()
            .displayName("testuser")
            .email("first@example.com")
            .passwordHash("hash")
            .role(UserRole.USER)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build();
    User second =
        User.builder()
            .displayName("testuser")
            .email("second@example.com")
            .passwordHash("hash")
            .role(UserRole.USER)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build();

    userRepository.saveAllAndFlush(List.of(first, second));

    assertThat(userRepository.findAll())
        .filteredOn(user -> "testuser".equals(user.getDisplayName()))
        .hasSize(2);
  }

  @Test
  void existsByEmail_existingEmail_returnsTrue() {
    User user =
        User.builder()
            .displayName("testuser")
            .email("test@example.com")
            .passwordHash("hash")
            .role(UserRole.USER)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build();

    entityManager.persistAndFlush(user);

    boolean exists = userRepository.existsByEmail("test@example.com");

    assertThat(exists).isTrue();
  }

  @Test
  void existsByEmail_nonExistingEmail_returnsFalse() {
    boolean exists = userRepository.existsByEmail("nonexistent@example.com");

    assertThat(exists).isFalse();
  }

  @Test
  void save_userWithAllFields_persistsCorrectly() {
    User user =
        User.builder()
            .displayName("testuser")
            .email("test@example.com")
            .passwordHash("hash")
            .role(UserRole.USER)
            .status(UserStatus.created)
            .emailVerified(false)
            .build();

    User saved = userRepository.save(user);
    entityManager.flush();

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
    assertThat(saved.getDisplayName()).isEqualTo("testuser");
    assertThat(saved.getEmail()).isEqualTo("test@example.com");
    assertThat(saved.getRole()).isEqualTo(UserRole.USER);
    assertThat(saved.getStatus()).isEqualTo(UserStatus.created);
    assertThat(saved.getEmailVerified()).isFalse();
  }
}
