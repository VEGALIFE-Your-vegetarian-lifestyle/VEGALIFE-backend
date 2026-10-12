package com.vegalife.repository.post;

import com.vegalife.model.post.Media;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class MediaSpecifications {

  private MediaSpecifications() {}

  /**
   * Every non-deleted video upload, optionally narrowed by status, uploader, and createdAt range.
   * The MIME predicate keeps image rows out of the admin video list without a second table.
   */
  public static Specification<Media> allVideosWithFilters(
      Media.Status status, UUID userId, Instant createdFrom, Instant createdTo) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("deletedAt")));
      predicates.add(cb.like(root.get("mimeType"), "video/%"));

      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (userId != null) {
        predicates.add(cb.equal(root.get("uploadedBy").get("id"), userId));
      }
      if (createdFrom != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
      }
      if (createdTo != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
