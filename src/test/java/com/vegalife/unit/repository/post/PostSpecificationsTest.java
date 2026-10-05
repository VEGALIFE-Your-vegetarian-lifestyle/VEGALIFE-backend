package com.vegalife.unit.repository.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.model.post.Category;
import com.vegalife.model.post.Post;
import com.vegalife.model.user.User;
import com.vegalife.repository.post.PostRepository;
import com.vegalife.repository.post.PostSpecifications;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class PostSpecificationsTest {

  private static final Instant CREATED_MARCH = Instant.parse("2026-03-15T12:00:00Z");
  private static final Instant CREATED_JUNE = Instant.parse("2026-06-15T12:00:00Z");
  private static final Instant CREATED_SEPTEMBER = Instant.parse("2026-09-15T12:00:00Z");

  @Autowired private PostRepository postRepository;

  @Autowired private EntityManager entityManager;

  private User alice;
  private User bob;
  private Category vegan;
  private Category quick;
  private Post alicePublished;
  private Post aliceFlagged;
  private Post aliceDeleted;
  private Post bobCreated;

  @BeforeEach
  void seed() {
    alice = createUser("alice", "alice@example.com");
    bob = createUser("bob", "bob@example.com");
    vegan = createCategory("Vegan");
    quick = createCategory("Quick meals");

    alicePublished =
        createPost(alice, "Alice published", Post.Status.published, null, Set.of(vegan, quick));
    aliceFlagged = createPost(alice, "Alice flagged", Post.Status.flagged, null, Set.of(quick));
    aliceDeleted =
        createPost(alice, "Alice deleted", Post.Status.published, Instant.now(), Set.of(vegan));
    bobCreated = createPost(bob, "Bob created", Post.Status.created, null, Set.of());

    entityManager.flush();
    setCreatedAt(alicePublished, CREATED_MARCH);
    setCreatedAt(aliceFlagged, CREATED_JUNE);
    setCreatedAt(aliceDeleted, CREATED_MARCH);
    setCreatedAt(bobCreated, CREATED_SEPTEMBER);
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void allWithFilters_withoutFilters_returnsEveryNonDeletedPostAcrossAuthorsAndStatuses() {
    Page<Post> result = findAll(null, null, null, null, null);

    assertThat(result.getContent())
        .extracting(Post::getTitle)
        .containsExactlyInAnyOrder("Alice published", "Alice flagged", "Bob created");
    assertThat(result.getTotalElements()).isEqualTo(3);
  }

  @Test
  void allWithFilters_byStatus_returnsOnlyThatStatus() {
    Page<Post> result = findAll(Post.Status.flagged, null, null, null, null);

    assertThat(result.getContent()).extracting(Post::getTitle).containsExactly("Alice flagged");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allWithFilters_byUserId_returnsOnlyThatAuthorsPosts() {
    Page<Post> result = findAll(null, bob.getId(), null, null, null);

    assertThat(result.getContent()).extracting(Post::getTitle).containsExactly("Bob created");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allWithFilters_byCategoryId_returnsEachMatchingPostExactlyOnce() {
    Page<Post> result = findAll(null, null, quick.getId(), null, null);

    assertThat(result.getContent())
        .extracting(Post::getTitle)
        .containsExactlyInAnyOrder("Alice published", "Alice flagged");
    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  void allWithFilters_byCategoryWithSingleMembership_returnsThePostOnce() {
    Page<Post> result = findAll(null, null, vegan.getId(), null, null);

    assertThat(result.getContent())
        .extracting(Post::getTitle)
        .containsExactlyInAnyOrder("Alice published");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allWithFilters_byCreatedAtRange_isInclusiveOnBothBounds() {
    Page<Post> result = findAll(null, null, null, CREATED_MARCH, CREATED_JUNE);

    assertThat(result.getContent())
        .extracting(Post::getTitle)
        .containsExactlyInAnyOrder("Alice published", "Alice flagged");
    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  void allWithFilters_byCreatedAtLowerBound_excludesPostsCreatedBeforeIt() {
    Page<Post> result = findAll(null, null, null, Instant.parse("2026-04-01T00:00:00Z"), null);

    assertThat(result.getContent())
        .extracting(Post::getTitle)
        .containsExactlyInAnyOrder("Alice flagged", "Bob created");
    assertThat(result.getTotalElements()).isEqualTo(2);
  }

  @Test
  void allWithFilters_withCombinedFilters_appliesEveryPredicate() {
    Page<Post> result =
        findAll(Post.Status.published, alice.getId(), vegan.getId(), CREATED_MARCH, CREATED_JUNE);

    assertThat(result.getContent()).extracting(Post::getTitle).containsExactly("Alice published");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allWithFilters_withFilterMatchingNothing_returnsEmptyPage() {
    Page<Post> result = findAll(Post.Status.unpublished, null, null, null, null);

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
  }

  private Page<Post> findAll(
      Post.Status status, UUID userId, UUID categoryId, Instant createdFrom, Instant createdTo) {
    Specification<Post> spec =
        PostSpecifications.allWithFilters(status, userId, categoryId, createdFrom, createdTo);
    Sort sort = Sort.by(Sort.Direction.ASC, "createdAt");
    return postRepository.findAll(spec, PageRequest.of(0, 20, sort));
  }

  private User createUser(String username, String email) {
    User user =
        User.builder()
            .username(username)
            .email(email)
            .passwordHash("password-hash")
            .role(User.Role.USER)
            .status(User.Status.activated)
            .emailVerified(true)
            .build();
    entityManager.persist(user);
    return user;
  }

  private Category createCategory(String name) {
    Category category = Category.builder().name(name).build();
    entityManager.persist(category);
    return category;
  }

  private Post createPost(
      User owner, String title, Post.Status status, Instant deletedAt, Set<Category> categories) {
    Post post =
        Post.builder()
            .user(owner)
            .title(title)
            .content("Post content")
            .rawContent(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
            .status(status)
            .flag(Post.Flag.PENDING)
            .viewCount(0)
            .deletedAt(deletedAt)
            .categories(categories)
            .build();
    entityManager.persist(post);
    return post;
  }

  private void setCreatedAt(Post post, Instant createdAt) {
    entityManager
        .createNativeQuery("UPDATE post SET created_at = :createdAt WHERE id = :id")
        .setParameter("createdAt", Timestamp.from(createdAt))
        .setParameter("id", post.getId())
        .executeUpdate();
  }
}
