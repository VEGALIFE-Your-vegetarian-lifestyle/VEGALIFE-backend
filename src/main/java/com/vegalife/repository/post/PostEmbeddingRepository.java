package com.vegalife.repository.post;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Post embedding storage and nearest-neighbour lookup (ADR-015).
 *
 * <p>Deliberately {@link JdbcTemplate} rather than a JPA entity: pgjdbc surfaces pgvector's {@code
 * vector} type as text, so an entity would need a custom Hibernate {@code BasicType}, while the
 * read path never needs the raw vector back — only a post id and a similarity score. Binding the
 * vector as text with a {@code ::vector} cast is the documented pgvector idiom and needs no new
 * dependency.
 */
@Repository
@RequiredArgsConstructor
public class PostEmbeddingRepository {

  private final JdbcTemplate jdbcTemplate;

  /**
   * Stores a post's vector, replacing any previous row for the same post.
   *
   * <p>Re-publishing a post therefore updates its vector rather than adding a second row, which is
   * what keeps a post from being compared against its own earlier content.
   *
   * @param postId the post the vector belongs to
   * @param embedding the raw {@code embed()} output, already unit-normalised by the client
   * @param model the embedding model that produced it
   */
  public void upsert(UUID postId, float[] embedding, String model) {
    jdbcTemplate.update(
        "INSERT INTO post_embedding (post_id, embedding, model) VALUES (?, ?::vector, ?)"
            + " ON CONFLICT (post_id) DO UPDATE"
            + "   SET embedding = EXCLUDED.embedding,"
            + "       model = EXCLUDED.model,"
            + "       created_at = now()",
        postId,
        literal(embedding),
        model);
  }

  /**
   * Finds the most similar published post to a candidate vector.
   *
   * <p>Only published, non-deleted posts are compared against — a draft, an unpublished post or a
   * rejected one must never flag someone else's content. Rows written under a different embedding
   * model are excluded rather than compared, since different models do not share a vector space.
   *
   * <p>The {@code ORDER BY} and {@code LIMIT} together are what make pgvector use the HNSW index;
   * the {@code LIMIT} is load-bearing, not an optimisation.
   *
   * @param embedding the candidate's vector
   * @param model the active embedding model
   * @param excludePostId the candidate's own id, excluded so it cannot match itself
   * @return the nearest match and its cosine similarity, or empty when there is no candidate
   */
  public Optional<NearestMatch> findNearestPublished(
      float[] embedding, String model, UUID excludePostId) {
    String vector = literal(embedding);
    return jdbcTemplate
        .query(
            "SELECT e.post_id, 1 - (e.embedding <=> ?::vector) AS similarity"
                + " FROM post_embedding e"
                + " JOIN post p ON p.id = e.post_id"
                + " WHERE p.status = 'published'"
                + "   AND p.deleted_at IS NULL"
                + "   AND e.model = ?"
                + "   AND e.post_id <> ?"
                + " ORDER BY e.embedding <=> ?::vector"
                + " LIMIT 1",
            (rs, rowNum) ->
                new NearestMatch(rs.getObject("post_id", UUID.class), rs.getDouble("similarity")),
            vector,
            model,
            excludePostId,
            vector)
        .stream()
        .findFirst();
  }

  /**
   * Reads back a post's own stored vector as pgvector text ({@code [0.1,0.2,…]}), for reuse as the
   * query vector of a related-posts lookup (issue #7) — never parsed into {@code float[]} in Java,
   * just round-tripped as the same literal {@link #findNearestPublished} and {@link
   * #findTopNearestPublished} accept.
   */
  public Optional<StoredEmbedding> findStoredEmbedding(UUID postId) {
    return jdbcTemplate
        .query(
            "SELECT embedding::text AS vec, model FROM post_embedding WHERE post_id = ?",
            (rs, rowNum) -> new StoredEmbedding(rs.getString("vec"), rs.getString("model")),
            postId)
        .stream()
        .findFirst();
  }

  /**
   * Finds the {@code limit} most similar published posts to a candidate vector (issue #7),
   * excluding the candidate itself, ranked nearest first.
   *
   * @param embeddingLiteral the candidate's vector in pgvector text form (from {@link
   *     #findStoredEmbedding} or {@link #literal})
   * @param model the embedding model the vector was produced with
   * @param excludePostId the candidate's own id, excluded so it cannot match itself
   * @param limit maximum number of matches to return
   */
  public List<NearestMatch> findTopNearestPublished(
      String embeddingLiteral, String model, UUID excludePostId, int limit) {
    return jdbcTemplate.query(
        "SELECT e.post_id, 1 - (e.embedding <=> ?::vector) AS similarity"
            + " FROM post_embedding e"
            + " JOIN post p ON p.id = e.post_id"
            + " WHERE p.status = 'published'"
            + "   AND p.deleted_at IS NULL"
            + "   AND e.model = ?"
            + "   AND e.post_id <> ?"
            + " ORDER BY e.embedding <=> ?::vector"
            + " LIMIT ?",
        (rs, rowNum) ->
            new NearestMatch(rs.getObject("post_id", UUID.class), rs.getDouble("similarity")),
        embeddingLiteral,
        model,
        excludePostId,
        embeddingLiteral,
        limit);
  }

  /** A candidate match: which published post, and how similar. */
  public record NearestMatch(UUID postId, double similarity) {}

  /** A post's own stored vector, in pgvector text form, with the model that produced it. */
  public record StoredEmbedding(String vectorLiteral, String model) {}

  /**
   * Renders a vector in pgvector's text form, {@code [0.1,0.2,…]}.
   *
   * <p>{@link Locale#ROOT} is not cosmetic: on a JVM whose default locale uses a decimal comma the
   * literal would render as {@code [0,1,0,2]} and be rejected by the cast.
   */
  private static String literal(float[] embedding) {
    StringBuilder sb = new StringBuilder(embedding.length * 10).append('[');
    for (int i = 0; i < embedding.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(String.format(Locale.ROOT, "%.7f", embedding[i]));
    }
    return sb.append(']').toString();
  }
}
