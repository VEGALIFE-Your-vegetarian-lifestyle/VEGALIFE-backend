package com.vegalife.repository.post;

import com.vegalife.enums.post.PlaceType;
import com.vegalife.model.post.Location;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class LocationSpecifications {

  private LocationSpecifications() {}

  /**
   * Every non-deleted location, optionally narrowed by minimum rating, place type, and maximum
   * price level (issue #9). A location whose {@code rating} or {@code priceLevel} is unset never
   * satisfies a minRating/maxPriceLevel filter, since its value is unknown rather than qualifying.
   */
  public static Specification<Location> activeWithFilters(
      BigDecimal minRating, PlaceType placeType, Integer maxPriceLevel) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.isNull(root.get("deletedAt")));

      if (minRating != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), minRating));
      }
      if (placeType != null) {
        predicates.add(cb.equal(root.get("placeType"), placeType));
      }
      if (maxPriceLevel != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("priceLevel"), maxPriceLevel));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Every non-deleted location whose coordinates fall inside a lat/lng bounding box — a coarse,
   * index-friendly pre-filter over {@code idx_location_coords} (issue #10). The exact radius cut
   * and distance are computed afterward with the haversine formula, since a bounding box is a
   * rectangle, not a circle.
   */
  public static Specification<Location> activeWithinBoundingBox(
      BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng) {
    return (root, query, cb) ->
        cb.and(
            cb.isNull(root.get("deletedAt")),
            cb.between(root.get("latitude"), minLat, maxLat),
            cb.between(root.get("longitude"), minLng, maxLng));
  }
}
