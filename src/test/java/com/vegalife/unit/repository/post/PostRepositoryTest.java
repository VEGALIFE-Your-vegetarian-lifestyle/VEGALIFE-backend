package com.vegalife.unit.repository.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.PostRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class PostRepositoryTest {

  @Autowired private PostRepository postRepository;

  @Autowired private EntityManager entityManager;

  @Test
  void findByUserIdAndDeletedAtIsNull_returnsOnlyOwnersNonDeletedPosts() {
    User owner = createUser("owner", "owner@example.com");
    User anotherUser = createUser("another", "another@example.com");
    createPost(owner, "Owner's post", PostStatus.published, null);
    createPost(owner, "Owner's deleted post", PostStatus.published, Instant.now());
    createPost(anotherUser, "Another user's post", PostStatus.published, null);
    entityManager.flush();
    entityManager.clear();

    Page<Post> result =
        postRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
            owner.getId(), PageRequest.of(0, 20));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent()).extracting(Post::getTitle).containsExactly("Owner's post");
  }

  @Test
  void findByUserIdAndDeletedAtIsNull_returnsEmptyPageWhenOwnerHasNoPosts() {
    User owner = createUser("emptyowner", "emptyowner@example.com");
    entityManager.flush();
    entityManager.clear();

    Page<Post> result =
        postRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
            owner.getId(), PageRequest.of(0, 20));

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
  }

  private User createUser(String username, String email) {
    User user =
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("password-hash")
            .role(UserRole.USER)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build();
    entityManager.persist(user);
    return user;
  }

  private void createPost(User owner, String title, PostStatus status, Instant deletedAt) {
    entityManager.persist(
        Post.builder()
            .user(owner)
            .title(title)
            .content("Post content")
            .rawContent(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
            .status(status)
            .viewCount(0)
            .deletedAt(deletedAt)
            .build());
  }
}
