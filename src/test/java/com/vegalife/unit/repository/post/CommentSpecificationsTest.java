package com.vegalife.unit.repository.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.vegalife.model.post.Comment;
import com.vegalife.repository.post.CommentRepository;
import com.vegalife.repository.post.CommentSpecifications;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class CommentSpecificationsTest {

  @Autowired private CommentRepository commentRepository;

  @Autowired private EntityManager entityManager;

  private final UUID userA = UUID.randomUUID();
  private final UUID userB = UUID.randomUUID();
  private final UUID post1 = UUID.randomUUID();
  private final UUID post2 = UUID.randomUUID();

  @Test
  void withFilters_noFilters_returnsAllCommentsRegardlessOfStatus() {
    createComment(userA, post1, null);
    createComment(userA, post1, Instant.now());
    createComment(userB, post2, null);
    createComment(userB, post2, Instant.now());
    flushAndClear();

    List<Comment> result = findAll(null, null, null, null, null);

    assertThat(result).hasSize(4);
  }

  @Test
  void withFilters_statusActive_returnsOnlyNonDeletedComments() {
    createComment(userA, post1, null);
    createComment(userA, post1, null);
    createComment(userB, post2, Instant.now());
    flushAndClear();

    List<Comment> result = findAll("active", null, null, null, null);

    assertThat(result).hasSize(2).allMatch(c -> c.getDeletedAt() == null);
  }

  @Test
  void withFilters_statusRemoved_returnsOnlyDeletedComments() {
    createComment(userA, post1, null);
    createComment(userB, post2, Instant.now());
    createComment(userB, post2, Instant.now());
    flushAndClear();

    List<Comment> result = findAll("removed", null, null, null, null);

    assertThat(result).hasSize(2).allMatch(c -> c.getDeletedAt() != null);
  }

  @Test
  void withFilters_byUser_returnsOnlyThatUsersComments() {
    createComment(userA, post1, null);
    createComment(userA, post2, Instant.now());
    createComment(userB, post1, null);
    flushAndClear();

    List<Comment> result = findAll(null, userA, null, null, null);

    assertThat(result).hasSize(2).allMatch(c -> userA.equals(c.getUserId()));
  }

  @Test
  void withFilters_byPost_returnsOnlyThatPostsComments() {
    createComment(userA, post1, null);
    createComment(userB, post1, Instant.now());
    createComment(userB, post2, null);
    flushAndClear();

    List<Comment> result = findAll(null, null, post1, null, null);

    assertThat(result).hasSize(2).allMatch(c -> post1.equals(c.getPostId()));
  }

  @Test
  void withFilters_createdRange_returnsOnlyCommentsCreatedInRange() {
    Comment old = createComment(userA, post1, null);
    Comment recent = createComment(userA, post1, null);
    Comment outside = createComment(userA, post1, null);
    setCreatedAt(old, Instant.now().minus(30, ChronoUnit.DAYS));
    setCreatedAt(recent, Instant.now().minus(1, ChronoUnit.DAYS));
    setCreatedAt(outside, Instant.now().plus(30, ChronoUnit.DAYS));
    flushAndClear();

    List<Comment> result =
        findAll(
            null,
            null,
            null,
            Instant.now().minus(7, ChronoUnit.DAYS),
            Instant.now().plus(1, ChronoUnit.HOURS));

    assertThat(result).extracting(Comment::getId).containsExactly(recent.getId());
  }

  @Test
  void withFilters_combinedFilters_narrowsToIntersection() {
    createComment(userA, post1, null);
    createComment(userA, post1, Instant.now());
    createComment(userA, post2, null);
    createComment(userB, post1, null);
    flushAndClear();

    List<Comment> result = findAll("active", userA, post1, null, null);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getUserId()).isEqualTo(userA);
    assertThat(result.getFirst().getPostId()).isEqualTo(post1);
    assertThat(result.getFirst().getDeletedAt()).isNull();
  }

  private List<Comment> findAll(
      String status, UUID userId, UUID postId, Instant createdFrom, Instant createdTo) {
    return commentRepository.findAll(
        CommentSpecifications.withFilters(status, userId, postId, createdFrom, createdTo));
  }

  private Comment createComment(UUID userId, UUID postId, Instant deletedAt) {
    Comment comment =
        Comment.builder()
            .userId(userId)
            .postId(postId)
            .content("Test comment content")
            .deletedAt(deletedAt)
            .build();
    entityManager.persist(comment);
    return comment;
  }

  private void setCreatedAt(Comment comment, Instant createdAt) {
    entityManager
        .createNativeQuery("UPDATE comment SET created_at = :ts WHERE id = :id")
        .setParameter("ts", createdAt)
        .setParameter("id", comment.getId())
        .executeUpdate();
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }
}
