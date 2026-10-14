package com.vegalife.repository.search;

import com.vegalife.dto.request.search.SearchType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SearchDao {

  private static final String POST_SELECT =
      "SELECT p.id, 'POST' AS type, p.title, p.content AS summary, p.featured_image_url AS thumbnail_url,"
          + " NULL::text AS media_url, NULL::uuid AS related_post_id,"
          + " p.user_id AS owner_id, p.published_at AS result_date,"
          + " CASE WHEN lower(p.title) = lower(?) THEN 1.00"
          + " WHEN strpos(lower(p.title), lower(?)) = 1 THEN 0.90"
          + " WHEN strpos(lower(p.title), lower(?)) > 0 THEN 0.80"
          + " WHEN strpos(lower(p.content), lower(?)) > 0 THEN 0.45 END AS score,"
          + " CASE WHEN strpos(lower(p.content), lower(?)) > 0 THEN p.content END AS snippet_source"
          + " FROM post p WHERE p.deleted_at IS NULL"
          + " AND p.status = 'published'"
          + " AND (strpos(lower(p.title), lower(?)) > 0 OR strpos(lower(p.content), lower(?)) > 0)";

  private static final String VIDEO_SELECT =
      "SELECT m.id, 'VIDEO' AS type, COALESCE(linked.title, m.description, 'Video') AS title,"
          + " m.description AS summary, m.thumbnail_url, m.media_url, linked.id AS related_post_id,"
          + " m.uploaded_by AS owner_id,"
          + " m.created_at AS result_date,"
          + " CASE WHEN lower(COALESCE(linked.title, '')) = lower(?) THEN 1.00"
          + " WHEN strpos(lower(COALESCE(linked.title, '')), lower(?)) = 1 THEN 0.90"
          + " WHEN strpos(lower(COALESCE(linked.title, '')), lower(?)) > 0 THEN 0.80"
          + " WHEN strpos(lower(COALESCE(m.description, '')), lower(?)) > 0 THEN 0.65 END AS score,"
          + " CASE WHEN strpos(lower(COALESCE(m.description, '')), lower(?)) > 0"
          + " THEN m.description ELSE linked.title END AS snippet_source FROM media m"
          + " JOIN LATERAL (SELECT p.title, p.id FROM post_media pm"
          + " JOIN post p ON p.id = pm.post_id WHERE pm.media_id = m.id"
          + " AND p.deleted_at IS NULL AND p.status = 'published'"
          + " ORDER BY CASE WHEN strpos(lower(p.title), lower(?)) > 0 THEN 0 ELSE 1 END,"
          + " p.published_at DESC, p.id ASC LIMIT 1) linked ON TRUE"
          + " WHERE m.deleted_at IS NULL AND m.status = 'succeed'"
          + " AND m.mime_type LIKE 'video/%'"
          + " AND (strpos(lower(COALESCE(linked.title, '')), lower(?)) > 0"
          + " OR strpos(lower(COALESCE(m.description, '')), lower(?)) > 0)";

  private static final String RECIPE_SELECT =
      "SELECT r.id, 'RECIPE' AS type, r.name AS title,"
          + " COALESCE(r.description, d.description) AS summary, d.image_url AS thumbnail_url,"
          + " NULL::text AS media_url, NULL::uuid AS related_post_id,"
          + " r.user_id AS owner_id, r.created_at AS result_date,"
          + " CASE WHEN lower(r.name) = lower(?) THEN 1.00"
          + " WHEN strpos(lower(r.name), lower(?)) = 1 THEN 0.90"
          + " WHEN strpos(lower(r.name), lower(?)) > 0 THEN 0.80"
          + " WHEN strpos(lower(COALESCE(d.name, '')), lower(?)) > 0"
          + " OR strpos(lower(COALESCE(r.description, '')), lower(?)) > 0 THEN 0.65"
          + " WHEN strpos(lower(r.instructions), lower(?)) > 0 THEN 0.45 END AS score,"
          + " CASE WHEN strpos(lower(COALESCE(r.description, '')), lower(?)) > 0"
          + " THEN r.description WHEN strpos(lower(COALESCE(d.description, '')), lower(?)) > 0"
          + " THEN d.description WHEN strpos(lower(r.instructions), lower(?)) > 0"
          + " THEN r.instructions WHEN strpos(lower(d.name), lower(?)) > 0 THEN d.name END AS snippet_source"
          + " FROM recipe r JOIN dish d ON d.id = r.dish_id"
          + " WHERE r.deleted_at IS NULL AND d.deleted_at IS NULL"
          + " AND (strpos(lower(r.name), lower(?)) > 0"
          + " OR strpos(lower(COALESCE(d.name, '')), lower(?)) > 0"
          + " OR strpos(lower(COALESCE(r.description, '')), lower(?)) > 0"
          + " OR strpos(lower(r.instructions), lower(?)) > 0)";

  private final JdbcTemplate jdbcTemplate;

  public SearchPage search(
      String query,
      SearchType type,
      Instant from,
      Instant to,
      UUID categoryId,
      int page,
      int size) {
    List<Object> selectParameters = new ArrayList<>();
    List<String> selects = new ArrayList<>();
    if (type == null || type == SearchType.POST) {
      selects.add(
          withFilters(
              POST_SELECT,
              "p",
              "p.published_at",
              true,
              categoryId,
              from,
              to,
              selectParameters,
              query,
              7));
    }
    if (type == null || type == SearchType.VIDEO) {
      selects.add(
          withFilters(
              VIDEO_SELECT,
              "linked",
              "m.created_at",
              false,
              categoryId,
              from,
              to,
              selectParameters,
              query,
              8));
    }
    if (type == null || type == SearchType.RECIPE) {
      selects.add(
          withFilters(
              RECIPE_SELECT,
              "r",
              "r.created_at",
              false,
              categoryId,
              from,
              to,
              selectParameters,
              query,
              14));
    }
    String cte = "WITH results AS (" + String.join(" UNION ALL ", selects) + ") ";
    Long total =
        jdbcTemplate.queryForObject(
            cte + "SELECT COUNT(*) FROM results", Long.class, selectParameters.toArray());
    List<Object> pageParameters = new ArrayList<>(selectParameters);
    pageParameters.add(size);
    pageParameters.add((long) page * size);
    List<SearchRow> rows =
        jdbcTemplate.query(
            cte
                + "SELECT * FROM results ORDER BY score DESC, result_date DESC, id ASC LIMIT ? OFFSET ?",
            (rs, rowNum) -> mapRow(rs),
            pageParameters.toArray());
    return new SearchPage(rows, total == null ? 0 : total);
  }

  private String withFilters(
      String base,
      String alias,
      String dateColumn,
      boolean post,
      UUID categoryId,
      Instant from,
      Instant to,
      List<Object> parameters,
      String query,
      int queryCount) {
    StringBuilder sql = new StringBuilder(base);
    for (int i = 0; i < queryCount; i++) {
      parameters.add(query);
    }
    if (from != null) {
      sql.append(" AND ").append(dateColumn).append(" >= ?");
      parameters.add(OffsetDateTime.ofInstant(from, ZoneOffset.UTC));
    }
    if (to != null) {
      sql.append(" AND ").append(dateColumn).append(" <= ?");
      parameters.add(OffsetDateTime.ofInstant(to, ZoneOffset.UTC));
    }
    if (categoryId != null) {
      if (post) {
        sql.append(
            " AND EXISTS (SELECT 1 FROM post_category pc WHERE pc.post_id = p.id"
                + " AND pc.category_id = ?)");
      } else if (alias.equals("linked")) {
        sql.append(
            " AND EXISTS (SELECT 1 FROM post_media pcm JOIN post cp ON cp.id = pcm.post_id"
                + " JOIN post_category pcc ON pcc.post_id = cp.id WHERE pcm.media_id = m.id"
                + " AND cp.deleted_at IS NULL AND cp.status = 'published' AND pcc.category_id = ?)");
      } else {
        sql.append(
            " AND EXISTS (SELECT 1 FROM post_recipe pr JOIN post cp ON cp.id = pr.post_id"
                + " JOIN post_category pcc ON pcc.post_id = cp.id WHERE pr.recipe_id = r.id"
                + " AND cp.deleted_at IS NULL AND cp.status = 'published' AND pcc.category_id = ?)");
      }
      parameters.add(categoryId);
    }
    return sql.toString();
  }

  private SearchRow mapRow(ResultSet rs) throws SQLException {
    Timestamp timestamp = rs.getTimestamp("result_date");
    return new SearchRow(
        rs.getObject("id", UUID.class),
        SearchType.valueOf(rs.getString("type")),
        rs.getString("title"),
        rs.getString("summary"),
        rs.getString("thumbnail_url"),
        rs.getString("media_url"),
        rs.getObject("related_post_id", UUID.class),
        rs.getObject("owner_id", UUID.class),
        timestamp == null ? null : timestamp.toInstant(),
        rs.getDouble("score"),
        rs.getString("snippet_source"));
  }

  public record SearchPage(List<SearchRow> rows, long total) {}
}
