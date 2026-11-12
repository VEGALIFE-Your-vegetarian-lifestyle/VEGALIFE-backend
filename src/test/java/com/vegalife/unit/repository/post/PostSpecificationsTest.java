package com.vegalife.unit.repository.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.enums.post.PostFlag;
import com.vegalife.enums.post.PostStatus;
import com.vegalife.enums.user.UserRole;
import com.vegalife.enums.user.UserStatus;
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

/**
 * Filter-composition tests for the admin post list (issue #5).
 *
 * <p>The fixtures respect the ADR-023 invariant: a post carries a flag only while it is {@code
 * processing}. Published, drafted and admin-rejected posts have no blocker, which is why the flag
 * filters can never be combined with those statuses to select anything.
 */
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
  private Post aliceHeld;
  private Post aliceUnpublished;
  private Post aliceDeleted;
  private Post bobDrafted;

  @BeforeEach
  void seed() {
    alice = createUser("alice", "alice@example.com");
    bob = createUser("bob", "bob@example.com");
    vegan = createCategory("Vegan");
    quick = createCategory("Quick meals");

    alicePublished =
        createPost(
            alice, "Alice published", PostStatus.published, null, null, Set.of(vegan, quick));
    aliceHeld =
        createPost(
            alice,
            "Alice held",
            PostStatus.processing,
            PostFlag.FILTER_FAILED,
            null,
            Set.of(quick));
    aliceUnpublished =
        createPost(alice, "Alice unpublished", PostStatus.unpublished, null, null, Set.of(quick));
    aliceDeleted =
        createPost(
            alice, "Alice deleted", PostStatus.published, null, Instant.now(), Set.of(vegan));
    bobDrafted = createPost(bob, "Bob drafted", PostStatus.drafted, null, null, Set.of());

    entityManager.flush();
    setCreatedAt(alicePublished, CREATED_MARCH);
    setCreatedAt(aliceHeld, CREATED_JUNE);
    setCreatedAt(aliceUnpublished, CREATED_JUNE);
    setCreatedAt(aliceDeleted, CREATED_MARCH);
    setCreatedAt(bobDrafted, CREATED_SEPTEMBER);
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  void allWithFilters_withoutFilters_returnsEveryNonDeletedPostAcrossAuthorsAndStatuses() {
    Page<Post> result = findAll(null, null, null, null, null, null);

    assertThat(result.getContent())
        .extracting(Post::getTitle)
        .containsExactlyInAnyOrder(
            "Alice published", "Alice held", "Alice unpublished", "Bob drafted");
    assertThat(result.getTotalElements()).isEqualTo(4);
  }

  @Test
  void allWithFilters_byStatus_returnsOnlyThatStatus() {
    Page<Post> result = findAll(PostStatus.unpublished, null, null, null, null, null);

    assertThat(result.getContent()).extracting(Post::getTitle).containsExactly("Alice unpublished");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allWithFilters_byProcessing_returnsTheReviewQueue() {
    Page<Post> result = findAll(PostStatus.processing, null, null, null, null, null);

    assertThat(result.getContent()).extracting(Post::getTitle).containsExactly("Alice held");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allWithFilters_byFlag_returnsOnlyThatFlag() {
    Page<Post> result = findAll(null, PostFlag.FILTER_FAILED, null, null, null, null);

    assertThat(result.getContent()).extracting(Post::getTitle).containsExactly("Alice held");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allWithFilters_byStatusAndFlag_appliesBothPredicates() {
    Page<Post> result =
        findAll(PostStatus.processing, PostFlag.FILTER_FAILED, null, null, null, null);

    assertThat(result.getContent()).extracting(Post::getTitle).containsExactly("Alice held");
    assertThat(result.getTotalElements()).isOne();

    // The invariant makes a blocker on an admin-rejected post impossible, so this is empty.
    Page<Post> contradictory =
        findAll(PostStatus.unpublished, PostFlag.FILTER_FAILED, null, null, null, null);
    assertThat(contradictory.getContent()).isEmpty();
  }

  @Test
  void allWithFilters_byUserId_returnsOnlyThatAuthorsPosts() {
    Page<Post> result = findAll(null, null, bob.getId(), null, null, null);

    assertThat(result.getContent()).extracting(Post::getTitle).containsExactly("Bob drafted");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allWithFilters_byCategoryId_returnsEachMatchingPostExactlyOnce() {
    Page<Post> result = findAll(null, null, null, quick.getId(), null, null);

    assertThat(result.getContent())
        .extracting(Post::getTitle)
        .containsExactlyInAnyOrder("Alice published", "Alice held", "Alice unpublished");
    assertThat(result.getTotalElements()).isEqualTo(3);
  }

  @Test
  void allWithFilters_byCategoryWithSingleMembership_returnsThePostOnce() {
    Page<Post> result = findAll(null, null, null, vegan.getId(), null, null);

    assertThat(result.getContent())
        .extracting(Post::getTitle)
        .containsExactlyInAnyOrder("Alice published");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allWithFilters_byCreatedAtRange_isInclusiveOnBothBounds() {
    Page<Post> result = findAll(null, null, null, null, CREATED_MARCH, CREATED_JUNE);

    assertThat(result.getContent())
        .extracting(Post::getTitle)
        .containsExactlyInAnyOrder("Alice published", "Alice held", "Alice unpublished");
    assertThat(result.getTotalElements()).isEqualTo(3);
  }

  @Test
  void allWithFilters_byCreatedAtLowerBound_excludesPostsCreatedBeforeIt() {
    Page<Post> result =
        findAll(null, null, null, null, Instant.parse("2026-04-01T00:00:00Z"), null);

    assertThat(result.getContent())
        .extracting(Post::getTitle)
        .containsExactlyInAnyOrder("Alice held", "Alice unpublished", "Bob drafted");
    assertThat(result.getTotalElements()).isEqualTo(3);
  }

  @Test
  void allWithFilters_withCombinedFilters_appliesEveryPredicate() {
    Page<Post> result =
        findAll(
            PostStatus.published, null, alice.getId(), vegan.getId(), CREATED_MARCH, CREATED_JUNE);

    assertThat(result.getContent()).extracting(Post::getTitle).containsExactly("Alice published");
    assertThat(result.getTotalElements()).isOne();
  }

  @Test
  void allWithFilters_withFilterMatchingNothing_returnsEmptyPage() {
    Page<Post> result = findAll(null, PostFlag.FILTER_SKIPPED, null, null, null, null);

    assertThat(result.getContent()).isEmpty();
    assertThat(result.getTotalElements()).isZero();
  }

  private Page<Post> findAll(
      PostStatus status,
      PostFlag flag,
      UUID userId,
      UUID categoryId,
      Instant createdFrom,
      Instant createdTo) {
    Specification<Post> spec =
        PostSpecifications.allWithFilters(status, flag, userId, categoryId, createdFrom, createdTo);
    Sort sort = Sort.by(Sort.Direction.ASC, "createdAt");
    return postRepository.findAll(spec, PageRequest.of(0, 20, sort));
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

  private Category createCategory(String name) {
    Category category = Category.builder().name(name).build();
    entityManager.persist(category);
    return category;
  }

  private Post createPost(
      User owner,
      String title,
      PostStatus status,
      PostFlag flag,
      Instant deletedAt,
      Set<Category> categories) {
    Post post =
        Post.builder()
            .user(owner)
            .title(title)
            .content("Post content")
            .rawContent(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
            .status(status)
            .flag(flag)
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
