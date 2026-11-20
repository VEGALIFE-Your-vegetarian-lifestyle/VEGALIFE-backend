package com.vegalife.unit.repository.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.PostSpecifications;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
        postRepository.findAll(
            PostSpecifications.filtering(null, null, owner.getId(), null, null, null, null),
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

    assertThat(result.getTotalElements()).isEqualTo(1);
    assertThat(result.getContent()).extracting(Post::getTitle).containsExactly("Owner's post");
  }

  @Test
  void findByUserIdAndDeletedAtIsNull_returnsEmptyPageWhenOwnerHasNoPosts() {
    User owner = createUser("emptyowner", "emptyowner@example.com");
    entityManager.flush();
    entityManager.clear();

    Page<Post> result =
        postRepository.findAll(
            PostSpecifications.filtering(null, null, owner.getId(), null, null, null, null),
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
  }

  @Test
  void incrementViewCount_addsOneWithoutTouchingUpdatedAt() {
    User owner = createUser("counter", "counter@example.com");
    Post post = createPost(owner, "Viewed post", PostStatus.published, null);
    entityManager.flush();
    entityManager.clear();
    Instant updatedAtBefore = postRepository.findById(post.getId()).orElseThrow().getUpdatedAt();

    int updated = postRepository.incrementViewCount(post.getId());

    assertThat(updated).isEqualTo(1);
    entityManager.clear();
    Post reloaded = postRepository.findById(post.getId()).orElseThrow();
    assertThat(reloaded.getViewCount()).isEqualTo(1);
    assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAtBefore);
  }

  @Test
  void incrementViewCount_unknownPostUpdatesNothing() {
    assertThat(postRepository.incrementViewCount(UUID.randomUUID())).isZero();
  }

  private User createUser(String displayName, String email) {
    User user =
        User.builder()
            .displayName(displayName)
            .email(email)
            .passwordHash("password-hash")
            .role(UserRole.USER)
            .status(UserStatus.activated)
            .emailVerified(true)
            .build();
    entityManager.persist(user);
    return user;
  }

  private Post createPost(User owner, String title, PostStatus status, Instant deletedAt) {
    Post post =
        Post.builder()
            .user(owner)
            .title(title)
            .content("Post content")
            .rawContent(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
            .status(status)
            .viewCount(0)
            .deletedAt(deletedAt)
            .build();
    entityManager.persist(post);
    return post;
  }
}
